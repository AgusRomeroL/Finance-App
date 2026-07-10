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
  Attribution,
  AttributionInput,
  CategoryWithId,
  ExpenseInput,
  ExpenseWithId,
  Household,
  HouseholdRole,
  HouseholdWithId,
  IncomeInput,
  IncomeSourceWithId,
  InstallmentPlanInput,
  InstallmentPlanWithId,
  Invite,
  InviteCodeDoc,
  LoanInput,
  LoanWithId,
  MemberWithId,
  Proposal,
  ProposalKind,
  ProposalWithId,
  QuincenaWithId,
  Role,
  SavingsGoalInput,
  SavingsGoalWithId,
  UserDoc,
  UserHouseholdRef,
  WalletInput,
  WalletWithId,
} from './types'
import { normalizeRole } from './types'

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
 * Formato ACTUAL (opaco): el dueño comparte SOLO el código de 8 chars A-Z0-9
 * (ej. `7QX4K2AB`), sin el household id — el hogar se resuelve leyendo la
 * colección global `invite_codes/{code}` (así lo compartido no filtra el id
 * del hogar, p. ej. `default_household`).
 *
 * Formato LEGACY (aún aceptado al canjear): `{hid}.{code}`
 *   - {hid}  = id del documento del household
 *   - {code} = el secreto de 8 chars A-Z0-9 (el id del doc invite)
 * separados por un punto ".". La web parte por el ÚLTIMO punto para tolerar
 * hids que (teóricamente) contengan puntos. [parseInviteCode] solo aplica a
 * este formato legacy.
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
        role: normalizeRole(ref.role),
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

export interface MyRoleInfo {
  role: Role
  /** Member del hogar al que este usuario está vinculado (invites v2 nominados). */
  linkedMemberId: string | null
}

/**
 * Rol del usuario en el household leyendo households/{hid}/roles/{uid}
 * (normalizado v2: COLLABORATOR legacy → MEMBER) + el linkedMemberId del
 * mismo doc. Devuelve null si no hay doc de rol (no es miembro). La UI
 * bifurca con esto: OWNER|PAYER escriben el ledger; MEMBER solo propone.
 */
export async function getMyRoleInfo(hid: string, uid: string): Promise<MyRoleInfo | null> {
  const snap = await getDoc(doc(db, 'households', hid, 'roles', uid))
  if (!snap.exists()) return null
  const data = snap.data() as FirestoreData
  return {
    role: normalizeRole(pickStr(data, 'role', 'role')),
    linkedMemberId: pickStr(data, 'linkedMemberId', 'linked_member_id') ?? null,
  }
}

/** Compat: solo el rol normalizado. */
export async function getMyRole(hid: string, uid: string): Promise<Role | null> {
  const info = await getMyRoleInfo(hid, uid)
  return info?.role ?? null
}

/* --------------------------------- Invites -------------------------------- */

export interface JoinResult {
  hid: string
  role: Role
}

/**
 * Une al usuario a un household usando un código de invitación.
 * Acepta el formato OPACO actual (8 chars sin punto → el hogar se resuelve en
 * `invite_codes/{code}`) y el LEGACY `{hid}.{code}` (contiene un punto).
 * Valida expiresAt/maxUses del lado cliente (las reglas lo refuerzan server-side).
 */
export async function joinByCode(
  user: User,
  rawCode: string,
  displayName: string,
): Promise<JoinResult> {
  const trimmed = rawCode.trim()
  let hid: string
  let code: string
  // Doc del índice global (solo en el camino opaco): su contador de usos
  // también se incrementa al final, junto con el del invite de la subcolección.
  let globalCodeRef: ReturnType<typeof doc> | null = null

  if (trimmed.includes('.')) {
    // Camino LEGACY {hid}.{code}.
    const parsed = parseInviteCode(trimmed)
    if (!parsed) {
      throw new Error('Código inválido.')
    }
    hid = parsed.hid
    code = parsed.code
  } else {
    // Camino OPACO: resolver el hogar en invite_codes/{code}.
    code = trimmed.toUpperCase()
    if (!/^[A-Z0-9]{8}$/.test(code)) {
      throw new Error('Código inválido. Debe tener 8 letras o números.')
    }
    globalCodeRef = doc(db, 'invite_codes', code)
    const globalSnap = await getDoc(globalCodeRef)
    if (!globalSnap.exists()) {
      throw new Error('El código no existe o ya fue eliminado.')
    }
    const globalData = globalSnap.data() as InviteCodeDoc
    if (!globalData.householdId) {
      throw new Error('El código está dañado (sin grupo asociado).')
    }
    hid = globalData.householdId
  }

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

  // Rol CRUDO del invite: las reglas comparan igualdad LITERAL entre el role
  // que te asignas y el del invite, así que se escribe tal cual viene (un
  // invite legacy con 'COLLABORATOR' se escribe 'COLLABORATOR'); la app lo
  // normaliza al leer.
  const rawRole: string = invite.role ?? 'MEMBER'

  // Crea el rol en el household. `inviteCode` es OBLIGATORIO para las reglas:
  // el create de un rol no-OWNER solo pasa si existe invites/{inviteCode} y su
  // `role` coincide con el que el usuario se asigna. En invites v2 NOMINADOS
  // las reglas además exigen que `linkedMemberId` coincida con el del invite —
  // se copia tal cual (y se omite si el invite no lo trae, como los legacy).
  const roleDoc: HouseholdRole = { role: rawRole, displayName, inviteCode: code }
  if (invite.linkedMemberId) roleDoc.linkedMemberId = invite.linkedMemberId
  await setDoc(doc(db, 'households', hid, 'roles', user.uid), roleDoc)

  // Espejo en users/{uid}/households/{hid}.
  const mirror: UserHouseholdRef = { role: rawRole, joinedAt: Date.now() }
  await setDoc(doc(db, 'users', user.uid, 'households', hid), mirror)

  // Incrementa el contador de usos (atómico) — en AMBOS docs si el código se
  // resolvió por el índice global.
  await updateDoc(inviteRef, { uses: increment(1) })
  if (globalCodeRef) {
    await updateDoc(globalCodeRef, { uses: increment(1) })
  }

  return { hid, role: normalizeRole(rawRole) }
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
    creditLimitMxn: pickNum(data, 'creditLimitMxn', 'credit_limit_mxn'),
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

/**
 * Todas las quincenas del hogar ordenadas DESC por fecha de inicio (la más
 * reciente primero). Orden en cliente con fallback dual: un orderBy del
 * servidor sobre `startDate` excluiría los docs seed que solo traen
 * `start_date`.
 */
export async function listQuincenas(hid: string): Promise<QuincenaWithId[]> {
  const snap = await getDocs(collection(db, 'households', hid, 'quincenas'))
  const rows = snap.docs.map((d) => normQuincena(d.id, d.data() as FirestoreData))
  return rows.sort((a, b) => {
    const sa = toDateStr(a.startDate) ?? ''
    const sb = toDateStr(b.startDate) ?? ''
    return sb.localeCompare(sa)
  })
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

/**
 * Gastos POSTED **y** PLANNED de una quincena (Historial). Mismo criterio que
 * listPostedExpenses: `where in` por status en servidor, quincena y lápidas en
 * cliente (fallback dual), orden por fecha desc en cliente.
 */
export async function listExpensesByQuincena(hid: string, quincenaId: string): Promise<ExpenseWithId[]> {
  const snap = await getDocs(
    query(collection(db, 'households', hid, 'expenses'), where('status', 'in', ['POSTED', 'PLANNED'])),
  )
  const rows = snap.docs
    .map((d) => normExpense(d.id, d.data() as FirestoreData))
    .filter((e) => e.quincenaId === quincenaId && !isTombstoned(e))
  return rows.sort((a, b) => (b.occurredAt ?? 0) - (a.occurredAt ?? 0))
}

/**
 * TODOS los gastos PLANNED del hogar (pagos futuros, cruzan quincenas — los
 * usa el Calendario para pintar y confirmar planeados de cualquier mes).
 */
export async function listPlannedExpenses(hid: string): Promise<ExpenseWithId[]> {
  const snap = await getDocs(
    query(collection(db, 'households', hid, 'expenses'), where('status', '==', 'PLANNED')),
  )
  const rows = snap.docs
    .map((d) => normExpense(d.id, d.data() as FirestoreData))
    .filter((e) => !isTombstoned(e))
  return rows.sort((a, b) => (a.occurredAt ?? 0) - (b.occurredAt ?? 0))
}

/**
 * Atribuciones de un gasto (subcolección `attributions`), normalizadas con
 * fallback dual. Las usa el Historial para el filtro por miembro y el modal
 * de edición (updateExpense REEMPLAZA la subcolección completa, así que hay
 * que reenviar las participaciones existentes).
 */
export async function listExpenseAttributions(hid: string, expenseId: string): Promise<Attribution[]> {
  const snap = await getDocs(
    collection(db, 'households', hid, 'expenses', expenseId, 'attributions'),
  )
  return snap.docs
    .map((d) => {
      const data = d.data() as FirestoreData
      const role = pickStr(data, 'role', 'role')
      return {
        expenseId: pickStr(data, 'expenseId', 'expense_id') ?? expenseId,
        memberId: pickStr(data, 'memberId', 'member_id') ?? '',
        role: role === 'PAYER' ? ('PAYER' as const) : ('BENEFICIARY' as const),
        shareBps: pickNum(data, 'shareBps', 'share_bps') ?? 0,
        shareAmountMxn: pickNum(data, 'shareAmountMxn', 'share_amount_mxn') ?? 0,
      }
    })
    .filter((a) => a.memberId !== '')
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
 * Crea un gasto REAL como lo haría la captura del teléfono: doc del gasto +
 * subcolección attributions + descuento del saldo del wallet, todo en un
 * writeBatch atómico. Devuelve el id del gasto.
 *
 * Con `opts.planned = true` (modo "Planear", fecha futura) el gasto nace con
 * status PLANNED y NO toca el saldo del wallet — se descuenta al confirmarlo
 * (confirmPlanned aquí o el flujo del calendario en el teléfono).
 */
export async function createExpense(
  hid: string,
  input: ExpenseInput,
  opts?: { planned?: boolean },
): Promise<string> {
  const planned = opts?.planned === true
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

  // Wallet: read-before-batch (ver riesgo aceptado arriba). Se valida también
  // en modo planned (Android descarta expenses sin paymentMethodId válido).
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
    status: planned ? 'PLANNED' : 'POSTED',
    settlementStatus: 'NONE',
    notes: input.notes?.trim() || null,
    createdAt: now,
    updatedAt: now, // > 0 SIEMPRE: llave del LWW en el pull de Android.
  })

  for (const row of buildAttributionRows(expenseRef.id, amount, input)) {
    batch.set(doc(collection(expenseRef, 'attributions')), row)
  }

  // Un PLANNED nunca mueve saldo: se descuenta al confirmar.
  if (!planned) {
    batch.update(walletRef, walletBalanceUpdate(walletData, balance - amount))
  }

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

  // Un PLANNED nunca movió saldo (se descuenta al confirmar), así que editarlo
  // tampoco debe ajustar ningún wallet.
  if (prev.status === 'PLANNED') {
    await batch.commit()
    return
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

/* -------------------------------- Incomes --------------------------------- */

/**
 * Registra un INGRESO real en `households/{hid}/income_source` (misma
 * subcolección que empuja/lee el sync de Android). Contrato con el pull
 * (FirestoreMappers.toIncomeSourceEntity): householdId, quincenaId, memberId,
 * label, amountMxn y expectedDate son OBLIGATORIOS o el mapper descarta el
 * doc. Se escribe camelCase, status POSTED (ya se recibió) y el saldo del
 * wallet se ACREDITA en el mismo batch (espejo del creditWallet de
 * IncomeRepositoryImpl al insertar un ingreso POSTED). Devuelve el id.
 */
export async function createIncome(hid: string, input: IncomeInput): Promise<string> {
  const amount = input.amountMxn
  if (!Number.isFinite(amount) || amount <= 0) {
    throw new Error('El monto debe ser mayor que cero.')
  }
  const label = input.label.trim()
  if (!label) throw new Error('El concepto del ingreso no puede estar vacío.')
  if (!input.memberId) throw new Error('Selecciona quién recibe el ingreso.')
  if (!input.paymentMethodId) throw new Error('Selecciona la cuenta donde se deposita.')

  // Quincena REAL por fecha de recepción (nunca inventada).
  const quincena = await findQuincenaForDate(hid, input.receivedAt)

  const walletRef = doc(db, 'households', hid, 'wallets', input.paymentMethodId)
  const walletSnap = await getDoc(walletRef)
  if (!walletSnap.exists()) {
    throw new Error('La cuenta seleccionada ya no existe.')
  }
  const walletData = walletSnap.data() as FirestoreData
  const balance = pickNum(walletData, 'currentBalanceMxn', 'current_balance_mxn') ?? 0

  const now = Date.now()
  const batch = writeBatch(db)

  const incomeRef = doc(collection(db, 'households', hid, 'income_source'))
  batch.set(incomeRef, {
    id: incomeRef.id,
    householdId: hid,
    quincenaId: quincena.id,
    memberId: input.memberId,
    label,
    amountMxn: amount,
    cadence: 'QUINCENAL',
    expectedDate: epochToMxDateStr(input.receivedAt),
    paymentMethodId: input.paymentMethodId,
    status: 'POSTED',
    createdAt: now,
    updatedAt: now, // > 0 SIEMPRE: llave del LWW en el pull de Android.
  })

  // Ingreso POSTED acredita (+) el wallet destino.
  batch.update(walletRef, walletBalanceUpdate(walletData, balance + amount))

  await batch.commit()
  return incomeRef.id
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
 * Resuelve una propuesta (OWNER|PAYER — las reglas rechazan a cualquier
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

/* ===========================================================================
 * WEB-WAVE2 — hoja de balance (Cuentas / Deudas / Analíticas)
 * ===========================================================================
 * Mismo contrato que createExpense/createIncome: toda escritura es un
 * writeBatch atómico en camelCase con `updatedAt: Date.now()` (llave del LWW
 * del pull de Android). Los campos de cada doc respetan los OBLIGATORIOS de
 * FirestoreMappers.kt (toPaymentMethodEntity, toWalletTransferEntity,
 * toSavingsGoalEntity, toLoanEntity, toInstallmentPlanEntity): el mapper
 * descarta docs sin ellos.
 * ------------------------------------------------------------------------- */

function camelToSnake(key: string): string {
  return key.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`)
}

/**
 * Espejo snake_case en EDICIONES (patrón walletBalanceUpdate generalizado):
 * por cada campo camelCase del update, si el doc existente traía la variante
 * snake_case (docs seed), también se actualiza — para no dejar un valor viejo
 * divergente en el mismo doc. `last4` es idéntico en ambas variantes.
 */
function mirrorSnake(existing: FirestoreData, update: FirestoreData): FirestoreData {
  const out: FirestoreData = { ...update }
  for (const [k, v] of Object.entries(update)) {
    const snake = camelToSnake(k)
    if (snake !== k && snake in existing) out[snake] = v
  }
  return out
}

/* ------------------------------ Wallets CRUD ------------------------------ */

/**
 * Crea un wallet (payment_method). Obligatorios del mapper: householdId,
 * displayName, kind. El saldo inicial va a currentBalanceMxn Y
 * openingBalanceMxn (mismo criterio que un alta local en la app).
 */
export async function createWallet(hid: string, input: WalletInput): Promise<string> {
  const displayName = input.displayName.trim()
  if (!displayName) throw new Error('El nombre de la cuenta no puede estar vacío.')
  if (!input.kind) throw new Error('Selecciona el tipo de cuenta.')

  const now = Date.now()
  const ref = doc(collection(db, 'households', hid, 'wallets'))
  const initial = Number.isFinite(input.initialBalanceMxn) ? round2(input.initialBalanceMxn as number) : 0

  const data: FirestoreData = {
    id: ref.id,
    householdId: hid,
    displayName,
    kind: input.kind,
    currentBalanceMxn: initial,
    openingBalanceMxn: initial,
    isActive: input.isActive,
    updatedAt: now,
  }
  if (input.ownerMemberId) data.ownerMemberId = input.ownerMemberId
  if (Number.isFinite(input.creditLimitMxn)) data.creditLimitMxn = input.creditLimitMxn

  const batch = writeBatch(db)
  batch.set(ref, data)
  await batch.commit()
  return ref.id
}

/**
 * Edita un wallet existente (displayName, kind, ownerMemberId, creditLimitMxn,
 * isActive). NO toca saldos — para eso está [reconcileWalletBalance]. Campos
 * opcionales vaciados se escriben null (el mapper de Android los trata como
 * ausentes).
 */
export async function updateWallet(hid: string, walletId: string, input: WalletInput): Promise<void> {
  const displayName = input.displayName.trim()
  if (!displayName) throw new Error('El nombre de la cuenta no puede estar vacío.')

  const ref = doc(db, 'households', hid, 'wallets', walletId)
  const snap = await getDoc(ref)
  if (!snap.exists()) throw new Error('La cuenta ya no existe.')
  const existing = snap.data() as FirestoreData

  const update: FirestoreData = {
    displayName,
    kind: input.kind,
    ownerMemberId: input.ownerMemberId ?? null,
    creditLimitMxn: Number.isFinite(input.creditLimitMxn) ? input.creditLimitMxn : null,
    isActive: input.isActive,
    updatedAt: Date.now(),
  }

  const batch = writeBatch(db)
  batch.update(ref, mirrorSnake(existing, update))
  await batch.commit()
}

/**
 * Reconciliar: fija el saldo ACTUAL de un wallet al valor observado (misma
 * válvula de escape que "Reconciliar saldo" en la app). update con updatedAt.
 */
export async function reconcileWalletBalance(hid: string, walletId: string, newBalance: number): Promise<void> {
  if (!Number.isFinite(newBalance)) throw new Error('El saldo debe ser un número válido.')
  const ref = doc(db, 'households', hid, 'wallets', walletId)
  const snap = await getDoc(ref)
  if (!snap.exists()) throw new Error('La cuenta ya no existe.')
  const existing = snap.data() as FirestoreData

  const batch = writeBatch(db)
  batch.update(ref, walletBalanceUpdate(existing, newBalance))
  await batch.commit()
}

/* ------------------------------ Transferencias ---------------------------- */

export interface TransferInput {
  fromPaymentMethodId: string
  toPaymentMethodId: string
  amountMxn: number
  /** Epoch ms; default ahora. */
  occurredAt?: number
  note?: string
}

/**
 * Transferencia entre wallets (RF-41): doc en `wallet_transfer` (obligatorios
 * del mapper: householdId, fromPaymentMethodId, toPaymentMethodId, amountMxn,
 * occurredAt) + decremento del origen + incremento del destino, en UN batch.
 * Mismo riesgo aceptado de read-before-batch que createExpense.
 */
export async function createWalletTransfer(hid: string, input: TransferInput): Promise<string> {
  const amount = input.amountMxn
  if (!Number.isFinite(amount) || amount <= 0) throw new Error('El monto debe ser mayor que cero.')
  if (!input.fromPaymentMethodId || !input.toPaymentMethodId) {
    throw new Error('Selecciona la cuenta origen y la cuenta destino.')
  }
  if (input.fromPaymentMethodId === input.toPaymentMethodId) {
    throw new Error('La cuenta origen y la destino deben ser distintas.')
  }

  const fromRef = doc(db, 'households', hid, 'wallets', input.fromPaymentMethodId)
  const toRef = doc(db, 'households', hid, 'wallets', input.toPaymentMethodId)
  const [fromSnap, toSnap] = await Promise.all([getDoc(fromRef), getDoc(toRef)])
  if (!fromSnap.exists()) throw new Error('La cuenta origen ya no existe.')
  if (!toSnap.exists()) throw new Error('La cuenta destino ya no existe.')
  const fromData = fromSnap.data() as FirestoreData
  const toData = toSnap.data() as FirestoreData
  const fromBalance = pickNum(fromData, 'currentBalanceMxn', 'current_balance_mxn') ?? 0
  const toBalance = pickNum(toData, 'currentBalanceMxn', 'current_balance_mxn') ?? 0

  const now = Date.now()
  const transferRef = doc(collection(db, 'households', hid, 'wallet_transfer'))

  const batch = writeBatch(db)
  batch.set(transferRef, {
    id: transferRef.id,
    householdId: hid,
    fromPaymentMethodId: input.fromPaymentMethodId,
    toPaymentMethodId: input.toPaymentMethodId,
    amountMxn: round2(amount),
    occurredAt: input.occurredAt ?? now,
    note: input.note?.trim() || null,
    createdAt: now,
    updatedAt: now,
  })
  batch.update(fromRef, walletBalanceUpdate(fromData, fromBalance - amount))
  batch.update(toRef, walletBalanceUpdate(toData, toBalance + amount))
  await batch.commit()
  return transferRef.id
}

/* ------------------------------ Metas de ahorro --------------------------- */

function normSavingsGoal(id: string, data: FirestoreData): SavingsGoalWithId {
  return {
    id,
    name: pickStr(data, 'name', 'name') ?? '(sin nombre)',
    targetMxn: pickNum(data, 'targetMxn', 'target_mxn') ?? 0,
    currentMxn: pickNum(data, 'currentMxn', 'current_mxn') ?? 0,
    targetDate: pickStr(data, 'targetDate', 'target_date') ?? null,
    linkedPaymentMethodId: pickStr(data, 'linkedPaymentMethodId', 'linked_payment_method_id') ?? null,
    updatedAt: pickNum(data, 'updatedAt', 'updated_at') ?? 0,
  }
}

export async function listSavingsGoals(hid: string): Promise<SavingsGoalWithId[]> {
  const snap = await getDocs(collection(db, 'households', hid, 'savings_goal'))
  const rows = snap.docs.map((d) => normSavingsGoal(d.id, d.data() as FirestoreData))
  return rows.sort((a, b) => a.name.localeCompare(b.name, 'es'))
}

/**
 * Crea o edita una meta de ahorro. Obligatorios del mapper: householdId, name,
 * targetMxn. `goalId` null = crear.
 */
export async function upsertSavingsGoal(
  hid: string,
  goalId: string | null,
  input: SavingsGoalInput,
): Promise<string> {
  const name = input.name.trim()
  if (!name) throw new Error('El nombre de la meta no puede estar vacío.')
  if (!Number.isFinite(input.targetMxn) || input.targetMxn <= 0) {
    throw new Error('La meta debe ser mayor que cero.')
  }

  const now = Date.now()
  const batch = writeBatch(db)

  if (goalId === null) {
    const ref = doc(collection(db, 'households', hid, 'savings_goal'))
    batch.set(ref, {
      id: ref.id,
      householdId: hid,
      name,
      targetMxn: round2(input.targetMxn),
      currentMxn: round2(input.currentMxn || 0),
      targetDate: input.targetDate || null,
      linkedPaymentMethodId: input.linkedPaymentMethodId || null,
      updatedAt: now,
    })
    await batch.commit()
    return ref.id
  }

  const ref = doc(db, 'households', hid, 'savings_goal', goalId)
  const snap = await getDoc(ref)
  if (!snap.exists()) throw new Error('La meta ya no existe.')
  const existing = snap.data() as FirestoreData
  batch.update(
    ref,
    mirrorSnake(existing, {
      name,
      targetMxn: round2(input.targetMxn),
      currentMxn: round2(input.currentMxn || 0),
      targetDate: input.targetDate || null,
      linkedPaymentMethodId: input.linkedPaymentMethodId || null,
      updatedAt: now,
    }),
  )
  await batch.commit()
  return goalId
}

/** Abono a una meta: currentMxn += monto (con updatedAt para el LWW). */
export async function addToSavingsGoal(hid: string, goalId: string, amountMxn: number): Promise<void> {
  if (!Number.isFinite(amountMxn) || amountMxn <= 0) throw new Error('El abono debe ser mayor que cero.')
  const ref = doc(db, 'households', hid, 'savings_goal', goalId)
  const snap = await getDoc(ref)
  if (!snap.exists()) throw new Error('La meta ya no existe.')
  const existing = snap.data() as FirestoreData
  const current = pickNum(existing, 'currentMxn', 'current_mxn') ?? 0

  const batch = writeBatch(db)
  batch.update(ref, mirrorSnake(existing, { currentMxn: round2(current + amountMxn), updatedAt: Date.now() }))
  await batch.commit()
}

/* --------------------------- Préstamos por cobrar ------------------------- */

function normLoan(id: string, data: FirestoreData): LoanWithId {
  return {
    id,
    debtorMemberId: pickStr(data, 'debtorMemberId', 'debtor_member_id') ?? '',
    principalMxn: pickNum(data, 'principalMxn', 'principal_mxn') ?? 0,
    remainingBalanceMxn: pickNum(data, 'remainingBalanceMxn', 'remaining_balance_mxn') ?? 0,
    agreedInterestMxn: pickNum(data, 'agreedInterestMxn', 'agreed_interest_mxn') ?? 0,
    issuedAt: pickStr(data, 'issuedAt', 'issued_at') ?? '',
    dueAt: pickStr(data, 'dueAt', 'due_at') ?? null,
    notes: pickStr(data, 'notes', 'notes') ?? null,
    paymentCount: pickNum(data, 'paymentCount', 'payment_count') ?? null,
    paymentFrequency: pickStr(data, 'paymentFrequency', 'payment_frequency') ?? null,
    paymentAmountMxn: pickNum(data, 'paymentAmountMxn', 'payment_amount_mxn') ?? null,
    scheduleStartDate: pickStr(data, 'scheduleStartDate', 'schedule_start_date') ?? null,
    updatedAt: pickNum(data, 'updatedAt', 'updated_at') ?? 0,
  }
}

export async function listLoans(hid: string): Promise<LoanWithId[]> {
  const snap = await getDocs(collection(db, 'households', hid, 'loan'))
  const rows = snap.docs.map((d) => normLoan(d.id, d.data() as FirestoreData))
  // Con saldo vivo primero, luego por saldo desc.
  return rows.sort(
    (a, b) =>
      Number(b.remainingBalanceMxn > 0) - Number(a.remainingBalanceMxn > 0) ||
      b.remainingBalanceMxn - a.remainingBalanceMxn,
  )
}

/**
 * Crea o edita un préstamo por cobrar. Obligatorios del mapper: householdId,
 * debtorMemberId, principalMxn, remainingBalanceMxn, issuedAt ("YYYY-MM-DD").
 */
export async function upsertLoan(hid: string, loanId: string | null, input: LoanInput): Promise<string> {
  if (!input.debtorMemberId) throw new Error('Selecciona quién debe.')
  if (!Number.isFinite(input.principalMxn) || input.principalMxn <= 0) {
    throw new Error('El monto prestado debe ser mayor que cero.')
  }
  if (!Number.isFinite(input.remainingBalanceMxn) || input.remainingBalanceMxn < 0) {
    throw new Error('El saldo pendiente no puede ser negativo.')
  }
  if (!input.issuedAt) throw new Error('Indica la fecha del préstamo.')

  const body: FirestoreData = {
    householdId: hid,
    debtorMemberId: input.debtorMemberId,
    principalMxn: round2(input.principalMxn),
    remainingBalanceMxn: round2(input.remainingBalanceMxn),
    agreedInterestMxn: round2(input.agreedInterestMxn || 0),
    issuedAt: input.issuedAt,
    dueAt: input.dueAt || null,
    notes: input.notes?.trim() || null,
    paymentCount: Number.isFinite(input.paymentCount) ? input.paymentCount : null,
    paymentFrequency: input.paymentFrequency || null,
    paymentAmountMxn: Number.isFinite(input.paymentAmountMxn) ? round2(input.paymentAmountMxn as number) : null,
    scheduleStartDate: input.scheduleStartDate || null,
    updatedAt: Date.now(),
  }

  const batch = writeBatch(db)
  if (loanId === null) {
    const ref = doc(collection(db, 'households', hid, 'loan'))
    batch.set(ref, { id: ref.id, ...body })
    await batch.commit()
    return ref.id
  }
  const ref = doc(db, 'households', hid, 'loan', loanId)
  const snap = await getDoc(ref)
  if (!snap.exists()) throw new Error('El préstamo ya no existe.')
  batch.update(ref, mirrorSnake(snap.data() as FirestoreData, body))
  await batch.commit()
  return loanId
}

/**
 * Abono a un préstamo: remainingBalanceMxn = max(0, saldo − abono), con
 * updatedAt (espejo de LoanRepositoryImpl.applyPayment en Android).
 */
export async function applyLoanPayment(hid: string, loanId: string, amountMxn: number): Promise<void> {
  if (!Number.isFinite(amountMxn) || amountMxn <= 0) throw new Error('El abono debe ser mayor que cero.')
  const ref = doc(db, 'households', hid, 'loan', loanId)
  const snap = await getDoc(ref)
  if (!snap.exists()) throw new Error('El préstamo ya no existe.')
  const existing = snap.data() as FirestoreData
  const remaining = pickNum(existing, 'remainingBalanceMxn', 'remaining_balance_mxn') ?? 0

  const batch = writeBatch(db)
  batch.update(
    ref,
    mirrorSnake(existing, {
      remainingBalanceMxn: round2(Math.max(0, remaining - amountMxn)),
      updatedAt: Date.now(),
    }),
  )
  await batch.commit()
}

/* --------------------------------- MSI ------------------------------------ */

function normInstallmentPlan(id: string, data: FirestoreData): InstallmentPlanWithId {
  return {
    id,
    displayName: pickStr(data, 'displayName', 'display_name') ?? '(sin nombre)',
    principalMxn: pickNum(data, 'principalMxn', 'principal_mxn') ?? 0,
    totalInstallments: pickNum(data, 'totalInstallments', 'total_installments') ?? 0,
    installmentAmountMxn: pickNum(data, 'installmentAmountMxn', 'installment_amount_mxn') ?? 0,
    startDate: pickStr(data, 'startDate', 'start_date') ?? '',
    currentInstallment: pickNum(data, 'currentInstallment', 'current_installment') ?? 0,
    status: pickStr(data, 'status', 'status') ?? 'ACTIVE',
    paymentMethodId: pickStr(data, 'paymentMethodId', 'payment_method_id') ?? null,
    fundingPaymentMethodId: pickStr(data, 'fundingPaymentMethodId', 'funding_payment_method_id') ?? null,
    creditorMemberId: pickStr(data, 'creditorMemberId', 'creditor_member_id') ?? null,
    categoryId: pickStr(data, 'categoryId', 'category_id') ?? null,
    interestRateApr: pickNum(data, 'interestRateApr', 'interest_rate_apr') ?? null,
    updatedAt: pickNum(data, 'updatedAt', 'updated_at') ?? 0,
  }
}

export async function listInstallmentPlans(hid: string): Promise<InstallmentPlanWithId[]> {
  const snap = await getDocs(collection(db, 'households', hid, 'installment_plan'))
  const rows = snap.docs.map((d) => normInstallmentPlan(d.id, d.data() as FirestoreData))
  // ACTIVE primero, luego por nombre.
  return rows.sort(
    (a, b) =>
      Number(b.status === 'ACTIVE') - Number(a.status === 'ACTIVE') ||
      a.displayName.localeCompare(b.displayName, 'es'),
  )
}

/**
 * Crea o edita un plan MSI. Obligatorios del mapper: householdId, displayName,
 * principalMxn, totalInstallments, installmentAmountMxn, startDate. La web NO
 * materializa cuotas (las genera el teléfono).
 */
export async function upsertInstallmentPlan(
  hid: string,
  planId: string | null,
  input: InstallmentPlanInput,
): Promise<string> {
  const displayName = input.displayName.trim()
  if (!displayName) throw new Error('El nombre del plan no puede estar vacío.')
  if (!Number.isFinite(input.principalMxn) || input.principalMxn <= 0) {
    throw new Error('El monto total debe ser mayor que cero.')
  }
  if (!Number.isInteger(input.totalInstallments) || input.totalInstallments <= 0) {
    throw new Error('El número de mensualidades debe ser un entero mayor que cero.')
  }
  if (!Number.isFinite(input.installmentAmountMxn) || input.installmentAmountMxn <= 0) {
    throw new Error('La mensualidad debe ser mayor que cero.')
  }
  if (!input.startDate) throw new Error('Indica la fecha de inicio.')
  const current = input.currentInstallment
  if (!Number.isInteger(current) || current < 0 || current > input.totalInstallments) {
    throw new Error('El pago actual debe estar entre 0 y el total de mensualidades.')
  }

  const body: FirestoreData = {
    householdId: hid,
    displayName,
    principalMxn: round2(input.principalMxn),
    totalInstallments: input.totalInstallments,
    installmentAmountMxn: round2(input.installmentAmountMxn),
    startDate: input.startDate,
    currentInstallment: current,
    status: current >= input.totalInstallments ? 'COMPLETED' : 'ACTIVE',
    paymentMethodId: input.paymentMethodId || null,
    fundingPaymentMethodId: input.fundingPaymentMethodId || null,
    categoryId: input.categoryId || null,
    interestRateApr: Number.isFinite(input.interestRateApr) ? input.interestRateApr : null,
    updatedAt: Date.now(),
  }

  const batch = writeBatch(db)
  if (planId === null) {
    const ref = doc(collection(db, 'households', hid, 'installment_plan'))
    batch.set(ref, { id: ref.id, ...body })
    await batch.commit()
    return ref.id
  }
  const ref = doc(db, 'households', hid, 'installment_plan', planId)
  const snap = await getDoc(ref)
  if (!snap.exists()) throw new Error('El plan ya no existe.')
  batch.update(ref, mirrorSnake(snap.data() as FirestoreData, body))
  await batch.commit()
  return planId
}

/* --------------------- Deudas entre miembros (derivado) ------------------- */

/**
 * Gastos POSTED pendientes de reembolso (`settlementStatus =
 * 'PENDING_REIMBURSEMENT'`) — la semántica EXACTA de "el hogar le debe" de
 * MemberBalancesViewModel en Android: gastos que un tercero adelantó,
 * agrupables por `externalPayerMemberId`. Query del servidor solo por status
 * (mismo criterio que listPostedExpenses); settlement y lápidas en cliente
 * con fallback dual.
 */
export async function listPendingReimbursements(hid: string): Promise<ExpenseWithId[]> {
  const snap = await getDocs(
    query(collection(db, 'households', hid, 'expenses'), where('status', '==', 'POSTED')),
  )
  return snap.docs
    .map((d) => normExpense(d.id, d.data() as FirestoreData))
    .filter(
      (e) =>
        !isTombstoned(e) &&
        e.settlementStatus === 'PENDING_REIMBURSEMENT' &&
        !!e.externalPayerMemberId,
    )
    .sort((a, b) => (b.occurredAt ?? 0) - (a.occurredAt ?? 0))
}

/**
 * Marca un gasto adelantado por un tercero como REEMBOLSADO (espejo del
 * markReimbursed de Android): settlementStatus = 'REIMBURSED' + updatedAt.
 * NO mueve saldos de wallet (la reposición ocurre fuera del ledger). Gate en
 * cliente: solo desde PENDING_REIMBURSEMENT.
 */
export async function markExpenseReimbursed(hid: string, expenseId: string): Promise<void> {
  const ref = doc(db, 'households', hid, 'expenses', expenseId)
  const snap = await getDoc(ref)
  if (!snap.exists()) throw new Error('El gasto ya no existe.')
  const expense = normExpense(snap.id, snap.data() as FirestoreData)
  if (isTombstoned(expense)) throw new Error('El gasto fue eliminado en otro dispositivo.')
  if (expense.settlementStatus !== 'PENDING_REIMBURSEMENT') {
    throw new Error('Este gasto ya no está pendiente de reembolso.')
  }

  const batch = writeBatch(db)
  batch.update(
    ref,
    mirrorSnake(snap.data() as FirestoreData, {
      settlementStatus: 'REIMBURSED',
      updatedAt: Date.now(),
    }),
  )
  await batch.commit()
}

/* --------------------------- Ingresos (lectura) ---------------------------- */

function normIncome(id: string, data: FirestoreData): IncomeSourceWithId {
  return {
    id,
    quincenaId: pickStr(data, 'quincenaId', 'quincena_id') ?? '',
    memberId: pickStr(data, 'memberId', 'member_id') ?? '',
    label: pickStr(data, 'label', 'label') ?? '',
    amountMxn: pickNum(data, 'amountMxn', 'amount_mxn') ?? 0,
    status: pickStr(data, 'status', 'status') ?? 'PLANNED',
    expectedDate: pickStr(data, 'expectedDate', 'expected_date'),
    paymentMethodId: pickStr(data, 'paymentMethodId', 'payment_method_id'),
    updatedAt: pickNum(data, 'updatedAt', 'updated_at') ?? 0,
  }
}

/**
 * Ingresos de una quincena (Analíticas). Sin where por quincenaId en servidor
 * (los docs seed solo traen `quincena_id`): se lee la colección y se filtra en
 * cliente con fallback dual. Volúmenes chicos (2-4 docs por quincena).
 */
export async function listIncomesByQuincena(hid: string, quincenaId: string): Promise<IncomeSourceWithId[]> {
  const snap = await getDocs(collection(db, 'households', hid, 'income_source'))
  return snap.docs
    .map((d) => normIncome(d.id, d.data() as FirestoreData))
    .filter((i) => i.quincenaId === quincenaId)
    .sort((a, b) => b.amountMxn - a.amountMxn)
}
