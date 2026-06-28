#!/usr/bin/env bash
# Instala y lanza el APK debug en un Pixel FÍSICO (no el emulador) y filtra los
# logs de la Capa 3 (Gemini Nano / §F.8) para confirmar si el LLM razonó o si
# cayó al fallback SQL.
#
# Uso (Git-bash, con el Pixel conectado por USB y depuración USB autorizada):
#   bash scripts/run_on_pixel9.sh
#
# Pre-requisitos en el teléfono (una sola vez):
#   1. Ajustes → Información del teléfono → toca "Número de compilación" 7 veces.
#   2. Ajustes → Sistema → Opciones para desarrolladores → activa "Depuración USB".
#   3. Conecta el USB y, en el prompt del teléfono, toca "Permitir" (marca
#      "Permitir siempre desde esta computadora").
set -euo pipefail

SDK="${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk"
ADB="$SDK/platform-tools/adb.exe"
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="mx.budget"

[ -x "$ADB" ] || { echo "✗ No encuentro adb en $ADB"; exit 1; }
[ -f "$APK" ] || { echo "✗ No existe $APK — corre primero: ./gradlew.bat :app:assembleDebug"; exit 1; }

# El primer device cuyo serial NO empiece con "emulator-" = el teléfono físico.
SERIAL="$("$ADB" devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}')"

if [ -z "${SERIAL:-}" ]; then
  echo "✗ No veo ningún Pixel físico conectado (solo el emulador, o ninguno)."
  echo "  Revisa: cable USB, 'Depuración USB' activada, y el prompt 'Permitir' en el teléfono."
  echo "  Diagnóstico: '$ADB' devices -l"
  exit 1
fi

MODEL="$("$ADB" -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
echo "✓ Dispositivo físico: $SERIAL ($MODEL)"

echo "→ Instalando APK…"
"$ADB" -s "$SERIAL" install -r "$APK"

echo "→ Lanzando $PKG…"
"$ADB" -s "$SERIAL" logcat -c
"$ADB" -s "$SERIAL" shell am start -n "$PKG/.MainActivity" >/dev/null

cat <<'NOTE'

──────────────────────────────────────────────────────────────────────────────
 Capa 3 (Gemini Nano) — qué esperar en el Pixel 9:
   • En el PRIMER uso, AICore puede estar DESCARGANDO el modelo (necesita Wi-Fi).
     Mientras tanto verás "fallback SQL" — es lo correcto. Reabre la app un par
     de veces en unos minutos.
   • Para disparar una sugerencia proactiva: necesitas que el día/hora actual
     coincida con un patrón histórico (p. ej. el hogar suele registrar algo los
     domingos por la mañana). En el Pixel real no puedes mover el reloj fácil; lo
     más simple es esperar a un momento con patrón, o probar al inicio de quincena.
   • Logs en vivo abajo (Ctrl+C para salir):
       - "AICore no disponible (...) → fallback SQL"  = NO usó el LLM
       - "Gemini Nano OK: reordenó N, top='…' motivo='…'" = SÍ razonó on-device
──────────────────────────────────────────────────────────────────────────────

NOTE

echo "→ Filtrando logs de ProactiveReasoner (Ctrl+C para salir)…"
"$ADB" -s "$SERIAL" logcat -s ProactiveReasoner:*
