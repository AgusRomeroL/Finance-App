package mx.budget.wear.presentation.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.wear.watchface.complications.data.PlainComplicationText
import mx.budget.wear.MainActivity

/** Códigos distintos por complication: ver [hubTapAction]. */
internal const val REQ_DISPONIBLE = 101
internal const val REQ_PROXIMO_PAGO = 102

internal fun plain(text: CharSequence): PlainComplicationText =
    PlainComplicationText.Builder(text).build()

/**
 * Tocar la complication abre el hub, y eso no es solo navegación: es la cura.
 * `MainActivity.onResume` dispara `requestSync()`, así que tocar una carátula con
 * un dato viejo lo refresca por el camino largo (petición, empuje, cache y
 * repintado de las superficies) sin que nadie tenga que saber que eso existe.
 *
 * Un `requestCode` distinto por complication: dos `Intent` iguales producirían el
 * MISMO `PendingIntent`, y las dos complications acabarían compartiendo destino
 * y banderas.
 */
internal fun Context.hubTapAction(requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        this,
        requestCode,
        Intent(this, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        // FLAG_IMMUTABLE sin condicional: minSdk del reloj es 30 y desde 31 es
        // obligatorio declarar una de las dos mutabilidades.
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
