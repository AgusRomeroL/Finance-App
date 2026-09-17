package mx.budget.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Runner de las pruebas instrumentadas del telefono.
 *
 * Arranca una `Application` vacia en lugar de `BudgetApplication`: esta abre
 * Room sobre `budget.db`, inicializa Firebase, programa workers y corre el
 * rollover de quincena en `onCreate`. Nada de eso hace falta para probar las
 * migraciones sobre un archivo propio ni para componer un tema, y en un aparato
 * real con la app instalada seria una fuente de escrituras concurrentes.
 */
class PlainAppRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
