#!/usr/bin/env bash
#
# Instala los APK de un modulo en el dispositivo conectado y corre sus pruebas
# instrumentadas con `am instrument -w`, que es lo que funciona contra los AVD
# de Wear (connectedDebugAndroidTest falla al recoger los resultados de UTP
# aunque las pruebas pasen). Sirve igual en CI y en esta maquina.
#
# Uso:
#   bash scripts/ci/run_instrumented.sh app  [serial]
#   bash scripts/ci/run_instrumented.sh wear [serial]
#
# Antes hay que construir los APK:
#   ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest
#   ./gradlew.bat :wear:assembleDebug :wear:assembleDebugAndroidTest
#
# Sale con 0 solo si `am instrument` reporta "OK (N tests)". La salida completa
# queda en build/instrumented/<modulo>.txt.
set -uo pipefail

module="${1:?modulo: app | wear}"
serial="${2:-}"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

ADB="${ADB:-adb}"
if ! command -v "$ADB" >/dev/null 2>&1; then
  ADB="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$LOCALAPPDATA/Android/Sdk}}/platform-tools/adb"
fi
[ -n "$serial" ] && ADB="$ADB -s $serial"

case "$module" in
  app)
    apk="app/build/outputs/apk/debug/app-debug.apk"
    test_apk="app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
    runner="mx.budget.test/mx.budget.testing.PlainAppRunner"
    ;;
  wear)
    apk="wear/build/outputs/apk/debug/wear-debug.apk"
    test_apk="wear/build/outputs/apk/androidTest/debug/wear-debug-androidTest.apk"
    runner="mx.budget.test/androidx.test.runner.AndroidJUnitRunner"
    ;;
  *) echo "modulo desconocido: $module" >&2; exit 2 ;;
esac

[ -f "$apk" ] || { echo "falta $apk (construye primero)" >&2; exit 2; }
[ -f "$test_apk" ] || { echo "falta $test_apk (construye primero)" >&2; exit 2; }

mkdir -p build/instrumented
out="build/instrumented/$module.txt"

$ADB wait-for-device
$ADB install -r -t "$apk"
$ADB install -r -t "$test_apk"
$ADB shell am instrument -w -r "$runner" 2>&1 | tee "$out"

if grep -q "^OK (" "$out" && ! grep -q "FAILURES!!!" "$out" && ! grep -q "INSTRUMENTATION_RESULT: shortMsg=" "$out"; then
  echo "PASA: $(grep '^OK (' "$out")"
  exit 0
fi
echo "FALLA: revisa $out" >&2
exit 1
