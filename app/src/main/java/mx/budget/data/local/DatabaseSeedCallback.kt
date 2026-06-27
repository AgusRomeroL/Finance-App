package mx.budget.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase

/**
 * Callback de inicialización de la base de datos.
 *
 * La base se entrega precargada mediante `createFromAsset`, por lo que
 * este callback es intencionalmente un no-op: existe como punto de
 * extensión para sembrar datos o ejecutar PRAGMAs adicionales si en el
 * futuro se deja de depender del asset precargado.
 */
class DatabaseSeedCallback : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // No-op: el seed proviene de budget_database.db (createFromAsset).
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // No-op.
    }
}
