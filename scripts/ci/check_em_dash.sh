#!/usr/bin/env bash
#
# Regla "cero rayas largas" (U+2014) en integracion continua.
#
# Dos capas, en el mismo espiritu que el hook pre-commit global de esta maquina:
#
#  1. DIFF: falla si el rango de commits que se esta integrando INTRODUCE una
#     linea con U+2014 en cualquier archivo. Es lo unico que impide que vuelvan
#     mientras la documentacion historica sigue teniendo las suyas.
#  2. ARBOL: falla si alguna ruta de las que ya estan limpias (el codigo de la
#     app, del reloj, la web, las reglas y los scripts de CI) contiene una sola
#     raya larga en el estado actual, sin importar de donde vino.
#
# Uso:
#   bash scripts/ci/check_em_dash.sh <base-sha> <head-sha>    # diff + arbol
#   bash scripts/ci/check_em_dash.sh                          # solo arbol
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

EM=$(printf '\342\200\224')
status=0

# Rutas que deben seguir limpias. Ampliar conforme avance la limpieza de la
# documentacion (Fase 7 dejo el barrido de docs para despues).
CLEAN_PATHS=(
  "app/src" "app/build.gradle.kts" "app/schemas"
  "wear/src" "wear/build.gradle.kts"
  "wearcore"
  "web/src" "web/package.json" "web/vite.config.ts" "web/vitest.config.ts"
  "scripts/rules" "scripts/ci" "scripts/check_seed_integrity.sh"
  ".github"
  "firestore.rules" "build.gradle.kts" "settings.gradle.kts" "gradle.properties"
)

if [ "$#" -ge 2 ]; then
  base="$1"; head="$2"
  if git cat-file -e "$base^{commit}" 2>/dev/null; then
    added=$(git diff --unified=0 --no-color "$base" "$head" -- . ':(exclude)*.db' \
      | grep '^+' | grep -v '^+++' | grep -F "$EM" || true)
    if [ -n "$added" ]; then
      echo "FALLA: el rango $base..$head introduce rayas largas (U+2014):" >&2
      git diff --unified=0 --no-color "$base" "$head" -- . ':(exclude)*.db' \
        | grep -n '^+' | grep -v '^+++' | grep -F "$EM" | head -40 >&2
      status=1
    else
      echo "OK: el diff $base..$head no introduce U+2014."
    fi
  else
    echo "AVISO: no existe el commit base $base; se omite la revision del diff."
  fi
fi

existing=()
for p in "${CLEAN_PATHS[@]}"; do
  [ -e "$p" ] && existing+=("$p")
done
hits=$(git grep -n -I -F "$EM" -- "${existing[@]}" || true)
if [ -n "$hits" ]; then
  echo "FALLA: hay rayas largas (U+2014) en rutas que deben estar limpias:" >&2
  printf '%s\n' "$hits" | head -60 >&2
  status=1
else
  echo "OK: cero U+2014 en ${#existing[@]} rutas limpias."
fi

exit $status
