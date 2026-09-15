package mx.budget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import kotlin.system.exitProcess

/**
 * Relanza la app despues de restaurar un respaldo.
 *
 * Vive en su propio proceso (`android:process=":restart"`) porque el proceso
 * principal tiene que morir para soltar la base vieja, y alguien tiene que seguir
 * vivo para volver a abrir la app. Lanzar la actividad desde una alarma no sirve:
 * desde Android 12 el arranque de actividades en segundo plano no esta
 * garantizado.
 *
 * `BudgetApplication` se apaga sola en este proceso, asi que aqui no hay base de
 * datos, ni Firebase, ni workers.
 */
class RestartActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
        // Sin esto el proceso auxiliar queda rondando y vuelve a abrir la base.
        Runtime.getRuntime().exit(0)
    }
}

/** Codigo de salida del proceso principal tras instalar el respaldo. */
internal fun morirTrasRestaurar(): Nothing = exitProcess(0)
