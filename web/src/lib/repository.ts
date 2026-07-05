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
  orderBy,
  limit,
  increment,
} from 'firebase/firestore'
import type { User } from 'firebase/auth'
import { db } from './firebase'
import type {
  Category,
  CategoryWithId,
  Expense,
  ExpenseWithId,
  Household,
  HouseholdRole,
  HouseholdWithId,
  Invite,
  Proposal,
  ProposalKind,
  Quincena,
  QuincenaWithId,
  Role,
  UserDoc,
  UserHouseholdRef,
} from './types'

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
      const h = (hSnap.data() as Household | undefined) ?? {
        name: '(sin nombre)',
        currency: 'MXN' as const,
        timezone: 'America/Mexico_City' as const,
      }
      return { id: hid, role: ref.role, ...h }
    }),
  )
  return results
}

/** Crea un household nuevo, el rol OWNER y el espejo en users/{uid}/households. */
export async function createHousehold(uid: string, name: string, displayName: string): Promise<string> {
  const householdsCol = collection(db, 'households')
  const hRef = doc(householdsCol)
  const hid = hRef.id

  const household: Household = {
    name: name.trim(),
    currency: 'MXN',
    timezone: 'America/Mexico_City',
  }
  await setDoc(hRef, household)

  const role: HouseholdRole = { role: 'OWNER', displayName }
  await setDoc(doc(db, 'households', hid, 'roles', uid), role)

  const mirror: UserHouseholdRef = { role: 'OWNER', joinedAt: Date.now() }
  await setDoc(doc(db, 'users', uid, 'households', hid), mirror)

  return hid
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

  // Crea el rol en el household.
  const roleDoc: HouseholdRole = { role, displayName }
  await setDoc(doc(db, 'households', hid, 'roles', user.uid), roleDoc)

  // Espejo en users/{uid}/households/{hid}.
  const mirror: UserHouseholdRef = { role, joinedAt: Date.now() }
  await setDoc(doc(db, 'users', user.uid, 'households', hid), mirror)

  // Incrementa el contador de usos (atómico).
  await updateDoc(inviteRef, { uses: increment(1) })

  return { hid, role }
}

/* -------------------------------- Categories ------------------------------ */

export async function listCategories(hid: string): Promise<CategoryWithId[]> {
  const snap = await getDocs(
    query(collection(db, 'households', hid, 'categories'), orderBy('sortOrder')),
  )
  return snap.docs.map((d) => ({ id: d.id, ...(d.data() as Category) }))
}

/* -------------------------------- Quincenas ------------------------------- */

/** Devuelve la quincena con status ACTIVE del household (o null). */
export async function getActiveQuincena(hid: string): Promise<QuincenaWithId | null> {
  const snap = await getDocs(
    query(collection(db, 'households', hid, 'quincenas'), where('status', '==', 'ACTIVE'), limit(1)),
  )
  if (snap.empty) return null
  const d = snap.docs[0]
  return { id: d.id, ...(d.data() as Quincena) }
}

/* -------------------------------- Expenses -------------------------------- */

/** Gastos POSTED de una quincena, ordenados por fecha desc. Solo lectura (dashboard). */
export async function listPostedExpenses(hid: string, quincenaId: string): Promise<ExpenseWithId[]> {
  const snap = await getDocs(
    query(
      collection(db, 'households', hid, 'expenses'),
      where('quincenaId', '==', quincenaId),
      where('status', '==', 'POSTED'),
    ),
  )
  const rows = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Expense) }))
  // Ordenamos en cliente para no exigir un índice compuesto de Firestore.
  return rows.sort((a, b) => (b.occurredAt ?? 0) - (a.occurredAt ?? 0))
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

/** Propuestas del usuario en el household (para feedback tras enviar). */
export async function listMyProposals(hid: string, uid: string): Promise<import('./types').ProposalWithId[]> {
  const snap = await getDocs(
    query(collection(db, 'households', hid, 'proposals'), where('proposedByUid', '==', uid)),
  )
  const rows = snap.docs.map((d) => ({ id: d.id, ...(d.data() as Proposal) }))
  return rows.sort((a, b) => (b.createdAt ?? 0) - (a.createdAt ?? 0))
}
