import { useEffect, useMemo, useState } from 'react'
import { useHousehold } from '../context/HouseholdContext'
import { Button, EmptyState, ErrorState, Field, fieldCls, LoadingState, Modal } from '../components/ui'
import {
  addToSavingsGoal,
  applyLoanPayment,
  createWallet,
  createWalletTransfer,
  listInstallmentPlans,
  listLoans,
  listMembers,
  listSavingsGoals,
  listWallets,
  reconcileWalletBalance,
  updateWallet,
  upsertInstallmentPlan,
  upsertLoan,
  upsertSavingsGoal,
} from '../lib/repository'
import { formatMxn } from '../lib/format'
import type {
  InstallmentPlanWithId,
  LoanWithId,
  MemberWithId,
  SavingsGoalWithId,
  WalletWithId,
} from '../lib/types'

/* ---------------------------------------------------------------------------
 * Cuentas (/cuentas) — OWNER | PAYER.
 *
 * Hoja de balance del hogar como la pantalla Cuentas de la app: wallets por
 * secciones (kind) con saldo, CRUD de wallets, transferencias entre cuentas
 * (RF-41), reconciliar saldo, metas de ahorro (con abonos), préstamos por
 * cobrar (con abonos) y planes MSI (sin materializar cuotas — eso lo hace el
 * teléfono). Todas las escrituras van por el repository (writeBatch camelCase
 * con updatedAt, campos obligatorios de FirestoreMappers.kt).
 * ------------------------------------------------------------------------- */

/** Kinds cuyo saldo es dinero disponible (mismo criterio que WalletsScreen). */
const LIQUID_KINDS = new Set(['DEBIT_ACCOUNT', 'CASH', 'DIGITAL_WALLET', 'EMPLOYER_SAVINGS_FUND'])
const CREDIT_KINDS = new Set(['CREDIT_CARD', 'DEPARTMENT_STORE_CARD', 'BNPL_INSTALLMENT'])

const KIND_LABELS: Record<string, string> = {
  DEBIT_ACCOUNT: 'Cuenta de débito',
  CREDIT_CARD: 'Tarjeta de crédito',
  DEPARTMENT_STORE_CARD: 'Tarjeta departamental',
  BNPL_INSTALLMENT: 'Plazos sin tarjeta (BNPL)',
  DIGITAL_WALLET: 'Monedero digital',
  CASH: 'Efectivo',
  EMPLOYER_SAVINGS_FUND: 'Fondo de ahorro',
}

/** Secciones en orden de presentación (espejo de WALLET_SECTIONS de la app). */
const WALLET_SECTIONS: Array<{ label: string; match: (kind: string) => boolean }> = [
  { label: 'Disponible', match: (k) => LIQUID_KINDS.has(k) },
  { label: 'Tarjetas de crédito', match: (k) => k === 'CREDIT_CARD' },
  { label: 'Tiendas departamentales', match: (k) => k === 'DEPARTMENT_STORE_CARD' },
  { label: 'Meses sin intereses', match: (k) => k === 'BNPL_INSTALLMENT' },
]

interface PageData {
  wallets: WalletWithId[]
  members: MemberWithId[]
  goals: SavingsGoalWithId[]
  loans: LoanWithId[]
  plans: InstallmentPlanWithId[]
}

/* Drafts de los modales */

interface WalletDraft {
  id: string | null // null = crear
  displayName: string
  kind: string
  ownerMemberId: string
  creditLimit: string
  isActive: boolean
  initialBalance: string
}

interface TransferDraft {
  fromId: string
  toId: string
  amount: string
  note: string
}

interface ReconcileDraft {
  wallet: WalletWithId
  balance: string
}

interface GoalDraft {
  id: string | null
  name: string
  target: string
  current: string
  targetDate: string
  linkedPaymentMethodId: string
}

interface LoanDraft {
  id: string | null
  debtorMemberId: string
  principal: string
  remaining: string
  interest: string
  issuedAt: string
  dueAt: string
  notes: string
}

interface PlanDraft {
  id: string | null
  displayName: string
  principal: string
  totalInstallments: string
  installmentAmount: string
  startDate: string
  currentInstallment: string
  paymentMethodId: string
  fundingPaymentMethodId: string
}

/** Abono (meta o préstamo). */
interface DepositDraft {
  kind: 'goal' | 'loan'
  id: string
  title: string
  amount: string
}

function todayInput(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export default function AccountsPage() {
  const { active, loading: hhLoading } = useHousehold()
  const hid = active?.id ?? null

  const [data, setData] = useState<PageData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const [walletDraft, setWalletDraft] = useState<WalletDraft | null>(null)
  const [transferDraft, setTransferDraft] = useState<TransferDraft | null>(null)
  const [reconcileDraft, setReconcileDraft] = useState<ReconcileDraft | null>(null)
  const [goalDraft, setGoalDraft] = useState<GoalDraft | null>(null)
  const [loanDraft, setLoanDraft] = useState<LoanDraft | null>(null)
  const [planDraft, setPlanDraft] = useState<PlanDraft | null>(null)
  const [depositDraft, setDepositDraft] = useState<DepositDraft | null>(null)

  const [busy, setBusy] = useState(false)
  const [modalError, setModalError] = useState<string | null>(null)

  useEffect(() => {
    if (!hid) return
    let cancelled = false
    setData(null)
    setError(null)
    Promise.all([listWallets(hid), listMembers(hid), listSavingsGoals(hid), listLoans(hid), listInstallmentPlans(hid)])
      .then(([wallets, members, goals, loans, plans]) => {
        if (!cancelled) setData({ wallets, members, goals, loans, plans })
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'No se pudieron cargar las cuentas.')
      })
    return () => {
      cancelled = true
    }
  }, [hid, reloadKey])

  const memberMap = useMemo(
    () => new Map((data?.members ?? []).map((m) => [m.id, m.displayName])),
    [data],
  )
  const walletMap = useMemo(
    () => new Map((data?.wallets ?? []).map((w) => [w.id, w.displayName])),
    [data],
  )

  const sections = useMemo(() => {
    const wallets = data?.wallets ?? []
    const used = new Set<string>()
    const rows = WALLET_SECTIONS.map((s) => {
      const items = wallets.filter((w) => s.match(w.kind))
      items.forEach((w) => used.add(w.id))
      return { label: s.label, items }
    }).filter((s) => s.items.length > 0)
    const others = wallets.filter((w) => !used.has(w.id))
    if (others.length > 0) rows.push({ label: 'Otros', items: others })
    return rows
  }, [data])

  const liquidTotal = useMemo(
    () =>
      (data?.wallets ?? [])
        .filter((w) => w.isActive && LIQUID_KINDS.has(w.kind))
        .reduce((s, w) => s + w.currentBalanceMxn, 0),
    [data],
  )

  function closeModals() {
    if (busy) return
    setWalletDraft(null)
    setTransferDraft(null)
    setReconcileDraft(null)
    setGoalDraft(null)
    setLoanDraft(null)
    setPlanDraft(null)
    setDepositDraft(null)
    setModalError(null)
  }

  function reload() {
    closeModals()
    setReloadKey((k) => k + 1)
  }

  /** Envuelve una escritura: busy + captura de error + recarga al terminar. */
  async function run(action: () => Promise<unknown>) {
    if (busy) return
    setBusy(true)
    setModalError(null)
    try {
      await action()
      setBusy(false)
      reload()
    } catch (e) {
      setBusy(false)
      setModalError(e instanceof Error ? e.message : 'No se pudo guardar.')
    }
  }

  function parseAmount(raw: string, label: string): number {
    const n = Number.parseFloat(raw)
    if (!Number.isFinite(n) || n <= 0) throw new Error(`${label} debe ser mayor que cero.`)
    return n
  }

  if (hhLoading) return <LoadingState />
  if (!hid) {
    return (
      <EmptyState
        title="Selecciona un grupo primero"
        hint="Ve a la pestaña Grupos y elige (o crea) un grupo activo."
      />
    )
  }
  if (error) return <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
  if (!data) return <LoadingState label="Cargando cuentas…" />

  const members = data.members.filter((m) => m.isActive)
  const activeWallets = data.wallets.filter((w) => w.isActive)

  return (
    <div className="space-y-6 pb-8">
      {/* Header + acciones */}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="px-1 text-lg font-semibold text-on-surface">Cuentas</h2>
          <p className="px-1 text-xs text-on-surface-variant">
            Disponible total: <span className="tnum font-semibold">{formatMxn(liquidTotal)}</span>
          </p>
        </div>
        <div className="flex gap-2">
          <Button
            variant="secondary"
            onClick={() => {
              setModalError(null)
              setTransferDraft({ fromId: '', toId: '', amount: '', note: '' })
            }}
          >
            Transferir
          </Button>
          <Button
            onClick={() => {
              setModalError(null)
              setWalletDraft({
                id: null,
                displayName: '',
                kind: 'DEBIT_ACCOUNT',
                ownerMemberId: '',
                creditLimit: '',
                isActive: true,
                initialBalance: '',
              })
            }}
          >
            Nueva cuenta
          </Button>
        </div>
      </div>

      {/* Secciones de wallets */}
      {sections.length === 0 ? (
        <EmptyState title="Sin cuentas" hint="Crea la primera con “Nueva cuenta”." />
      ) : (
        sections.map((section) => (
          <section key={section.label}>
            <h3 className="eyebrow mb-2 px-1">{section.label}</h3>
            <ul className="space-y-2">
              {section.items.map((w) => (
                <li
                  key={w.id}
                  className={`rounded-card-sm bg-surface-1 px-4 py-3.5 ${w.isActive ? '' : 'opacity-55'}`}
                >
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-on-surface">
                        {w.displayName}
                        {!w.isActive && (
                          <span className="ml-2 text-[11px] font-semibold uppercase tracking-wide text-on-surface-variant">
                            Inactiva
                          </span>
                        )}
                      </p>
                      <p className="mt-0.5 text-xs text-on-surface-variant">
                        {KIND_LABELS[w.kind] ?? w.kind}
                        {w.ownerMemberId && memberMap.get(w.ownerMemberId)
                          ? ` · ${memberMap.get(w.ownerMemberId)}`
                          : ''}
                        {w.last4 ? ` · •••• ${w.last4}` : ''}
                      </p>
                    </div>
                    <div className="shrink-0 text-right">
                      <p
                        className={`tnum text-sm font-semibold ${
                          CREDIT_KINDS.has(w.kind)
                            ? 'text-on-surface'
                            : w.currentBalanceMxn < 0
                              ? 'text-expense'
                              : 'text-on-surface'
                        }`}
                      >
                        {formatMxn(w.currentBalanceMxn)}
                      </p>
                      <div className="mt-1.5 flex justify-end gap-1.5">
                        <button
                          type="button"
                          onClick={() => {
                            setModalError(null)
                            setReconcileDraft({ wallet: w, balance: String(w.currentBalanceMxn) })
                          }}
                          className="rounded-full bg-surface-2 px-3 py-1 text-xs font-medium text-on-surface transition-colors hover:bg-surface-3"
                        >
                          Reconciliar
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setModalError(null)
                            setWalletDraft({
                              id: w.id,
                              displayName: w.displayName,
                              kind: w.kind,
                              ownerMemberId: w.ownerMemberId ?? '',
                              creditLimit: w.creditLimitMxn !== undefined ? String(w.creditLimitMxn) : '',
                              isActive: w.isActive,
                              initialBalance: '',
                            })
                          }}
                          className="rounded-full bg-surface-2 px-3 py-1 text-xs font-medium text-on-surface transition-colors hover:bg-surface-3"
                        >
                          Editar
                        </button>
                      </div>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          </section>
        ))
      )}

      {/* Metas de ahorro */}
      <section>
        <div className="mb-2 flex items-center justify-between gap-2 px-1">
          <h3 className="eyebrow">Metas de ahorro</h3>
          <button
            type="button"
            onClick={() => {
              setModalError(null)
              setGoalDraft({ id: null, name: '', target: '', current: '', targetDate: '', linkedPaymentMethodId: '' })
            }}
            className="text-xs font-semibold text-primary hover:underline"
          >
            + Nueva meta
          </button>
        </div>
        {data.goals.length === 0 ? (
          <EmptyState title="Sin metas de ahorro" hint="Crea una meta y regístrale abonos." />
        ) : (
          <ul className="space-y-2">
            {data.goals.map((g) => {
              const pct = g.targetMxn > 0 ? Math.min(100, (g.currentMxn / g.targetMxn) * 100) : 0
              return (
                <li key={g.id} className="rounded-card-sm bg-surface-1 px-4 py-3.5">
                  <div className="mb-2 flex items-baseline justify-between gap-2">
                    <p className="truncate text-sm font-medium text-on-surface">{g.name}</p>
                    <p className="tnum shrink-0 text-xs text-on-surface-variant">
                      <span className="font-semibold text-income">{formatMxn(g.currentMxn)}</span>
                      <span className="opacity-60"> / {formatMxn(g.targetMxn)}</span>
                    </p>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-full bg-surface-3">
                    <div className="h-full rounded-full bg-income" style={{ width: `${pct}%` }} />
                  </div>
                  <div className="mt-2 flex items-center justify-between gap-2">
                    <p className="text-xs text-on-surface-variant">
                      {g.targetDate ? `Meta: ${g.targetDate}` : `${Math.round(pct)}% completado`}
                      {g.linkedPaymentMethodId && walletMap.get(g.linkedPaymentMethodId)
                        ? ` · ${walletMap.get(g.linkedPaymentMethodId)}`
                        : ''}
                    </p>
                    <div className="flex shrink-0 gap-1.5">
                      <button
                        type="button"
                        onClick={() => {
                          setModalError(null)
                          setDepositDraft({ kind: 'goal', id: g.id, title: g.name, amount: '' })
                        }}
                        className="rounded-full bg-primary-container px-3 py-1 text-xs font-semibold text-on-primary-container transition-all active:scale-95"
                      >
                        Abonar
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setModalError(null)
                          setGoalDraft({
                            id: g.id,
                            name: g.name,
                            target: String(g.targetMxn),
                            current: String(g.currentMxn),
                            targetDate: g.targetDate ?? '',
                            linkedPaymentMethodId: g.linkedPaymentMethodId ?? '',
                          })
                        }}
                        className="rounded-full bg-surface-2 px-3 py-1 text-xs font-medium text-on-surface transition-colors hover:bg-surface-3"
                      >
                        Editar
                      </button>
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      {/* Préstamos por cobrar */}
      <section>
        <div className="mb-2 flex items-center justify-between gap-2 px-1">
          <h3 className="eyebrow">Préstamos por cobrar</h3>
          <button
            type="button"
            onClick={() => {
              setModalError(null)
              setLoanDraft({
                id: null,
                debtorMemberId: members[0]?.id ?? '',
                principal: '',
                remaining: '',
                interest: '',
                issuedAt: todayInput(),
                dueAt: '',
                notes: '',
              })
            }}
            className="text-xs font-semibold text-primary hover:underline"
          >
            + Nuevo préstamo
          </button>
        </div>
        {data.loans.length === 0 ? (
          <EmptyState title="Sin préstamos por cobrar" />
        ) : (
          <ul className="space-y-2">
            {data.loans.map((l) => {
              const settled = l.remainingBalanceMxn <= 0
              return (
                <li key={l.id} className={`rounded-card-sm bg-surface-1 px-4 py-3.5 ${settled ? 'opacity-55' : ''}`}>
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-on-surface">
                        {memberMap.get(l.debtorMemberId) ?? 'Miembro'}
                        {settled && (
                          <span className="ml-2 text-[11px] font-semibold uppercase tracking-wide text-income">
                            Liquidado
                          </span>
                        )}
                      </p>
                      <p className="mt-0.5 text-xs text-on-surface-variant">
                        Prestado {formatMxn(l.principalMxn)} · {l.issuedAt || 'sin fecha'}
                        {l.notes ? ` · ${l.notes}` : ''}
                      </p>
                    </div>
                    <div className="shrink-0 text-right">
                      <p className="tnum text-sm font-semibold text-on-surface">
                        {formatMxn(l.remainingBalanceMxn)}
                      </p>
                      <p className="text-[11px] text-on-surface-variant">pendiente</p>
                    </div>
                  </div>
                  <div className="mt-2 flex justify-end gap-1.5">
                    {!settled && (
                      <button
                        type="button"
                        onClick={() => {
                          setModalError(null)
                          setDepositDraft({
                            kind: 'loan',
                            id: l.id,
                            title: memberMap.get(l.debtorMemberId) ?? 'Préstamo',
                            amount: '',
                          })
                        }}
                        className="rounded-full bg-primary-container px-3 py-1 text-xs font-semibold text-on-primary-container transition-all active:scale-95"
                      >
                        Abonar
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => {
                        setModalError(null)
                        setLoanDraft({
                          id: l.id,
                          debtorMemberId: l.debtorMemberId,
                          principal: String(l.principalMxn),
                          remaining: String(l.remainingBalanceMxn),
                          interest: String(l.agreedInterestMxn || ''),
                          issuedAt: l.issuedAt || todayInput(),
                          dueAt: l.dueAt ?? '',
                          notes: l.notes ?? '',
                        })
                      }}
                      className="rounded-full bg-surface-2 px-3 py-1 text-xs font-medium text-on-surface transition-colors hover:bg-surface-3"
                    >
                      Editar
                    </button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      {/* Meses sin intereses (planes) */}
      <section>
        <div className="mb-2 flex items-center justify-between gap-2 px-1">
          <h3 className="eyebrow">Planes a meses (MSI)</h3>
          <button
            type="button"
            onClick={() => {
              setModalError(null)
              setPlanDraft({
                id: null,
                displayName: '',
                principal: '',
                totalInstallments: '',
                installmentAmount: '',
                startDate: todayInput(),
                currentInstallment: '0',
                paymentMethodId: '',
                fundingPaymentMethodId: '',
              })
            }}
            className="text-xs font-semibold text-primary hover:underline"
          >
            + Nuevo plan
          </button>
        </div>
        <p className="mb-2 px-1 text-xs text-on-surface-variant">
          Las cuotas mensuales las materializa el teléfono; aquí solo se administra el plan.
        </p>
        {data.plans.length === 0 ? (
          <EmptyState title="Sin planes a meses" />
        ) : (
          <ul className="space-y-2">
            {data.plans.map((p) => {
              const pct = p.totalInstallments > 0 ? Math.min(100, (p.currentInstallment / p.totalInstallments) * 100) : 0
              const done = p.status !== 'ACTIVE'
              return (
                <li key={p.id} className={`rounded-card-sm bg-surface-1 px-4 py-3.5 ${done ? 'opacity-55' : ''}`}>
                  <div className="mb-2 flex items-baseline justify-between gap-2">
                    <p className="truncate text-sm font-medium text-on-surface">{p.displayName}</p>
                    <p className="tnum shrink-0 text-xs text-on-surface-variant">
                      <span className="font-semibold text-on-surface">{formatMxn(p.installmentAmountMxn)}</span>
                      <span className="opacity-60">/mes</span>
                    </p>
                  </div>
                  <div className="h-2 w-full overflow-hidden rounded-full bg-surface-3">
                    <div className="h-full rounded-full bg-primary" style={{ width: `${pct}%` }} />
                  </div>
                  <div className="mt-2 flex items-center justify-between gap-2">
                    <p className="text-xs text-on-surface-variant">
                      Pago {Math.min(p.currentInstallment + 1, p.totalInstallments)} de {p.totalInstallments} · termina{' '}
                      {planEndLabel(p.startDate, p.totalInstallments)}
                      {p.paymentMethodId && walletMap.get(p.paymentMethodId)
                        ? ` · ${walletMap.get(p.paymentMethodId)}`
                        : ''}
                    </p>
                    <button
                      type="button"
                      onClick={() => {
                        setModalError(null)
                        setPlanDraft({
                          id: p.id,
                          displayName: p.displayName,
                          principal: String(p.principalMxn),
                          totalInstallments: String(p.totalInstallments),
                          installmentAmount: String(p.installmentAmountMxn),
                          startDate: p.startDate || todayInput(),
                          currentInstallment: String(p.currentInstallment),
                          paymentMethodId: p.paymentMethodId ?? '',
                          fundingPaymentMethodId: p.fundingPaymentMethodId ?? '',
                        })
                      }}
                      className="shrink-0 rounded-full bg-surface-2 px-3 py-1 text-xs font-medium text-on-surface transition-colors hover:bg-surface-3"
                    >
                      Editar
                    </button>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      {/* ── Modal: wallet crear/editar ─────────────────────────────────────── */}
      {walletDraft && (
        <Modal
          title={walletDraft.id === null ? 'Nueva cuenta' : 'Editar cuenta'}
          onClose={closeModals}
          busy={busy}
        >
          <div className="space-y-3">
            <Field label="Nombre">
              <input
                value={walletDraft.displayName}
                onChange={(e) => setWalletDraft((d) => (d ? { ...d, displayName: e.target.value } : d))}
                placeholder="BBVA débito, Efectivo…"
                className={fieldCls}
              />
            </Field>
            <Field label="Tipo">
              <select
                value={walletDraft.kind}
                onChange={(e) => setWalletDraft((d) => (d ? { ...d, kind: e.target.value } : d))}
                className={fieldCls}
              >
                {Object.entries(KIND_LABELS).map(([k, label]) => (
                  <option key={k} value={k}>
                    {label}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Titular (opcional)">
              <select
                value={walletDraft.ownerMemberId}
                onChange={(e) => setWalletDraft((d) => (d ? { ...d, ownerMemberId: e.target.value } : d))}
                className={fieldCls}
              >
                <option value="">Del hogar</option>
                {members.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.displayName}
                  </option>
                ))}
              </select>
            </Field>
            {CREDIT_KINDS.has(walletDraft.kind) && (
              <Field label="Límite de crédito MXN (opcional)">
                <input
                  type="number"
                  inputMode="decimal"
                  min="0"
                  step="0.01"
                  value={walletDraft.creditLimit}
                  onChange={(e) => setWalletDraft((d) => (d ? { ...d, creditLimit: e.target.value } : d))}
                  className={fieldCls}
                />
              </Field>
            )}
            {walletDraft.id === null && (
              <Field label="Saldo inicial MXN (opcional)">
                <input
                  type="number"
                  inputMode="decimal"
                  step="0.01"
                  value={walletDraft.initialBalance}
                  onChange={(e) => setWalletDraft((d) => (d ? { ...d, initialBalance: e.target.value } : d))}
                  className={fieldCls}
                />
              </Field>
            )}
            <label className="flex items-center gap-2.5 px-1 py-1">
              <input
                type="checkbox"
                checked={walletDraft.isActive}
                onChange={(e) => setWalletDraft((d) => (d ? { ...d, isActive: e.target.checked } : d))}
                className="h-4 w-4 accent-[rgb(var(--c-primary))]"
              />
              <span className="text-sm text-on-surface">Cuenta activa</span>
            </label>
            <ModalFooter
              busy={busy}
              error={modalError}
              onCancel={closeModals}
              onSave={() =>
                void run(() => {
                  const d = walletDraft
                  // El límite solo aplica a kinds de crédito (si cambió a un
                  // kind líquido, el límite previo se limpia).
                  const creditLimit =
                    CREDIT_KINDS.has(d.kind) && d.creditLimit.trim() !== ''
                      ? Number.parseFloat(d.creditLimit)
                      : undefined
                  const input = {
                    displayName: d.displayName,
                    kind: d.kind,
                    ownerMemberId: d.ownerMemberId || undefined,
                    creditLimitMxn: Number.isFinite(creditLimit) ? creditLimit : undefined,
                    isActive: d.isActive,
                    initialBalanceMxn:
                      d.initialBalance.trim() === '' ? undefined : Number.parseFloat(d.initialBalance),
                  }
                  return d.id === null ? createWallet(hid, input) : updateWallet(hid, d.id, input)
                })
              }
            />
          </div>
        </Modal>
      )}

      {/* ── Modal: transferencia ───────────────────────────────────────────── */}
      {transferDraft && (
        <Modal title="Transferir entre cuentas" onClose={closeModals} busy={busy}>
          <div className="space-y-3">
            <Field label="Desde">
              <select
                value={transferDraft.fromId}
                onChange={(e) => setTransferDraft((d) => (d ? { ...d, fromId: e.target.value } : d))}
                className={fieldCls}
              >
                <option value="">Selecciona la cuenta origen…</option>
                {activeWallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.displayName} · {formatMxn(w.currentBalanceMxn)}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Hacia">
              <select
                value={transferDraft.toId}
                onChange={(e) => setTransferDraft((d) => (d ? { ...d, toId: e.target.value } : d))}
                className={fieldCls}
              >
                <option value="">Selecciona la cuenta destino…</option>
                {activeWallets
                  .filter((w) => w.id !== transferDraft.fromId)
                  .map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.displayName} · {formatMxn(w.currentBalanceMxn)}
                    </option>
                  ))}
              </select>
            </Field>
            <Field label="Monto MXN">
              <input
                type="number"
                inputMode="decimal"
                min="0.01"
                step="0.01"
                value={transferDraft.amount}
                onChange={(e) => setTransferDraft((d) => (d ? { ...d, amount: e.target.value } : d))}
                className={fieldCls}
              />
            </Field>
            <Field label="Nota (opcional)">
              <input
                value={transferDraft.note}
                onChange={(e) => setTransferDraft((d) => (d ? { ...d, note: e.target.value } : d))}
                placeholder="Pago de tarjeta, retiro…"
                className={fieldCls}
              />
            </Field>
            <ModalFooter
              busy={busy}
              error={modalError}
              saveLabel="Transferir"
              onCancel={closeModals}
              onSave={() =>
                void run(() =>
                  createWalletTransfer(hid, {
                    fromPaymentMethodId: transferDraft.fromId,
                    toPaymentMethodId: transferDraft.toId,
                    amountMxn: parseAmount(transferDraft.amount, 'El monto'),
                    note: transferDraft.note || undefined,
                  }),
                )
              }
            />
          </div>
        </Modal>
      )}

      {/* ── Modal: reconciliar saldo ───────────────────────────────────────── */}
      {reconcileDraft && (
        <Modal title={`Reconciliar ${reconcileDraft.wallet.displayName}`} onClose={closeModals} busy={busy}>
          <div className="space-y-3">
            <p className="text-xs text-on-surface-variant">
              Fija el saldo REAL observado de la cuenta (estado de cuenta, app del banco). Saldo
              sincronizado actual: <span className="tnum font-semibold">{formatMxn(reconcileDraft.wallet.currentBalanceMxn)}</span>.
            </p>
            <Field label="Saldo actual MXN">
              <input
                type="number"
                inputMode="decimal"
                step="0.01"
                value={reconcileDraft.balance}
                onChange={(e) => setReconcileDraft((d) => (d ? { ...d, balance: e.target.value } : d))}
                className={fieldCls}
              />
            </Field>
            <ModalFooter
              busy={busy}
              error={modalError}
              saveLabel="Fijar saldo"
              onCancel={closeModals}
              onSave={() =>
                void run(() => {
                  const n = Number.parseFloat(reconcileDraft.balance)
                  if (!Number.isFinite(n)) throw new Error('El saldo debe ser un número válido.')
                  return reconcileWalletBalance(hid, reconcileDraft.wallet.id, n)
                })
              }
            />
          </div>
        </Modal>
      )}

      {/* ── Modal: meta de ahorro crear/editar ─────────────────────────────── */}
      {goalDraft && (
        <Modal title={goalDraft.id === null ? 'Nueva meta de ahorro' : 'Editar meta'} onClose={closeModals} busy={busy}>
          <div className="space-y-3">
            <Field label="Nombre">
              <input
                value={goalDraft.name}
                onChange={(e) => setGoalDraft((d) => (d ? { ...d, name: e.target.value } : d))}
                placeholder="Vacaciones, fondo de emergencia…"
                className={fieldCls}
              />
            </Field>
            <div className="grid grid-cols-2 gap-2">
              <Field label="Meta MXN">
                <input
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  step="0.01"
                  value={goalDraft.target}
                  onChange={(e) => setGoalDraft((d) => (d ? { ...d, target: e.target.value } : d))}
                  className={fieldCls}
                />
              </Field>
              <Field label="Ahorrado MXN">
                <input
                  type="number"
                  inputMode="decimal"
                  min="0"
                  step="0.01"
                  value={goalDraft.current}
                  onChange={(e) => setGoalDraft((d) => (d ? { ...d, current: e.target.value } : d))}
                  className={fieldCls}
                />
              </Field>
            </div>
            <Field label="Fecha objetivo (opcional)">
              <input
                type="date"
                value={goalDraft.targetDate}
                onChange={(e) => setGoalDraft((d) => (d ? { ...d, targetDate: e.target.value } : d))}
                className={fieldCls}
              />
            </Field>
            <Field label="Cuenta vinculada (opcional)">
              <select
                value={goalDraft.linkedPaymentMethodId}
                onChange={(e) => setGoalDraft((d) => (d ? { ...d, linkedPaymentMethodId: e.target.value } : d))}
                className={fieldCls}
              >
                <option value="">Ninguna</option>
                {activeWallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.displayName}
                  </option>
                ))}
              </select>
            </Field>
            <ModalFooter
              busy={busy}
              error={modalError}
              onCancel={closeModals}
              onSave={() =>
                void run(() =>
                  upsertSavingsGoal(hid, goalDraft.id, {
                    name: goalDraft.name,
                    targetMxn: parseAmount(goalDraft.target, 'La meta'),
                    currentMxn: goalDraft.current.trim() === '' ? 0 : Number.parseFloat(goalDraft.current) || 0,
                    targetDate: goalDraft.targetDate || undefined,
                    linkedPaymentMethodId: goalDraft.linkedPaymentMethodId || undefined,
                  }),
                )
              }
            />
          </div>
        </Modal>
      )}

      {/* ── Modal: préstamo crear/editar ───────────────────────────────────── */}
      {loanDraft && (
        <Modal title={loanDraft.id === null ? 'Nuevo préstamo' : 'Editar préstamo'} onClose={closeModals} busy={busy}>
          <div className="space-y-3">
            <Field label="Quién debe">
              <select
                value={loanDraft.debtorMemberId}
                onChange={(e) => setLoanDraft((d) => (d ? { ...d, debtorMemberId: e.target.value } : d))}
                className={fieldCls}
              >
                <option value="">Selecciona al deudor…</option>
                {data.members.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.displayName}
                  </option>
                ))}
              </select>
            </Field>
            <div className="grid grid-cols-2 gap-2">
              <Field label="Prestado MXN">
                <input
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  step="0.01"
                  value={loanDraft.principal}
                  onChange={(e) =>
                    setLoanDraft((d) => {
                      if (!d) return d
                      // Al crear, el pendiente sigue al prestado si no se editó a mano.
                      const followRemaining = d.id === null && d.remaining === d.principal
                      return { ...d, principal: e.target.value, remaining: followRemaining ? e.target.value : d.remaining }
                    })
                  }
                  className={fieldCls}
                />
              </Field>
              <Field label="Pendiente MXN">
                <input
                  type="number"
                  inputMode="decimal"
                  min="0"
                  step="0.01"
                  value={loanDraft.remaining}
                  onChange={(e) => setLoanDraft((d) => (d ? { ...d, remaining: e.target.value } : d))}
                  className={fieldCls}
                />
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <Field label="Fecha del préstamo">
                <input
                  type="date"
                  value={loanDraft.issuedAt}
                  onChange={(e) => setLoanDraft((d) => (d ? { ...d, issuedAt: e.target.value } : d))}
                  className={fieldCls}
                />
              </Field>
              <Field label="Vence (opcional)">
                <input
                  type="date"
                  value={loanDraft.dueAt}
                  onChange={(e) => setLoanDraft((d) => (d ? { ...d, dueAt: e.target.value } : d))}
                  className={fieldCls}
                />
              </Field>
            </div>
            <Field label="Interés acordado MXN (opcional)">
              <input
                type="number"
                inputMode="decimal"
                min="0"
                step="0.01"
                value={loanDraft.interest}
                onChange={(e) => setLoanDraft((d) => (d ? { ...d, interest: e.target.value } : d))}
                className={fieldCls}
              />
            </Field>
            <Field label="Notas (opcional)">
              <input
                value={loanDraft.notes}
                onChange={(e) => setLoanDraft((d) => (d ? { ...d, notes: e.target.value } : d))}
                className={fieldCls}
              />
            </Field>
            <ModalFooter
              busy={busy}
              error={modalError}
              onCancel={closeModals}
              onSave={() =>
                void run(() => {
                  const principal = parseAmount(loanDraft.principal, 'El monto prestado')
                  const remaining =
                    loanDraft.remaining.trim() === '' ? principal : Number.parseFloat(loanDraft.remaining)
                  return upsertLoan(hid, loanDraft.id, {
                    debtorMemberId: loanDraft.debtorMemberId,
                    principalMxn: principal,
                    remainingBalanceMxn: Number.isFinite(remaining) ? remaining : principal,
                    agreedInterestMxn:
                      loanDraft.interest.trim() === '' ? 0 : Number.parseFloat(loanDraft.interest) || 0,
                    issuedAt: loanDraft.issuedAt,
                    dueAt: loanDraft.dueAt || undefined,
                    notes: loanDraft.notes || undefined,
                  })
                })
              }
            />
          </div>
        </Modal>
      )}

      {/* ── Modal: plan MSI crear/editar ───────────────────────────────────── */}
      {planDraft && (
        <Modal title={planDraft.id === null ? 'Nuevo plan a meses' : 'Editar plan'} onClose={closeModals} busy={busy}>
          <div className="space-y-3">
            <Field label="Nombre">
              <input
                value={planDraft.displayName}
                onChange={(e) => setPlanDraft((d) => (d ? { ...d, displayName: e.target.value } : d))}
                placeholder="Pantalla Liverpool 12 MSI…"
                className={fieldCls}
              />
            </Field>
            <div className="grid grid-cols-2 gap-2">
              <Field label="Monto total MXN">
                <input
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  step="0.01"
                  value={planDraft.principal}
                  onChange={(e) =>
                    setPlanDraft((d) => {
                      if (!d) return d
                      const next = { ...d, principal: e.target.value }
                      // Sugerir mensualidad = total / meses si no se ha editado a mano.
                      const total = Number.parseFloat(e.target.value)
                      const n = Number.parseInt(d.totalInstallments, 10)
                      if (d.id === null && Number.isFinite(total) && n > 0 && d.installmentAmount === '') {
                        next.installmentAmount = (total / n).toFixed(2)
                      }
                      return next
                    })
                  }
                  className={fieldCls}
                />
              </Field>
              <Field label="Mensualidades">
                <input
                  type="number"
                  inputMode="numeric"
                  min="1"
                  step="1"
                  value={planDraft.totalInstallments}
                  onChange={(e) => setPlanDraft((d) => (d ? { ...d, totalInstallments: e.target.value } : d))}
                  className={fieldCls}
                />
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-2">
              <Field label="Mensualidad MXN">
                <input
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  step="0.01"
                  value={planDraft.installmentAmount}
                  onChange={(e) => setPlanDraft((d) => (d ? { ...d, installmentAmount: e.target.value } : d))}
                  className={fieldCls}
                />
              </Field>
              <Field label="Pagos hechos">
                <input
                  type="number"
                  inputMode="numeric"
                  min="0"
                  step="1"
                  value={planDraft.currentInstallment}
                  onChange={(e) => setPlanDraft((d) => (d ? { ...d, currentInstallment: e.target.value } : d))}
                  className={fieldCls}
                />
              </Field>
            </div>
            <Field label="Fecha de inicio">
              <input
                type="date"
                value={planDraft.startDate}
                onChange={(e) => setPlanDraft((d) => (d ? { ...d, startDate: e.target.value } : d))}
                className={fieldCls}
              />
            </Field>
            <Field label="Tarjeta del plan (opcional)">
              <select
                value={planDraft.paymentMethodId}
                onChange={(e) => setPlanDraft((d) => (d ? { ...d, paymentMethodId: e.target.value } : d))}
                className={fieldCls}
              >
                <option value="">Sin tarjeta</option>
                {data.wallets
                  .filter((w) => CREDIT_KINDS.has(w.kind))
                  .map((w) => (
                    <option key={w.id} value={w.id}>
                      {w.displayName}
                    </option>
                  ))}
              </select>
            </Field>
            <Field label="Cuenta que paga la mensualidad (opcional)">
              <select
                value={planDraft.fundingPaymentMethodId}
                onChange={(e) => setPlanDraft((d) => (d ? { ...d, fundingPaymentMethodId: e.target.value } : d))}
                className={fieldCls}
              >
                <option value="">Sin definir</option>
                {activeWallets.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.displayName}
                  </option>
                ))}
              </select>
            </Field>
            <ModalFooter
              busy={busy}
              error={modalError}
              onCancel={closeModals}
              onSave={() =>
                void run(() =>
                  upsertInstallmentPlan(hid, planDraft.id, {
                    displayName: planDraft.displayName,
                    principalMxn: parseAmount(planDraft.principal, 'El monto total'),
                    totalInstallments: Number.parseInt(planDraft.totalInstallments, 10),
                    installmentAmountMxn: parseAmount(planDraft.installmentAmount, 'La mensualidad'),
                    startDate: planDraft.startDate,
                    currentInstallment: Number.parseInt(planDraft.currentInstallment, 10) || 0,
                    paymentMethodId: planDraft.paymentMethodId || undefined,
                    fundingPaymentMethodId: planDraft.fundingPaymentMethodId || undefined,
                  }),
                )
              }
            />
          </div>
        </Modal>
      )}

      {/* ── Modal: abonar (meta o préstamo) ────────────────────────────────── */}
      {depositDraft && (
        <Modal title={`Abonar a ${depositDraft.title}`} onClose={closeModals} busy={busy}>
          <div className="space-y-3">
            <Field label="Monto del abono MXN">
              <input
                type="number"
                inputMode="decimal"
                min="0.01"
                step="0.01"
                autoFocus
                value={depositDraft.amount}
                onChange={(e) => setDepositDraft((d) => (d ? { ...d, amount: e.target.value } : d))}
                className={fieldCls}
              />
            </Field>
            {depositDraft.kind === 'loan' && (
              <p className="px-1 text-xs text-on-surface-variant">
                El abono reduce el saldo pendiente del préstamo. No mueve saldos de cuentas.
              </p>
            )}
            <ModalFooter
              busy={busy}
              error={modalError}
              saveLabel="Abonar"
              onCancel={closeModals}
              onSave={() =>
                void run(() => {
                  const amount = parseAmount(depositDraft.amount, 'El abono')
                  return depositDraft.kind === 'goal'
                    ? addToSavingsGoal(hid, depositDraft.id, amount)
                    : applyLoanPayment(hid, depositDraft.id, amount)
                })
              }
            />
          </div>
        </Modal>
      )}
    </div>
  )
}

/** "termina jul 2026" a partir de startDate + total de mensualidades. */
function planEndLabel(startDate: string, totalInstallments: number): string {
  const [y, m] = startDate.split('-').map(Number)
  if (!y || !m || totalInstallments <= 0) return '—'
  const end = new Date(y, m - 1 + (totalInstallments - 1), 1)
  return end.toLocaleDateString('es-MX', { month: 'short', year: 'numeric' })
}

function ModalFooter({
  busy,
  error,
  onCancel,
  onSave,
  saveLabel = 'Guardar',
}: {
  busy: boolean
  error: string | null
  onCancel: () => void
  onSave: () => void
  saveLabel?: string
}) {
  return (
    <>
      {error && (
        <p className="rounded-2xl bg-expense/10 px-4 py-2.5 text-xs text-expense" role="alert">
          {error}
        </p>
      )}
      <div className="flex justify-end gap-2 pt-1">
        <Button type="button" variant="secondary" disabled={busy} onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="button" loading={busy} onClick={onSave}>
          {saveLabel}
        </Button>
      </div>
    </>
  )
}
