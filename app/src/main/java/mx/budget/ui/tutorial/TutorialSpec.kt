package mx.budget.ui.tutorial

import mx.budget.ui.navigation.BudgetDestinations

/** Dónde colocar el globo respecto al elemento resaltado. */
enum class CalloutAnchor { Auto, Above, Below }

/**
 * Un paso del tour: qué elemento resaltar, en qué pantalla, y qué explicar.
 *
 * @property key                  sección a iluminar (ver [TutorialKey]).
 * @property route                destino `BudgetDestinations.*` que contiene el target;
 *                                el controller navega ahí antes de resaltar.
 * @property title                título del globo.
 * @property body                 explicación breve (qué es y cómo se usa).
 * @property calloutAnchor        preferencia de posición del globo (default [CalloutAnchor.Auto]).
 * @property requiresCaptureSheet `true` si el target vive dentro del `CaptureBottomSheet`
 *                                (lo dibuja el overlay hospedado en la hoja, no el principal).
 * @property allowTargetInteraction si `true`, el scrim deja pasar toques al elemento real.
 */
data class TutorialStep(
    val key: TutorialKey,
    val route: String,
    val title: String,
    val body: String,
    val calloutAnchor: CalloutAnchor = CalloutAnchor.Auto,
    val requiresCaptureSheet: Boolean = false,
    val allowTargetInteraction: Boolean = false,
)

/**
 * Guion del tutorial guiado. **El orden de la lista ES el orden del tour.**
 *
 * Cobertura completa (decisión de producto): Dashboard → Captura → Calendario → Cuentas →
 * Analíticas → Libro Mayor. Ver `TUTORIAL.md` para la tabla de mapeo y la regla de mantenimiento.
 */
object TutorialSpec {

    val steps: List<TutorialStep> = listOf(
        // ── Dashboard ──────────────────────────────────────────────────────────
        TutorialStep(
            key = TutorialKey.DASH_HERO_KPI,
            route = BudgetDestinations.DASHBOARD,
            title = "Disponible para gastar",
            body = "Este es el corazón de tu quincena: lo que te queda tras ingresos y gastos. " +
                "Toca la barra de ritmo para ver si vas adelantado o atrasado en el gasto.",
        ),
        TutorialStep(
            key = TutorialKey.DASH_MEMBER_BARS,
            route = BudgetDestinations.DASHBOARD,
            title = "¿Quién gasta y quién paga?",
            body = "Cada barra es un miembro del hogar. Usa el interruptor Beneficiario/Pagador " +
                "para alternar entre quién consume el gasto y quién lo pagó.",
        ),
        TutorialStep(
            key = TutorialKey.DASH_SUGGESTIONS,
            route = BudgetDestinations.DASHBOARD,
            title = "Sugerencias inteligentes",
            body = "Aquí aparecen capturas detectadas y recomendaciones. Tócalas para revisarlas " +
                "y confirmarlas en segundos.",
        ),
        TutorialStep(
            key = TutorialKey.DASH_ACTION_BAR,
            route = BudgetDestinations.DASHBOARD,
            title = "Registrar un gasto",
            body = "El botón + abre la captura rápida. También puedes buscar o dictar por voz " +
                "desde esta barra.",
        ),
        TutorialStep(
            key = TutorialKey.DASH_NAV,
            route = BudgetDestinations.DASHBOARD,
            title = "Navegación",
            body = "Muévete entre Inicio, Calendario, Cuentas, Analíticas y Perfil desde aquí. " +
                "Te acompañaré por cada sección.",
        ),

        // ── Captura (hoja modal) ─────────────────────────────────────────────────
        TutorialStep(
            key = TutorialKey.CAP_KIND_TOGGLE,
            route = BudgetDestinations.DASHBOARD,
            title = "Gasto o ingreso",
            body = "Empieza eligiendo si registras un gasto o un ingreso. El resto de la hoja se " +
                "adapta a lo que elijas.",
            requiresCaptureSheet = true,
        ),
        TutorialStep(
            key = TutorialKey.CAP_AMOUNT_KEYPAD,
            route = BudgetDestinations.DASHBOARD,
            title = "Monto y concepto",
            body = "Teclea el monto y escribe un concepto corto. El teclado numérico está pensado " +
                "para capturar sin soltar el teléfono.",
            requiresCaptureSheet = true,
        ),
        TutorialStep(
            key = TutorialKey.CAP_CATEGORY,
            route = BudgetDestinations.DASHBOARD,
            title = "Categoría",
            body = "Elige de las recientes, busca, o crea una nueva. Clasificar bien alimenta tus " +
                "analíticas y presupuestos.",
            requiresCaptureSheet = true,
        ),
        TutorialStep(
            key = TutorialKey.CAP_ATTRIBUTION,
            route = BudgetDestinations.DASHBOARD,
            title = "Beneficia a / Pagó",
            body = "Reparte el gasto por porcentaje: a quién beneficia y quién lo pagó. Los ± ajustan " +
                "en pasos de 5% y \"Todos\" reparte en partes iguales.",
            requiresCaptureSheet = true,
        ),

        // ── Calendario ────────────────────────────────────────────────────────────
        TutorialStep(
            key = TutorialKey.CAL_MONTH_GRID,
            route = BudgetDestinations.CALENDAR,
            title = "Tu mes de un vistazo",
            body = "Los días con pagos planeados llevan un marcador. Toca un día para filtrar los " +
                "movimientos de esa fecha.",
        ),
        TutorialStep(
            key = TutorialKey.CAL_PLANNED,
            route = BudgetDestinations.CALENDAR,
            title = "Pagos planeados",
            body = "Cada tarjeta es un pago por venir. Confírmalo cuando ocurra, edítalo o " +
                "pospónlo si cambió la fecha.",
        ),
        TutorialStep(
            key = TutorialKey.CAL_FAB,
            route = BudgetDestinations.CALENDAR,
            title = "Nuevo pago planeado",
            body = "Agrega un pago único a futuro. Para pagos que se repiten, usa las plantillas " +
                "recurrentes (icono de repetir arriba).",
        ),

        // ── Cuentas ────────────────────────────────────────────────────────────────
        TutorialStep(
            key = TutorialKey.WAL_HEADER,
            route = BudgetDestinations.WALLETS,
            title = "Ingresos y transferencias",
            body = "Registra ingresos o mueve dinero entre cuentas desde estos botones del " +
                "encabezado.",
        ),
        TutorialStep(
            key = TutorialKey.WAL_LIST,
            route = BudgetDestinations.WALLETS,
            title = "Tus cuentas y saldos",
            body = "Efectivo, tarjetas, metas de ahorro, préstamos por cobrar y compras a meses " +
                "(MSI) viven aquí. Toca una cuenta para ver su detalle.",
        ),
        TutorialStep(
            key = TutorialKey.WAL_FAB,
            route = BudgetDestinations.WALLETS,
            title = "Nueva cuenta",
            body = "Crea una cuenta nueva: efectivo, tarjeta, tienda o cualquier bolsillo que " +
                "quieras seguir.",
        ),

        // ── Analíticas ───────────────────────────────────────────────────────────
        TutorialStep(
            key = TutorialKey.ANA_SUMMARY,
            route = BudgetDestinations.ANALYTICS,
            title = "Resumen de tu quincena",
            body = "Un vistazo escrito de cómo vas. Debajo tienes las gráficas que lo sustentan.",
        ),
        TutorialStep(
            key = TutorialKey.ANA_KPI_ROW,
            route = BudgetDestinations.ANALYTICS,
            title = "Indicadores clave",
            body = "Ahorro, dinero por cobrar y MSI pendiente, siempre a la vista.",
        ),
        TutorialStep(
            key = TutorialKey.ANA_WIDGETS,
            route = BudgetDestinations.ANALYTICS,
            title = "Gráficas y presupuestos",
            body = "Distribución del gasto, flujo, tendencia, presupuesto vs. gasto y más. Desplázate " +
                "para explorarlas todas.",
        ),
        TutorialStep(
            key = TutorialKey.ANA_ASK_FAB,
            route = BudgetDestinations.ANALYTICS,
            title = "Pregúntale a la app",
            body = "El asistente responde en lenguaje natural: \"¿cuánto gasté en comida esta " +
                "quincena?\" y ejecuta la consulta por ti.",
        ),
        TutorialStep(
            key = TutorialKey.ANA_ASK_FAB,
            route = BudgetDestinations.ANALYTICS,
            title = "Atajos que aprenden",
            body = "Dentro del chat, los atajos cambian según tu quincena: si una categoría se " +
                "pasó del presupuesto o una tarjeta acumula deuda, la pregunta aparece lista " +
                "para tocarse.",
        ),

        // ── Perfil ───────────────────────────────────────────────────────────────
        TutorialStep(
            key = TutorialKey.PROFILE_STATEMENTS,
            route = BudgetDestinations.PROFILE,
            title = "Concilia tu estado de cuenta",
            body = "Desde Perfil puedes importar el PDF de tu tarjeta: cada movimiento se " +
                "compara con tus gastos ya registrados para vincularlos sin duplicar; lo " +
                "nuevo llega a la bandeja para que lo confirmes.",
        ),

        // ── Libro Mayor ──────────────────────────────────────────────────────────
        TutorialStep(
            key = TutorialKey.LED_FILTERS,
            route = BudgetDestinations.LEDGER,
            title = "Filtra el historial",
            body = "Acota por categoría o cuenta para encontrar movimientos al instante.",
        ),
        TutorialStep(
            key = TutorialKey.LED_ROWS,
            route = BudgetDestinations.LEDGER,
            title = "Cada movimiento, editable",
            body = "Toca cualquier fila para ver el detalle y editar o borrar el gasto. Aquí termina " +
                "el recorrido: ¡ya puedes explorar por tu cuenta!",
        ),
    )
}
