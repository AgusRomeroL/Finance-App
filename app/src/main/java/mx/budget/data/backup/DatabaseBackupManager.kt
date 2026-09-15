package mx.budget.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mx.budget.data.export.ExportFiles
import mx.budget.data.local.BudgetDatabase
import java.io.File

/** Lo que la app entendio del archivo que la persona eligio para restaurar. */
data class BackupInspection(
    val archivo: File,
    val gastos: Int,
    val quincenas: Int,
    val version: Int,
    val hogarCoincide: Boolean,
    val nombreHogar: String?,
)

/** Motivo por el que un archivo no sirve como respaldo, ya en lenguaje llano. */
class BackupInvalidException(mensaje: String) : IllegalArgumentException(mensaje)

/**
 * Respaldo y restauracion de la base completa.
 *
 * El respaldo se hace con `VACUUM INTO`, que lee a traves del pager: incluye lo
 * que todavia vive en el diario WAL sin necesidad de un checkpoint previo y
 * entrega un archivo compacto y consistente. Tiene que correr FUERA de cualquier
 * transaccion.
 *
 * La restauracion no intenta ser elegante: valida el archivo, cierra la base,
 * reemplaza los tres archivos de SQLite y reinicia el proceso. Room y una docena
 * de componentes guardan referencias a DAOs que quedarian colgando de la base
 * vieja, y el `householdId` se resuelve una sola vez al arrancar.
 */
class DatabaseBackupManager(
    private val context: Context,
    private val database: BudgetDatabase,
    private val householdId: String,
) {

    /** Genera el respaldo en la cache y devuelve el archivo listo para guardar. */
    suspend fun export(): File = withContext(Dispatchers.IO) {
        val destino = ExportFiles.newFile(
            context,
            "presupuesto-respaldo-${ExportFiles.selloDeTiempo()}.db",
        )
        // VACUUM INTO exige que el destino no exista y no tolera transaccion viva.
        destino.delete()
        database.openHelper.writableDatabase.execSQL(
            "VACUUM INTO '${destino.absolutePath.replace("'", "''")}'"
        )
        destino
    }

    /**
     * Copia el archivo elegido a la cache y lo revisa antes de tocar nada. Se
     * copia primero porque un archivo con bandera WAL necesita crear su `-shm`
     * al abrirse, y sobre un flujo del selector del sistema eso no es posible.
     */
    suspend fun inspect(origen: Uri): BackupInspection = withContext(Dispatchers.IO) {
        val candidato = File(context.cacheDir, "restore/candidato.db").apply {
            parentFile?.mkdirs()
            delete()
        }
        context.contentResolver.openInputStream(origen).use { entrada ->
            if (entrada == null) throw BackupInvalidException("No se pudo leer el archivo elegido.")
            candidato.outputStream().use { entrada.copyTo(it) }
        }
        val db = runCatching {
            SQLiteDatabase.openDatabase(candidato.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrElse {
            throw BackupInvalidException("Ese archivo no es una base de datos de la app.")
        }
        db.use {
            val integridad = it.escalar("PRAGMA integrity_check")
            if (!integridad.equals("ok", ignoreCase = true)) {
                throw BackupInvalidException("El respaldo está dañado y no se puede restaurar.")
            }
            val version = it.escalar("PRAGMA user_version")?.toIntOrNull() ?: 0
            if (version < 1 || version > BudgetDatabase.SCHEMA_VERSION) {
                throw BackupInvalidException(
                    "El respaldo viene de una versión de la app que este teléfono no entiende."
                )
            }
            val tablas = it.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'", null
            ).use { cursor ->
                buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
            }
            val faltantes = REQUERIDAS.filterNot { tabla -> tabla in tablas }
            if (faltantes.isNotEmpty()) {
                throw BackupInvalidException("Ese archivo no es una base de datos de la app.")
            }
            val gastos = it.escalar("SELECT COUNT(*) FROM expense")?.toIntOrNull() ?: 0
            val quincenas = it.escalar("SELECT COUNT(*) FROM quincena")?.toIntOrNull() ?: 0
            val nombreHogar = it.escalar("SELECT name FROM household LIMIT 1")
            val coincide = it.rawQuery(
                "SELECT 1 FROM household WHERE id = ? LIMIT 1", arrayOf(householdId)
            ).use { cursor -> cursor.moveToFirst() }
            BackupInspection(candidato, gastos, quincenas, version, coincide, nombreHogar)
        }
    }

    /**
     * Deja el candidato listo y lo instala en lugar de la base viva. Devuelve
     * cuando ya solo falta reiniciar el proceso.
     */
    suspend fun restore(inspeccion: BackupInspection) = withContext(Dispatchers.IO) {
        val candidato = inspeccion.archivo
        if (!candidato.exists()) throw BackupInvalidException("El archivo ya no está disponible.")

        // El outbox del respaldo se descarta: el push es un merge incondicional y
        // reproducir filas viejas pisaria en la nube documentos mas nuevos.
        runCatching {
            SQLiteDatabase.openDatabase(candidato.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                .use {
                    it.execSQL("DELETE FROM sync_queue")
                    it.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { c -> c.moveToFirst() }
                }
        }.onFailure { Log.w(TAG, "No se pudo limpiar el outbox del respaldo", it) }

        val viva = context.getDatabasePath(BudgetDatabase.FILE_NAME)
        runCatching { database.close() }
        val temporal = File(viva.parentFile, "${BudgetDatabase.FILE_NAME}.restore")
        temporal.delete()
        candidato.copyTo(temporal, overwrite = true)
        listOf("", "-wal", "-shm").forEach { sufijo ->
            File(viva.parentFile, BudgetDatabase.FILE_NAME + sufijo).delete()
        }
        if (!temporal.renameTo(viva)) {
            temporal.copyTo(viva, overwrite = true)
            temporal.delete()
        }
        candidato.delete()
    }

    /** Fuerza el volcado del diario para que la copia de Android no quede vieja. */
    fun checkpoint() {
        runCatching {
            database.openHelper.writableDatabase
                .query("PRAGMA wal_checkpoint(TRUNCATE)")
                .use { it.moveToFirst() }
        }.onFailure { Log.w(TAG, "No se pudo hacer checkpoint del diario", it) }
    }

    private fun SQLiteDatabase.escalar(sql: String): String? =
        rawQuery(sql, null).use { if (it.moveToFirst()) it.getString(0) else null }

    private companion object {
        const val TAG = "DatabaseBackup"
        val REQUERIDAS = listOf("expense", "quincena", "household", "room_master_table")
    }
}
