package mx.budget.data.local.converter

import androidx.room.TypeConverter
import mx.budget.core.model.*

/**
 * Type converters para serialización Room.
 *
 * Room requiere que los campos de las entidades sean tipos primitivos
 * o tipos soportados nativamente. Estos converters traducen enums
 * a String (name) y viceversa.
 *
 * Nota: las fechas se almacenan como TEXT (ISO) o INTEGER (epoch millis)
 * directamente en las entidades, sin necesidad de converter.
 * Los campos JSON (aliases, meta) se almacenan como TEXT plano.
 */
class Converters {

    // ── MemberRole ──────────────────────────────────────────────

    @TypeConverter
    fun fromMemberRole(value: MemberRole): String = value.name

    @TypeConverter
    fun toMemberRole(value: String): MemberRole = MemberRole.valueOf(value)

    // ── CategoryKind ────────────────────────────────────────────

    @TypeConverter
    fun fromCategoryKind(value: CategoryKind): String = value.name

    @TypeConverter
    fun toCategoryKind(value: String): CategoryKind = CategoryKind.valueOf(value)

    // ── PaymentMethodKind ───────────────────────────────────────

    @TypeConverter
    fun fromPaymentMethodKind(value: PaymentMethodKind): String = value.name

    @TypeConverter
    fun toPaymentMethodKind(value: String): PaymentMethodKind = PaymentMethodKind.valueOf(value)

    // ── QuincenaStatus ──────────────────────────────────────────

    @TypeConverter
    fun fromQuincenaStatus(value: QuincenaStatus): String = value.name

    @TypeConverter
    fun toQuincenaStatus(value: String): QuincenaStatus = QuincenaStatus.valueOf(value)

    // ── QuincenaHalf ────────────────────────────────────────────

    @TypeConverter
    fun fromQuincenaHalf(value: QuincenaHalf): String = value.name

    @TypeConverter
    fun toQuincenaHalf(value: String): QuincenaHalf = QuincenaHalf.valueOf(value)

    // ── ExpenseStatus ───────────────────────────────────────────

    @TypeConverter
    fun fromExpenseStatus(value: ExpenseStatus): String = value.name

    @TypeConverter
    fun toExpenseStatus(value: String): ExpenseStatus = ExpenseStatus.valueOf(value)

    // ── AttributionRole ─────────────────────────────────────────

    @TypeConverter
    fun fromAttributionRole(value: AttributionRole): String = value.name

    @TypeConverter
    fun toAttributionRole(value: String): AttributionRole = AttributionRole.valueOf(value)

    // ── RecurrenceCadence ───────────────────────────────────────

    @TypeConverter
    fun fromRecurrenceCadence(value: RecurrenceCadence): String = value.name

    @TypeConverter
    fun toRecurrenceCadence(value: String): RecurrenceCadence = RecurrenceCadence.valueOf(value)

    // ── InstallmentStatus ───────────────────────────────────────

    @TypeConverter
    fun fromInstallmentStatus(value: InstallmentStatus): String = value.name

    @TypeConverter
    fun toInstallmentStatus(value: String): InstallmentStatus = InstallmentStatus.valueOf(value)

    // ── IncomeCadence ───────────────────────────────────────────

    @TypeConverter
    fun fromIncomeCadence(value: IncomeCadence): String = value.name

    @TypeConverter
    fun toIncomeCadence(value: String): IncomeCadence = IncomeCadence.valueOf(value)

    // ── QuincenaAnchor ──────────────────────────────────────────

    @TypeConverter
    fun fromQuincenaAnchor(value: QuincenaAnchor): String = value.name

    @TypeConverter
    fun toQuincenaAnchor(value: String): QuincenaAnchor = QuincenaAnchor.valueOf(value)
}
