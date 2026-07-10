import { useEffect, useMemo, useState } from 'react'
import { useHousehold } from '../context/HouseholdContext'
import { Button, EmptyState, ErrorState, Field, fieldCls, LoadingState, Modal } from '../components/ui'
import {
  applyLoanPayment,
  listLoans,
  listMembers,
  listPendingReimbursements,
  markExpenseReimbursed,
} from '../lib/repository'
import { formatDate, formatMxn } from '../lib/format'
import type { ExpenseWithId, LoanWithId } from '../lib/types'

/* ---------------------------------------------------------------------------
 * Deudas entre miembros (/deudas) — OWNER | PAYER.
 *
 * Réplica EXACTA de la semántica de MemberBalancesViewModel (Android):
 * deudas EXPLÍCITAS y opt-in en dos sentidos, por miembro, SIN netear
 * (decisión de producto — nada de derivar deudas de las atribuciones):
 *
 *  - "El hogar le debe" (por pagar): gastos POSTED con settlementStatus =
 *    PENDING_REIMBURSEMENT, agrupados por externalPayerMemberId (un tercero
 *    adelantó el gasto). Acción: "Marcar pagado" → settlementStatus =
 *    REIMBURSED + updatedAt; NO mueve saldos (la reposición es fuera del
 *    ledger).
 *  - "Le debe al hogar" (por cobrar): préstamos con remainingBalanceMxn > 0,
 *    agrupados por debtorMemberId. Acción: "Abonar" → remainingBalanceMxn −=
 *    monto (mín. 0) + updatedAt.
 *
 * Un mismo miembro puede aparecer con deuda en AMBOS sentidos: se muestran
 * las dos cifras lado a lado.
 * ------------------------------------------------------------------------- */

interface MemberDebtRow {
  memberId: string
  name: string
  payableTotal: number
  payables: ExpenseWithId[]
  receivableTotal: number
  receivables: LoanWithId[]
}

interface PageData {
  reimbursements: ExpenseWithId[]
  loans: LoanWithId[]
  memberNames: Map<string, string>
}

interface LoanDeposit {
  loan: LoanWithId
  debtorName: string
  amount: string
}

export default function DebtsPage() {
  const { active, loading: hhLoading } = useHousehold()
  const hid = active?.id ?? null

  const [data, setData] = useState<PageData | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  const [deposit, setDeposit] = useState<LoanDeposit | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    if (!hid) return
    let cancelled = false
    setData(null)
    setError(null)
    Promise.all([listPendingReimbursements(hid), listLoans(hid), listMembers(hid)])
      .then(([reimbursements, loans, members]) => {
        if (cancelled) return
        setData({
          reimbursements,
          loans,
          memberNames: new Map(members.map((m) => [m.id, m.displayName])),
        })
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'No se pudieron cargar las deudas.')
      })
    return () => {
      cancelled = true
    }
  }, [hid, reloadKey])

  // Construcción por miembro (espejo de MemberBalancesViewModel.build).
  const rows = useMemo<MemberDebtRow[]>(() => {
    if (!data) return []
    const payableByMember = new Map<string, ExpenseWithId[]>()
    for (const e of data.reimbursements) {
      const payer = e.externalPayerMemberId
      if (!payer) continue
      const list = payableByMember.get(payer) ?? []
      list.push(e)
      payableByMember.set(payer, list)
    }
    const receivableByMember = new Map<string, LoanWithId[]>()
    for (const l of data.loans) {
      if (l.remainingBalanceMxn <= 0) continue
      const list = receivableByMember.get(l.debtorMemberId) ?? []
      list.push(l)
      receivableByMember.set(l.debtorMemberId, list)
    }
    const memberIds = new Set([...payableByMember.keys(), ...receivableByMember.keys()])
    return [...memberIds]
      .map((id) => {
        const payables = payableByMember.get(id) ?? []
        const receivables = (receivableByMember.get(id) ?? []).sort(
          (a, b) => b.remainingBalanceMxn - a.remainingBalanceMxn,
        )
        return {
          memberId: id,
          name: data.memberNames.get(id) ?? 'Miembro',
          payableTotal: payables.reduce((s, e) => s + e.amountMxn, 0),
          payables,
          receivableTotal: receivables.reduce((s, l) => s + l.remainingBalanceMxn, 0),
          receivables,
        }
      })
      .sort((a, b) => Math.max(b.payableTotal, b.receivableTotal) - Math.max(a.payableTotal, a.receivableTotal))
  }, [data])

  function reload() {
    setDeposit(null)
    setReloadKey((k) => k + 1)
  }

  async function onMarkReimbursed(expenseId: string) {
    if (!hid || busyId) return
    setBusyId(expenseId)
    setActionError(null)
    try {
      await markExpenseReimbursed(hid, expenseId)
      reload()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'No se pudo marcar como pagado.')
    } finally {
      setBusyId(null)
    }
  }

  async function onApplyDeposit() {
    if (!hid || !deposit || busyId) return
    const amount = Number.parseFloat(deposit.amount)
    if (!Number.isFinite(amount) || amount <= 0) {
      setActionError('El abono debe ser mayor que cero.')
      return
    }
    setBusyId(deposit.loan.id)
    setActionError(null)
    try {
      await applyLoanPayment(hid, deposit.loan.id, amount)
      reload()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'No se pudo registrar el abono.')
    } finally {
      setBusyId(null)
    }
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
  if (!data) return <LoadingState label="Cargando deudas…" />

  return (
    <div className="space-y-5 pb-8">
      <div>
        <h2 className="px-1 text-lg font-semibold text-on-surface">Deudas entre miembros</h2>
        <p className="px-1 text-xs text-on-surface-variant">
          Deudas explícitas en dos sentidos, sin netear: gastos que alguien adelantó al hogar y
          préstamos pendientes de cobrar.
        </p>
      </div>

      {actionError && (
        <p className="rounded-2xl bg-expense/10 px-4 py-3 text-sm text-expense" role="alert">
          {actionError}
        </p>
      )}

      {rows.length === 0 ? (
        <EmptyState
          title="Sin deudas pendientes"
          hint="Aquí aparecen los gastos por reembolsar a terceros y los préstamos con saldo vivo."
        />
      ) : (
        <ul className="space-y-3">
          {rows.map((row) => (
            <li key={row.memberId} className="rounded-card bg-surface-1 p-5">
              {/* Cabecera del miembro: las dos cifras lado a lado */}
              <div className="flex flex-wrap items-start justify-between gap-3">
                <p className="text-base font-semibold text-on-surface">{row.name}</p>
                <div className="flex gap-4 text-right">
                  {row.payableTotal > 0 && (
                    <div>
                      <p className="eyebrow">Le debemos</p>
                      <p className="tnum text-sm font-semibold text-expense">{formatMxn(row.payableTotal)}</p>
                    </div>
                  )}
                  {row.receivableTotal > 0 && (
                    <div>
                      <p className="eyebrow">Nos debe</p>
                      <p className="tnum text-sm font-semibold text-income">{formatMxn(row.receivableTotal)}</p>
                    </div>
                  )}
                </div>
              </div>

              {/* Por pagar: gastos que este miembro adelantó */}
              {row.payables.length > 0 && (
                <div className="mt-4">
                  <p className="mb-1.5 px-1 text-xs font-semibold text-on-surface-variant">
                    Gastos que adelantó (el hogar le repone)
                  </p>
                  <ul className="space-y-1.5">
                    {row.payables.map((e) => {
                      const busy = busyId === e.id
                      return (
                        <li
                          key={e.id}
                          className="flex items-center justify-between gap-3 rounded-2xl bg-surface-2/60 px-3.5 py-2.5"
                        >
                          <div className="min-w-0">
                            <p className="truncate text-sm text-on-surface">{e.concept}</p>
                            <p className="text-xs text-on-surface-variant">{formatDate(e.occurredAt)}</p>
                          </div>
                          <div className="flex shrink-0 items-center gap-2">
                            <p className="tnum text-sm font-semibold text-expense">{formatMxn(e.amountMxn)}</p>
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => void onMarkReimbursed(e.id)}
                              className="rounded-full bg-primary-container px-3 py-1.5 text-xs font-semibold text-on-primary-container transition-all active:scale-95 disabled:opacity-60"
                            >
                              {busy ? 'Guardando…' : 'Marcar pagado'}
                            </button>
                          </div>
                        </li>
                      )
                    })}
                  </ul>
                  <p className="mt-1.5 px-1 text-[11px] text-on-surface-variant/80">
                    Marcar pagado no mueve saldos de cuentas: la reposición ocurre fuera del ledger.
                  </p>
                </div>
              )}

              {/* Por cobrar: préstamos con saldo vivo */}
              {row.receivables.length > 0 && (
                <div className="mt-4">
                  <p className="mb-1.5 px-1 text-xs font-semibold text-on-surface-variant">
                    Préstamos pendientes (le debe al hogar)
                  </p>
                  <ul className="space-y-1.5">
                    {row.receivables.map((l) => (
                      <li
                        key={l.id}
                        className="flex items-center justify-between gap-3 rounded-2xl bg-surface-2/60 px-3.5 py-2.5"
                      >
                        <div className="min-w-0">
                          <p className="truncate text-sm text-on-surface">
                            Prestado {formatMxn(l.principalMxn)}
                            {l.notes ? ` · ${l.notes}` : ''}
                          </p>
                          <p className="text-xs text-on-surface-variant">
                            {l.issuedAt || 'sin fecha'}
                            {l.dueAt ? ` · vence ${l.dueAt}` : ''}
                          </p>
                        </div>
                        <div className="flex shrink-0 items-center gap-2">
                          <p className="tnum text-sm font-semibold text-income">{formatMxn(l.remainingBalanceMxn)}</p>
                          <button
                            type="button"
                            disabled={busyId === l.id}
                            onClick={() => {
                              setActionError(null)
                              setDeposit({ loan: l, debtorName: row.name, amount: '' })
                            }}
                            className="rounded-full bg-primary-container px-3 py-1.5 text-xs font-semibold text-on-primary-container transition-all active:scale-95 disabled:opacity-60"
                          >
                            Abonar
                          </button>
                        </div>
                      </li>
                    ))}
                  </ul>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {/* Modal de abono a préstamo */}
      {deposit && (
        <Modal
          title={`Abono de ${deposit.debtorName}`}
          onClose={() => (busyId ? null : setDeposit(null))}
          busy={busyId !== null}
        >
          <div className="space-y-3">
            <p className="text-xs text-on-surface-variant">
              Saldo pendiente:{' '}
              <span className="tnum font-semibold">{formatMxn(deposit.loan.remainingBalanceMxn)}</span>. El
              abono lo reduce (mínimo $0.00) y se sincroniza al teléfono.
            </p>
            <Field label="Monto del abono MXN">
              <input
                type="number"
                inputMode="decimal"
                min="0.01"
                step="0.01"
                autoFocus
                value={deposit.amount}
                onChange={(e) => setDeposit((d) => (d ? { ...d, amount: e.target.value } : d))}
                className={fieldCls}
              />
            </Field>
            {actionError && (
              <p className="rounded-2xl bg-expense/10 px-4 py-2.5 text-xs text-expense" role="alert">
                {actionError}
              </p>
            )}
            <div className="flex justify-end gap-2 pt-1">
              <Button
                type="button"
                variant="secondary"
                disabled={busyId !== null}
                onClick={() => setDeposit(null)}
              >
                Cancelar
              </Button>
              <Button type="button" loading={busyId === deposit.loan.id} onClick={() => void onApplyDeposit()}>
                Abonar
              </Button>
            </div>
          </div>
        </Modal>
      )}
    </div>
  )
}
