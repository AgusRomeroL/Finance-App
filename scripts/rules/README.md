# Pruebas de las reglas de Firestore

Comprueban `firestore.rules` contra el emulador de Firestore, que evalúa el mismo
archivo que se despliega. No hacen falta credenciales ni cuentas reales: el
emulador acepta identidades simuladas, así que la prueba responde la pregunta que
importa ("¿un Colaborador puede leer el ledger?") sin iniciar sesión con nadie.

La API de pruebas de la Rules API no sirve aquí: el service account del proyecto
no tiene el permiso `firebaserules.rulesets.test`.

## Correr

Necesita Node y un JDK en el PATH. En este equipo el JDK es el de Android Studio,
que **no** está en el PATH por defecto, así que hay que ponerlo. Además `npm ci`
falla sobre el volumen de Google Drive (`G:`): copia esta carpeta a un disco NTFS
(por ejemplo `C:\dev\finance-rules`) y corre ahí.

```bash
cp -r scripts/rules/. /c/dev/finance-rules/ && cp firestore.rules /c/dev/finance-rules/
cd /c/dev/finance-rules
npm ci
PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH" npm test
```

En PowerShell:

```powershell
$env:PATH = "C:\Program Files\Android\Android Studio\jbr\bin;$env:PATH"
npm test
```

El emulador lee `firestore.rules` desde la carpeta de la prueba, así que antes de
correr hay que copiar el de la raíz (arriba ya va en el `cp`). El
`package-lock.json` está versionado a propósito (el `.gitignore` global lo
excluye, se añadió con `git add -f`) para que `npm ci` sea reproducible aquí y
en CI.

## Qué cubre (Fase 7 del cierre)

395 casos en `test.mjs`:

- **Matriz rol × colección × operación.** Cinco identidades (Dueño,
  Administrador, Colaborador, el alias legacy `COLLABORATOR` y un miembro de
  otro hogar) contra las doce colecciones de datos del hogar, con `get`, `list`,
  `create`, `update` y `delete` cada una, más las atribuciones que cuelgan del
  gasto. El Colaborador lee solo `members`, `categories` y `wallets`; el ledger
  (`expenses`, `quincenas`, `income_source`, `wallet_transfer`, `savings_goal`,
  `loan`, `installment_plan`, `recurrence_template`, `statement_import`) queda
  para Dueño y Administrador, que son los únicos que escriben.
- **Sin sesión:** nada se lee ni se escribe.
- **Documento del hogar:** solo el Dueño lo edita o borra; un hogar sin
  `createdBy` lo reclama el primer autenticado a su nombre y ya no se vuelve a
  reclamar; nadie roba un hogar con dueño reescribiendo `createdBy`.
- **`roles/{uid}`:** el fundador se auto-asigna `OWNER` solo en el hogar que
  fundó; cualquier otro rol exige un invite real del hogar con el mismo rol y el
  mismo miembro nominado; nadie cambia su propio rol (ni el Dueño); solo el
  Dueño administra y expulsa.
- **`invites/{code}`:** cualquier autenticado lee uno por código pero nadie los
  enumera salvo el Dueño; el canje solo incrementa `uses`.
- **`proposals`:** cualquier miembro crea y lee; solo Dueño y Administrador
  resuelven o borran.
- **`users/{uid}/**`:** solo el propio uid.
- **`invite_codes/{code}`:** lectura por código para cualquier autenticado, sin
  enumeración; solo el Dueño del hogar apuntado crea o borra; el canje solo toca
  `uses`.
- **Sin comodín final:** una colección no declarada nace cerrada.

Al añadir una colección al sync hay que añadirla a `MEMBER_READABLE` o a
`LEDGER_ONLY` en `test.mjs` y la matriz la cubre sola.

## En CI

El trabajo `rules` de `.github/workflows/ci.yml` corre exactamente esto en cada
push y pull request a `develop` y `main`.

## Despliegue

`python scripts/admin/deploy_rules.py --service-account service-account.json`.
El deploy de hosting NO despliega reglas y la CLI exige un permiso de
`serviceusage` que el service account no tiene, por eso el script habla directo
con la Firebase Rules API. `--dry-run` solo imprime el ruleset activo.
