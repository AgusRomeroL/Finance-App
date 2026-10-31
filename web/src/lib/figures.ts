import { toDateStr } from './repository'
import type { CategoryWithId, ExpenseWithId, QuincenaWithId, RecurrenceTemplate } from './types'

/* ---------------------------------------------------------------------------
 * Cifras de la quincena con la convención de la app (Fase 7b), espejo de
 * `QuincenaFigures.kt`, `CategoryCaps.kt` y `CarryOver.kt`. Puro: sin red.
 *
 *   disponible = ingreso + arrastre − gastado − reservado − topes por gastar
 *
 *  - Ingreso: el mayor entre el proyectado de la quincena y lo recibido.
 *  - Arrastre: lo que sobró (o faltó) en la quincena inmediata anterior, solo
 *    si ya está cerrada; no se encadena.
 *  - Gastado: lo ejecutado (POSTED).
 *  - Reservado: lo planeado prorrateado por la cadencia de su plantilla.
 *  - Topes por gastar: lo que falta de cada tope de una categoría SIN plantilla
 *    recurrente activa (la plantilla ya aparta su cargo como reservado).
 *
 * Antes la web restaba a su manera (ingreso proyectado menos gastado en el
 * Panel; recibido menos gastado menos planeado entero en Analíticas) y daba
 * otra cifra que el teléfono.
 * ------------------------------------------------------------------------- */

/** Cuánto de un cargo planeado carga su quincena, según la cadencia de su plantilla. */
export function cadenceWeight(cadence: string | undefined): number {
  if (cadence === 'MONTHLY_SPECIFIC_HALF') return 0.5
  if (cadence === 'BIMONTHLY') return 0.25
  return 1
}

/** "YYYY-MM-DD" del día anterior a [iso]. */
function dayBefore(iso: string): string {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1, d) - 86_400_000).toISOString().slice(0, 10)
}

/**
 * La quincena inmediata anterior a [q]: la que contiene el día anterior a su
 * inicio. Por fecha y no por id, porque las quincenas sembradas tienen ids UUID.
 */
export function previousQuincena(quincenas: QuincenaWithId[], q: QuincenaWithId): QuincenaWithId | null {
  const start = toDateStr(q.startDate)
  if (!start) return null
  const target = dayBefore(start)
  return (
    quincenas.find((c) => {
      const s = toDateStr(c.startDate)
      const e = toDateStr(c.endDate)
      return s !== null && e !== null && s <= target && target <= e
    }) ?? null
  )
}

export interface CarryOver {
  /** Sobrante (positivo) o déficit (negativo) congelado al cerrar la anterior. */
  amount: number
  fromLabel: string
  /** La anterior sigue sin cerrar: todavía no arrastra nada. */
  pendingClose: boolean
  /** Lo que de verdad suma: cero mientras la anterior no cierre. */
  effective: number
}

/** El arrastre que deja [previous], o null si no hay quincena anterior. */
export function carryOverFrom(previous: QuincenaWithId | null): CarryOver | null {
  if (!previous) return null
  const closed = previous.status === 'CLOSED'
  const income = Math.max(previous.projectedIncomeMxn ?? 0, previous.actualIncomeMxn ?? 0)
  const amount = closed ? income - (previous.actualExpensesMxn ?? 0) : 0
  return { amount, fromLabel: previous.label, pendingClose: !closed, effective: closed ? amount : 0 }
}

export interface CapRow {
  categoryId: string
  name: string
  cap: number
  spent: number
  plannedProrated: number
  /** Lo que falta por gastar del tope, nunca negativo. */
  pending: number
}

/**
 * Topes de gasto libre de la quincena: categorías con tope y sin plantilla
 * recurrente activa, con lo gastado y lo planeado (prorrateado) descontados.
 */
export function categoryCaps(
  categories: CategoryWithId[],
  expenses: ExpenseWithId[],
  templates: RecurrenceTemplate[],
): CapRow[] {
  const byTemplate = new Map(templates.map((t) => [t.id, t]))
  const withActiveTemplate = new Set(templates.filter((t) => t.isActive).map((t) => t.categoryId))
  return categories
    .filter((c) => (c.budgetDefaultMxn ?? 0) > 0 && !withActiveTemplate.has(c.id))
    .map((c) => {
      const mine = expenses.filter((e) => e.categoryId === c.id)
      const spent = mine.filter((e) => e.status === 'POSTED').reduce((s, e) => s + (e.amountMxn ?? 0), 0)
      const plannedProrated = mine
        .filter((e) => e.status === 'PLANNED')
        .reduce((s, e) => s + (e.amountMxn ?? 0) * weightOf(e, byTemplate), 0)
      const cap = c.budgetDefaultMxn ?? 0
      return {
        categoryId: c.id,
        name: c.displayName,
        cap,
        spent,
        plannedProrated,
        pending: Math.max(0, cap - spent - plannedProrated),
      }
    })
}

function weightOf(e: ExpenseWithId, byTemplate: Map<string, RecurrenceTemplate>): number {
  const t = e.recurrenceTemplateId ? byTemplate.get(e.recurrenceTemplateId) : undefined
  return cadenceWeight(t?.cadence)
}

export interface QuincenaFigures {
  income: number
  carryOver: number
  carry: CarryOver | null
  spent: number
  reserved: number
  capsPending: number
  /** Ingreso más arrastre, menos gastado: sin apartar nada. */
  gross: number
  available: number
}

export interface FiguresInput {
  quincena: QuincenaWithId
  /** Ingreso recibido (income_source POSTED) de la quincena. */
  receivedIncome: number
  /** Gastos POSTED y PLANNED de la quincena, sin lápidas. */
  expenses: ExpenseWithId[]
  templates: RecurrenceTemplate[]
  categories: CategoryWithId[]
  /** La quincena inmediata anterior (ver [previousQuincena]). */
  previous: QuincenaWithId | null
}

export function quincenaFigures(input: FiguresInput): QuincenaFigures {
  const byTemplate = new Map(input.templates.map((t) => [t.id, t]))
  const income = Math.max(input.quincena.projectedIncomeMxn ?? 0, input.receivedIncome)
  const carry = carryOverFrom(input.previous)
  const carryOver = carry?.effective ?? 0
  const spent = input.expenses
    .filter((e) => e.status === 'POSTED')
    .reduce((s, e) => s + (e.amountMxn ?? 0), 0)
  const reserved = input.expenses
    .filter((e) => e.status === 'PLANNED')
    .reduce((s, e) => s + (e.amountMxn ?? 0) * weightOf(e, byTemplate), 0)
  const capsPending = categoryCaps(input.categories, input.expenses, input.templates)
    .reduce((s, r) => s + r.pending, 0)
  const gross = income + carryOver - spent
  return { income, carryOver, carry, spent, reserved, capsPending, gross, available: gross - reserved - capsPending }
}

/**
 * Los términos de la resta escrita, redondeados a pesos ANTES de restarse: el
 * disponible sale de esa resta, así que la línea siempre cuadra con la cifra
 * grande (la misma regla que el héroe del teléfono).
 */
export function writtenSubtraction(f: QuincenaFigures) {
  const entro = Math.round(f.income)
  const arrastre = Math.round(f.carryOver)
  const gastado = Math.round(f.spent)
  const reservado = Math.round(f.reserved)
  const topes = Math.round(f.capsPending)
  return { entro, arrastre, gastado, reservado, topes, disponible: entro + arrastre - gastado - reservado - topes }
}
