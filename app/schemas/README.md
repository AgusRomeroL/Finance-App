# Schemas exportados de Room (`mx.budget.data.local.BudgetDatabase`)

JSONs generados por KSP (`room.schemaLocation`) con el esquema de cada versión
de la base. KSP solo genera el JSON de la **versión actual** al compilar; los
anteriores quedan como registro histórico y son el insumo de
`MigrationTestHelper`.

## Hueco conocido: falta `17.json`

Existen `1.json`…`16.json` y `18.json`, pero **no hay `17.json`** en esta
línea, y no es regenerable honestamente:

- La versión 17 de esta cadena (integración `feat/integration-2026-07`:
  `MIGRATION_16_17` = columnas de esquema de pago en `loan`) **nunca se
  compiló con `exportSchema`**: el trabajo saltó de v16 a v18 dentro del mismo
  ciclo de desarrollo, así que KSP jamás emitió su JSON.
- La numeración **divergió** con la rama `expressive-ux`: en aquella línea sí
  existió un `17.json` (recuperable en el commit `7a416d2`), pero describe un
  esquema DISTINTO — ya traía `installment_plan.funding_payment_method_id` y
  las columnas de reembolso de `recurrence_template` (que aquí añade
  `MIGRATION_17_18`) y **no** tenía la tabla `statement_line` (que aquí nace
  en v15→v16). Restaurar ese archivo mentiría sobre la v17 de esta cadena;
  por eso `MIGRATION_16_17`/`MIGRATION_17_18` son idempotentes
  (`addColumnIfMissing` + `CREATE TABLE IF NOT EXISTS`) para absorber ambas
  formas de v17.
- Reconstruir `17.json` a mano (copiar `18.json` y quitar lo de
  `MIGRATION_17_18`) daría las tablas correctas pero **no el `identityHash`**,
  que solo lo calcula el compilador de Room. Un hash inventado sería peor que
  el hueco: `MigrationTestHelper` validaría contra un artefacto falso.

**Consecuencias prácticas:**

- `18.json` contiene el esquema final **verificado** (es el que KSP genera y
  Room valida en runtime); la cadena completa 1→18 sí es ejercitable
  extremo a extremo.
- `MigrationTestHelper` **no puede ejercitar 16→17 ni 17→18 aisladas** (no hay
  `17.json` contra el cual abrir/validar). La cobertura equivalente es probar
  16→18 corriendo ambas migraciones en cadena.

Si en el futuro una versión N se salta de nuevo, preferir documentarlo aquí
antes que fabricar el JSON a mano.
