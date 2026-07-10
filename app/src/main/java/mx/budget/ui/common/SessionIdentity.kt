package mx.budget.ui.common

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Identidad de sesión "(Tú)" transversal a toda la app (paquete IDENTIDAD-TÚ).
 *
 * [LocalSessionMemberId] expone el member del hogar activo VINCULADO a esta
 * sesión ([mx.budget.BudgetApplication.linkedMemberId], roles v2) a cualquier
 * composable que pinte nombres de miembros, sin encadenar el parámetro por
 * decenas de firmas. Lo provee MainActivity alrededor del NavGraph; `null` =
 * sin vínculo resuelto (dueño legacy, offline sin caché, o aún resolviéndose).
 */
val LocalSessionMemberId = staticCompositionLocalOf<String?> { null }

/**
 * Etiqueta el nombre de un member con el sufijo " (Tú)" cuando ese member es el
 * vinculado a la sesión actual. Úsalo en TODA lista visible de miembros del
 * hogar (chips de atribución, barras del dashboard, deudas, maestros…):
 * `youLabel(m.displayName, m.id, LocalSessionMemberId.current)`.
 */
fun youLabel(name: String, memberId: String?, sessionId: String?): String =
    if (memberId != null && memberId == sessionId) "$name (Tú)" else name
