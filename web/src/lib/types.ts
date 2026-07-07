/**
 * Tipos que reflejan el contrato Firestore EXACTO compartido con la app Android.
 * Campos en camelCase (así los serializa el cliente Android al hacer set()).
 * Fechas (occurredAt/createdAt/expiresAt) = epoch millis (number).
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
  currency: 'MXN'
  timezone: 'America/Mexico_City'
}

export interface HouseholdWithId extends Household {
  id: string
  role: Role
}

// households/{hid}/roles/{uid}
export interface HouseholdRole {
  role: Role
  linkedMemberId?: string
  displayName: string
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
  shortAliases?: string[]
  meta?: Record<string, unknown>
  updatedAt: number
}

export interface MemberWithId extends Member {
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
  half: number
  startDate: number
  endDate: number
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
  updatedAt: number
}

export interface ExpenseWithId extends Expense {
  id: string
}

// households/{hid}/expenses/{id}/attributions/{id}
export interface Attribution {
  memberId: string
  role: 'BENEFICIARY' | 'PAYER'
  shareBps: number
  shareAmountMxn: number
}

export type ProposalKind = 'EXPENSE' | 'FUTURE_PAYMENT'

/**
 * Estado del ciclo de vida de una propuesta. La web solo escribe PENDING;
 * ACCEPTED/REJECTED (y los campos de resolución) los escribe la app Android
 * cuando el titular la revisa.
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
  /** Epoch ms en que el titular resolvió (aceptó/rechazó). Lo escribe Android. */
  resolvedAt?: number
  /** Id del expense creado al aceptar. Lo escribe Android. */
  expenseId?: string
  /** Epoch ms en que el titular reembolsó al proposer. Lo escribe Android. */
  reimbursedAt?: number
}

export interface ProposalWithId extends Proposal {
  id: string
}
