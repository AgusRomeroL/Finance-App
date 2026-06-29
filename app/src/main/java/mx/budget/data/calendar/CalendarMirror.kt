package mx.budget.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import mx.budget.data.local.dao.ExpenseDao
import mx.budget.data.settings.SettingsRepository
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Espejo **opt-in de UNA vía** de los gastos `PLANNED` hacia un calendario
 * dedicado del dispositivo (Apéndice G.2, Fase 6).
 *
 * Principios de seguridad/privacidad:
 *  - **Calendario dedicado** (cuenta LOCAL "Presupuesto Familiar"): nunca escribe
 *    en el calendario personal de la usuaria ni lo lee.
 *  - **Una vía:** la app empuja; jamás importa de vuelta. Guarda `expenseId→eventId`
 *    en DataStore para poder actualizar/borrar lo que ella misma creó.
 *  - **Best-effort:** todo va envuelto en `runCatching`; si falta permiso o el
 *    proveedor falla, la app sigue normal (el espejo es accesorio).
 *
 * Reconciliación idempotente: inserta los PLANNED que aún no tienen evento y borra
 * los eventos cuyos PLANNED ya no existen (confirmados/borrados).
 */
class CalendarMirror(
    private val context: Context,
    private val settings: SettingsRepository,
    private val expenseDao: ExpenseDao,
    private val householdId: String,
) {

    private val money = NumberFormat.getCurrencyInstance(Locale("es", "MX"))

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Sincroniza el calendario dedicado con los PLANNED actuales (one-way). */
    suspend fun reconcile() {
        if (!hasPermission()) return
        runCatching {
            val calId = ensureCalendar() ?: return
            val planned = expenseDao.getPlannedForReminder(householdId)
            val plannedById = planned.associateBy { it.expenseId }
            val map = settings.getCalendarEventMap().toMutableMap()

            // Borra eventos cuyos PLANNED ya no existen.
            map.keys.filter { it !in plannedById }.toList().forEach { eid ->
                deleteEvent(map[eid]!!)
                map.remove(eid)
            }
            // Inserta los PLANNED que aún no tienen evento.
            planned.filter { it.expenseId !in map }.forEach { p ->
                insertEvent(calId, p.concept, p.amountMxn, p.occurredAt)?.let { map[p.expenseId] = it }
            }
            settings.setCalendarEventMap(map)
        }
    }

    /** Borra todos los eventos espejados + el calendario dedicado y limpia el estado. */
    suspend fun disableAndPurge() {
        runCatching {
            if (hasPermission()) {
                settings.getCalendarEventMap().values.forEach { deleteEvent(it) }
                settings.getMirrorCalendarId()?.let { deleteCalendar(it) }
            }
        }
        settings.setCalendarEventMap(emptyMap())
        settings.setMirrorCalendarId(null)
    }

    // ── CalendarContract ────────────────────────────────────────────────────────

    private suspend fun ensureCalendar(): Long? {
        settings.getMirrorCalendarId()?.let { existing ->
            if (calendarExists(existing)) return existing
        }
        // Buscar por nombre (por si quedó de una sesión previa) o crear.
        val found = findCalendarByName()
        if (found != null) { settings.setMirrorCalendarId(found); return found }
        val created = createCalendar()
        if (created != null) settings.setMirrorCalendarId(created)
        return created
    }

    private fun calendarExists(id: Long): Boolean = runCatching {
        context.contentResolver.query(
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, id),
            arrayOf(CalendarContract.Calendars._ID), null, null, null,
        )?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)

    private fun findCalendarByName(): Long? = runCatching {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.NAME} = ?", arrayOf(CALENDAR_NAME), null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }.getOrNull()

    private fun createCalendar(): Long? = runCatching {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, "Presupuesto Familiar")
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xFF016E3E.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, ACCOUNT_NAME)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.VISIBLE, 1)
        }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        context.contentResolver.insert(uri, values)?.let { ContentUris.parseId(it) }
    }.getOrNull()

    private fun insertEvent(calId: Long, concept: String, amount: Double, startMillis: Long): Long? = runCatching {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, "${money.format(amount)} · $concept")
            put(CalendarContract.Events.DESCRIPTION, "Gasto planeado — Presupuesto Familiar")
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + TimeUnit.HOURS.toMillis(1))
            put(CalendarContract.Events.EVENT_TIMEZONE, ZONE)
        }
        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
    }.getOrNull()

    private fun deleteEvent(eventId: Long) {
        runCatching {
            context.contentResolver.delete(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null,
            )
        }
    }

    private fun deleteCalendar(calId: Long) {
        runCatching {
            val uri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calId).buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                .build()
            context.contentResolver.delete(uri, null, null)
        }
    }

    companion object {
        private const val ACCOUNT_NAME = "Presupuesto Familiar"
        private const val CALENDAR_NAME = "presupuesto_familiar_planned"
        private const val ZONE = "America/Mexico_City"
    }
}
