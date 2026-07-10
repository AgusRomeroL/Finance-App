package mx.budget.ui.tutorial

/**
 * Identidad estable de cada sección de la UI que el tutorial guiado resalta (spotlight).
 *
 * **FUENTE ÚNICA DE VERDAD.** Cada `Modifier.tutorialTarget(...)` referencia una de estas
 * claves y cada [TutorialStep] apunta a una de ellas. Como es un `enum`, un tag que use una
 * clave inexistente NO compila — imposible que un target quede huérfano por un typo.
 *
 * MANTENIMIENTO (ver `TUTORIAL.md`): al **añadir / renombrar / borrar** una sección de la app
 * que el tour explica:
 *   1. actualiza esta enum,
 *   2. actualiza su tag `Modifier.tutorialTarget(TutorialKey.X, ...)`,
 *   3. actualiza/reordena su entrada en `TutorialSpec.steps`,
 *   4. actualiza la tabla de mapeo en `TUTORIAL.md`.
 *
 * Un paso cuya clave ya no tiene tag en pantalla degrada a un globo centrado (no crashea);
 * en debug el controller loguea las claves que nunca se registraron durante un tour.
 */
enum class TutorialKey {
    // ── Dashboard ──
    DASH_HERO_KPI,
    DASH_MEMBER_BARS,
    DASH_SUGGESTIONS,
    DASH_ACTION_BAR,
    DASH_NAV,

    // ── Captura (dentro del ModalBottomSheet) ──
    CAP_KIND_TOGGLE,
    CAP_AMOUNT_KEYPAD,
    CAP_CATEGORY,
    CAP_ATTRIBUTION,

    // ── Calendario ──
    CAL_MONTH_GRID,
    CAL_PLANNED,
    CAL_FAB,

    // ── Cuentas ──
    WAL_HEADER,
    WAL_LIST,
    WAL_FAB,

    // ── Analíticas ──
    ANA_SUMMARY,
    ANA_KPI_ROW,
    ANA_WIDGETS,
    ANA_ASK_FAB,

    // ── Perfil ──
    /** Entrada "Importar estado de cuenta" (conciliación Fase 5). Sin tag aún:
     *  el paso degrada a globo centrado sobre Perfil (comportamiento previsto). */
    PROFILE_STATEMENTS,

    // ── Libro Mayor ──
    LED_FILTERS,
    LED_ROWS,
}
