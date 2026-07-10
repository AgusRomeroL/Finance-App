/**
 * Tipos que reflejan el contrato Firestore compartido con la app Android.
 *
 * OJO con el naming: los docs que ESCRIBE Android (push del sync) van en
 * camelCase (`set(entity)` serializa los nombres de propiedad Kotlin), pero
 * los docs sembrados por `scripts/seed_firebase.py` van en snake_case (dump
 * directo de las filas SQLite). El pull de Android (FirestoreMappers.kt) lee
 * con fallback dual camelCase/snake_case; la web hace lo mismo vía el helper
 * `pick()` de repository.ts. La web SIEMPRE escribe camelCase (la variante
 * que Android lee primero).
 *
 * Fechas (occurredAt/createdAt/expiresAt/updatedAt) = epoch millis (number).
 * Excepción: quincena.startDate/endDate y quincena.half viajan como los
 * persiste Room (startDate/endDate = "YYYY-MM-DD" string, half = "H1"/"H2"),
 * aunque docs legados podrían traer numbers — por eso los tipos son uniones.
 * Montos en MXN (number).
 */

/**
 * Rol NORMALIZADO v2 (nivel app):
 *   - OWNER  ("Dueño"): fundador, administra roles/invites y escribe todo.
 *   - PAYER  ("Administrador"): escribe el ledger igual que OWNER (reglas v2).
 *   - MEMBER ("Colaborador"): solo lee y propone. El valor legacy en el wire
 *     es 'COLLABORATOR'; se normaliza al leer con [normalizeRole].
 *
 * En el WIRE (roles/{uid}, invites, espejo users/…/households) el campo `role`
 * es un string crudo que puede traer el valor legacy — por eso esos interfaces
 * lo tipan como `string` y la app normaliza en el borde de lectura.
 */
export type Role = 'OWNER' | 'PAYER' | 'MEMBER'

/** Normaliza un rol crudo del wire al modelo v2. Default seguro: MEMBER. */
export function normalizeRole(raw?: string): Role {
  switch ((raw ?? '').toUpperCase()) {
    case 'OWNER':
      return 'OWNER'
    case 'PAYER':
      return 'PAYER'
    case 'COLLABORATOR':
    case 'MEMBER':
    default:
      return 'MEMBER'
  }
}

/** Etiquetas visibles en español para cada rol v2. */
export const ROLE_LABELS: Record<Role, string> = {
  OWNER: 'Dueño',
  PAYER: 'Administrador',
  MEMBER: 'Colaborador',
}

// users/{uid}
export interface UserDoc {
  displayName: string
  email: string
  photoUrl: string
  activeHouseholdId?: string
}

// users/{uid}/households/{hid}
export interface UserHouseholdRef {
  /** Rol CRUDO del wire (puede traer 'COLLABORATOR' legacy). */
  role: string
  joinedAt: number
}

// households/{hid}
export interface Household {
  name: string
  /** Default 'MXN'. Opcional porque docs legados pueden no traerlo. */
  currency?: string
  /** Default 'America/Mexico_City'. Opcional en docs legados. */
  timezone?: string
  /** uid del fundador. Las reglas exigen createdBy == auth.uid al crear. */
  createdBy?: string
  updatedAt?: number
}

export interface HouseholdWithId extends Household {
  id: string
  /** Rol NORMALIZADO v2 (el espejo crudo se normaliza al listar). */
  role: Role
  /** En la lista mergeada siempre viene resuelto (con default). */
  currency: string
  timezone: string
}

// households/{hid}/roles/{uid}
export interface HouseholdRole {
  /** Rol CRUDO del wire ('OWNER' | 'PAYER' | 'COLLABORATOR' legacy | 'MEMBER'). */
  role: string
  /**
   * Member del hogar al que este usuario queda vinculado. En invites v2
   * nominados las reglas exigen que coincida con el `linkedMemberId` del
   * invite canjeado — el canje DEBE copiarlo.
   */
  linkedMemberId?: string
  displayName: string
  /**
   * Código de invite usado al unirse. Las reglas de Firestore validan al
   * CREAR un rol no-OWNER que exista households/{hid}/invites/{inviteCode}
   * y que su `role` coincida — sin este campo el create es rechazado.
   */
  inviteCode?: string
}

// households/{hid}/invites/{code}
export interface Invite {
  /** Rol CRUDO que otorga el invite (las reglas comparan igualdad literal). */
  role: string
  /** Invites v2 nominados: member al que queda vinculado quien canjea. */
  linkedMemberId?: string
  expiresAt: number // epoch ms
  maxUses: number
  uses: number
  createdBy: string
}

/**
 * invite_codes/{code} — índice GLOBAL de códigos de invitación OPACOS.
 * Mapea el código de 8 chars (el id del doc) al household, para poder canjear
 * sin conocer (ni exponer al compartir) el household id. Lo escribe el OWNER
 * al generar el invite; el canje solo puede incrementar `uses` (reglas).
 */
export interface InviteCodeDoc {
  householdId: string
  /** Rol CRUDO (mismo criterio que Invite.role). */
  role: string
  expiresAt: number // epoch ms
  maxUses: number
  uses: number
  createdBy: string
  updatedAt: number
}

// households/{hid}/members/{id}
export interface Member {
  displayName: string
  role: string
  isActive: boolean
  /** Android lo persiste como string JSON; docs legados podrían traer array. */
  shortAliases?: string | string[]
  meta?: unknown
  updatedAt: number
}

export interface MemberWithId extends Member {
  id: string
}

// households/{hid}/wallets/{id}  (payment_method en Room)
export interface Wallet {
  displayName: string
  kind: string
  issuer?: string
  last4?: string
  currentBalanceMxn: number
  ownerMemberId?: string
  isActive: boolean
  updatedAt: number
}

export interface WalletWithId extends Wallet {
  id: string
}

export type CategoryKind = string

// households/{hid}/categories/{id}
export interface Category {
  displayName: string
  code: string
  parentId?: string
  kind: CategoryKind
  colorHex?: string
  budgetDefaultMxn?: number
  sortOrder: number
  updatedAt: number
}

export interface CategoryWithId extends Category {
  id: string
}

export type QuincenaStatus = string

// households/{hid}/quincenas/{id}
export interface Quincena {
  year: number
  month: number
  /** Room lo persiste como "H1"/"H2"; docs legados podrían traer 1/2. */
  half: number | string
  /** Room lo persiste como "YYYY-MM-DD"; docs legados podrían traer epoch ms. */
  startDate: string | number
  endDate: string | number
  label: string
  status: QuincenaStatus
  projectedIncomeMxn: number
  actualIncomeMxn: number
  projectedExpensesMxn: number
  actualExpensesMxn: number
}

export interface QuincenaWithId extends Quincena {
  id: string
}

// households/{hid}/expenses/{id}
export interface Expense {
  concept: string
  amountMxn: number
  categoryId: string
  quincenaId: string
  occurredAt: number
  paymentMethodId?: string
  status: string
  settlementStatus?: string
  externalPayerMemberId?: string
  notes?: string | null
  householdId?: string
  createdAt?: number
  updatedAt: number
  /**
   * Lápida (tombstone): epoch ms del soft-delete, ausente/0 = vivo. El delete
   * NO borra el doc (un dispositivo offline prolongado lo resucitaría al no
   * ver el REMOVED); lo reemplaza por una lápida mínima con este campo. Toda
   * lectura debe filtrar los docs con deletedAt > 0.
   */
  deletedAt?: number
}

export interface ExpenseWithId extends Expense {
  id: string
}

// households/{hid}/expenses/{id}/attributions/{id}
export interface Attribution {
  expenseId: string
  memberId: string
  role: 'BENEFICIARY' | 'PAYER'
  shareBps: number
  shareAmountMxn: number
}

/* ── Captura real del titular (OWNER) desde la web ───────────────────────── */

/** Una participación de un miembro en una dimensión de atribución, en % (0-100). */
export interface AttributionInput {
  memberId: string
  /** Porcentaje entero 0-100. Cada dimensión debe sumar exactamente 100. */
  percent: number
}

/**
 * Entrada para crear/editar un gasto real (OWNER|PAYER; los colaboradores usan
 * proposals). Mismo contrato que la captura de la app Android: dos dimensiones
 * independientes de atribución (BENEFICIARY = quién consume, PAYER = quién
 * paga), cada una convertida a basis points que suman EXACTO 10000.
 */
export interface ExpenseInput {
  concept: string
  amountMxn: number
  /** Epoch ms. La quincena se resuelve consultando las quincenas del hogar. */
  occurredAt: number
  categoryId: string
  /** Wallet/método de pago. OBLIGATORIO: el mapper de Android descarta docs sin él. */
  paymentMethodId: string
  notes?: string
  /** Dimensión BENEFICIARY: debe sumar 100%. */
  beneficiaries: AttributionInput[]
  /** Dimensión PAYER: debe sumar 100%. */
  payers: AttributionInput[]
}

/**
 * Entrada para registrar un INGRESO real (OWNER|PAYER). Contrato con el pull
 * de Android (FirestoreMappers.toIncomeSourceEntity): householdId, quincenaId,
 * memberId, label, amountMxn y expectedDate son OBLIGATORIOS (el mapper
 * descarta el doc si falta cualquiera); cadence default "QUINCENAL", status
 * "POSTED" (un ingreso capturado ya se recibió) acredita el wallet.
 */
export interface IncomeInput {
  /** Descripción del ingreso (p. ej. "Nómina", "Aguinaldo"). */
  label: string
  amountMxn: number
  /** Epoch ms de recepción; define la quincena y el expectedDate (fecha MX). */
  receivedAt: number
  /** Miembro que recibe el ingreso. OBLIGATORIO para el mapper de Android. */
  memberId: string
  /** Wallet donde se deposita (se acredita el saldo). */
  paymentMethodId: string
}

export type ProposalKind = 'EXPENSE' | 'FUTURE_PAYMENT'

/**
 * Estado del ciclo de vida de una propuesta. La web (colaborador) solo escribe
 * PENDING; ACCEPTED/REJECTED los escribe el titular (desde la app Android o
 * desde la web vía resolveProposal — las reglas solo lo permiten al OWNER).
 */
export type ProposalStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED'

// households/{hid}/proposals/{id}
export interface Proposal {
  kind: ProposalKind
  concept: string
  amountMxn: number
  occurredAt: number
  categoryId?: string
  note?: string
  proposedByUid: string
  proposedByName: string
  createdAt: number
  status: ProposalStatus
  /** Epoch ms en que el titular resolvió (aceptó/rechazó). */
  resolvedAt?: number
  /** Id del expense creado al aceptar. Lo escribe Android. */
  expenseId?: string
  /** Epoch ms en que el titular reembolsó al proposer. Lo escribe Android. */
  reimbursedAt?: number
}

export interface ProposalWithId extends Proposal {
  id: string
}
