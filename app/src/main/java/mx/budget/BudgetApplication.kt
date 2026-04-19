package mx.budget

import android.app.Application
import androidx.room.Room
import mx.budget.data.local.BudgetDatabase

/**
 * Aplicación base para inicializar componentes globales y
 * proveer inyección de dependencias manual.
 * 
 * TODO para el usuario: Añadir android:name=".BudgetApplication" en el tag <application> del AndroidManifest.xml.
 */
class BudgetApplication : Application() {

    // Instancia única de la base de datos Room
    lateinit var database: BudgetDatabase
        private set

    // Expuestos para los Services (ej. WearableListenerService) e inyección manual
    // Normalmente usarías abstracciones como Repository, pero para el prototipo
    // permitiremos el acceso al DAO directamente si el Repository no está instanciado aquí.
    
    override fun onCreate() {
        super.onCreate()
        
        // Inicialización de la persistencia real (Cero Mocking)
        database = Room.databaseBuilder(
            this,
            BudgetDatabase::class.java,
            "budget-database"
        )
        // .createFromAsset("database/budget.db") // Asumiendo que el ETL popule los datos iniciales
        .build()
        
        // Aquí se instanciarían los repositorios concretos inyectando logicamente
        // los DAOs extraídos de `database`
    }
}
