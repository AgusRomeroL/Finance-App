# Semilla original (golden) — base de datos del Excel

Este directorio guarda la **copia inmutable** de la base de datos original: el Excel
real de 33 hojas (ene-2025 → jun-2026) convertido a SQLite por el ETL. Es la fuente
de verdad histórica del hogar (793 gastos sembrados). **No la edites a mano.**

## Archivos

- `budget_database.golden.db` — la DB original, congelada. Schema **v1**, `PRAGMA user_version = 1`.
- `budget_database.golden.db.sha256` — su checksum, para detectar cualquier deriva.

## Invariante

El asset que **se embarca** en la app —
`app/src/main/assets/budget_database.db` — debe ser **idéntico** a este golden,
salvo que regeneres la semilla a propósito (ver abajo). Así, por más cambios que
hagamos en el código (migraciones Room en runtime, pruebas, manipulación en el
emulador), la versión final de la app siempre arranca con los datos originales.

> Las migraciones de esquema NO cambian este archivo: el asset se queda en v1 y Room
> aplica `MIGRATION_1_2`, `MIGRATION_2_3`, … en el dispositivo al primer arranque
> (ver `CLAUDE.md` §Room). Por eso el golden casi nunca debería cambiar.

## Verificar integridad

```bash
bash scripts/check_seed_integrity.sh
```
Confirma que (1) el golden coincide con su checksum, (2) el asset embarcado es
idéntico al golden, y (3) el asset declara `user_version=1`.

## Restaurar el asset desde el golden

Si el asset se corrompió o cambió sin querer (p. ej. una prueba lo tocó):

```bash
bash scripts/check_seed_integrity.sh --restore
```

## Regenerar la semilla a propósito (datos/reglas nuevas del ETL)

Solo cuando el Excel o las reglas de atribución cambien de verdad:

```bash
python scripts/etl/excel_to_room_etl.py --excel "<ruta.xlsx>" --output app/src/main/assets/budget_database.db
python scripts/verify_db.py            # renombra household.id -> "default_household"
# fija user_version=1 ANTES de promover (CLAUDE.md §Room):
sqlite3 app/src/main/assets/budget_database.db 'PRAGMA user_version = 1;'
# promueve el nuevo asset a golden y actualiza el checksum:
cp app/src/main/assets/budget_database.db seed/budget_database.golden.db
printf '%s  budget_database.golden.db\n' \
  "$(sha256sum seed/budget_database.golden.db | cut -d' ' -f1)" \
  > seed/budget_database.golden.db.sha256
```
Commitea el golden, su `.sha256` y el asset juntos, con un mensaje que explique
por qué cambió la semilla.
