package mx.budget.data.capture

import mx.budget.data.local.entity.PendingCaptureEntity
import mx.budget.ui.capture.CaptureField
import mx.budget.ui.capture.CapturePrefill
import mx.budget.ui.capture.CaptureSheetMode
import org.json.JSONObject

// ─────────────────────────────────────────────────────────────────────────────
// PendingCaptureReview — puente bandeja unificada → CaptureBottomSheet (SP-A1)
// Contrato compartido entre los paquetes A2 y A4: "Registrar" una captura
// pendiente NUNCA inserta directo; siempre abre la hoja en modo Review con
// prefill completo y los campos que la fuente no pudo determinar marcados
// "Por decidir".
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Convierte una fila de `pending_capture` al modo [CaptureSheetMode.Review] del
 * [mx.budget.ui.capture.CaptureBottomSheet].
 *
 * - Prefill completo: monto, concepto, categoría/wallet sugeridos, fecha,
 *   atribuciones ricas (JSON `{memberId: bps}` → mapa) y notas.
 * - `missingFields`: CATEGORY y/o WALLET cuando la fuente no los resolvió
 *   (quedan "Por decidir" y bloquean Registrar hasta decidirse).
 * - `pendingCaptureId`: al registrar, el ViewModel de captura marca esta fila
 *   CONFIRMED y sale de la bandeja.
 */
fun PendingCaptureEntity.toReviewMode(): CaptureSheetMode.Review =
    CaptureSheetMode.Review(
        prefill = CapturePrefill(
            amountMxn = amountMxn.takeIf { it > 0.0 },
            concept = concept.takeIf { it.isNotBlank() },
            categoryId = suggestedCategoryId,
            walletId = suggestedWalletId,
            occurredAt = occurredAt,
            beneficiaryBps = parseBpsJson(suggestedBeneficiaryJson),
            payerBps = parseBpsJson(suggestedPayerJson),
            notes = notes?.takeIf { it.isNotBlank() },
            pendingCaptureId = id,
        ),
        missingFields = buildSet {
            if (suggestedCategoryId == null) add(CaptureField.CATEGORY)
            if (suggestedWalletId == null) add(CaptureField.WALLET)
        },
    )

/** JSON `{memberId: bps}` → mapa, o null si no hay/está malformado/queda vacío. */
private fun parseBpsJson(json: String?): Map<String, Int>? {
    if (json.isNullOrBlank()) return null
    return runCatching {
        val obj = JSONObject(json)
        obj.keys().asSequence()
            .associateWith { obj.getInt(it) }
            .filterValues { it > 0 }
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}
