package mx.budget.data.local

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.Calendar
import java.util.UUID

/**
 * Callback de Room que siembra los datos maestros en `onCreate`.
 *
 * Replica las constantes del ETL Python (excel_to_room_etl.py §1)
 * directamente en SQL raw, porque los DAOs no están disponibles
 * durante la creación de la BD.
 *
 * Los IDs son determinísticos via [did] para estabilidad entre
 * reinstalaciones.
 */
class DatabaseSeedCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        db.beginTransaction()
        try {
            seedHousehold(db)
            seedMembers(db)
            seedCategories(db)
            seedPaymentMethods(db)
            seedActiveQuincena(db)
            seedIncomeSources(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ── Utilidades ──────────────────────────────────────────────────────────

    /** UUID determinístico compatible con el ETL Python. */
    private fun did(key: String): String =
        UUID.nameUUIDFromBytes(key.toByteArray(Charsets.UTF_8)).toString()

    private val householdId = "default_household"
    private val nowEpoch = System.currentTimeMillis()

    // ── Household ───────────────────────────────────────────────────────────

    private fun seedHousehold(db: SupportSQLiteDatabase) {
        db.execSQL(
            """INSERT INTO household (id, name, currency, timezone, quincena_anchor, created_at)
               VALUES (?, 'Familia', 'MXN', 'America/Mexico_City', 'CALENDAR', ?)""",
            arrayOf(householdId, nowEpoch)
        )
    }

    // ── Members ─────────────────────────────────────────────────────────────

    private data class MemberSeed(
        val key: String,
        val displayName: String,
        val role: String,
        val aliases: List<String>,
        val defaultIncome: Double?
    )

    private val members = listOf(
        MemberSeed("benjamin", "Benjamín", "PAYER_ADULT",
            listOf("benji", "benjamin", "benjamín"), 45_000.0),
        MemberSeed("norma", "Norma", "PAYER_ADULT",
            listOf("norma"), 60_000.0),
        MemberSeed("pau", "Pau", "BENEFICIARY_DEPENDENT",
            listOf("pau", "paulina"), null),
        MemberSeed("david", "David", "BENEFICIARY_DEPENDENT",
            listOf("david", "dav", "dave"), null),
        MemberSeed("agustin", "Agustín", "BENEFICIARY_DEPENDENT",
            listOf("agus", "agustin", "agustín"), null),
        MemberSeed("santiago", "Santiago", "BENEFICIARY_DEPENDENT",
            listOf("santi", "santiago"), null),
        MemberSeed("omar", "Omar", "EXTERNAL_CREDITOR",
            listOf("omar", "prestamo omar"), null),
        MemberSeed("jaudiel", "Jaudiel", "EXTERNAL_DEBTOR",
            listOf("jaudiel"), null),
        MemberSeed("araceli", "Araceli", "EXTERNAL_SERVICE",
            listOf("araceli"), null),
    )

    private fun memberId(key: String) = did("member:$key")

    private fun seedMembers(db: SupportSQLiteDatabase) {
        members.forEach { m ->
            val aliasesJson = m.aliases.joinToString(",", "[", "]") { "\"$it\"" }
            db.execSQL(
                """INSERT INTO member (id, household_id, display_name, short_aliases, role, is_active, default_income_mxn, meta)
                   VALUES (?, ?, ?, ?, ?, 1, ?, '{}')""",
                arrayOf(
                    memberId(m.key), householdId, m.displayName,
                    aliasesJson, m.role, m.defaultIncome
                )
            )
        }
    }

    // ── Categories ──────────────────────────────────────────────────────────

    private data class CatSeed(
        val code: String,
        val displayName: String,
        val parentCode: String?,
        val kind: String,
        val budgetDefault: Double?,
        val sortOrder: Int = 0
    )

    private fun catId(code: String) = did("category:$code")

    private val categories = listOf(
        // Raíces
        CatSeed("HOUSING", "Vivienda", null, "EXPENSE_FIXED", null, 10),
        CatSeed("TRANSPORTATION", "Transporte", null, "EXPENSE_VARIABLE", null, 20),
        CatSeed("SEGUROS_MEDICOS", "Seguros Médicos", null, "EXPENSE_FIXED", null, 30),
        CatSeed("FOOD", "Alimentación", null, "EXPENSE_VARIABLE", null, 40),
        CatSeed("PETS", "Mascotas", null, "EXPENSE_VARIABLE", null, 50),
        CatSeed("ENTERTAINMENT", "Entretenimiento", null, "EXPENSE_VARIABLE", null, 60),
        CatSeed("LOANS", "Tarjetas y préstamos", null, "EXPENSE_INSTALLMENT", null, 70),
        CatSeed("TRANSFERENCIAS_FAMILIARES", "Transferencias familiares", null, "TRANSFER_INTRA_HOUSEHOLD", null, 80),
        CatSeed("SAVINGS", "Ahorros e inversiones", null, "SAVINGS", null, 90),
        CatSeed("GIFTS", "Regalos y donaciones", null, "EXPENSE_VARIABLE", null, 100),
        CatSeed("LEGAL", "Legal", null, "EXPENSE_VARIABLE", null, 110),
        CatSeed("PERSONAL_CARE", "Cuidado personal", null, "EXPENSE_VARIABLE", null, 120),
        CatSeed("SERVICIOS_EXTERNOS", "Servicios externos", null, "EXPENSE_VARIABLE", null, 130),
        CatSeed("OTHER", "Otros", null, "EXPENSE_VARIABLE", null, 200),
        CatSeed("INGRESOS", "Ingresos", null, "INCOME", null, 5),
        CatSeed("PRESTAMOS_OTORGADOS", "Préstamos otorgados", null, "LOAN_RECEIVABLE", null, 140),

        // Hojas — Vivienda
        CatSeed("HOUSING.HIPOTECA", "Hipoteca", "HOUSING", "EXPENSE_FIXED", 3750.0),
        CatSeed("HOUSING.INTERNET", "Internet", "HOUSING", "EXPENSE_FIXED", 899.0),
        CatSeed("HOUSING.ELECTRICIDAD", "Electricidad", "HOUSING", "EXPENSE_VARIABLE", 1000.0),
        CatSeed("HOUSING.AGUA", "Agua", "HOUSING", "EXPENSE_VARIABLE", null),
        CatSeed("HOUSING.TELEFONO", "Teléfono", "HOUSING", "EXPENSE_FIXED", null),
        CatSeed("HOUSING.FRACCIONAMIENTO", "Fraccionamiento", "HOUSING", "EXPENSE_FIXED", null),

        // Hojas — Transporte
        CatSeed("TRANSPORTATION.GASOLINA", "Gasolina", "TRANSPORTATION", "EXPENSE_VARIABLE", 2600.0),
        CatSeed("TRANSPORTATION.INSURANCE", "Seguro vehículo", "TRANSPORTATION", "EXPENSE_FIXED", null),
        CatSeed("TRANSPORTATION.LICENSING", "Licencias", "TRANSPORTATION", "EXPENSE_FIXED", null),
        CatSeed("TRANSPORTATION.MAINTENANCE", "Mantenimiento", "TRANSPORTATION", "EXPENSE_VARIABLE", null),

        // Hojas — Entretenimiento
        CatSeed("ENTERTAINMENT.NETFLIX", "Netflix", "ENTERTAINMENT", "EXPENSE_FIXED", 329.0),
        CatSeed("ENTERTAINMENT.HBO", "HBO", "ENTERTAINMENT", "EXPENSE_FIXED", 85.0),
        CatSeed("ENTERTAINMENT.PRIME", "Amazon Prime", "ENTERTAINMENT", "EXPENSE_FIXED", 99.0),
        CatSeed("ENTERTAINMENT.DISNEY", "Disney+ y Star", "ENTERTAINMENT", "EXPENSE_FIXED", null),
        CatSeed("ENTERTAINMENT.SPOTIFY", "Spotify", "ENTERTAINMENT", "EXPENSE_FIXED", 179.0),
        CatSeed("ENTERTAINMENT.HAWAIANO", "Hawaiano", "ENTERTAINMENT", "EXPENSE_VARIABLE", null),
        CatSeed("ENTERTAINMENT.DIVERSION", "Diversión", "ENTERTAINMENT", "EXPENSE_VARIABLE", null),

        // Hojas — Alimentación
        CatSeed("FOOD.COMIDA", "Comida", "FOOD", "EXPENSE_VARIABLE", 3000.0),
        CatSeed("FOOD.DESPENSA", "Despensa", "FOOD", "EXPENSE_VARIABLE", 1500.0),
        CatSeed("FOOD.LIMPIEZA", "Limpieza", "FOOD", "EXPENSE_VARIABLE", null),

        // Hojas — Mascotas
        CatSeed("PETS.COMIDA", "Comida gatas", "PETS", "EXPENSE_FIXED", 980.0),
        CatSeed("PETS.GROOMING", "Grooming", "PETS", "EXPENSE_VARIABLE", null),
        CatSeed("PETS.VETERINARIO", "Veterinario", "PETS", "EXPENSE_VARIABLE", null),

        // Hojas — Tarjetas y préstamos
        CatSeed("LOANS.COPPEL", "Coppel", "LOANS", "EXPENSE_INSTALLMENT", null),
        CatSeed("LOANS.LIVERPOOL", "Liverpool", "LOANS", "EXPENSE_INSTALLMENT", null),
        CatSeed("LOANS.SEARS", "Sears", "LOANS", "EXPENSE_INSTALLMENT", null),
        CatSeed("LOANS.WALMART", "Walmart", "LOANS", "EXPENSE_INSTALLMENT", null),
        CatSeed("LOANS.BANAMEX_CC", "Banamex Clásica", "LOANS", "EXPENSE_INSTALLMENT", null),
        CatSeed("LOANS.MERCADO_LIBRE", "Mercado Libre", "LOANS", "EXPENSE_INSTALLMENT", null),
        CatSeed("LOANS.MERCADO_PAGO", "Mercado Pago", "LOANS", "EXPENSE_INSTALLMENT", null),
        CatSeed("LOANS.OMAR", "Préstamo Omar", "LOANS", "EXPENSE_INSTALLMENT", null),

        // Hojas — Seguros
        CatSeed("SEGUROS_MEDICOS.BENJI", "Seguro Benji", "SEGUROS_MEDICOS", "EXPENSE_FIXED", null),
        CatSeed("SEGUROS_MEDICOS.NORMA", "Seguro Norma", "SEGUROS_MEDICOS", "EXPENSE_FIXED", null),
        CatSeed("SEGUROS_MEDICOS.HIJOS", "Seguro hijos", "SEGUROS_MEDICOS", "EXPENSE_FIXED", null),
        CatSeed("SEGUROS_MEDICOS.SANTI", "Seguro Santi", "SEGUROS_MEDICOS", "EXPENSE_FIXED", null),

        // Hojas — Transferencias familiares
        CatSeed("TRANSFERENCIAS_FAMILIARES.DAVID", "Transferencia David", "TRANSFERENCIAS_FAMILIARES", "TRANSFER_INTRA_HOUSEHOLD", null),
        CatSeed("TRANSFERENCIAS_FAMILIARES.PAU", "Transferencia Pau", "TRANSFERENCIAS_FAMILIARES", "TRANSFER_INTRA_HOUSEHOLD", null),
        CatSeed("TRANSFERENCIAS_FAMILIARES.SANTIAGO", "Transferencia Santi", "TRANSFERENCIAS_FAMILIARES", "TRANSFER_INTRA_HOUSEHOLD", null),
        CatSeed("TRANSFERENCIAS_FAMILIARES.COCHE", "Transferencia coche", "TRANSFERENCIAS_FAMILIARES", "TRANSFER_INTRA_HOUSEHOLD", null),
        CatSeed("TRANSFERENCIAS_FAMILIARES.INSCRIPCIONES", "Inscripciones", "TRANSFERENCIAS_FAMILIARES", "TRANSFER_INTRA_HOUSEHOLD", null),

        // Hojas — Ahorros
        CatSeed("SAVINGS.EMPRESA", "Ahorro Empresa", "SAVINGS", "SAVINGS", null),
        CatSeed("SAVINGS.TARJETA", "Tarjeta de ahorro", "SAVINGS", "SAVINGS", null),
        CatSeed("SAVINGS.RETIREMENT", "Retiro", "SAVINGS", "SAVINGS", null),
        CatSeed("SAVINGS.INVESTMENT", "Inversión", "SAVINGS", "SAVINGS", null),
        CatSeed("SAVINGS.EFECTIVO", "Ahorro efectivo", "SAVINGS", "SAVINGS", null),

        // Hojas — Servicios externos
        CatSeed("SERVICIOS_EXTERNOS.ARACELI", "Araceli", "SERVICIOS_EXTERNOS", "EXPENSE_FIXED", null),
        CatSeed("SERVICIOS_EXTERNOS.PSICOLOGA", "Psicóloga", "SERVICIOS_EXTERNOS", "EXPENSE_FIXED", null),

        // Hojas — Ingresos
        CatSeed("INGRESOS.SUELDO", "Sueldo", "INGRESOS", "INCOME", null),
    )

    private fun seedCategories(db: SupportSQLiteDatabase) {
        categories.forEach { c ->
            val parentId = c.parentCode?.let { catId(it) }
            db.execSQL(
                """INSERT INTO category (id, household_id, parent_id, code, display_name, icon, color_hex, kind, budget_default_mxn, sort_order)
                   VALUES (?, ?, ?, ?, ?, NULL, NULL, ?, ?, ?)""",
                arrayOf(
                    catId(c.code), householdId, parentId,
                    c.code, c.displayName, c.kind,
                    c.budgetDefault, c.sortOrder
                )
            )
        }
    }

    // ── Payment Methods ─────────────────────────────────────────────────────

    private data class WalletSeed(
        val key: String,
        val displayName: String,
        val kind: String,
        val issuer: String?,
        val ownerKey: String?
    )

    private fun walletId(key: String) = did("payment_method:$key")

    private val wallets = listOf(
        WalletSeed("banamex_deb", "Banamex Débito", "DEBIT_ACCOUNT", "Citibanamex", "norma"),
        WalletSeed("bbva", "BBVA", "DEBIT_ACCOUNT", "BBVA México", "norma"),
        WalletSeed("banamex_cc", "Banamex Clásica", "CREDIT_CARD", "Citibanamex", "norma"),
        WalletSeed("mercado_pago", "Mercado Pago", "DIGITAL_WALLET", "Mercado Pago", null),
        WalletSeed("mercado_libre", "Mercado Libre BNPL", "BNPL_INSTALLMENT", "Mercado Libre", null),
        WalletSeed("coppel", "Coppel", "DEPARTMENT_STORE_CARD", "Coppel", null),
        WalletSeed("liverpool", "Liverpool", "DEPARTMENT_STORE_CARD", "Liverpool", null),
        WalletSeed("sears", "Sears", "DEPARTMENT_STORE_CARD", "Sears", null),
        WalletSeed("walmart", "Walmart", "DEPARTMENT_STORE_CARD", "Walmart", null),
        WalletSeed("klar", "Klar", "DIGITAL_WALLET", "Klar", null),
        WalletSeed("efectivo", "Efectivo", "CASH", null, null),
        WalletSeed("ahorro_empresa", "Ahorro Empresa", "EMPLOYER_SAVINGS_FUND", null, "benjamin"),
    )

    private fun seedPaymentMethods(db: SupportSQLiteDatabase) {
        wallets.forEach { w ->
            val ownerId = w.ownerKey?.let { memberId(it) }
            db.execSQL(
                """INSERT INTO payment_method (id, household_id, display_name, kind, issuer, last4, cutoff_day, due_day, credit_limit_mxn, current_balance_mxn, interest_apr, owner_member_id, is_active)
                   VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, 0.0, NULL, ?, 1)""",
                arrayOf(
                    walletId(w.key), householdId, w.displayName,
                    w.kind, w.issuer, ownerId
                )
            )
        }
    }

    // ── Active Quincena ─────────────────────────────────────────────────────

    private lateinit var activeQuincenaId: String

    private fun seedActiveQuincena(db: SupportSQLiteDatabase) {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-based
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val half = if (day <= 15) "FIRST" else "SECOND"
        val paddedMonth = month.toString().padStart(2, '0')
        val startDay = if (half == "FIRST") "01" else "16"
        val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val endDay = if (half == "FIRST") "15" else lastDay.toString().padStart(2, '0')

        val startDate = "$year-$paddedMonth-$startDay"
        val endDate = "$year-$paddedMonth-$endDay"

        val monthNames = listOf(
            "", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
            "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
        )
        val halfLabel = if (half == "FIRST") "Q1" else "Q2"
        val label = "$halfLabel ${monthNames[month]} $year"

        activeQuincenaId = did("quincena:$year-$paddedMonth-$half")

        // Ingreso proyectado: solo Norma ($60k) por defecto — editable por el usuario
        val projectedIncome = 60_000.0

        db.execSQL(
            """INSERT INTO quincena (id, household_id, year, month, half, start_date, end_date, label, projected_income_mxn, projected_expenses_mxn, actual_income_mxn, actual_expenses_mxn, status, closed_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0.0, 0.0, 0.0, 'ACTIVE', NULL)""",
            arrayOf(
                activeQuincenaId, householdId, year, month, half,
                startDate, endDate, label, projectedIncome
            )
        )
    }

    // ── Income Sources ──────────────────────────────────────────────────────

    private fun seedIncomeSources(db: SupportSQLiteDatabase) {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val paddedMonth = month.toString().padStart(2, '0')
        val expectedDate = "$year-$paddedMonth-15"

        // Norma — ingreso activo (POSTED)
        db.execSQL(
            """INSERT INTO income_source (id, household_id, quincena_id, member_id, label, amount_mxn, cadence, expected_date, payment_method_id, status, created_at)
               VALUES (?, ?, ?, ?, 'Sueldo quincenal', 60000.0, 'QUINCENAL', ?, ?, 'POSTED', ?)""",
            arrayOf(
                did("income:norma:${activeQuincenaId}"),
                householdId, activeQuincenaId, memberId("norma"),
                expectedDate, walletId("banamex_deb"), nowEpoch
            )
        )

        // Benjamín — ingreso PLANNED ($0 para que el usuario lo ajuste)
        db.execSQL(
            """INSERT INTO income_source (id, household_id, quincena_id, member_id, label, amount_mxn, cadence, expected_date, payment_method_id, status, created_at)
               VALUES (?, ?, ?, ?, 'Sueldo quincenal', 0.0, 'QUINCENAL', ?, NULL, 'PLANNED', ?)""",
            arrayOf(
                did("income:benjamin:${activeQuincenaId}"),
                householdId, activeQuincenaId, memberId("benjamin"),
                expectedDate, nowEpoch
            )
        )
    }
}
