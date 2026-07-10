import { useEffect, useMemo, useRef, useState } from 'react'
import { useHousehold } from '../context/HouseholdContext'
import { EmptyState, ErrorState, fieldCls, LoadingState } from '../components/ui'
import {
  listCategories,
  listExpensesByQuincena,
  listIncomesByQuincena,
  listQuincenas,
} from '../lib/repository'
import { formatMxn } from '../lib/format'
import type { CategoryWithId, ExpenseWithId, IncomeSourceWithId, QuincenaWithId } from '../lib/types'

/* ---------------------------------------------------------------------------
 * Analíticas (/analiticas) — OWNER | PAYER. CERO IA: todo es agregación
 * determinista en cliente sobre los mismos reads del repository.
 *
 *  - KPIs de la quincena seleccionada: gastado (POSTED), ingreso recibido
 *    (income_source POSTED de la quincena), reservado (PLANNED) y disponible
 *    (= ingreso recibido − gastado − reservado).
 *  - Dona por categoría (SVG puro, sin libs) del gasto POSTED.
 *  - Presupuesto-vs-gasto por categoría (budgetDefaultMxn de categories).
 *  - Tendencia del gasto POSTED de las últimas 6 quincenas (hasta la
 *    seleccionada), con cache por quincena para no re-leer al navegar.
 *  - Top conceptos de la quincena.
 * ------------------------------------------------------------------------- */

/** Paleta de respaldo para categorías sin colorHex (tonos medios, legibles en claro y oscuro). */
const FALLBACK_COLORS = [
  '#4C9A6E',
  '#5B8DBE',
  '#C0794A',
  '#9A6FB5',
  '#4FA3A5',
  '#C06578',
  '#8B9A46',
  '#7A82C9',
  '#B58A3C',
  '#5FA07F',
]

interface CatalogData {
  quincenas: QuincenaWithId[]
  categories: CategoryWithId[]
}

interface QuincenaData {
  expenses: ExpenseWithId[]
  incomes: IncomeSourceWithId[]
}

interface TrendPoint {
  quincena: QuincenaWithId
  totalPosted: number
}

export default function AnalyticsPage() {
  const { active, loading: hhLoading } = useHousehold()
  const hid = active?.id ?? null

  const [catalog, setCatalog] = useState<CatalogData | null>(null)
  const [catalogError, setCatalogError] = useState<string | null>(null)
  const [catalogKey, setCatalogKey] = useState(0)

  const [quincenaId, setQuincenaId] = useState('')
  const [data, setData] = useState<QuincenaData | null>(null)
  const [trend, setTrend] = useState<TrendPoint[] | null>(null)
  const [dataError, setDataError] = useState<string | null>(null)
  const [dataKey, setDataKey] = useState(0)

  // Cache por quincena (id → total POSTED) para la tendencia: al cambiar de
  // quincena solo se leen las que aún no se han visto.
  const trendCache = useRef(new Map<string, number>())

  // ── Catálogo (quincenas + categorías), una vez ────────────────────────────
  useEffect(() => {
    if (!hid) return
    let cancelled = false
    setCatalog(null)
    setCatalogError(null)
    trendCache.current.clear()
    Promise.all([listQuincenas(hid), listCategories(hid)])
      .then(([quincenas, categories]) => {
        if (cancelled) return
        setCatalog({ quincenas, categories })
        const activeQ = quincenas.find((q) => q.status === 'ACTIVE') ?? quincenas[0]
        setQuincenaId((prev) => (prev && quincenas.some((q) => q.id === prev) ? prev : activeQ?.id ?? ''))
      })
      .catch((e) => {
        if (!cancelled) setCatalogError(e instanceof Error ? e.message : 'No se pudieron cargar las analíticas.')
      })
    return () => {
      cancelled = true
    }
  }, [hid, catalogKey])

  // ── Datos de la quincena seleccionada + tendencia (con cache) ─────────────
  useEffect(() => {
    if (!hid || !quincenaId || !catalog) return
    let cancelled = false
    setData(null)
    setTrend(null)
    setDataError(null)
    ;(async () => {
      try {
        const [expenses, incomes] = await Promise.all([
          listExpensesByQuincena(hid, quincenaId),
          listIncomesByQuincena(hid, quincenaId),
        ])
        // Sembrar el cache con la quincena recién leída.
        const postedTotal = expenses
          .filter((e) => e.status === 'POSTED')
          .reduce((s, e) => s + (e.amountMxn ?? 0), 0)
        trendCache.current.set(quincenaId, postedTotal)

        // Últimas 6 quincenas hasta la seleccionada (lista ya viene DESC).
        const idx = catalog.quincenas.findIndex((q) => q.id === quincenaId)
        const window = idx >= 0 ? catalog.quincenas.slice(idx, idx + 6) : []
        const points = await Promise.all(
          window.map(async (q) => {
            const cached = trendCache.current.get(q.id)
            if (cached !== undefined) return { quincena: q, totalPosted: cached }
            const rows = await listExpensesByQuincena(hid, q.id)
            const total = rows
              .filter((e) => e.status === 'POSTED')
              .reduce((s, e) => s + (e.amountMxn ?? 0), 0)
            trendCache.current.set(q.id, total)
            return { quincena: q, totalPosted: total }
          }),
        )
        if (cancelled) return
        setData({ expenses, incomes })
        setTrend(points.reverse()) // cronológico: la más vieja primero
      } catch (e) {
        if (!cancelled) setDataError(e instanceof Error ? e.message : 'No se pudieron cargar los datos.')
      }
    })()
    return () => {
      cancelled = true
    }
  }, [hid, quincenaId, catalog, dataKey])

  const categories = catalog?.categories ?? []
  const catById = useMemo(() => new Map(categories.map((c) => [c.id, c])), [categories])

  // ── Agregaciones de la quincena ───────────────────────────────────────────
  const stats = useMemo(() => {
    const expenses = data?.expenses ?? []
    const incomes = data?.incomes ?? []
    const posted = expenses.filter((e) => e.status === 'POSTED')
    const planned = expenses.filter((e) => e.status === 'PLANNED')
    const spent = posted.reduce((s, e) => s + (e.amountMxn ?? 0), 0)
    const reserved = planned.reduce((s, e) => s + (e.amountMxn ?? 0), 0)
    const received = incomes
      .filter((i) => i.status === 'POSTED')
      .reduce((s, i) => s + (i.amountMxn ?? 0), 0)
    return { posted, spent, reserved, received, available: received - spent - reserved }
  }, [data])

  /** Segmentos de la dona + filas presupuesto-vs-gasto, por categoría. */
  const byCategory = useMemo(() => {
    const totals = new Map<string, number>()
    for (const e of stats.posted) {
      totals.set(e.categoryId, (totals.get(e.categoryId) ?? 0) + (e.amountMxn ?? 0))
    }
    const rows = [...totals.entries()]
      .map(([categoryId, spent], i) => {
        const cat = catById.get(categoryId)
        return {
          categoryId,
          name: cat?.displayName ?? 'Sin categoría',
          color: cat?.colorHex || FALLBACK_COLORS[i % FALLBACK_COLORS.length],
          spent,
          budget: cat?.budgetDefaultMxn ?? 0,
        }
      })
      .sort((a, b) => b.spent - a.spent)
    // Presupuestadas sin gasto también aparecen en la comparativa.
    const withoutSpend = categories
      .filter((c) => (c.budgetDefaultMxn ?? 0) > 0 && !totals.has(c.id))
      .map((c) => ({
        categoryId: c.id,
        name: c.displayName,
        color: c.colorHex || FALLBACK_COLORS[0],
        spent: 0,
        budget: c.budgetDefaultMxn ?? 0,
      }))
    return { donut: rows, budgetRows: [...rows, ...withoutSpend] }
  }, [stats.posted, catById, categories])

  const topConcepts = useMemo(() => {
    const totals = new Map<string, { concept: string; total: number; count: number }>()
    for (const e of stats.posted) {
      const key = e.concept.trim().toLowerCase()
      const row = totals.get(key) ?? { concept: e.concept.trim(), total: 0, count: 0 }
      row.total += e.amountMxn ?? 0
      row.count += 1
      totals.set(key, row)
    }
    return [...totals.values()].sort((a, b) => b.total - a.total).slice(0, 8)
  }, [stats.posted])

  if (hhLoading) return <LoadingState />
  if (!hid) {
    return (
      <EmptyState
        title="Selecciona un grupo primero"
        hint="Ve a la pestaña Grupos y elige (o crea) un grupo activo."
      />
    )
  }
  if (catalogError) return <ErrorState message={catalogError} onRetry={() => setCatalogKey((k) => k + 1)} />
  if (!catalog) return <LoadingState label="Cargando analíticas…" />
  if (catalog.quincenas.length === 0) {
    return (
      <EmptyState
        title="No hay quincenas sincronizadas"
        hint="Abre la app del teléfono con conexión para sincronizar el presupuesto."
      />
    )
  }

  return (
    <div className="space-y-5 pb-8">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="px-1 text-lg font-semibold text-on-surface">Analíticas</h2>
          <p className="px-1 text-xs text-on-surface-variant">Agregados deterministas por quincena.</p>
        </div>
        <label className="block w-full sm:w-64">
          <span className="eyebrow mb-1.5 block px-1">Quincena</span>
          <select value={quincenaId} onChange={(e) => setQuincenaId(e.target.value)} className={fieldCls}>
            {catalog.quincenas.map((q) => (
              <option key={q.id} value={q.id}>
                {q.label || q.id}
                {q.status === 'ACTIVE' ? ' · activa' : ''}
              </option>
            ))}
          </select>
        </label>
      </div>

      {dataError ? (
        <ErrorState message={dataError} onRetry={() => setDataKey((k) => k + 1)} />
      ) : !data ? (
        <LoadingState label="Calculando…" />
      ) : (
        <>
          {/* ── KPIs ──────────────────────────────────────────────────────── */}
          <div className="grid grid-cols-2 gap-2 lg:grid-cols-4">
            <KpiCard label="Gastado (registrado)" value={formatMxn(stats.spent)} tone="expense" />
            <KpiCard label="Ingreso recibido" value={formatMxn(stats.received)} tone="income" />
            <KpiCard label="Reservado (planeado)" value={formatMxn(stats.reserved)} tone="alert" />
            <KpiCard
              label={stats.available < 0 ? 'Sobregirado' : 'Disponible'}
              value={`${stats.available < 0 ? '−' : ''}${formatMxn(Math.abs(stats.available))}`}
              tone={stats.available < 0 ? 'expense' : 'neutral'}
              hint="ingreso − gastado − reservado"
            />
          </div>

          {/* ── Dona por categoría + presupuesto-vs-gasto ─────────────────── */}
          <div className="grid gap-5 lg:grid-cols-2">
            <section className="rounded-card bg-surface-1 p-5">
              <h3 className="eyebrow mb-4">Gasto por categoría</h3>
              {byCategory.donut.length === 0 ? (
                <EmptyState title="Sin gastos registrados en esta quincena" />
              ) : (
                <div className="flex flex-col items-center gap-5 sm:flex-row sm:items-start">
                  <DonutChart segments={byCategory.donut} total={stats.spent} />
                  <ul className="w-full min-w-0 flex-1 space-y-1.5">
                    {byCategory.donut.slice(0, 8).map((r) => (
                      <li key={r.categoryId} className="flex items-center gap-2.5">
                        <span
                          className="h-2.5 w-2.5 shrink-0 rounded-full"
                          style={{ backgroundColor: r.color }}
                          aria-hidden="true"
                        />
                        <span className="min-w-0 flex-1 truncate text-sm text-on-surface">{r.name}</span>
                        <span className="tnum shrink-0 text-xs text-on-surface-variant">
                          {stats.spent > 0 ? `${Math.round((r.spent / stats.spent) * 100)}%` : '0%'}
                        </span>
                        <span className="tnum shrink-0 text-sm font-medium text-on-surface">
                          {formatMxn(r.spent)}
                        </span>
                      </li>
                    ))}
                    {byCategory.donut.length > 8 && (
                      <li className="pt-1 text-xs text-on-surface-variant">
                        +{byCategory.donut.length - 8} categorías más
                      </li>
                    )}
                  </ul>
                </div>
              )}
            </section>

            <section className="rounded-card bg-surface-1 p-5">
              <h3 className="eyebrow mb-4">Presupuesto vs gasto</h3>
              {byCategory.budgetRows.length === 0 ? (
                <EmptyState title="Sin categorías con gasto o presupuesto" />
              ) : (
                <div className="space-y-3">
                  {byCategory.budgetRows.slice(0, 10).map((r) => (
                    <BudgetBar key={r.categoryId} name={r.name} spent={r.spent} budget={r.budget} color={r.color} />
                  ))}
                </div>
              )}
            </section>
          </div>

          {/* ── Tendencia últimas 6 quincenas ─────────────────────────────── */}
          <section className="rounded-card bg-surface-1 p-5">
            <h3 className="eyebrow mb-4">Tendencia de gasto (últimas 6 quincenas)</h3>
            {!trend || trend.length === 0 ? (
              <EmptyState title="Sin quincenas anteriores" />
            ) : (
              <TrendChart points={trend} selectedId={quincenaId} />
            )}
          </section>

          {/* ── Top conceptos ─────────────────────────────────────────────── */}
          <section className="rounded-card bg-surface-1 p-5">
            <h3 className="eyebrow mb-4">Top conceptos de la quincena</h3>
            {topConcepts.length === 0 ? (
              <EmptyState title="Sin gastos registrados en esta quincena" />
            ) : (
              <ul className="space-y-2">
                {topConcepts.map((c, i) => {
                  const max = topConcepts[0]?.total ?? 1
                  return (
                    <li key={`${c.concept}-${i}`}>
                      <div className="mb-1 flex items-baseline justify-between gap-2">
                        <p className="min-w-0 truncate text-sm text-on-surface">
                          {c.concept}
                          {c.count > 1 && (
                            <span className="ml-1.5 text-xs text-on-surface-variant">×{c.count}</span>
                          )}
                        </p>
                        <p className="tnum shrink-0 text-sm font-medium text-on-surface">{formatMxn(c.total)}</p>
                      </div>
                      <div className="h-1.5 w-full overflow-hidden rounded-full bg-surface-3">
                        <div
                          className="h-full rounded-full bg-primary/70"
                          style={{ width: `${Math.max(2, (c.total / max) * 100)}%` }}
                        />
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  )
}

/* ────────────────────────────── Componentes ─────────────────────────────── */

function KpiCard({
  label,
  value,
  tone,
  hint,
}: {
  label: string
  value: string
  tone: 'income' | 'expense' | 'alert' | 'neutral'
  hint?: string
}) {
  const toneCls =
    tone === 'income' ? 'text-income' : tone === 'expense' ? 'text-expense' : tone === 'alert' ? 'text-alert' : 'text-on-surface'
  return (
    <div className="rounded-card-sm bg-surface-1 px-4 py-3.5">
      <p className="text-xs text-on-surface-variant">{label}</p>
      <p className={`tnum mt-1 text-lg font-semibold ${toneCls}`}>{value}</p>
      {hint && <p className="mt-0.5 text-[11px] text-on-surface-variant/70">{hint}</p>}
    </div>
  )
}

/**
 * Dona SVG pura: cada segmento es un círculo con stroke-dasharray sobre la
 * circunferencia, rotado al offset acumulado. Total al centro.
 */
function DonutChart({
  segments,
  total,
}: {
  segments: Array<{ categoryId: string; name: string; color: string; spent: number }>
  total: number
}) {
  const size = 180
  const stroke = 26
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  let offset = 0

  return (
    <svg
      viewBox={`0 0 ${size} ${size}`}
      className="h-44 w-44 shrink-0"
      role="img"
      aria-label={`Gasto por categoría, total ${formatMxn(total)}`}
    >
      {/* Pista de fondo */}
      <circle
        cx={size / 2}
        cy={size / 2}
        r={r}
        fill="none"
        stroke="rgb(var(--c-surface-3))"
        strokeWidth={stroke}
      />
      {total > 0 &&
        segments.map((s) => {
          const frac = s.spent / total
          const dash = frac * c
          const el = (
            <circle
              key={s.categoryId}
              cx={size / 2}
              cy={size / 2}
              r={r}
              fill="none"
              stroke={s.color}
              strokeWidth={stroke}
              strokeDasharray={`${dash} ${c - dash}`}
              strokeDashoffset={-offset}
              transform={`rotate(-90 ${size / 2} ${size / 2})`}
            >
              <title>{`${s.name}: ${formatMxn(s.spent)}`}</title>
            </circle>
          )
          offset += dash
          return el
        })}
      <text
        x="50%"
        y="46%"
        textAnchor="middle"
        className="tnum"
        fill="rgb(var(--c-on-surface))"
        fontSize="17"
        fontWeight="600"
      >
        {formatMxn(total)}
      </text>
      <text x="50%" y="58%" textAnchor="middle" fill="rgb(var(--c-on-surface-variant))" fontSize="10">
        gastado
      </text>
    </svg>
  )
}

function BudgetBar({
  name,
  spent,
  budget,
  color,
}: {
  name: string
  spent: number
  budget: number
  color: string
}) {
  const hasBudget = budget > 0
  const pct = hasBudget ? Math.min(100, (spent / budget) * 100) : spent > 0 ? 100 : 0
  const over = hasBudget && spent > budget
  return (
    <div>
      <div className="mb-1 flex items-baseline justify-between gap-2">
        <p className="min-w-0 truncate text-sm text-on-surface">{name}</p>
        <p className="tnum shrink-0 text-xs text-on-surface-variant">
          {formatMxn(spent)}
          {hasBudget && <span className="opacity-60"> / {formatMxn(budget)}</span>}
        </p>
      </div>
      <div className="h-2 w-full overflow-hidden rounded-full bg-surface-3">
        <div
          className={`h-full rounded-full ${over ? 'bg-expense' : ''}`}
          style={{ width: `${pct}%`, ...(over ? {} : { backgroundColor: color }) }}
        />
      </div>
      {over && <p className="mt-1 text-xs font-medium text-expense">▲ Excede el presupuesto</p>}
    </div>
  )
}

/** Barras SVG-menos: columnas flex con altura proporcional (cronológicas). */
function TrendChart({ points, selectedId }: { points: TrendPoint[]; selectedId: string }) {
  const max = Math.max(...points.map((p) => p.totalPosted), 1)
  return (
    <div>
      <div className="flex h-40 items-end gap-2 sm:gap-3">
        {points.map((p) => {
          const hPct = Math.max(3, (p.totalPosted / max) * 100)
          const selected = p.quincena.id === selectedId
          return (
            <div key={p.quincena.id} className="flex min-w-0 flex-1 flex-col items-center gap-1.5">
              <p className="tnum text-[11px] text-on-surface-variant">{formatShortMxn(p.totalPosted)}</p>
              <div className="flex w-full flex-1 items-end">
                <div
                  className={`w-full rounded-t-lg transition-all ${selected ? 'bg-primary' : 'bg-primary/35'}`}
                  style={{ height: `${hPct}%` }}
                  title={`${p.quincena.label}: ${formatMxn(p.totalPosted)}`}
                />
              </div>
            </div>
          )
        })}
      </div>
      <div className="mt-1.5 flex gap-2 sm:gap-3">
        {points.map((p) => (
          <p
            key={p.quincena.id}
            className={`min-w-0 flex-1 truncate text-center text-[11px] ${
              p.quincena.id === selectedId ? 'font-semibold text-on-surface' : 'text-on-surface-variant'
            }`}
          >
            {p.quincena.label || p.quincena.id}
          </p>
        ))}
      </div>
    </div>
  )
}

/** "$12.3k" para las etiquetas compactas de la tendencia. */
function formatShortMxn(n: number): string {
  if (n >= 1000) return `$${(n / 1000).toFixed(1)}k`
  return `$${Math.round(n)}`
}
