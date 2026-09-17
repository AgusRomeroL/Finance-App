package mx.budget.ai.golden

import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mx.budget.ai.dispatch.AliasResolver
import mx.budget.ai.dispatch.HeuristicIntentGuesser
import mx.budget.ai.dispatch.IntentDispatcher
import mx.budget.ai.domain.AssistantResponse
import mx.budget.ai.rag.QuestionClassifier
import mx.budget.data.local.entity.CategoryEntity
import mx.budget.data.local.entity.InstallmentPlanEntity
import mx.budget.data.local.entity.MemberEntity
import mx.budget.data.local.entity.PaymentMethodEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.DriverManager

/**
 * Golden suite del asistente (assets/ai/golden): los casos canonicos que la
 * Fase 4 dejo como datos y esta fase convierte en pruebas.
 *
 * Sin LLM en la JVM, lo que se prueba es el camino DETERMINISTA que cada
 * archivo declara en su contrato:
 *  - intents.json: la columna `heuristic` es lo que HeuristicIntentGuesser tiene
 *    que producir para cada pregunta, con alias resueltos contra la semilla real
 *    (app/src/main/assets/budget_database.db, leida con JDBC, sin Room). Cuando la
 *    heuristica coincide con el intent esperado, los argumentos se comparan
 *    modulo AliasResolver.
 *  - open_analysis.json: QuestionClassifier.isOpenAnalysis.
 *  - out_of_scope.json: IntentDispatcher.isWithinScope con la misma semilla.
 *
 * Si un caso cambia de columna `heuristic` a proposito, se edita el JSON: es el
 * contrato, no la prueba.
 */
class GoldenSuiteTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun load(name: String): JsonObject {
        val f = File("src/main/assets/ai/golden/$name")
        assertTrue("falta ${f.path}", f.isFile)
        return json.parseToJsonElement(f.readText(Charsets.UTF_8)).jsonObject
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    /** Entidades del hogar sembrado, tal como las veria el resolver en la app. */
    private val resolver: AliasResolver by lazy {
        val db = File("src/main/assets/budget_database.db")
        assertTrue("falta la semilla ${db.path}", db.isFile)
        DriverManager.getConnection("jdbc:sqlite:${db.path}").use { c ->
            val members = mutableListOf<MemberEntity>()
            c.createStatement().executeQuery("SELECT id, household_id, display_name, short_aliases, role, is_active FROM member").use { r ->
                while (r.next()) members += MemberEntity(
                    id = r.getString(1), householdId = r.getString(2), displayName = r.getString(3),
                    shortAliases = r.getString(4) ?: "[]", role = r.getString(5), isActive = r.getInt(6) != 0,
                )
            }
            val categories = mutableListOf<CategoryEntity>()
            c.createStatement().executeQuery("SELECT id, household_id, code, display_name, kind FROM category").use { r ->
                while (r.next()) categories += CategoryEntity(
                    id = r.getString(1), householdId = r.getString(2), code = r.getString(3),
                    displayName = r.getString(4), kind = r.getString(5),
                )
            }
            val wallets = mutableListOf<PaymentMethodEntity>()
            c.createStatement().executeQuery("SELECT id, household_id, display_name, kind FROM payment_method").use { r ->
                while (r.next()) wallets += PaymentMethodEntity(
                    id = r.getString(1), householdId = r.getString(2), displayName = r.getString(3), kind = r.getString(4),
                )
            }
            val plans = mutableListOf<InstallmentPlanEntity>()
            c.createStatement().executeQuery(
                "SELECT id, household_id, display_name, principal_mxn, total_installments, installment_amount_mxn, start_date FROM installment_plan",
            ).use { r ->
                while (r.next()) plans += InstallmentPlanEntity(
                    id = r.getString(1), householdId = r.getString(2), displayName = r.getString(3),
                    principalMxn = r.getDouble(4), totalInstallments = r.getInt(5),
                    installmentAmountMxn = r.getDouble(6), startDate = r.getString(7),
                )
            }
            assertTrue(members.isNotEmpty() && categories.isNotEmpty() && wallets.isNotEmpty() && plans.isNotEmpty())
            AliasResolver(members, categories, wallets, plans)
        }
    }

    @Test
    fun `intents - cada caso declara un intent valido y un id unico`() {
        val cases = load("intents.json")["cases"]!!.jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 20)
        assertEquals(cases.size, cases.map { it.str("id") }.distinct().size)
        val valid = AssistantResponse.Intent.entries.map { it.name }.toSet()
        for (c in cases) {
            assertTrue("${c.str("id")}: intent ${c.str("expected_intent")}", c.str("expected_intent") in valid)
            val h = c.str("heuristic")
            assertTrue("${c.str("id")}: heuristica $h", h == null || h in valid)
        }
    }

    @Test
    fun `intents - la heuristica produce exactamente lo que el contrato declara`() {
        val cases = load("intents.json")["cases"]!!.jsonArray.map { it.jsonObject }
        val failures = mutableListOf<String>()
        for (c in cases) {
            val id = c.str("id")
            val question = c.str("question")!!
            val expectedHeuristic = c.str("heuristic")
            val guess = HeuristicIntentGuesser.guess(question, resolver)
            val actual = guess?.intent?.name
            if (actual != expectedHeuristic) {
                failures += "$id \"$question\": heuristica esperada $expectedHeuristic, salio $actual"
                continue
            }
            // Argumentos: solo cuando la heuristica coincide con el intent esperado.
            if (guess != null && actual == c.str("expected_intent")) {
                val expectedArgs = c["expected_args"]?.jsonObject ?: JsonObject(emptyMap())
                expectedArgs["category_code"]?.jsonPrimitive?.content?.let { want ->
                    val code = resolver.resolveCategory(want)?.code
                    if (code != guess.args.category_code) failures += "$id: categoria esperada $want ($code), salio ${guess.args.category_code}"
                }
                expectedArgs["member_alias"]?.jsonPrimitive?.content?.let { want ->
                    val name = resolver.resolveMember(want)?.displayName
                    if (name != guess.args.member_alias) failures += "$id: miembro esperado $want ($name), salio ${guess.args.member_alias}"
                }
                expectedArgs["wallet_name"]?.jsonPrimitive?.content?.let { want ->
                    val name = resolver.resolveWallet(want)?.displayName
                    if (name != guess.args.wallet_name) failures += "$id: cuenta esperada $want ($name), salio ${guess.args.wallet_name}"
                }
                expectedArgs["plan_name"]?.jsonPrimitive?.content?.let { want ->
                    val name = resolver.resolveInstallmentPlan(want)?.displayName
                    if (name != guess.args.plan_name) failures += "$id: plan esperado $want ($name), salio ${guess.args.plan_name}"
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `intents - las entidades que citan los casos existen en la semilla`() {
        val cases = load("intents.json")["cases"]!!.jsonArray.map { it.jsonObject }
        val failures = mutableListOf<String>()
        for (c in cases) {
            val args = c["expected_args"]?.jsonObject ?: continue
            args["category_code"]?.jsonPrimitive?.content?.let { if (resolver.resolveCategory(it) == null) failures += "${c.str("id")}: categoria $it" }
            args["member_alias"]?.jsonPrimitive?.content?.let { if (resolver.resolveMember(it) == null) failures += "${c.str("id")}: miembro $it" }
            args["wallet_name"]?.jsonPrimitive?.content?.let { if (resolver.resolveWallet(it) == null) failures += "${c.str("id")}: cuenta $it" }
            args["plan_name"]?.jsonPrimitive?.content?.let { if (resolver.resolveInstallmentPlan(it) == null) failures += "${c.str("id")}: plan $it" }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `open_analysis - el clasificador separa analisis abierto de pregunta puntual`() {
        val cases = load("open_analysis.json")["cases"]!!.jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 10)
        val failures = cases.filter { c ->
            QuestionClassifier.isOpenAnalysis(c.str("question")!!) != c["expected_open"]!!.jsonPrimitive.boolean
        }.map { "${it.str("id")} \"${it.str("question")}\" esperaba ${it["expected_open"]}" }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `out_of_scope - el despacho distingue lo que es del presupuesto`() {
        val cases = load("out_of_scope.json")["cases"]!!.jsonArray.map { it.jsonObject }
        assertTrue(cases.size >= 10)
        val dispatcher = IntentDispatcher(
            resolverProvider = { resolver },
            analyticsRepository = mockk(relaxed = true),
            expenseRepository = mockk(relaxed = true),
            incomeRepository = mockk(relaxed = true),
            walletRepository = mockk(relaxed = true),
            installmentRepository = mockk(relaxed = true),
            quincenaRepository = mockk(relaxed = true),
            memberRepository = mockk(relaxed = true),
            householdId = "default_household",
        )
        val failures = runBlocking {
            cases.filter { c ->
                dispatcher.isWithinScope(c.str("question")!!) != c["expected_within_scope"]!!.jsonPrimitive.boolean
            }
        }.map { "${it.str("id")} \"${it.str("question")}\" esperaba ${it["expected_within_scope"]}" }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
