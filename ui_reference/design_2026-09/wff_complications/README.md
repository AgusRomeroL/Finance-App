# Carátula de prueba para ver las complications del reloj

Carátula mínima en Watch Face Format v1 (XML declarativo, sin código) que declara los dos
huecos de complication de Presupuesto Familiar con sus proveedores por defecto:

| Hueco | Proveedor | Tipo |
|---|---|---|
| 0 | `mx.budget/mx.budget.wear.presentation.complication.DisponibleComplicationService` | `RANGED_VALUE` (con `SHORT_TEXT` como alternativa) |
| 1 | `mx.budget/mx.budget.wear.presentation.complication.UpcomingPaymentComplicationService` | `SHORT_TEXT` |

No forma parte del producto. Existe porque el emulador `FinanceWatch` solo trae carátulas
declarativas cuyo selector de complications vive dentro de sysui y no expone ningún nodo de
accesibilidad, así que colocar las complications a mano no se puede automatizar (Fase 3 del
plan de cierre). Con esta carátula el sistema las coloca solo, y lo que se ve en pantalla es
lo que una carátula real recibiría del reloj: el dato, el título y el arco del `RANGED_VALUE`.

Verificado el 2026-09-17 sobre el emulador `FinanceWatch` (Wear OS API 34) emparejado con el
Pixel 7 real: captura en `../capturas/2026-09-17_reloj_complications_en_caratula.png`.

## Cómo construirla e instalarla

No hay módulo Gradle: se compila con `aapt2` y se firma con la llave de debug. En Git Bash,
desde esta carpeta:

```bash
SDK="$LOCALAPPDATA/Android/Sdk"; BT="$SDK/build-tools/36.0.0"
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
"$BT/aapt2.exe" compile --dir res -o compiled.zip
"$BT/aapt2.exe" link -o unsigned.apk -I "$SDK/platforms/android-34/android.jar" --manifest AndroidManifest.xml compiled.zip
"$BT/zipalign.exe" -f 4 unsigned.apk aligned.apk
"$BT/apksigner.bat" sign --ks "$USERPROFILE/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android --out wff.apk aligned.apk
"$SDK/platform-tools/adb.exe" -s emulator-5556 install -r wff.apk
```

Para dejarla como carátula activa sin tocar el selector:

```bash
"$SDK/platform-tools/adb.exe" -s emulator-5556 shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE --es operation set-watchface --es watchFaceId mx.budget.wff.prueba
```

Después, apagar y encender la pantalla del emulador (`input keyevent KEYCODE_SLEEP` y
`KEYCODE_WAKEUP`) deja la carátula al frente. El reloj necesita un snapshot en `WearCache`
para que las complications tengan dato: con el Pixel 7 emparejado (`adb -s <pixel7> forward
tcp:5601 tcp:5601` y la companion "Wear OS" del teléfono) basta abrir la app en el teléfono.

## Límite conocido

El `DigitalClock` de esta carátula no pinta la hora en el emulador; no se investigó porque el
objetivo eran las complications, y la hora no forma parte de lo que se verifica.
