import { useEffect, useMemo, useState } from 'react'
import { useHousehold } from '../context/HouseholdContext'
import { Card, EmptyState, ErrorState, LoadingState } from '../components/ui'
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
      {/* KPI héroe */}
      <Card className="bg-brand text-white">
        <p className="text-xs font-medium uppercase tracking-wide text-white/70">{quincena.label}</p>
        <p className="mt-1 text-3xl font-bold">{formatMxn(totalSpent)}</p>
        <p className="mt-1 text-sm text-white/80">Gastado esta quincena (registrado)</p>
        <div className="mt-4 grid grid-cols-2 gap-3 text-sm">
          <MiniKpi label="Ingreso real" value={formatMxn(quincena.actualIncomeMxn ?? 0)} />
          <MiniKpi label="Gasto proyectado" value={formatMxn(quincena.projectedExpensesMxn ?? 0)} />
        </div>
      </Card>

      {/* Presupuesto vs gasto por categoría */}
      <section>
        <h2 className="mb-3 text-sm font-semibold text-gray-900">Por categoría</h2>
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

      {/* Últimos movimientos */}
      <section>
        <h2 className="mb-3 text-sm font-semibold text-gray-900">Últimos movimientos</h2>
        {expenses.length === 0 ? (
          <EmptyState title="Sin movimientos" />
        ) : (
          <ul className="space-y-2">
            {expenses.slice(0, 20).map((e) => (
              <li
                key={e.id}
                className="flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-gray-900">{e.concept}</p>
                  <p className="text-xs text-gray-500">
                    {catMap.get(e.categoryId) ?? 'Sin categoría'} · {formatDate(e.occurredAt)}
                  </p>
                </div>
                <p className="shrink-0 text-sm font-semibold text-expense">{formatMxn(e.amountMxn)}</p>
              </li>
            ))}
          </ul>
        )}
      </section>

      <p className="pb-4 text-center text-xs text-gray-400">
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
    <div className="rounded-lg bg-white/10 px-3 py-2">
      <p className="text-xs text-white/70">{label}</p>
      <p className="font-semibold">{value}</p>
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
  const barColor = over ? '#c0392b' : colorHex || '#016E3E'

  return (
    <div className="rounded-xl border border-gray-200 bg-white px-4 py-3">
      <div className="mb-1.5 flex items-baseline justify-between gap-2">
        <p className="truncate text-sm font-medium text-gray-900">{name}</p>
        <p className="shrink-0 text-xs text-gray-500">
          {formatMxn(spent)}
          {hasBudget && <span className="text-gray-400"> / {formatMxn(budget)}</span>}
        </p>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-gray-100">
        <div
          className="h-full rounded-full transition-all"
          style={{ width: `${pct}%`, backgroundColor: barColor }}
        />
      </div>
      {over && <p className="mt-1 text-xs font-medium text-expense">Excede el presupuesto</p>}
    </div>
  )
}
