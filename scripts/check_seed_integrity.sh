#!/usr/bin/env bash
#
# Verifica la integridad de la base de datos semilla.
#
# Compara el asset que SE EMBARCA en la app
#   app/src/main/assets/budget_database.db
# contra la copia "golden" inmutable (el Excel original convertido a DB)
#   seed/budget_database.golden.db
# y, si hay sqlite3 disponible, confirma el PRAGMA user_version del asset.
#
# Salida 0 = todo coincide. Salida !=0 = hay deriva (revisar antes de commitear).
#
# Uso:
#   bash scripts/check_seed_integrity.sh            # verifica
#   bash scripts/check_seed_integrity.sh --restore  # restaura el asset desde el golden
#
# Si REGENERAS el asset a propósito con el ETL (datos/reglas nuevas), actualiza el
# golden y su checksum DELIBERADAMENTE:
#   cp app/src/main/assets/budget_database.db seed/budget_database.golden.db
#   sha256sum seed/budget_database.golden.db | sed 's# .*/# #' \
#     > seed/budget_database.golden.db.sha256   # ó edítalo a mano: "<hash>  budget_database.golden.db"
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ASSET="$ROOT/app/src/main/assets/budget_database.db"
GOLDEN="$ROOT/seed/budget_database.golden.db"
CHECKSUM="$ROOT/seed/budget_database.golden.db.sha256"

fail() { echo "FAIL: $*" >&2; exit 1; }

[ -f "$ASSET" ]  || fail "no existe el asset: $ASSET"
[ -f "$GOLDEN" ] || fail "no existe el golden: $GOLDEN"

if [ "${1:-}" = "--restore" ]; then
  cp "$GOLDEN" "$ASSET"
  echo "OK: asset restaurado desde el golden."
  exit 0
fi

asset_hash="$(sha256sum "$ASSET"  | cut -d' ' -f1)"
golden_hash="$(sha256sum "$GOLDEN" | cut -d' ' -f1)"

# 1) El golden debe coincidir con su checksum registrado (no se corrompió/cambió).
if [ -f "$CHECKSUM" ]; then
  recorded="$(cut -d' ' -f1 "$CHECKSUM")"
  [ "$golden_hash" = "$recorded" ] || fail "el golden no coincide con su checksum registrado.
  registrado: $recorded
  golden:     $golden_hash"
fi

# 2) El asset que se embarca debe ser idéntico al golden.
if [ "$asset_hash" != "$golden_hash" ]; then
  fail "el asset embarcado DIFIERE del golden original.
  asset:  $asset_hash
  golden: $golden_hash
  -> si el cambio NO es intencional: bash scripts/check_seed_integrity.sh --restore
  -> si SÍ regeneraste el asset a propósito: actualiza seed/budget_database.golden.db y su .sha256"
fi

# 3) El asset DEBE declarar user_version=1 (si no, Room corre onCreate en fresh
#    install y crashea — ver CLAUDE.md §Room). Solo si hay sqlite3 en el host.
if command -v sqlite3 >/dev/null 2>&1; then
  uv="$(sqlite3 "$ASSET" 'PRAGMA user_version;')"
  [ "$uv" = "1" ] || fail "el asset tiene user_version=$uv (debe ser 1). Ver CLAUDE.md §Room."
  echo "OK: user_version=1."
else
  echo "AVISO: sqlite3 no está en el PATH; se omite la verificación de user_version."
fi

echo "OK: asset == golden ($asset_hash)."
