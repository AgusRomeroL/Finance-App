package mx.budget.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Tokens de movimiento centralizados (Material Expressive).
 *
 * Antes estos specs vivían inline en ~22 archivos (90 ocurrencias de `spring(`);
 * este objeto es la fuente única para que el sello expresivo sea coherente. Los
 * valores son EXACTAMENTE los que ya se usaban en el código, no nuevos:
 *
 * - [standard] `dampingRatio 0.8 / stiffness 380` — el 90% de los casos: toggles,
 *   barras por miembro, KPI héroe, entrada/reordenamiento de listas, chevrons.
 * - [canvas] `dampingRatio 0.85 / stiffness 120` — barridos de gráficos dibujados
 *   a mano (dona, gauge, líneas, [mx.budget.ui.dashboard.BudgetRing]); más lento
 *   para que el trazo se lea fluido.
 * - [press] `stiffness alto` — feedback de press (scale 0.97) de `Modifier.pressScale`;
 *   debe asentar casi instantáneo (100-160ms de "feel" según Kowalski).
 *
 * Las funciones son genéricas porque `spring<T>()` se instancia con distintos tipos
 * (Float, Color, Dp, IntOffset, IntSize) según el `animate*AsState` que las consuma.
 */
object BudgetMotion {

    /** Spec expresivo estándar. Úsalo salvo que haya una razón concreta para otro. */
    fun <T> standard(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Barridos de Canvas (dona/gauge/ring): más lento y suave. */
    fun <T> canvas(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.85f, stiffness = 120f)

    /** Feedback de press: casi crítico, asienta rápido sin rebote perceptible. */
    fun <T> press(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessHigh)

    /**
     * Retardo por índice para el stagger de entrada de listas (Kowalski: 30-80ms).
     * Aplicar SOLO en la carga inicial, nunca en items reciclados durante scroll.
     */
    const val ListStaggerMillis: Int = 40

    /** Tope de items a los que se aplica stagger (evita colas largas perceptibles). */
    const val ListStaggerCap: Int = 8
}
