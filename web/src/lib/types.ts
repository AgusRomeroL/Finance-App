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

export type Role = 'OWNER' | 'COLLABORATOR'

// users/{uid}
export interface UserDoc {
  displayName: string
  email: string
  photoUrl: string
  activeHouseholdId?: string
}

// users/{uid}/households/{hid}
export interface UserHouseholdRef {
  role: Role
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
  role: Role
  /** En la lista mergeada siempre viene resuelto (con default). */
  currency: string
  timezone: string
}

// households/{hid}/roles/{uid}
export interface HouseholdRole {
  role: Role
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
  role: Role
  expiresAt: number // epoch ms
  maxUses: number
  uses: number
  createdBy: string
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
 * Entrada para crear/editar un gasto real (solo OWNER; los colaboradores usan
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
