package mx.budget.wear.presentation.complication

import android.content.ComponentName
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import kotlinx.coroutines.runBlocking
import mx.budget.wear.data.PhoneLink
import mx.budget.wear.data.WearCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprueba el dato que las dos complications entregan a una carátula.
 *
 * Por qué existe: el editor de las carátulas que trae Wear OS 5 en el emulador
 * no expone los huecos de complication por accesibilidad, y su selector vive
 * dentro de sysui sin ninguna actividad lanzable por `adb`, así que colocarlas
 * a mano no se puede automatizar. Esta prueba cubre lo que de verdad importa de
 * ese paso: que cada servicio responda el tipo declarado, con las cifras del
 * cache y con los textos correctos en los estados degradados. Lo que queda
 * fuera es puramente visual (ver la complication puesta en una esfera) y se
 * cierra con el Pixel Watch 4.
 *
 * El contexto del instrumentation es el de `mx.budget`, así que las
 * preferencias que escribe la prueba son las mismas que lee el servicio. Se
 * respalda y se restaura el cache para no dejar al reloj con datos inventados.
 */
@RunWith(AndroidJUnit4::class)
class ComplicationDataTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val prefs by lazy {
        context.getSharedPreferences(WearCache.PREFS, Context.MODE_PRIVATE)
    }

    private var respaldo: Map<String, Any?> = emptyMap()

    /**
     * `attachBaseContext` es `protected` en `ContextWrapper`, así que una
     * subclase puede dárselo al servicio sin arrancarlo de verdad. Con eso
     * `onComplicationRequest` lee el cache igual que en producción, y la prueba
     * se ahorra el AIDL del binder.
     */
    private class Disponible : DisponibleComplicationService() {
        fun conContexto(ctx: Context) = apply { attachBaseContext(ctx) }
    }

    private class ProximoPago : UpcomingPaymentComplicationService() {
        fun conContexto(ctx: Context) = apply { attachBaseContext(ctx) }
    }

    @Before
    fun guardarCache() {
        respaldo = prefs.all.toMap()
    }

    @After
    fun restaurarCache() {
        prefs.edit().clear().apply()
        prefs.edit().apply {
            respaldo.forEach { (k, v) ->
                when (v) {
                    is Float -> putFloat(k, v)
                    is Long -> putLong(k, v)
                    is Int -> putInt(k, v)
                    is Boolean -> putBoolean(k, v)
                    is String -> putString(k, v)
                }
            }
        }.apply()
    }

    private fun sembrar(
        balance: Float,
        total: Float,
        recibidoHaceMs: Long,
        upcomingJson: String = "[]",
    ) {
        prefs.edit()
            .putFloat(WearCache.K_BALANCE, balance)
            .putFloat(WearCache.K_BUDGET_TOTAL, total)
            .putString(WearCache.K_LABEL, "Q1 Septiembre 2026")
            .putString(WearCache.K_UPCOMING, upcomingJson)
            .putBoolean(PhoneLink.K_PHONE_REACHABLE, true)
            .putLong(WearCache.K_RECEIVED_AT, System.currentTimeMillis() - recibidoHaceMs)
            .apply()
    }

    private fun sinDatos() {
        prefs.edit().clear().putBoolean(PhoneLink.K_PHONE_REACHABLE, true).apply()
    }

    private fun pedir(tipo: ComplicationType) = ComplicationRequest(1, tipo)

    private fun disponible(tipo: ComplicationType) = runBlocking {
        Disponible().conContexto(context).onComplicationRequest(pedir(tipo))
    }

    private fun proximoPago(tipo: ComplicationType) = runBlocking {
        ProximoPago().conContexto(context).onComplicationRequest(pedir(tipo))
    }

    private fun texto(t: Any?): String = when (t) {
        null -> ""
        else -> (t as androidx.wear.watchface.complications.data.ComplicationText)
            .getTextAt(context.resources, java.time.Instant.now()).toString()
    }

    // ── Disponible ───────────────────────────────────────────────────────────

    @Test
    fun disponible_conDatosFrescos_llenaElAnilloConLoConsumido() {
        // Ingreso 75000, disponible 48911: consumido = 26089.
        sembrar(balance = 48_911f, total = 75_000f, recibidoHaceMs = 60_000)

        val data = disponible(ComplicationType.RANGED_VALUE) as RangedValueComplicationData

        assertEquals(0f, data.min, 0.01f)
        assertEquals(75_000f, data.max, 0.01f)
        assertEquals(26_089f, data.value, 0.01f)
        assertEquals("$49k", texto(data.text))
        assertEquals("Saldo", texto(data.title))
        assertTrue(texto(data.contentDescription).contains("48,911"))
    }

    @Test
    fun disponible_enShortText_daLaCifraCompacta() {
        sembrar(balance = 8_150f, total = 24_000f, recibidoHaceMs = 60_000)

        val data = disponible(ComplicationType.SHORT_TEXT) as ShortTextComplicationData

        assertEquals("$8.2k", texto(data.text))
        assertEquals("Saldo", texto(data.title))
    }

    @Test
    fun disponible_sinSnapshot_noPintaCeroPesos() {
        sinDatos()

        val data = disponible(ComplicationType.SHORT_TEXT) as ShortTextComplicationData

        // El "$0" del valor por defecto de la preferencia sería una mentira:
        // confundir "no sé" con "no queda nada".
        assertEquals("$--", texto(data.text))
        assertTrue(texto(data.contentDescription).contains("Todavía sin datos"))
    }

    @Test
    fun disponible_conSnapshotViejo_loAvisaEnElTitulo() {
        sembrar(
            balance = 48_911f,
            total = 75_000f,
            recibidoHaceMs = WearCache.STALE_AFTER_MS + 60_000,
        )

        val data = disponible(ComplicationType.SHORT_TEXT) as ShortTextComplicationData

        assertEquals("Viejo", texto(data.title))
        assertTrue(texto(data.contentDescription).contains("dato de hace"))
    }

    @Test
    fun disponible_sinQuincenaActiva_noDivideEntreCero() {
        sembrar(balance = 0f, total = 0f, recibidoHaceMs = 60_000)

        val data = disponible(ComplicationType.RANGED_VALUE) as RangedValueComplicationData

        assertEquals(1f, data.max, 0.01f)
        assertEquals(0f, data.value, 0.01f)
    }

    @Test
    fun disponible_conSobregiro_dejaElAnilloLleno() {
        sembrar(balance = -1_200f, total = 75_000f, recibidoHaceMs = 60_000)

        val data = disponible(ComplicationType.RANGED_VALUE) as RangedValueComplicationData

        assertEquals(75_000f, data.value, 0.01f)
        assertEquals("-$1.2k", texto(data.text))
    }

    @Test
    fun disponible_rechazaLosTiposQueNoDeclara() {
        sembrar(balance = 48_911f, total = 75_000f, recibidoHaceMs = 60_000)

        assertNull(disponible(ComplicationType.LONG_TEXT))
        assertNull(disponible(ComplicationType.MONOCHROMATIC_IMAGE))
    }

    @Test
    fun disponible_laVistaPreviaNoDependeDelCache() {
        sinDatos()

        val preview = Disponible().conContexto(context)
            .getPreviewData(ComplicationType.RANGED_VALUE) as RangedValueComplicationData

        // En frío, el editor de la carátula la pide antes de que exista
        // snapshot; si saliera vacía, la complication parecería rota.
        assertEquals("$8.2k", texto(preview.text))
        assertEquals(24_000f, preview.max, 0.01f)
    }

    // ── Próximo pago ─────────────────────────────────────────────────────────

    @Test
    fun proximoPago_eligeElVencimientoMasCercano() {
        val hoy = System.currentTimeMillis()
        val json = """
            [{"concept":"Colegiatura","amount":1200,"dueDate":${hoy + 3 * DIA_MS}},
             {"concept":"Sears","amount":1483,"dueDate":${hoy + 1 * DIA_MS}}]
        """.trimIndent()
        sembrar(balance = 48_911f, total = 75_000f, recibidoHaceMs = 60_000, upcomingJson = json)

        val data = proximoPago(ComplicationType.SHORT_TEXT) as ShortTextComplicationData

        assertEquals("$1.5k", texto(data.text))
        assertEquals("mañana", texto(data.title))
        assertTrue(texto(data.contentDescription).contains("Sears"))
    }

    @Test
    fun proximoPago_sinPagos_loDiceSinInventarUnMonto() {
        sembrar(balance = 48_911f, total = 75_000f, recibidoHaceMs = 60_000, upcomingJson = "[]")

        val data = proximoPago(ComplicationType.SHORT_TEXT) as ShortTextComplicationData

        assertEquals("Sin", texto(data.text))
        assertEquals("Pagos", texto(data.title))
    }

    @Test
    fun proximoPago_conSnapshotViejo_conservaElVencimiento() {
        val hoy = System.currentTimeMillis()
        val json = """[{"concept":"Sears","amount":1483,"dueDate":${hoy + 1 * DIA_MS}}]"""
        sembrar(
            balance = 48_911f,
            total = 75_000f,
            recibidoHaceMs = WearCache.STALE_AFTER_MS + 60_000,
            upcomingJson = json,
        )

        val data = proximoPago(ComplicationType.SHORT_TEXT) as ShortTextComplicationData

        // A diferencia del saldo, la fecha es absoluta y la etiqueta se
        // recalcula al dibujar, así que sigue siendo cierta: el título NO se
        // roba para avisar de la vejez, que solo se dice en la descripción.
        assertEquals("mañana", texto(data.title))
        assertTrue(texto(data.contentDescription).contains("dato de hace"))
    }

    @Test
    fun proximoPago_rechazaLosTiposQueNoDeclara() {
        sembrar(balance = 48_911f, total = 75_000f, recibidoHaceMs = 60_000)

        assertNull(proximoPago(ComplicationType.RANGED_VALUE))
        assertNull(proximoPago(ComplicationType.LONG_TEXT))
    }

    private companion object {
        const val DIA_MS = 24L * 60 * 60 * 1000
    }
}
