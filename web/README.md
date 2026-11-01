# Presupuesto Familiar: Web de colaboradores (paquete B4)

Web SPA que permite a **colaboradores** (miembros invitados por código, sin la app Android)
participar en el presupuesto familiar: iniciar sesión con Google, unirse a un grupo,
**proponer** gastos/pagos futuros y ver un **dashboard de solo lectura** de la quincena activa.

Comparte el mismo proyecto Firebase (`finance-app-abdf9`) y el **mismo contrato Firestore**
que la app Android. La web NO es la fuente de verdad: solo lee y escribe **propuestas**
(`proposals`) y su membresía (`roles` + espejo en `users`). El titular aprueba desde la app.

## Stack

- **Vite** + **React 18** + **TypeScript** (estricto) + **Tailwind CSS**
- **Firebase JS SDK v10** (modular): Auth (Google popup) + Firestore
- SPA, sin SSR. Deploy previsto: **Firebase Hosting**.

## Requisitos

- Node 18+ (probado con Node 24) y npm.

> **Gotcha de Google Drive (importante):** este repo vive en `G:` (Google Drive File
> Stream), y `npm install` allí falla o se cuelga con errores `TAR_ENTRY_ERROR` / `EPERM` /
> `EBADF` por el I/O masivo de archivos pequeños que hace npm sobre el volumen virtual.
> **Workaround verificado:** copia `web/` (sin `node_modules`/`dist`) a un disco local NTFS
> (p. ej. `C:\dev\finance-web`), corre ahí `npm install` / `npm run build` / `npm run dev`,
> y para desplegar corre `firebase deploy` desde esa copia local (o copia solo `dist/` de
> vuelta). Un junction de `node_modules` NO funciona: Windows rechaza junctions que cruzan
> del volumen de Drive a NTFS. El `package-lock.json` sí está commiteado para instalaciones
> reproducibles (`npm ci`).

## Comandos

```bash
cd web
npm install        # instala dependencias
npm run dev        # servidor de desarrollo (http://localhost:5173)
npm run build      # type-check (tsc -b) + build de producción -> web/dist
npm run preview    # sirve el build de producción localmente
npm run lint       # solo type-check (tsc --noEmit)
npm test           # Vitest: normalizacion dual camelCase/snake_case y rutas por rol
```

## Configuración de Firebase

Los valores de cliente web de Firebase **no son secretos** (son config pública que el SDK
expone en el bundle). Vienen embebidos en `src/lib/firebase.ts`, así que el build de
producción no necesita ningún `.env`:

| Dato | Valor |
|------|-------|
| projectId | `finance-app-abdf9` |
| apiKey | `AIzaSyBPv01mYVEpURyvHilB982y7ILiFiF2zjw` |
| authDomain | `finance-app-abdf9.firebaseapp.com` |
| storageBucket | `finance-app-abdf9.firebasestorage.app` |
| messagingSenderId | `318390629591` |
| appId | `1:318390629591:web:860f1bcec6aff331c0ec92` (app web "Presupuesto Web") |

El `appId` es el de la app **Web** registrada el 2026-07-09 con
`scripts/admin/firebase_setup.py` y confirmada activa el 2026-10-31 (Fase 8). Hasta entonces el
código caía al appId de Android, que el SDK JS acepta pero registra mal.

### Variables de entorno (opcional)

Copia `web/.env.example` a `web/.env` solo para apuntar a otro proyecto: todas las variables
son opcionales.

## Formato del código de invitación (DECISIÓN)

El dueño comparte **un solo string**:

```
{hid}.{code}
```

- `{hid}` = id del documento del household (autogenerado por Firestore).
- `{code}` = el secreto de **8 caracteres A-Z0-9**: es el id del doc `invites/{code}` y ES el secreto.
- Separador: un **punto** (`.`). La web parte por el **último** punto (tolera hids con puntos).

Ejemplo: `AbC123xy.7QK9M2ZP`.

Se eligió este formato (en vez de pedir hid y code por separado) porque es un solo dato que
copiar/pegar y no expone el secreto sin el hid. La validación de `expiresAt`/`maxUses` se hace
**en cliente** como UX, pero **las reglas de Firestore son la autoridad** (las escribe otro paquete).

## Autorización

La **única** capa de autorización son las **reglas de Firestore** (fuera de `web/`, las escribe
otro paquete). La web asume que:

- Un usuario autenticado puede leer/escribir `users/{suUid}` y su subcolección `households`.
- Un colaborador puede leer `households/{hid}` (name, categories, quincenas, expenses POSTED) si
  tiene un doc en `households/{hid}/roles/{suUid}`.
- Cualquier autenticado con un `invites/{code}` válido puede crear su `roles/{uid}` e incrementar `uses`.
- Un colaborador puede **crear** documentos en `households/{hid}/proposals` (nunca en `expenses`).

Si las reglas aún no están desplegadas o Auth no está habilitado, las operaciones fallan con
`permission-denied` / `configuration-not-found` (la UI muestra el error).

## Contrato Firestore usado

Todos los campos en **camelCase**; fechas en **epoch millis (number)**; montos MXN (number).

```
users/{uid} → { displayName, email, photoUrl, activeHouseholdId }
users/{uid}/households/{hid} → { role, joinedAt }
households/{hid} → { name, currency:"MXN", timezone:"America/Mexico_City" }
households/{hid}/roles/{uid} → { role, linkedMemberId?, displayName }
households/{hid}/invites/{code} → { role, expiresAt, maxUses, uses, createdBy }
households/{hid}/members/{id} → { displayName, role, isActive, shortAliases, meta, updatedAt }
households/{hid}/categories/{id} → { displayName, code, parentId?, kind, colorHex?, budgetDefaultMxn?, sortOrder, updatedAt }
households/{hid}/quincenas/{id} → { year, month, half, startDate, endDate, label, status, projected/actual Income/Expenses Mxn }
households/{hid}/expenses/{id} → { concept, amountMxn, categoryId, quincenaId, occurredAt, paymentMethodId, status, settlementStatus, externalPayerMemberId?, updatedAt } (+ attributions/{id})
households/{hid}/proposals/{id} → { kind, concept, amountMxn, occurredAt, categoryId?, note?, proposedByUid, proposedByName, createdAt, status:"PENDING" }
```

## Despliegue (Firebase Hosting)

Se despliega **siempre** con el script de la Fase 8, desde la raíz del repo:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\web\deploy.ps1
```

El script sincroniza `web/` a la copia NTFS `C:\dev\finance-web` (npm no funciona en `G:`),
corre `npm ci` si cambió el lockfile, `npm test` y `npm run build`, escribe `dist/version.json`
con el commit y despliega solo hosting con una versión fija de `firebase-tools` vía `npx`. No
hace login: la primera vez hay que correr `npx --yes firebase-tools@15.34.0 login`. Para saber
qué commit se sirve: `https://finance-app-abdf9.web.app/version.json`.

La config de hosting vive **dentro de `web/`** (`web/firebase.json` + `web/.firebaserc`), para no
tocar la raíz del repo. `firebase.json` sirve `dist/` con rewrite SPA (`** → /index.html`).

> Si se integra al `firebase.json` de la raíz más adelante, mueve el bloque `hosting` allá con
> `"public": "web/dist"` y ajusta rutas. Por ahora se ejecuta `firebase deploy` desde `web/`.

## Qué falta para producción

1. **Habilitar Authentication → proveedor Google** en la consola de Firebase (hoy el proyecto
   nunca inicializó Auth → `configuration-not-found`). Sin esto el login no funciona.
2. ~~Registrar la app Web~~: hecho el 2026-07-09; su `appId` va embebido desde la Fase 8.
3. **Añadir el dominio de Hosting a los dominios autorizados de Auth** (Console → Auth → Settings
   → Authorized domains) para que `signInWithPopup` funcione en prod.
4. **Reglas de Firestore** que cubran `proposals`, `roles`, `invites` y lecturas de colaborador
   (las escribe otro paquete; son la capa de autorización real).
5. Opcional: índice compuesto si se quiere ordenar `expenses` por `occurredAt` en el servidor
   (hoy se ordena en cliente para evitarlo).

## Estructura

```
web/
├── index.html
├── package.json / tsconfig.json / vite.config.ts
├── tailwind.config.js / postcss.config.js
├── firebase.json / .firebaserc         # hosting (dentro de web/)
├── .env.example
├── public/favicon.svg
└── src/
    ├── main.tsx / App.tsx / index.css
    ├── vite-env.d.ts
    ├── lib/
    │   ├── firebase.ts       # init SDK (config con defaults + env)
    │   ├── types.ts          # tipos = contrato Firestore exacto
    │   ├── repository.ts     # todas las ops Firestore + parseInviteCode
    │   └── format.ts         # MXN + fechas MX
    ├── context/
    │   ├── AuthContext.tsx       # login Google, upsert users/{uid}
    │   └── HouseholdContext.tsx  # grupos + household activo
    ├── components/
    │   ├── ui.tsx            # Button, Card, Loading/Error/Empty, Spinner
    │   └── AppLayout.tsx     # header + nav
    └── pages/
        ├── LoginPage.tsx
        ├── GroupsPage.tsx    # mis grupos, crear, unirse por código
        ├── DashboardPage.tsx # quincena activa, presupuesto vs gasto (read-only)
        └── ProposePage.tsx   # proponer gasto / pago futuro
```
