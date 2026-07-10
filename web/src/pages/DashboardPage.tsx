import { useEffect, useMemo, useState } from 'react'
import { useHousehold } from '../context/HouseholdContext'
import { EmptyState, ErrorState, Eyebrow, LoadingState } from '../components/ui'
import {
  getActiveQuincena,
  listCategories,
  listPostedExpenses,
} from '../lib/repository'
import { formatDate, formatMxn } from '../lib/format'
import type { CategoryWithId, ExpenseWithId, QuincenaWithId } from '../lib/types'

interface DashboardData {
  quincena: QuincenaWithId | null
  categories: CategoryWithId[]
  expenses: ExpenseWithId[]
}

export default function DashboardPage() {
  const { active, loading: hhLoading } = useHousehold()
  const hid = active?.id ?? null

  const [data, setData] = useState<DashboardData | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

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
        const quincena = await getActiveQuincena(hid)
        const categories = await listCategories(hid)
        const expenses = quincena ? await listPostedExpenses(hid, quincena.id) : []
        if (!cancelled) setData({ quincena, categories, expenses })
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
      // Solo mostramos categorías con gasto o con presupuesto definido.
      .filter((r) => r.spent > 0 || r.budget > 0)
      .sort((a, b) => b.spent - a.spent)

    // Gastos en categorías desconocidas (sin match en el catálogo).
    const known = new Set(data.categories.map((c) => c.id))
    let uncategorized = 0
    for (const e of data.expenses) {
      if (!known.has(e.categoryId)) uncategorized += e.amountMxn ?? 0
    }
    return { rows, uncategorized }
  }, [data])

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

  const { quincena, expenses } = data
  if (!quincena) {
    return (
      <EmptyState
        title="No hay quincena activa"
        hint="El titular aún no ha abierto la quincena actual en la app."
      />
    )
  }

  const totalSpent = expenses.reduce((s, e) => s + (e.amountMxn ?? 0), 0)
  const catMap = new Map(data.categories.map((c) => [c.id, c.displayName]))

  return (
    <div className="space-y-6">
      {/* KPI héroe: superficie primary-container con monto grande y ligero */}
      <div className="rounded-card bg-primary-container px-6 pb-5 pt-6 text-on-primary-container">
        <Eyebrow className="!text-on-primary-container/70">{quincena.label}</Eyebrow>
        <p className="tnum mt-1 text-5xl font-light leading-tight">{formatMxn(totalSpent)}</p>
        <p className="mt-1.5 text-sm opacity-80">Gastado esta quincena (registrado)</p>
        <div className="mt-5 grid grid-cols-2 gap-2">
          <MiniKpi label="Ingreso real" value={formatMxn(quincena.actualIncomeMxn ?? 0)} />
          <MiniKpi label="Gasto proyectado" value={formatMxn(quincena.projectedExpensesMxn ?? 0)} />
        </div>
      </div>

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

      {/* Últimos movimientos: filas tonales sin divisores */}
      <section>
        <h2 className="mb-3 px-1 text-sm font-semibold text-on-surface">Últimos movimientos</h2>
        {expenses.length === 0 ? (
          <EmptyState title="Sin movimientos" />
        ) : (
          <ul className="space-y-2">
            {expenses.slice(0, 20).map((e) => (
              <li
                key={e.id}
                className="flex items-center justify-between gap-3 rounded-card-sm bg-surface-1 px-4 py-3.5"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-on-surface">{e.concept}</p>
                  <p className="mt-0.5 text-xs text-on-surface-variant">
                    {catMap.get(e.categoryId) ?? 'Sin categoría'} · {formatDate(e.occurredAt)}
                  </p>
                </div>
                <p className="tnum shrink-0 text-sm font-semibold text-expense">
                  −{formatMxn(e.amountMxn)}
                </p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <p className="pb-4 text-center text-xs text-on-surface-variant/70">
        Vista de solo lectura. Para registrar movimientos, envía una propuesta.
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
      {over && (
        <p className="mt-1.5 text-xs font-medium text-expense">▲ Excede el presupuesto</p>
      )}
    </div>
  )
}
