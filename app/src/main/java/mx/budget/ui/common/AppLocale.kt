package mx.budget.ui.common

import java.util.Locale

/**
 * Locale canonico de la app: espanol de Mexico.
 *
 * La app es monolingue y su formato de fecha, numero y moneda asume es-MX.
 * Antes cada pantalla construia su propio `Locale("es", "MX")` (36 literales
 * repartidos por la interfaz, ademas con el constructor ya deprecado), y los
 * componentes que leen el locale del sistema, como el selector de fecha de
 * Material 3, salian en el idioma del telefono.
 *
 * Se aplica de tres formas complementarias en [mx.budget.BudgetApplication]:
 * como `Locale` por defecto del proceso, como locale por app del sistema
 * (Android 13+) y declarado en `res/xml/locales_config.xml`.
 */
val AppLocale: Locale = Locale.forLanguageTag("es-MX")
