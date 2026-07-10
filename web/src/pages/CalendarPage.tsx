import { useEffect, useMemo, useState } from 'react'
import { useHousehold } from '../context/HouseholdContext'
import { EmptyState, ErrorState, Eyebrow, LoadingState } from '../components/ui'
import { getActiveQuincena, listCategories, listPostedExpenses } from '../lib/repository'
import { formatMxn } from '../lib/format'
import type { CategoryWithId, ExpenseWithId, QuincenaWithId } from '../lib/types'

/* ---------------------------------------------------------------------------
 * Calendario del TITULAR: mes con puntos por día y lista al tocar un día.
 *
 * Alcance de datos (deliberado): repository solo expone gastos POSTED por
 * quincena (listPostedExpenses) y la quincena ACTIVE (getActiveQuincena) — no
 * hay listado de quincenas ni de PLANNED. Por eso el calendario muestra los
 * movimientos REGISTRADOS de la quincena activa; los pagos planeados (PLANNED)
 * se consultan y confirman desde el teléfono, y así se indica en la UI.
 * ------------------------------------------------------------------------- */

const WEEKDAY_HEADERS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb']

/** "YYYY-MM-DD" en America/Mexico_City para un epoch ms. */
const MX_DATE_FMT = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Mexico_City',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

const MONTH_LABEL_FMT = new Intl.DateTimeFormat('es-MX', { month: 'long', year: 'numeric' })

function toDayKey(epochMs: number): string {
  return MX_DATE_FMT.format(new Date(epochMs))
}

function dayKey(year: number, month1: number, day: number): string {
  return `${year}-${String(month1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
}

interface MonthView {
  year: number
  /** 1-12 */
  month: number
}

function currentMonthMx(): MonthView {
  const [y, m] = toDayKey(Date.now()).split('-').map(Number)
  return { year: y ?? new Date().getFullYear(), month: m ?? new Date().getMonth() + 1 }
}

function shiftMonth(view: MonthView, delta: number): MonthView {
  const idx = view.year * 12 + (view.month - 1) + delta
  return { year: Math.floor(idx / 12), month: (idx % 12) + 1 }
}

interface CalendarData {
  quincena: QuincenaWithId | null
  expenses: ExpenseWithId[]
  categories: CategoryWithId[]
}

export default function CalendarPage() {
  const { active, loading: hhLoading } = useHousehold()
  const hid = active?.id ?? null

  const [view, setView] = useState<MonthView>(() => currentMonthMx())
  const [selectedDay, setSelectedDay] = useState<string | null>(() => toDayKey(Date.now()))
  const [data, setData] = useState<CalendarData | null>(null)
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
        const [quincena, categories] = await Promise.all([getActiveQuincena(hid), listCategories(hid)])
        const expenses = quincena ? await listPostedExpenses(hid, quincena.id) : []
        if (!cancelled) setData({ quincena, expenses, categories })
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : 'No se pudo cargar el calendario.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [hid, reloadKey])

  /** Movimientos agrupados por día calendario MX. */
  const byDay = useMemo<Map<string, ExpenseWithId[]>>(() => {
    const map = new Map<string, ExpenseWithId[]>()
    for (const e of data?.expenses ?? []) {
      const key = toDayKey(e.occurredAt)
      const list = map.get(key)
      if (list) list.push(e)
      else map.set(key, [e])
    }
    return map
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

  const todayKey = toDayKey(Date.now())
  const daysInMonth = new Date(view.year, view.month, 0).getDate()
  const firstWeekday = new Date(view.year, view.month - 1, 1).getDay() // 0 = domingo
  const monthLabel = MONTH_LABEL_FMT.format(new Date(view.year, view.month - 1, 1))
  const catMap = new Map((data?.categories ?? []).map((c) => [c.id, c.displayName]))

  const selectedExpenses = selectedDay ? byDay.get(selectedDay) ?? [] : []
  const selectedTotal = selectedExpenses.reduce((s, e) => s + (e.amountMxn ?? 0), 0)

  return (
    <div className="mx-auto max-w-md space-y-5 pb-8">
      {/* Encabezado del mes con navegación */}
      <div className="flex items-center justify-between gap-2">
        <button
          type="button"
          onClick={() => setView((v) => shiftMonth(v, -1))}
          aria-label="Mes anterior"
          className="flex h-10 w-10 items-center justify-center rounded-full bg-surface-1 text-on-surface transition-colors hover:bg-surface-2"
        >
          <ChevronIcon direction="left" />
        </button>
        <div className="text-center">
          <p className="text-sm font-semibold capitalize text-on-surface">{monthLabel}</p>
          <button
            type="button"
            onClick={() => {
              setView(currentMonthMx())
              setSelectedDay(todayKey)
            }}
            className="text-xs font-medium text-primary hover:underline"
          >
            Hoy
          </button>
        </div>
        <button
          type="button"
          onClick={() => setView((v) => shiftMonth(v, 1))}
          aria-label="Mes siguiente"
          className="flex h-10 w-10 items-center justify-center rounded-full bg-surface-1 text-on-surface transition-colors hover:bg-surface-2"
        >
          <ChevronIcon direction="right" />
        </button>
      </div>

      {/* Rejilla del mes */}
      <div className="rounded-card bg-surface-1 p-3">
        <div className="grid grid-cols-7 gap-1">
          {WEEKDAY_HEADERS.map((d) => (
            <p key={d} className="py-1 text-center text-[11px] font-semibold uppercase tracking-wide text-on-surface-variant">
              {d}
            </p>
          ))}
          {Array.from({ length: firstWeekday }, (_, i) => (
            <div key={`pad-${i}`} aria-hidden="true" />
          ))}
          {Array.from({ length: daysInMonth }, (_, i) => {
            const day = i + 1
            const key = dayKey(view.year, view.month, day)
            const dayExpenses = byDay.get(key) ?? []
            const isToday = key === todayKey
            const isSelected = key === selectedDay
            return (
              <button
                key={key}
                type="button"
                onClick={() => setSelectedDay(key)}
                aria-pressed={isSelected}
                aria-label={`Día ${day}${dayExpenses.length > 0 ? `, ${dayExpenses.length} movimiento${dayExpenses.length > 1 ? 's' : ''}` : ''}`}
                className={`flex aspect-square flex-col items-center justify-center gap-0.5 rounded-2xl text-sm transition-colors ${
                  isSelected
                    ? 'bg-primary font-semibold text-on-primary'
                    : isToday
                      ? 'bg-primary-container font-semibold text-on-primary-container'
                      : 'text-on-surface hover:bg-surface-2'
                }`}
              >
                <span className="tnum">{day}</span>
                <span className="flex h-1.5 items-center gap-0.5" aria-hidden="true">
                  {dayExpenses.slice(0, 3).map((e) => (
                    <span
                      key={e.id}
                      className={`h-1.5 w-1.5 rounded-full ${isSelected ? 'bg-on-primary/80' : 'bg-expense'}`}
                    />
                  ))}
                </span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Cobertura de datos */}
      <p className="px-1 text-center text-xs text-on-surface-variant/80">
        {data?.quincena
          ? `Se muestran los movimientos registrados de la quincena activa (${data.quincena.label}).`
          : 'No hay quincena activa: abre la app del teléfono para provisionarla.'}{' '}
        Los pagos planeados (PLANNED) se consultan y confirman desde el teléfono.
      </p>

      {/* Movimientos del día seleccionado */}
      {selectedDay && (
        <section>
          <div className="mb-3 flex items-baseline justify-between gap-2 px-1">
            <Eyebrow>{formatDayLong(selectedDay)}</Eyebrow>
            {selectedExpenses.length > 0 && (
              <p className="tnum text-xs font-semibold text-on-surface-variant">
                Total −{formatMxn(selectedTotal)}
              </p>
            )}
          </div>
          {selectedExpenses.length === 0 ? (
            <EmptyState title="Sin movimientos este día" />
          ) : (
            <ul className="space-y-2">
              {selectedExpenses.map((e) => (
                <li
                  key={e.id}
                  className="flex items-center justify-between gap-3 rounded-card-sm bg-surface-1 px-4 py-3.5"
                >
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-on-surface">{e.concept}</p>
                    <p className="mt-0.5 text-xs text-on-surface-variant">
                      {catMap.get(e.categoryId) ?? 'Sin categoría'}
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
      )}
    </div>
  )
}

/** "YYYY-MM-DD" -> "miércoles 8 de julio". */
function formatDayLong(key: string): string {
  const [y, m, d] = key.split('-').map(Number)
  const dt = new Date(y ?? 2026, (m ?? 1) - 1, d ?? 1, 12)
  return new Intl.DateTimeFormat('es-MX', { weekday: 'long', day: 'numeric', month: 'long' }).format(dt)
}

function ChevronIcon({ direction }: { direction: 'left' | 'right' }) {
  return (
    <svg viewBox="0 0 24 24" className="h-5 w-5" fill="none" aria-hidden="true">
      <path
        d={direction === 'left' ? 'M14.5 6l-6 6 6 6' : 'M9.5 6l6 6-6 6'}
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
