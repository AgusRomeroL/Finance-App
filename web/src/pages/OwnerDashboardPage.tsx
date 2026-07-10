import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useHousehold } from '../context/HouseholdContext'
import { EmptyState, ErrorState, Eyebrow, LoadingState } from '../components/ui'
import {
  deleteExpense,
  getActiveQuincena,
  listCategories,
  listPendingProposals,
  listPostedExpenses,
  resolveProposal,
} from '../lib/repository'
import { formatDate, formatMxn } from '../lib/format'
import type { CategoryWithId, ExpenseWithId, ProposalWithId, QuincenaWithId } from '../lib/types'

/* ---------------------------------------------------------------------------
 * Panel del TITULAR (OWNER): KPI "Disponible para gastar" de la quincena
 * ACTIVE, presupuesto-vs-gasto por categoría, últimos movimientos con
 * eliminación, y bandeja de propuestas pendientes (Aceptar/Rechazar).
 *
 * Nota sobre las propuestas: aceptar desde la web SOLO marca la propuesta como
 * ACCEPTED (resolveProposal). El contrato de ExpenseInput NO admite
 * settlementStatus/externalPayerMemberId y la propuesta no trae wallet ni
 * atribuciones, así que el gasto real lo materializa el teléfono desde su
 * bandeja pending_capture — se explica en el subtexto de la sección.
 * ------------------------------------------------------------------------- */

interface PanelData {
  quincena: QuincenaWithId | null
  categories: CategoryWithId[]
  expenses: ExpenseWithId[]
}

export default function OwnerDashboardPage() {
  const { active, loading: hhLoading } = useHousehold()
  const hid = active?.id ?? null

  const [data, setData] = useState<PanelData | null>(null)
  const [proposals, setProposals] = useState<ProposalWithId[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  // Acciones en filas
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null)
  const [busyExpenseId, setBusyExpenseId] = useState<string | null>(null)
  const [busyProposalId, setBusyProposalId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  useEffect(() => {
    if (!hid) {
      setLoading(false)
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)
    ;(async () => {
      try {
        const [quincena, categories, pending] = await Promise.all([
          getActiveQuincena(hid),
          listCategories(hid),
          listPendingProposals(hid),
        ])
        const expenses = quincena ? await listPostedExpenses(hid, quincena.id) : []
        if (!cancelled) {
          setData({ quincena, categories, expenses })
          setProposals(pending)
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'No se pudieron cargar los datos.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [hid, reloadKey])

  const perCategory = useMemo<{ rows: CategoryRow[]; uncategorized: number }>(() => {
    if (!data) return { rows: [], uncategorized: 0 }
    const spentByCat = new Map<string, number>()
    for (const e of data.expenses) {
      spentByCat.set(e.categoryId, (spentByCat.get(e.categoryId) ?? 0) + (e.amountMxn ?? 0))
    }
    const rows: CategoryRow[] = data.categories
      .map((c) => ({
        category: c,
        spent: spentByCat.get(c.id) ?? 0,
        budget: c.budgetDefaultMxn ?? 0,
      }))
      .filter((r) => r.spent > 0 || r.budget > 0)
      .sort((a, b) => b.spent - a.spent)

    const known = new Set(data.categories.map((c) => c.id))
    let uncategorized = 0
    for (const e of data.expenses) {
      if (!known.has(e.categoryId)) uncategorized += e.amountMxn ?? 0
    }
    return { rows, uncategorized }
  }, [data])

  async function onResolve(proposalId: string, accept: boolean) {
    if (!hid || busyProposalId) return
    setBusyProposalId(proposalId)
    setActionError(null)
    try {
      await resolveProposal(hid, proposalId, accept)
      setProposals((prev) => prev.filter((p) => p.id !== proposalId))
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'No se pudo resolver la propuesta.')
    } finally {
      setBusyProposalId(null)
    }
  }

  async function onDelete(expenseId: string) {
    if (!hid || busyExpenseId) return
    setBusyExpenseId(expenseId)
    setActionError(null)
    try {
      await deleteExpense(hid, expenseId)
      setConfirmDeleteId(null)
      // Recarga completa: el KPI, las barras y el saldo del wallet cambiaron.
      setReloadKey((k) => k + 1)
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'No se pudo eliminar el gasto.')
    } finally {
      setBusyExpenseId(null)
    }
  }

  if (hhLoading || loading) return <LoadingState />
  if (!hid) {
    return (
      <EmptyState
        title="Selecciona un grupo primero"
        hint="Ve a la pestaña Grupos y elige (o crea) un grupo activo."
      />
    )
  }
  if (error) return <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
  if (!data) return <EmptyState title="Sin datos" />

  const { quincena, expenses, categories } = data
  const totalSpent = expenses.reduce((s, e) => s + (e.amountMxn ?? 0), 0)
  const available = (quincena?.projectedIncomeMxn ?? 0) - totalSpent
  const catMap = new Map(categories.map((c) => [c.id, c.displayName]))

  return (
    <div className="space-y-6">
      {/* KPI héroe: Disponible para gastar */}
      {quincena ? (
        <div className="rounded-card bg-primary-container px-6 pb-5 pt-6 text-on-primary-container">
          <Eyebrow className="!text-on-primary-container/70">{quincena.label}</Eyebrow>
          <p className="tnum mt-1 text-5xl font-light leading-tight">
            {available < 0 ? '−' : ''}
            {formatMxn(Math.abs(available))}
          </p>
          <p className="mt-1.5 text-sm opacity-80">
            {available < 0 ? 'Sobregirado esta quincena' : 'Disponible para gastar'}
          </p>
          <div className="mt-5 grid grid-cols-2 gap-2">
            <MiniKpi label="Ingreso proyectado" value={formatMxn(quincena.projectedIncomeMxn ?? 0)} />
            <MiniKpi label="Gastado (registrado)" value={formatMxn(totalSpent)} />
          </div>
        </div>
      ) : (
        <EmptyState
          title="No hay quincena activa"
          hint="Abre la app del teléfono para que provisione la quincena actual."
        />
      )}

      {actionError && (
        <p className="rounded-2xl bg-expense/10 px-4 py-3 text-sm text-expense" role="alert">
          {actionError}
        </p>
      )}

      {/* Bandeja de propuestas pendientes */}
      <section>
        <h2 className="mb-1 px-1 text-sm font-semibold text-on-surface">Propuestas pendientes</h2>
        <p className="mb-3 px-1 text-xs text-on-surface-variant">
          Al aceptar, la propuesta pasa a la bandeja del teléfono: ahí se convierte en gasto real con
          su método de pago y reparto.
        </p>
        {proposals.length === 0 ? (
          <EmptyState title="Sin propuestas pendientes" hint="Cuando un colaborador proponga un movimiento aparecerá aquí." />
        ) : (
          <ul className="space-y-2">
            {proposals.map((p) => (
              <li key={p.id} className="rounded-card-sm bg-surface-1 px-4 py-3.5">
                <div className="flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-on-surface">{p.concept}</p>
                    <p className="mt-0.5 text-xs text-on-surface-variant">
                      {p.kind === 'EXPENSE' ? 'Gasto' : 'Pago futuro'} · {p.proposedByName} ·{' '}
                      {formatDate(p.occurredAt)}
                    </p>
                    {p.note && <p className="mt-1 text-xs italic text-on-surface-variant/80">“{p.note}”</p>}
                  </div>
                  <p className="tnum shrink-0 text-sm font-semibold text-on-surface">{formatMxn(p.amountMxn)}</p>
                </div>
                <div className="mt-3 flex gap-2">
                  <button
                    type="button"
                    disabled={busyProposalId !== null}
                    onClick={() => void onResolve(p.id, true)}
                    className="flex-1 rounded-full bg-primary-container px-4 py-2 text-sm font-medium text-on-primary-container transition-all hover:brightness-105 active:scale-[0.98] disabled:opacity-50"
                  >
                    {busyProposalId === p.id ? 'Guardando…' : 'Aceptar'}
                  </button>
                  <button
                    type="button"
                    disabled={busyProposalId !== null}
                    onClick={() => void onResolve(p.id, false)}
                    className="flex-1 rounded-full bg-expense/10 px-4 py-2 text-sm font-medium text-expense transition-all hover:bg-expense/20 active:scale-[0.98] disabled:opacity-50"
                  >
                    Rechazar
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Presupuesto vs gasto por categoría */}
      <section>
        <h2 className="mb-3 px-1 text-sm font-semibold text-on-surface">Por categoría</h2>
        {perCategory.rows.length === 0 && perCategory.uncategorized === 0 ? (
          <EmptyState title="Sin gastos registrados en esta quincena" />
        ) : (
          <div className="space-y-2">
            {perCategory.rows.map((r) => (
              <CategoryBar
                key={r.category.id}
                name={r.category.displayName}
                spent={r.spent}
                budget={r.budget}
                colorHex={r.category.colorHex}
              />
            ))}
            {perCategory.uncategorized > 0 && (
              <CategoryBar name="Otros / sin categoría" spent={perCategory.uncategorized} budget={0} />
            )}
          </div>
        )}
      </section>

      {/* Últimos movimientos con eliminación */}
      <section>
        <h2 className="mb-3 px-1 text-sm font-semibold text-on-surface">Últimos movimientos</h2>
        {expenses.length === 0 ? (
          <EmptyState title="Sin movimientos" hint="Captura el primero desde la pestaña Capturar." />
        ) : (
          <ul className="space-y-2">
            {expenses.slice(0, 30).map((e) => {
              const confirming = confirmDeleteId === e.id
              const deleting = busyExpenseId === e.id
              return (
                <li key={e.id} className="rounded-card-sm bg-surface-1 px-4 py-3.5">
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-on-surface">{e.concept}</p>
                      <p className="mt-0.5 text-xs text-on-surface-variant">
                        {catMap.get(e.categoryId) ?? 'Sin categoría'} · {formatDate(e.occurredAt)}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <p className="tnum text-sm font-semibold text-expense">−{formatMxn(e.amountMxn)}</p>
                      {!confirming && (
                        <button
                          type="button"
                          onClick={() => setConfirmDeleteId(e.id)}
                          aria-label={`Eliminar ${e.concept}`}
                          className="flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-expense/10 hover:text-expense"
                        >
                          <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" aria-hidden="true">
                            <path
                              d="M5 7h14M10 4h4M9 7v12m6-12v12M7 7l1 13a1 1 0 001 1h6a1 1 0 001-1l1-13"
                              stroke="currentColor"
                              strokeWidth="1.7"
                              strokeLinecap="round"
                              strokeLinejoin="round"
                            />
                          </svg>
                        </button>
                      )}
                    </div>
                  </div>
                  {confirming && (
                    <div className="mt-3 flex items-center justify-between gap-2 rounded-2xl bg-expense/10 px-3.5 py-2.5">
                      <p className="text-xs font-medium text-expense">
                        ¿Eliminar y revertir el saldo del wallet?
                      </p>
                      <div className="flex shrink-0 gap-2">
                        <button
                          type="button"
                          disabled={deleting}
                          onClick={() => void onDelete(e.id)}
                          className="rounded-full bg-expense px-3.5 py-1.5 text-xs font-semibold text-surface transition-all active:scale-95 disabled:opacity-60"
                        >
                          {deleting ? 'Eliminando…' : 'Eliminar'}
                        </button>
                        <button
                          type="button"
                          disabled={deleting}
                          onClick={() => setConfirmDeleteId(null)}
                          className="rounded-full bg-surface-2 px-3.5 py-1.5 text-xs font-medium text-on-surface transition-colors hover:bg-surface-3"
                        >
                          Cancelar
                        </button>
                      </div>
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <p className="pb-4 text-center text-xs text-on-surface-variant/70">
        La edición completa de un gasto se hace desde la app del teléfono.{' '}
        <Link to="/capturar" className="font-medium text-primary underline-offset-2 hover:underline">
          Capturar un gasto
        </Link>
      </p>
    </div>
  )
}

interface CategoryRow {
  category: CategoryWithId
  spent: number
  budget: number
}

function MiniKpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl bg-surface/50 px-3.5 py-2.5">
      <p className="text-xs opacity-75">{label}</p>
      <p className="tnum text-sm font-semibold">{value}</p>
    </div>
  )
}

function CategoryBar({
  name,
  spent,
  budget,
  colorHex,
}: {
  name: string
  spent: number
  budget: number
  colorHex?: string
}) {
  const hasBudget = budget > 0
  const pct = hasBudget ? Math.min(100, (spent / budget) * 100) : 100
  const over = hasBudget && spent > budget

  return (
    <div className="rounded-card-sm bg-surface-1 px-4 py-3.5">
      <div className="mb-2 flex items-baseline justify-between gap-2">
        <p className="truncate text-sm font-medium text-on-surface">{name}</p>
        <p className="tnum shrink-0 text-xs text-on-surface-variant">
          {formatMxn(spent)}
          {hasBudget && <span className="opacity-60"> / {formatMxn(budget)}</span>}
        </p>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-surface-3">
        <div
          className={`h-full rounded-full transition-all ${over ? 'bg-expense' : colorHex ? '' : 'bg-primary'}`}
          style={{
            width: `${pct}%`,
            ...(over || !colorHex ? {} : { backgroundColor: colorHex }),
          }}
        />
      </div>
      {over && <p className="mt-1.5 text-xs font-medium text-expense">▲ Excede el presupuesto</p>}
    </div>
  )
}
