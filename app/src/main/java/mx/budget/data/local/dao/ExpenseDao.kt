package mx.budget.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import mx.budget.data.local.entity.ExpenseEntity
import mx.budget.data.local.result.ExpenseWithDetails
import mx.budget.data.local.result.PlannedReminder
import mx.budget.data.local.result.TopExpense

/**
 * DAO del ledger de gastos.
 *
 * Las proyecciones de lectura ([ExpenseWithDetails], [TopExpense]) se
 * construyen con JOINs explícitos y alias que mapean a los campos de
 * los POJOs en `data.local.result`.
 */
@Dao
interface ExpenseDao {

    @Query(
        """
        SELECT
            e.id                       AS expenseId,
            e.concept                  AS concept,
            e.amount_mxn               AS amountMxn,
            e.occurred_at              AS occurredAt,
            e.status                   AS status,
            e.category_id              AS categoryId,
            c.display_name             AS categoryName,
            c.code                     AS categoryCode,
            c.color_hex                AS categoryColorHex,
            pm.display_name            AS paymentMethodName,
            pm.kind                    AS paymentMethodKind,
            q.label                    AS quincenaLabel,
            e.installment_number       AS installmentNumber,
            ip.total_installments      AS installmentTotal,
            e.notes                    AS notes
        FROM expense e
        INNER JOIN category c        ON c.id = e.category_id
        INNER JOIN payment_method pm ON pm.id = e.payment_method_id
        INNER JOIN quincena q        ON q.id = e.quincena_id
        LEFT JOIN installment_plan ip ON ip.id = e.installment_plan_id
        WHERE e.quincena_id = :quincenaId
        ORDER BY e.occurred_at DESC
        """
    )
    fun observeWithDetails(quincenaId: String): Flow<List<ExpenseWithDetails>>

    /**
     * Movimientos cargados a un wallet (método de pago), ordenados por fecha
     * descendente — alimenta el detalle de la pantalla Wallets ("ver movimientos").
     * Mismas columnas/JOINs que [observeWithDetails], filtrando por payment_method.
     */
    @Query(
        """
        SELECT
            e.id                       AS expenseId,
            e.concept                  AS concept,
            e.amount_mxn               AS amountMxn,
            e.occurred_at              AS occurredAt,
            e.status                   AS status,
            e.category_id              AS categoryId,
            c.display_name             AS categoryName,
            c.code                     AS categoryCode,
            c.color_hex                AS categoryColorHex,
            pm.display_name            AS paymentMethodName,
            pm.kind                    AS paymentMethodKind,
            q.label                    AS quincenaLabel,
            e.installment_number       AS installmentNumber,
            ip.total_installments      AS installmentTotal,
            e.notes                    AS notes
        FROM expense e
        INNER JOIN category c        ON c.id = e.category_id
        INNER JOIN payment_method pm ON pm.id = e.payment_method_id
        INNER JOIN quincena q        ON q.id = e.quincena_id
        LEFT JOIN installment_plan ip ON ip.id = e.installment_plan_id
        WHERE e.payment_method_id = :paymentMethodId
        ORDER BY e.occurred_at DESC
        """
    )
    fun observeByWallet(paymentMethodId: String): Flow<List<ExpenseWithDetails>>

    /**
     * Búsqueda de movimientos por texto (concepto o nombre de categoría), scope
     * household (búsqueda global, patrón Pixel Screenshots). Alimenta la barra de
     * búsqueda inferior. Mismas columnas/JOINs que [observeWithDetails].
     */
    @Query(
        """
        SELECT
            e.id                       AS expenseId,
            e.concept                  AS concept,
            e.amount_mxn               AS amountMxn,
            e.occurred_at              AS occurredAt,
            e.status                   AS status,
            e.category_id              AS categoryId,
            c.display_name             AS categoryName,
            c.code                     AS categoryCode,
            c.color_hex                AS categoryColorHex,
            pm.display_name            AS paymentMethodName,
            pm.kind                    AS paymentMethodKind,
            q.label                    AS quincenaLabel,
            e.installment_number       AS installmentNumber,
            ip.total_installments      AS installmentTotal,
            e.notes                    AS notes
        FROM expense e
        INNER JOIN category c        ON c.id = e.category_id
        INNER JOIN payment_method pm ON pm.id = e.payment_method_id
        INNER JOIN quincena q        ON q.id = e.quincena_id
        LEFT JOIN installment_plan ip ON ip.id = e.installment_plan_id
        WHERE e.household_id = :householdId
          AND (e.concept LIKE '%' || :q || '%' COLLATE NOCASE
               OR c.display_name LIKE '%' || :q || '%' COLLATE NOCASE)
        ORDER BY e.occurred_at DESC
        LIMIT :limit
        """
    )
    fun searchWithDetails(householdId: String, q: String, limit: Int = 100): Flow<List<ExpenseWithDetails>>

    @Query(
        """
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM expense
        WHERE quincena_id = :quincenaId AND status = 'POSTED'
        """
    )
    fun observePostedTotal(quincenaId: String): Flow<Double>

    @Query(
        """
        SELECT COALESCE(SUM(amount_mxn), 0.0)
        FROM expense
        WHERE quincena_id = :quincenaId AND status = 'PLANNED'
        """
    )
    fun observePlannedTotal(quincenaId: String): Flow<Double>

    /**
     * Gastos `PLANNED` del hogar con detalles (categoría/wallet/quincena),
     * ordenados por fecha ascendente — timeline del calendario (Apéndice G.2,
     * Fase 4). Mismas columnas/JOINs que [observeWithDetails], pero a nivel
     * household y filtrando solo lo planeado (no ejecutado).
     */
    @Query(
        """
        SELECT
            e.id                       AS expenseId,
            e.concept                  AS concept,
            e.amount_mxn               AS amountMxn,
            e.occurred_at              AS occurredAt,
            e.status                   AS status,
            e.category_id              AS categoryId,
            c.display_name             AS categoryName,
            c.code                     AS categoryCode,
            c.color_hex                AS categoryColorHex,
            pm.display_name            AS paymentMethodName,
            pm.kind                    AS paymentMethodKind,
            q.label                    AS quincenaLabel,
            e.installment_number       AS installmentNumber,
            ip.total_installments      AS installmentTotal,
            e.notes                    AS notes
        FROM expense e
        INNER JOIN category c        ON c.id = e.category_id
        INNER JOIN payment_method pm ON pm.id = e.payment_method_id
        INNER JOIN quincena q        ON q.id = e.quincena_id
        LEFT JOIN installment_plan ip ON ip.id = e.installment_plan_id
        WHERE e.household_id = :householdId AND e.status = 'PLANNED'
        ORDER BY e.occurred_at ASC
        """
    )
    fun observePlannedWithDetails(householdId: String): Flow<List<ExpenseWithDetails>>

    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun getById(id: String): ExpenseEntity?

    /**
     * Gastos pendientes de reembolso (Fase B, paquete B3): los que un tercero
     * adelantó y el hogar aún le debe (`settlement_status = 'PENDING_REIMBURSEMENT'`),
     * con detalles (categoría/wallet/quincena) para abrir el detalle al tocarlos.
     * Mismas columnas/JOINs que [observeWithDetails], a nivel household.
     *
     * @Query NUEVO de solo lectura — NO altera el esquema (excepción autorizada del
     * paquete B3 para la sección "Por reembolsar" del dashboard).
     */
    @Query(
        """
        SELECT
            e.id                       AS expenseId,
            e.concept                  AS concept,
            e.amount_mxn               AS amountMxn,
            e.occurred_at              AS occurredAt,
            e.status                   AS status,
            e.category_id              AS categoryId,
            c.display_name             AS categoryName,
            c.code                     AS categoryCode,
            c.color_hex                AS categoryColorHex,
            pm.display_name            AS paymentMethodName,
            pm.kind                    AS paymentMethodKind,
            q.label                    AS quincenaLabel,
            e.installment_number       AS installmentNumber,
            ip.total_installments      AS installmentTotal,
            e.notes                    AS notes
        FROM expense e
        INNER JOIN category c        ON c.id = e.category_id
        INNER JOIN payment_method pm ON pm.id = e.payment_method_id
        INNER JOIN quincena q        ON q.id = e.quincena_id
        LEFT JOIN installment_plan ip ON ip.id = e.installment_plan_id
        WHERE e.household_id = :householdId
          AND e.settlement_status = 'PENDING_REIMBURSEMENT'
          AND e.status = 'POSTED'
        ORDER BY e.occurred_at DESC
        """
    )
    fun observePendingReimbursements(householdId: String): Flow<List<ExpenseWithDetails>>

    /**
     * Totales por tercero de lo pendiente de reembolso (paquete B3): agrega
     * `amount_mxn` de los gastos `PENDING_REIMBURSEMENT` por `external_payer_member_id`.
     * Alimenta los chips agrupados de "Por reembolsar" (cuánto se debe a cada quién).
     *
     * @Query NUEVO de solo lectura — NO altera el esquema.
     */
    @Query(
        """
        SELECT
            e.external_payer_member_id AS externalPayerMemberId,
            COALESCE(SUM(e.amount_mxn), 0.0) AS totalMxn,
            COUNT(*) AS expenseCount
        FROM expense e
        WHERE e.household_id = :householdId
          AND e.settlement_status = 'PENDING_REIMBURSEMENT'
          AND e.status = 'POSTED'
        GROUP BY e.external_payer_member_id
        ORDER BY totalMxn DESC
        """
    )
    fun observePendingReimbursementTotals(householdId: String): Flow<List<mx.budget.data.local.result.PendingReimbursementByPayer>>

    /**
     * Gastos pendientes de reembolso con el tercero que los adelantó
     * (`external_payer_member_id`), para la pantalla "Cuentas entre miembros"
     * (deudas explícitas por pagar). A diferencia de [observePendingReimbursements],
     * expone el pagador para agrupar por miembro y desglosar la deuda por concepto.
     *
     * @Query NUEVO de solo lectura — NO altera el esquema.
     */
    @Query(
        """
        SELECT
            e.id                       AS expenseId,
            e.concept                  AS concept,
            e.amount_mxn               AS amountMxn,
            e.occurred_at              AS occurredAt,
            e.external_payer_member_id AS externalPayerMemberId
        FROM expense e
        WHERE e.household_id = :householdId
          AND e.settlement_status = 'PENDING_REIMBURSEMENT'
          AND e.status = 'POSTED'
        ORDER BY e.occurred_at DESC
        """
    )
    fun observePendingReimbursementExpenses(
        householdId: String
    ): Flow<List<mx.budget.data.local.result.PendingReimbursementExpense>>

    /**
     * Marca un gasto adelantado por un tercero como **reembolsado**
     * (`settlement_status = 'REIMBURSED'`): el hogar ya le repuso el dinero al que
     * lo adelantó. NO mueve saldos de wallet (la reposición ocurre en efectivo/fuera
     * del ledger) — a diferencia de [mx.budget.data.repository.ExpenseRepository.reimburseFrom],
     * que reasigna el gasto a un wallet real. Gate `PENDING_REIMBURSEMENT` para no
     * pisar otros estados. Sube `updated_at` para el LWW del sync.
     */
    @Query(
        "UPDATE expense SET settlement_status = 'REIMBURSED', updated_at = :ts " +
            "WHERE id = :id AND settlement_status = 'PENDING_REIMBURSEMENT'"
    )
    suspend fun markReimbursed(id: String, ts: Long)

    /**
     * Gastos `PLANNED` del hogar con el contexto que el [ReminderWorker] (Fase 3)
     * necesita para decidir si recordar: fecha prevista, inicio de la quincena y
     * el `cadence_detail` de la plantilla que los originó (LEFT JOIN: los PLANNED
     * manuales sin plantilla traen `cadenceDetail` nulo y caen al lead global).
     */
    @Query(
        """
        SELECT
            e.id          AS expenseId,
            e.concept     AS concept,
            e.amount_mxn  AS amountMxn,
            e.occurred_at AS occurredAt,
            q.start_date  AS quincenaStartDate,
            rt.cadence_detail AS cadenceDetail
        FROM expense e
        INNER JOIN quincena q ON q.id = e.quincena_id
        LEFT JOIN recurrence_template rt ON rt.id = e.recurrence_template_id
        WHERE e.household_id = :householdId AND e.status = 'PLANNED'
        """
    )
    suspend fun getPlannedForReminder(householdId: String): List<PlannedReminder>

    /**
     * Cuántos gastos (de cualquier estado) ya provienen de [templateId] en
     * [quincenaId]. Idempotencia de la materialización (Apéndice G.2, Fase 1):
     * incluir POSTED evita re-crear un PLANNED si el usuario ya confirmó el pago.
     */
    @Query(
        "SELECT COUNT(*) FROM expense " +
            "WHERE recurrence_template_id = :templateId AND quincena_id = :quincenaId"
    )
    suspend fun countForTemplateInQuincena(templateId: String, quincenaId: String): Int

    /**
     * Todos los gastos del hogar — usado por el pipeline de canonicalización
     * retroactiva (Apéndice F.3.4) para recalcular `concept_canonical` en lote.
     */
    @Query("SELECT * FROM expense WHERE household_id = :householdId")
    suspend fun getAll(householdId: String): List<ExpenseEntity>

    /**
     * ¿Existe al menos un gasto del hogar? Para la detección de primer arranque
     * (paquete B2) sin materializar toda la tabla en memoria en el hilo principal.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM expense WHERE household_id = :householdId)")
    suspend fun hasAny(householdId: String): Boolean

    /**
     * Gastos POSTED recientes (ventana móvil por `occurred_at`), para el snapshot
     * del reloj y el motor de sugerencias: evita cargar el historial completo
     * (~800 filas) en cada push. `sinceEpochMs` = ahora − ventana (p. ej. 90 días).
     */
    @Query(
        "SELECT * FROM expense WHERE household_id = :householdId AND status = 'POSTED' " +
            "AND occurred_at >= :sinceEpochMs ORDER BY occurred_at DESC"
    )
    suspend fun getRecentPosted(householdId: String, sinceEpochMs: Long): List<ExpenseEntity>

    /** Gasto (cualquier status) de una cuota MSI concreta — dedupe del materializador. */
    @Query(
        "SELECT * FROM expense WHERE household_id = :householdId AND installment_plan_id = :planId " +
            "AND installment_number = :number LIMIT 1"
    )
    suspend fun getByPlanAndNumber(householdId: String, planId: String, number: Int): ExpenseEntity?

    /**
     * Ids de categoría usados más recientemente en gastos POSTED del hogar,
     * ordenados por último uso (v13, A0). Alimenta las "recientes" reales de
     * la hoja de captura — antes eran un top-5 estático por `sort_order`.
     */
    @Query(
        """
        SELECT category_id FROM expense
        WHERE household_id = :householdId AND status = 'POSTED'
        GROUP BY category_id
        ORDER BY MAX(occurred_at) DESC
        LIMIT :limit
        """
    )
    fun observeRecentCategoryIds(householdId: String, limit: Int): Flow<List<String>>

    /** Persiste la clave canónica calculada para un gasto. */
    @Query("UPDATE expense SET concept_canonical = :canonical WHERE id = :id")
    suspend fun updateConceptCanonical(id: String, canonical: String?)

    /**
     * Marca un gasto como liquidado por netting ("Cuentas entre miembros"):
     * `settlement_status = 'NETTED'`. El gate `= 'NONE'` garantiza que NO pise el
     * flujo "alguien más pagó" (PENDING_REIMBURSEMENT/REIMBURSED/ABSORBED): solo
     * transiciona gastos normales aún no liquidados. Sube `updated_at` para el LWW.
     */
    @Query(
        "UPDATE expense SET settlement_status = 'NETTED', updated_at = :ts " +
            "WHERE id = :id AND settlement_status = 'NONE'"
    )
    suspend fun markNetted(id: String, ts: Long)

    @Query(
        """
        SELECT
            e.id            AS expenseId,
            CAST(e.occurred_at AS TEXT) AS date,
            e.concept       AS concept,
            e.amount_mxn    AS amountMxn,
            c.display_name  AS categoryName,
            pm.display_name AS walletName
        FROM expense e
        INNER JOIN category c        ON c.id = e.category_id
        INNER JOIN payment_method pm ON pm.id = e.payment_method_id
        WHERE e.quincena_id = :quincenaId
        ORDER BY e.amount_mxn DESC
        LIMIT :limit
        """
    )
    suspend fun getTopExpenses(quincenaId: String, limit: Int): List<TopExpense>

    /**
     * Candidatos para el pre-match de estados de cuenta (Fase 5): gastos POSTED
     * del wallet dentro de la ventana del periodo del estado. Añadir un método
     * @Dao no cambia el esquema.
     */
    @Query(
        """
        SELECT * FROM expense
        WHERE payment_method_id = :walletId AND status = 'POSTED'
          AND occurred_at BETWEEN :fromEpoch AND :toEpoch
        ORDER BY occurred_at
        """
    )
    suspend fun getPostedByWalletBetween(walletId: String, fromEpoch: Long, toEpoch: Long): List<ExpenseEntity>

    @Query(
        """
        SELECT * FROM expense
        WHERE household_id = :householdId
          AND occurred_at BETWEEN :fromEpoch AND :toEpoch
        ORDER BY occurred_at DESC
        """
    )
    suspend fun getByDateRange(
        householdId: String,
        fromEpoch: Long,
        toEpoch: Long
    ): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    /**
     * Upsert idempotente usado EXCLUSIVAMENTE por el pull (Firestore → Room).
     * No encola en `sync_queue` (a diferencia de los repos públicos), evitando
     * el bucle push↔pull. Política de conflictos: REPLACE (last-write-wins).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ExpenseEntity)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    /**
     * Borrado por id usado EXCLUSIVAMENTE por el pull (removal remoto). Las
     * atribuciones caen por FK CASCADE. NO ajusta saldos: el saldo del wallet
     * llega como estado en su propio documento remoto.
     */
    @Query("DELETE FROM expense WHERE id = :id")
    suspend fun deleteById(id: String)
}
