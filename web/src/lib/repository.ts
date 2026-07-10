import {
  collection,
  doc,
  getDoc,
  getDocs,
  setDoc,
  updateDoc,
  addDoc,
  query,
  where,
  limit,
  increment,
  writeBatch,
} from 'firebase/firestore'
import type { User } from 'firebase/auth'
import { db } from './firebase'
import type {
  AttributionInput,
  CategoryWithId,
  ExpenseInput,
  ExpenseWithId,
  Household,
  HouseholdRole,
  HouseholdWithId,
  Invite,
  MemberWithId,
  Proposal,
  ProposalKind,
  ProposalWithId,
  QuincenaWithId,
  Role,
  UserDoc,
  UserHouseholdRef,
  WalletWithId,
} from './types'

/* ---------------------------------------------------------------------------
 * Normalización camelCase / snake_case
 * ---------------------------------------------------------------------------
 * Los docs que escribe Android (push del sync, `set(entity)`) van en camelCase;
 * los que sembró `scripts/seed_firebase.py` van en snake_case (dump directo de
 * SQLite). El pull de Android (FirestoreMappers.kt) lee con fallback dual; la
 * web hace lo mismo con `pick()`. Al ESCRIBIR, la web siempre usa camelCase
 * (la variante que Android lee primero) y, para campos que Android relee de
 * docs seed (p. ej. el saldo del wallet), también actualiza la variante
 * snake_case si el doc ya la traía, para no dejar valores viejos divergentes.
 * ------------------------------------------------------------------------- */

type FirestoreData = Record<string, unknown>

/** Lee un campo probando primero camelCase y luego snake_case. */
function pick<T>(data: FirestoreData, camel: string, snake: string): T | undefined {
  const v = data[camel] !== undefined && data[camel] !== null ? data[camel] : data[snake]
  return v === undefined || v === null ? undefined : (v as T)
}

function pickNum(data: FirestoreData, camel: string, snake: string): number | undefined {
  const v = pick<unknown>(data, camel, snake)
  return typeof v === 'number' && Number.isFinite(v) ? v : undefined
}

function pickStr(data: FirestoreData, camel: string, snake: string): string | undefined {
  const v = pick<unknown>(data, camel, snake)
  return typeof v === 'string' ? v : undefined
}

function round2(x: number): number {
  return Math.round(x * 100) / 100
}

/* ---------------------------------------------------------------------------
 * Formato del código de invitación
 * ---------------------------------------------------------------------------
 * El dueño comparte un solo string: `{hid}.{code}`
 *   - {hid}  = id del documento del household (autogenerado por Firestore)
 *   - {code} = el secreto de 8 chars A-Z0-9 (el id del doc invite; ES el secreto)
 * separados por un punto ".". La web parte por el ÚLTIMO punto para tolerar
 * hids que (teóricamente) contengan puntos.
 * ------------------------------------------------------------------------- */
export function parseInviteCode(raw: string): { hid: string; code: string } | null {
  const trimmed = raw.trim()
  const idx = trimmed.lastIndexOf('.')
  if (idx <= 0 || idx === trimmed.length - 1) return null
  const hid = trimmed.slice(0, idx).trim()
  const code = trimmed.slice(idx + 1).trim().toUpperCase()
  if (!hid || !/^[A-Z0-9]{8}$/.test(code)) return null
  return { hid, code }
}

/* --------------------------------- Users --------------------------------- */

export async function upsertUser(user: User): Promise<void> {
  const ref = doc(db, 'users', user.uid)
  const snap = await getDoc(ref)
  const base: Partial<UserDoc> = {
    displayName: user.displayName ?? '',
    email: user.email ?? '',
    photoUrl: user.photoURL ?? '',
  }
  if (snap.exists()) {
    await updateDoc(ref, base)
  } else {
    await setDoc(ref, { ...base, activeHouseholdId: '' })
  }
}

export async function getUserDoc(uid: string): Promise<UserDoc | null> {
  const snap = await getDoc(doc(db, 'users', uid))
  return snap.exists() ? (snap.data() as UserDoc) : null
}

export async function setActiveHousehold(uid: string, hid: string): Promise<void> {
  await setDoc(doc(db, 'users', uid), { activeHouseholdId: hid }, { merge: true })
}

/* ------------------------------- Households ------------------------------ */

/** Lista los households del usuario leyendo users/{uid}/households y resolviendo el nombre. */
export async function listUserHouseholds(uid: string): Promise<HouseholdWithId[]> {
  const refsSnap = await getDocs(collection(db, 'users', uid, 'households'))
  const results = await Promise.all(
    refsSnap.docs.map(async (d) => {
      const ref = d.data() as UserHouseholdRef
      const hid = d.id
      const hSnap = await getDoc(doc(db, 'households', hid))
      const h = hSnap.data() as Household | undefined
      return {
        id: hid,
        role: ref.role,
        name: h?.name ?? '(sin nombre)',
        currency: h?.currency ?? 'MXN',
        timezone: h?.timezone ?? 'America/Mexico_City',
        createdBy: h?.createdBy,
        updatedAt: h?.updatedAt,
      } satisfies HouseholdWithId
    }),
  )
  return results
}

/**
 * Crea un household nuevo, el rol OWNER y el espejo en users/{uid}/households.
 * Las reglas exigen `createdBy == auth.uid` en el create del household, y solo
 * permiten crear el rol OWNER si el household quedó a tu nombre — por eso el
 * doc del hogar se escribe PRIMERO.
 */
export async function createHousehold(uid: string, name: string, displayName: string): Promise<string> {
  const householdsCol = collection(db, 'households')
  const hRef = doc(householdsCol)
  const hid = hRef.id

  const household: Household = {
    name: name.trim(),
    currency: 'MXN',
    timezone: 'America/Mexico_City',
    createdBy: uid,
    updatedAt: Date.now(),
  }
  await setDoc(hRef, household)

  const role: HouseholdRole = { role: 'OWNER', displayName }
  await setDoc(doc(db, 'households', hid, 'roles', uid), role)

  const mirror: UserHouseholdRef = { role: 'OWNER', joinedAt: Date.now() }
  await setDoc(doc(db, 'users', uid, 'households', hid), mirror)

  return hid
}

/* ------------------------------- Roles ----------------------------------- */

/**
 * Rol del usuario en el household leyendo households/{hid}/roles/{uid}.
 * Devuelve null si no hay doc de rol (no es miembro) o si el valor es raro.
 * La UI bifurca con esto: OWNER captura gastos reales; COLLABORATOR propone.
 */
export async function getMyRole(hid: string, uid: string): Promise<Role | null> {
  const snap = await getDoc(doc(db, 'households', hid, 'roles', uid))
  if (!snap.exists()) return null
  const role = pickStr(snap.data() as FirestoreData, 'role', 'role')
  if (role === 'OWNER') return 'OWNER'
  if (role === 'COLLABORATOR') return 'COLLABORATOR'
  return null
}

/* --------------------------------- Invites -------------------------------- */

export interface JoinResult {
  hid: string
  role: Role
}

/**
 * Une al usuario a un household usando un código `{hid}.{code}`.
 * Valida expiresAt/maxUses del lado cliente (las reglas lo refuerzan server-side).
 */
export async function joinByCode(
  user: User,
  rawCode: string,
  displayName: string,
): Promise<JoinResult> {
  const parsed = parseInviteCode(rawCode)
  if (!parsed) {
    throw new Error('Código inválido. Formato esperado: IDGRUPO.CODIGO8')
  }
  const { hid, code } = parsed

  const inviteRef = doc(db, 'households', hid, 'invites', code)
  const inviteSnap = await getDoc(inviteRef)
  if (!inviteSnap.exists()) {
    throw new Error('El código no existe o ya fue eliminado.')
  }
  const invite = inviteSnap.data() as Invite

  if (typeof invite.expiresAt === 'number' && invite.expiresAt > 0 && invite.expiresAt < Date.now()) {
    throw new Error('El código de invitación expiró.')
  }
  if (typeof invite.maxUses === 'number' && invite.maxUses > 0 && (invite.uses ?? 0) >= invite.maxUses) {
    throw new Error('El código de invitación alcanzó su número máximo de usos.')
  }

  const role: Role = invite.role ?? 'COLLABORATOR'

  // Crea el rol en el household. `inviteCode` es OBLIGATORIO para las reglas:
  // el create de un rol no-OWNER solo pasa si existe invites/{inviteCode} y su
  // `role` coincide con el que el usuario se asigna.
  const roleDoc: HouseholdRole = { role, displayName, inviteCode: code }
  await setDoc(doc(db, 'households', hid, 'roles', user.uid), roleDoc)

  // Espejo en users/{uid}/households/{hid}.
  const mirror: UserHouseholdRef = { role, joinedAt: Date.now() }
  await setDoc(doc(db, 'users', user.uid, 'households', hid), mirror)

  // Incrementa el contador de usos (atómico).
  await updateDoc(inviteRef, { uses: increment(1) })

  return { hid, role }
}

/* -------------------------------- Categories ------------------------------ */

function normCategory(id: string, data: FirestoreData): CategoryWithId {
  return {
    id,
    displayName: pickStr(data, 'displayName', 'display_name') ?? '(sin nombre)',
    code: pickStr(data, 'code', 'code') ?? '',
    parentId: pickStr(data, 'parentId', 'parent_id'),
    kind: pickStr(data, 'kind', 'kind') ?? 'EXPENSE',
    colorHex: pickStr(data, 'colorHex', 'color_hex'),
    budgetDefaultMxn: pickNum(data, 'budgetDefaultMxn', 'budget_default_mxn'),
    sortOrder: pickNum(data, 'sortOrder', 'sort_order') ?? 0,
    updatedAt: pickNum(data, 'updatedAt', 'updated_at') ?? 0,
  }
}

/**
 * Categorías del hogar. SIN `orderBy` en el servidor: Firestore EXCLUYE del
 * resultado los docs que no tienen el campo del orderBy, y los docs sembrados
 * en snake_case no tienen `sortOrder` — un orderBy('sortOrder') los haría
 * desaparecer. Se ordena en cliente con fallback dual.
 */
export async function listCategories(hid: string): Promise<CategoryWithId[]> {
  const snap = await getDocs(collection(db, 'households', hid, 'categories'))
  const rows = snap.docs.map((d) => normCategory(d.id, d.data() as FirestoreData))
  return rows.sort(
    (a, b) => a.sortOrder - b.sortOrder || a.displayName.localeCompare(b.displayName, 'es'),
  )
}

/* -------------------------------- Members --------------------------------- */

function normMember(id: string, data: FirestoreData): MemberWithId {
  return {
    id,
    displayName: pickStr(data, 'displayName', 'display_name') ?? '(sin nombre)',
    role: pickStr(data, 'role', 'role') ?? '',
    isActive: pick<boolean>(data, 'isActive', 'is_active') ?? true,
    shortAliases: pick<string | string[]>(data, 'shortAliases', 'short_aliases'),
    meta: pick<unknown>(data, 'meta', 'meta'),
    updatedAt: pickNum(data, 'updatedAt', 'updated_at') ?? 0,
  }
}

/** Miembros del hogar (para la atribución de la captura). Activos primero. */
export async function listMembers(hid: string): Promise<MemberWithId[]> {
  const snap = await getDocs(collection(db, 'households', hid, 'members'))
  const rows = snap.docs.map((d) => normMember(d.id, d.data() as FirestoreData))
  return rows.sort(
    (a, b) =>
      Number(b.isActive) - Number(a.isActive) ||
      a.displayName.localeCompare(b.displayName, 'es'),
  )
}

/* -------------------------------- Wallets --------------------------------- */

function normWallet(id: string, data: FirestoreData): WalletWithId {
  return {
    id,
    displayName: pickStr(data, 'displayName', 'display_name') ?? '(sin nombre)',
    kind: pickStr(data, 'kind', 'kind') ?? '',
    issuer: pickStr(data, 'issuer', 'issuer'),
    last4: pickStr(data, 'last4', 'last4'),
    currentBalanceMxn: pickNum(data, 'currentBalanceMxn', 'current_balance_mxn') ?? 0,
    ownerMemberId: pickStr(data, 'ownerMemberId', 'owner_member_id'),
    isActive: pick<boolean>(data, 'isActive', 'is_active') ?? true,
    updatedAt: pickNum(data, 'updatedAt', 'updated_at') ?? 0,
  }
}

/** Wallets (payment_method) del hogar, para elegir método de pago en la captura. */
export async function listWallets(hid: string): Promise<WalletWithId[]> {
  const snap = await getDocs(collection(db, 'households', hid, 'wallets'))
  const rows = snap.docs.map((d) => normWallet(d.id, d.data() as FirestoreData))
  return rows.sort(
    (a, b) =>
      Number(b.isActive) - Number(a.isActive) ||
      a.displayName.localeCompare(b.displayName, 'es'),
  )
}

/**
 * Update parcial del saldo de un wallet. Escribe camelCase (lo que Android lee
 * primero) y, si el doc traía la variante snake_case (docs seed), TAMBIÉN la
 * actualiza para no dejar un valor viejo divergente en el mismo doc.
 */
function walletBalanceUpdate(existing: FirestoreData, newBalance: number): FirestoreData {
  const now = Date.now()
  const update: FirestoreData = { currentBalanceMxn: round2(newBalance), updatedAt: now }
  if ('current_balance_mxn' in existing) update.current_balance_mxn = round2(newBalance)
  if ('updated_at' in existing) update.updated_at = now
  return update
}

/* -------------------------------- Quincenas ------------------------------- */

function normQuincena(id: string, data: FirestoreData): QuincenaWithId {
  return {
    id,
    year: pickNum(data, 'year', 'year') ?? 0,
    month: pickNum(data, 'month', 'month') ?? 0,
    half: pick<number | string>(data, 'half', 'half') ?? 0,
    startDate: pick<string | number>(data, 'startDate', 'start_date') ?? 0,
    endDate: pick<string | number>(data, 'endDate', 'end_date') ?? 0,
    label: pickStr(data, 'label', 'label') ?? '',
    status: pickStr(data, 'status', 'status') ?? '',
    projectedIncomeMxn: pickNum(data, 'projectedIncomeMxn', 'projected_income_mxn') ?? 0,
    actualIncomeMxn: pickNum(data, 'actualIncomeMxn', 'actual_income_mxn') ?? 0,
    projectedExpensesMxn: pickNum(data, 'projectedExpensesMxn', 'projected_expenses_mxn') ?? 0,
    actualExpensesMxn: pickNum(data, 'actualExpensesMxn', 'actual_expenses_mxn') ?? 0,
  }
}

/** Devuelve la quincena con status ACTIVE del household (o null). */
export async function getActiveQuincena(hid: string): Promise<QuincenaWithId | null> {
  const snap = await getDocs(
    query(collection(db, 'households', hid, 'quincenas'), where('status', '==', 'ACTIVE'), limit(1)),
  )
  if (snap.empty) return null
  const d = snap.docs[0]
  return normQuincena(d.id, d.data() as FirestoreData)
}

/** Fecha calendario "YYYY-MM-DD" de un epoch ms en America/Mexico_City. */
const MX_DATE_FMT = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Mexico_City',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

function epochToMxDateStr(epochMs: number): string {
  return MX_DATE_FMT.format(new Date(epochMs))
}

/**
 * Normaliza un límite de quincena a "YYYY-MM-DD": Room lo persiste como string
 * ISO; docs muy legados podrían traer epoch ms (se convierte en zona MX).
 */
function toDateStr(v: string | number): string | null {
  if (typeof v === 'string') return v.length >= 10 ? v.slice(0, 10) : null
  if (typeof v === 'number' && Number.isFinite(v) && v > 0) return epochToMxDateStr(v)
  return null
}

/**
 * Resuelve la quincena cuyo rango [startDate, endDate] contiene el occurredAt
 * (comparación de fechas calendario en America/Mexico_City — los límites son
 * fechas, no instantes). Lanza error claro si ninguna quincena contiene la
 * fecha: el gasto NO debe crearse con quincenaId inventado.
 */
async function findQuincenaForDate(hid: string, occurredAtMs: number): Promise<QuincenaWithId> {
  const dateStr = epochToMxDateStr(occurredAtMs)
  const snap = await getDocs(collection(db, 'households', hid, 'quincenas'))
  const quincenas = snap.docs.map((d) => normQuincena(d.id, d.data() as FirestoreData))
  const match = quincenas.find((q) => {
    const start = toDateStr(q.startDate)
    const end = toDateStr(q.endDate)
    return start !== null && end !== null && start <= dateStr && dateStr <= end
  })
  if (!match) {
    throw new Error(
      `No existe una quincena que contenga la fecha ${dateStr}. ` +
        'El titular debe abrir esa quincena en la app antes de registrar el gasto.',
    )
  }
  return match
}

/* -------------------------------- Expenses -------------------------------- */

function normExpense(id: string, data: FirestoreData): ExpenseWithId {
  return {
    id,
    concept: pickStr(data, 'concept', 'concept') ?? '',
    amountMxn: pickNum(data, 'amountMxn', 'amount_mxn') ?? 0,
    categoryId: pickStr(data, 'categoryId', 'category_id') ?? '',
    quincenaId: pickStr(data, 'quincenaId', 'quincena_id') ?? '',
    occurredAt: pickNum(data, 'occurredAt', 'occurred_at') ?? 0,
    paymentMethodId: pickStr(data, 'paymentMethodId', 'payment_method_id'),
    status: pickStr(data, 'status', 'status') ?? 'POSTED',
    settlementStatus: pickStr(data, 'settlementStatus', 'settlement_status'),
    externalPayerMemberId: pickStr(data, 'externalPayerMemberId', 'external_payer_member_id'),
    notes: pickStr(data, 'notes', 'notes') ?? null,
    householdId: pickStr(data, 'householdId', 'household_id'),
    createdAt: pickNum(data, 'createdAt', 'created_at'),
    updatedAt: pickNum(data, 'updatedAt', 'updated_at') ?? 0,
    deletedAt: pickNum(data, 'deletedAt', 'deleted_at'),
  }
}

/** ¿El doc es una lápida (soft-delete)? Las lecturas deben excluirlo. */
function isTombstoned(e: ExpenseWithId): boolean {
  return (e.deletedAt ?? 0) > 0
}

/**
 * Gastos POSTED de una quincena, ordenados por fecha desc.
 *
 * La query del servidor filtra SOLO por status (campo con el mismo nombre en
 * ambas variantes); la quincena se filtra en CLIENTE leyendo quincenaId con
 * fallback dual — un where('quincenaId'==...) no matchearía los docs seed que
 * solo traen `quincena_id`. Los volúmenes por hogar son chicos (cientos de
 * docs), así que el filtro en cliente es aceptable.
 */
export async function listPostedExpenses(hid: string, quincenaId: string): Promise<ExpenseWithId[]> {
  const snap = await getDocs(
    query(collection(db, 'households', hid, 'expenses'), where('status', '==', 'POSTED')),
  )
  const rows = snap.docs
    .map((d) => normExpense(d.id, d.data() as FirestoreData))
    // Lápidas fuera: una lápida "limpia" ni siquiera matchea el where de
    // status, pero una zombi (campos vivos + deletedAt por un merge posterior)
    // sí llegaría — el filtro en cliente cubre ambos casos.
    .filter((e) => e.quincenaId === quincenaId && !isTombstoned(e))
  // Ordenamos en cliente para no exigir un índice compuesto de Firestore.
  return rows.sort((a, b) => (b.occurredAt ?? 0) - (a.occurredAt ?? 0))
}

/* ---------------------------------------------------------------------------
 * Captura real del titular (OWNER) — contrato con el pull de Android
 * ---------------------------------------------------------------------------
 * El pull de Android (RemotePullSync.applyExpenseChange) aplica un expense
 * remoto SOLO si su `updatedAt` es estrictamente mayor que el local (LWW), y
 * al recibirlo hace un get() de la subcolección `attributions` del gasto para
 * reflejarla en Room. Por eso:
 *   - `updatedAt` SIEMPRE > 0 (Date.now()) — es la llave del LWW.
 *   - Doc principal + subcolección attributions + ajuste del wallet van en UN
 *     writeBatch atómico: Android nunca ve un gasto sin sus atribuciones ya
 *     escritas (el snapshot listener dispara tras el commit del batch).
 *   - Los campos van en camelCase (lo que pushea el propio Android).
 *
 * RIESGO ACEPTADO (documentado): el ajuste del saldo del wallet se calcula con
 * un read previo al batch (Firestore writeBatch no lee). Si el teléfono
 * captura un gasto sobre el MISMO wallet en la misma ventana, uno de los dos
 * ajustes puede perderse (carrera read-modify-write + LWW del doc wallet).
 * La válvula de escape es "Reconciliar saldo" en la pantalla Cuentas de la
 * app, igual que para la limitación ya documentada de editar gastos seed.
 * ------------------------------------------------------------------------- */

interface AttributionRow {
  expenseId: string
  memberId: string
  role: 'BENEFICIARY' | 'PAYER'
  shareBps: number
  shareAmountMxn: number
}

/**
 * % → basis points con el MISMO algoritmo que la captura Android
 * (CaptureViewModel.buildRows): cada miembro recibe percent*100 bps y el
 * ÚLTIMO absorbe el residuo para que la dimensión sume EXACTO 10000.
 */
function sharesToBps(shares: AttributionInput[], roleLabel: string): Array<{ memberId: string; bps: number }> {
  if (shares.length === 0) {
    throw new Error(`La atribución de ${roleLabel} no puede estar vacía.`)
  }
  const total = shares.reduce((s, a) => s + a.percent, 0)
  if (Math.round(total) !== 100) {
    throw new Error(`La atribución de ${roleLabel} debe sumar 100% (suma ${total}%).`)
  }
  let assigned = 0
  return shares.map((a, i) => {
    const bps = i === shares.length - 1 ? 10_000 - assigned : Math.round(a.percent * 100)
    if (i < shares.length - 1) assigned += bps
    return { memberId: a.memberId, bps }
  })
}

function buildAttributionRows(expenseId: string, amountMxn: number, input: ExpenseInput): AttributionRow[] {
  const beneficiary = sharesToBps(input.beneficiaries, 'beneficiarios (BENEFICIARY)')
  const payer = sharesToBps(input.payers, 'pagadores (PAYER)')
  const toRow = (role: 'BENEFICIARY' | 'PAYER') => (s: { memberId: string; bps: number }): AttributionRow => ({
    expenseId,
    memberId: s.memberId,
    role,
    shareBps: s.bps,
    shareAmountMxn: round2((amountMxn * s.bps) / 10_000),
  })
  return [...beneficiary.map(toRow('BENEFICIARY')), ...payer.map(toRow('PAYER'))]
}

/**
 * Crea un gasto REAL (status POSTED) como lo haría la captura del teléfono:
 * doc del gasto + subcolección attributions + descuento del saldo del wallet,
 * todo en un writeBatch atómico. Devuelve el id del gasto.
 */
export async function createExpense(hid: string, input: ExpenseInput): Promise<string> {
  const amount = input.amountMxn
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new Error('El monto debe ser mayor que cero.')
  }
  const concept = input.concept.trim()
  if (!concept) throw new Error('El concepto no puede estar vacío.')
  if (!input.categoryId) throw new Error('Selecciona una categoría.')
  if (!input.paymentMethodId) throw new Error('Selecciona un método de pago (wallet).')

  // Quincena REAL consultada por fecha (nunca inventada).
  const quincena = await findQuincenaForDate(hid, input.occurredAt)

  // Wallet: read-before-batch (ver riesgo aceptado arriba).
  const walletRef = doc(db, 'households', hid, 'wallets', input.paymentMethodId)
  const walletSnap = await getDoc(walletRef)
  if (!walletSnap.exists()) {
    throw new Error('El método de pago seleccionado ya no existe.')
  }
  const walletData = walletSnap.data() as FirestoreData
  const balance = pickNum(walletData, 'currentBalanceMxn', 'current_balance_mxn') ?? 0

  const now = Date.now()
  const batch = writeBatch(db)

  const expenseRef = doc(collection(db, 'households', hid, 'expenses'))
  batch.set(expenseRef, {
    id: expenseRef.id,
    householdId: hid,
    occurredAt: input.occurredAt,
    quincenaId: quincena.id,
    categoryId: input.categoryId,
    concept,
    amountMxn: amount,
    paymentMethodId: input.paymentMethodId,
    status: 'POSTED',
    settlementStatus: 'NONE',
    notes: input.notes?.trim() || null,
    createdAt: now,
    updatedAt: now, // > 0 SIEMPRE: llave del LWW en el pull de Android.
  })

  for (const row of buildAttributionRows(expenseRef.id, amount, input)) {
    batch.set(doc(collection(expenseRef, 'attributions')), row)
  }

  batch.update(walletRef, walletBalanceUpdate(walletData, balance - amount))

  await batch.commit()
  return expenseRef.id
}

/**
 * Edita un gasto existente con el mismo patrón atómico: update del doc
 * principal (updatedAt nuevo = gana el LWW), REEMPLAZO completo de la
 * subcolección attributions (se leen y borran las previas) y ajuste del
 * wallet por el delta (revierte en el wallet anterior y descuenta en el
 * nuevo si cambió el método de pago).
 */
export async function updateExpense(hid: string, expenseId: string, input: ExpenseInput): Promise<void> {
  const amount = input.amountMxn
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new Error('El monto debe ser mayor que cero.')
  }
  const concept = input.concept.trim()
  if (!concept) throw new Error('El concepto no puede estar vacío.')

  const expenseRef = doc(db, 'households', hid, 'expenses', expenseId)
  const expenseSnap = await getDoc(expenseRef)
  if (!expenseSnap.exists()) throw new Error('El gasto ya no existe.')
  const prev = normExpense(expenseSnap.id, expenseSnap.data() as FirestoreData)
  if (isTombstoned(prev)) throw new Error('El gasto fue eliminado en otro dispositivo.')

  const quincena = await findQuincenaForDate(hid, input.occurredAt)

  // Atribuciones previas: se leen para poder borrarlas dentro del batch.
  const prevAttribs = await getDocs(collection(expenseRef, 'attributions'))

  // Wallets involucrados.
  const newWalletRef = doc(db, 'households', hid, 'wallets', input.paymentMethodId)
  const newWalletSnap = await getDoc(newWalletRef)
  if (!newWalletSnap.exists()) throw new Error('El método de pago seleccionado ya no existe.')
  const newWalletData = newWalletSnap.data() as FirestoreData

  const oldWalletId = prev.paymentMethodId
  const walletChanged = !!oldWalletId && oldWalletId !== input.paymentMethodId

  const now = Date.now()
  const batch = writeBatch(db)

  batch.update(expenseRef, {
    occurredAt: input.occurredAt,
    quincenaId: quincena.id,
    categoryId: input.categoryId,
    concept,
    amountMxn: amount,
    paymentMethodId: input.paymentMethodId,
    notes: input.notes?.trim() || null,
    updatedAt: now,
  })

  prevAttribs.docs.forEach((d) => batch.delete(d.ref))
  for (const row of buildAttributionRows(expenseId, amount, input)) {
    batch.set(doc(collection(expenseRef, 'attributions')), row)
  }

  if (walletChanged && oldWalletId) {
    // Revertir el monto anterior en el wallet viejo y descontar el nuevo en el nuevo.
    const oldWalletRef = doc(db, 'households', hid, 'wallets', oldWalletId)
    const oldWalletSnap = await getDoc(oldWalletRef)
    if (oldWalletSnap.exists()) {
      const oldData = oldWalletSnap.data() as FirestoreData
      const oldBalance = pickNum(oldData, 'currentBalanceMxn', 'current_balance_mxn') ?? 0
      batch.update(oldWalletRef, walletBalanceUpdate(oldData, oldBalance + prev.amountMxn))
    }
    const newBalance = pickNum(newWalletData, 'currentBalanceMxn', 'current_balance_mxn') ?? 0
    batch.update(newWalletRef, walletBalanceUpdate(newWalletData, newBalance - amount))
  } else {
    const delta = amount - prev.amountMxn
    if (delta !== 0) {
      const balance = pickNum(newWalletData, 'currentBalanceMxn', 'current_balance_mxn') ?? 0
      batch.update(newWalletRef, walletBalanceUpdate(newWalletData, balance - delta))
    }
  }

  await batch.commit()
}

/**
 * Borra un gasto con LÁPIDA (tombstone): el doc principal NO se borra — se
 * reemplaza por una lápida mínima ({ id, householdId, deletedAt, updatedAt })
 * para que un dispositivo offline prolongado, que ya no vería el REMOVED en su
 * cache, reciba la lápida como ADDED y borre localmente en vez de resucitar el
 * gasto. La subcolección attributions SÍ se borra de verdad y la reversión del
 * saldo del wallet se mantiene, todo en un batch. Android trata deleted_at > 0
 * como borrado (gate LWW) y el nuevo saldo llega por el listener del wallet.
 */
export async function deleteExpense(hid: string, expenseId: string): Promise<void> {
  const expenseRef = doc(db, 'households', hid, 'expenses', expenseId)
  const expenseSnap = await getDoc(expenseRef)
  if (!expenseSnap.exists()) throw new Error('El gasto ya no existe.')
  const expense = normExpense(expenseSnap.id, expenseSnap.data() as FirestoreData)
  // Ya era lápida: no volver a revertir el saldo del wallet (doble reversión).
  if (isTombstoned(expense)) throw new Error('El gasto ya fue eliminado.')

  const attribs = await getDocs(collection(expenseRef, 'attributions'))

  const now = Date.now()
  const batch = writeBatch(db)
  attribs.docs.forEach((d) => batch.delete(d.ref))
  // set SIN merge: reemplaza el doc entero por la lápida (queda chica y fuera
  // de las queries por status).
  batch.set(expenseRef, { id: expenseId, householdId: hid, deletedAt: now, updatedAt: now })

  // Reversión del saldo SOLO si el gasto estaba POSTED (un PLANNED nunca movió saldo).
  if (expense.status === 'POSTED' && expense.paymentMethodId) {
    const walletRef = doc(db, 'households', hid, 'wallets', expense.paymentMethodId)
    const walletSnap = await getDoc(walletRef)
    if (walletSnap.exists()) {
      const walletData = walletSnap.data() as FirestoreData
      const balance = pickNum(walletData, 'currentBalanceMxn', 'current_balance_mxn') ?? 0
      batch.update(walletRef, walletBalanceUpdate(walletData, balance + expense.amountMxn))
    }
  }

  await batch.commit()
}

/**
 * Confirma un pago futuro: PLANNED → POSTED con updatedAt nuevo + descuento
 * del wallet en el mismo batch. Android lo refleja por LWW (mismo flujo que
 * la confirmación local del calendario).
 */
export async function confirmPlanned(hid: string, expenseId: string): Promise<void> {
  const expenseRef = doc(db, 'households', hid, 'expenses', expenseId)
  const expenseSnap = await getDoc(expenseRef)
  if (!expenseSnap.exists()) throw new Error('El gasto ya no existe.')
  const expense = normExpense(expenseSnap.id, expenseSnap.data() as FirestoreData)
  if (isTombstoned(expense)) throw new Error('El gasto fue eliminado en otro dispositivo.')
  if (expense.status !== 'PLANNED') {
    throw new Error(`Solo se puede confirmar un gasto PLANNED (este está ${expense.status}).`)
  }

  const batch = writeBatch(db)
  batch.update(expenseRef, { status: 'POSTED', updatedAt: Date.now() })

  if (expense.paymentMethodId) {
    const walletRef = doc(db, 'households', hid, 'wallets', expense.paymentMethodId)
    const walletSnap = await getDoc(walletRef)
    if (walletSnap.exists()) {
      const walletData = walletSnap.data() as FirestoreData
      const balance = pickNum(walletData, 'currentBalanceMxn', 'current_balance_mxn') ?? 0
      batch.update(walletRef, walletBalanceUpdate(walletData, balance - expense.amountMxn))
    }
  }

  await batch.commit()
}

/* -------------------------------- Proposals ------------------------------- */

export interface ProposalInput {
  kind: ProposalKind
  concept: string
  amountMxn: number
  occurredAt: number // epoch ms
  categoryId?: string
  note?: string
}

/** Crea una propuesta PENDING en households/{hid}/proposals. */
export async function createProposal(
  hid: string,
  user: User,
  proposedByName: string,
  input: ProposalInput,
): Promise<string> {
  const proposal: Proposal = {
    kind: input.kind,
    concept: input.concept.trim(),
    amountMxn: input.amountMxn,
    occurredAt: input.occurredAt,
    proposedByUid: user.uid,
    proposedByName,
    createdAt: Date.now(),
    status: 'PENDING',
  }
  if (input.categoryId) proposal.categoryId = input.categoryId
  if (input.note && input.note.trim()) proposal.note = input.note.trim()

  const ref = await addDoc(collection(db, 'households', hid, 'proposals'), proposal)
  return ref.id
}

/**
 * MIS propuestas en el household (query por proposedByUid; se ordena en
 * cliente por createdAt desc para no requerir un índice compuesto).
 */
export async function listMyProposals(hid: string, uid: string): Promise<ProposalWithId[]> {
  const snap = await getDocs(
    query(collection(db, 'households', hid, 'proposals'), where('proposedByUid', '==', uid)),
  )
  const rows = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Proposal) }))
  return rows.sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0))
}

/**
 * Todas las propuestas PENDING del hogar (bandeja del titular en la web).
 * Orden en cliente por createdAt desc.
 */
export async function listPendingProposals(hid: string): Promise<ProposalWithId[]> {
  const snap = await getDocs(
    query(collection(db, 'households', hid, 'proposals'), where('status', '==', 'PENDING')),
  )
  const rows = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Proposal) }))
  return rows.sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0))
}

/**
 * Resuelve una propuesta (solo el OWNER — las reglas rechazan a cualquier
 * otro): escribe ACCEPTED/REJECTED + resolvedAt. NO crea el gasto: si el
 * titular la acepta y quiere materializarla, llama después a createExpense
 * con los datos de la propuesta (la app Android hace lo propio con su bandeja
 * pending_capture).
 */
export async function resolveProposal(hid: string, proposalId: string, accept: boolean): Promise<void> {
  await updateDoc(doc(db, 'households', hid, 'proposals', proposalId), {
    status: accept ? 'ACCEPTED' : 'REJECTED',
    resolvedAt: Date.now(),
  })
}
