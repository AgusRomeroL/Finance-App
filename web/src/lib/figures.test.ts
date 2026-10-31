import { describe, expect, it } from 'vitest'
import {
  cadenceWeight,
  carryOverFrom,
  categoryCaps,
  previousQuincena,
  quincenaFigures,
  writtenSubtraction,
} from './figures'
import type { CategoryWithId, ExpenseWithId, QuincenaWithId, RecurrenceTemplate } from './types'

/* Mismos casos que las pruebas Kotlin de la app (QuincenaFiguresTest,
 * CategoryCapsTest, CarryOverTest): las dos superficies tienen que dar la
 * misma cifra. */

function quincena(over: Partial<QuincenaWithId> = {}): QuincenaWithId {
  return {
    id: 'q-actual',
    year: 2026,
    month: 10,
    half: 'SECOND',
    startDate: '2026-10-16',
    endDate: '2026-10-31',
    label: 'Q2 Octubre 2026',
    status: 'ACTIVE',
    projectedIncomeMxn: 20000,
    actualIncomeMxn: 0,
    projectedExpensesMxn: 0,
    actualExpensesMxn: 0,
    ...over,
  }
}

function gasto(id: string, amount: number, over: Partial<ExpenseWithId> = {}): ExpenseWithId {
  return {
    id,
    concept: id,
    amountMxn: amount,
    categoryId: 'cat-otros',
    quincenaId: 'q-actual',
    occurredAt: 0,
    status: 'POSTED',
    updatedAt: 0,
    ...over,
  }
}

function categoria(id: string, budget?: number): CategoryWithId {
  return { id, displayName: id, code: id, kind: 'EXPENSE', budgetDefaultMxn: budget, sortOrder: 0, updatedAt: 0 }
}

const base = { receivedIncome: 0, templates: [] as RecurrenceTemplate[], categories: [] as CategoryWithId[], previous: null }

describe('convencion de cifras de la quincena', () => {
  it('el ingreso es el mayor entre proyectado y recibido', () => {
    expect(quincenaFigures({ ...base, quincena: quincena(), expenses: [], receivedIncome: 15000 }).income).toBe(20000)
    expect(quincenaFigures({ ...base, quincena: quincena(), expenses: [], receivedIncome: 23000 }).income).toBe(23000)
  })

  it('disponible es ingreso menos gastado menos reservado', () => {
    const f = quincenaFigures({
      ...base,
      quincena: quincena({ projectedIncomeMxn: 10000 }),
      expenses: [gasto('a', 7000), gasto('b', 2500, { status: 'PLANNED' })],
    })
    expect(f.spent).toBe(7000)
    expect(f.reserved).toBe(2500)
    expect(f.available).toBe(500)
  })

  it('lo planeado se prorratea por la cadencia de su plantilla', () => {
    expect(cadenceWeight('MONTHLY_SPECIFIC_HALF')).toBe(0.5)
    expect(cadenceWeight('BIMONTHLY')).toBe(0.25)
    expect(cadenceWeight('QUINCENAL_EVERY')).toBe(1)
    expect(cadenceWeight(undefined)).toBe(1)
    const templates: RecurrenceTemplate[] = [
      { id: 't-mes', categoryId: 'cat-luz', cadence: 'MONTHLY_SPECIFIC_HALF', isActive: true },
      { id: 't-bim', categoryId: 'cat-agua', cadence: 'BIMONTHLY', isActive: true },
    ]
    const f = quincenaFigures({
      ...base,
      quincena: quincena(),
      templates,
      expenses: [
        gasto('luz', 1000, { status: 'PLANNED', recurrenceTemplateId: 't-mes' }),
        gasto('agua', 400, { status: 'PLANNED', recurrenceTemplateId: 't-bim' }),
        gasto('suelto', 300, { status: 'PLANNED' }),
      ],
    })
    expect(f.reserved).toBe(500 + 100 + 300)
  })
})

describe('topes por gastar', () => {
  it('lo gastado y lo planeado de la categoria se descuentan sin contar dos veces', () => {
    const rows = categoryCaps(
      [categoria('cat-super', 2000)],
      [gasto('a', 500, { categoryId: 'cat-super' }), gasto('b', 900, { categoryId: 'cat-super', status: 'PLANNED' })],
      [],
    )
    expect(rows[0].pending).toBe(600)
  })

  it('un tope excedido aporta cero', () => {
    expect(categoryCaps([categoria('cat-rest', 1500)], [gasto('a', 1680, { categoryId: 'cat-rest' })], [])[0].pending).toBe(0)
  })

  it('una categoria con plantilla activa no aparta su tope; una inactiva si', () => {
    const cats = [categoria('cat-netflix', 329), categoria('cat-gym', 500), categoria('cat-sin-tope')]
    const activa: RecurrenceTemplate = { id: 't1', categoryId: 'cat-netflix', cadence: 'MONTHLY_SPECIFIC_HALF', isActive: true }
    const inactiva: RecurrenceTemplate = { id: 't2', categoryId: 'cat-gym', cadence: 'MONTHLY_SPECIFIC_HALF', isActive: false }
    expect(categoryCaps(cats, [], [activa, inactiva]).map((r) => r.categoryId)).toEqual(['cat-gym'])
  })

  it('un gasto dentro del tope no mueve el disponible y solo el exceso lo baja', () => {
    // Secuencia de Lukas: limite de 1,500 con 780 gastados; 500 quedan dentro,
    // 400 mas lo exceden por 180.
    const disponible = (spent: number) =>
      quincenaFigures({
        ...base,
        quincena: quincena(),
        categories: [categoria('cat-rest', 1500)],
        expenses: [gasto('r', spent, { categoryId: 'cat-rest' })],
      }).available
    expect(disponible(1280)).toBeCloseTo(disponible(780), 9)
    expect(disponible(1680)).toBeCloseTo(disponible(780) - 180, 9)
  })
})

describe('arrastre de la quincena anterior', () => {
  const anterior = (over: Partial<QuincenaWithId>) =>
    quincena({ id: 'q-ant', startDate: '2026-10-01', endDate: '2026-10-15', label: 'Q1 Octubre 2026', ...over })

  it('una anterior cerrada deja su sobrante', () => {
    const c = carryOverFrom(anterior({ status: 'CLOSED', projectedIncomeMxn: 75000, actualIncomeMxn: 75000, actualExpensesMxn: 72575.2 }))
    expect(c?.pendingClose).toBe(false)
    expect(c?.effective).toBeCloseTo(2424.8, 9)
  })

  it('el deficit tambien se arrastra', () => {
    const c = carryOverFrom(anterior({ status: 'CLOSED', projectedIncomeMxn: 60000, actualIncomeMxn: 60000, actualExpensesMxn: 61578.2 }))
    expect(c?.effective).toBeCloseTo(-1578.2, 9)
  })

  it('una anterior pendiente de cierre todavia no arrastra', () => {
    const c = carryOverFrom(anterior({ status: 'CLOSING_REVIEW', projectedIncomeMxn: 75000, actualExpensesMxn: 10000 }))
    expect(c?.pendingClose).toBe(true)
    expect(c?.effective).toBe(0)
  })

  it('sin anterior no hay arrastre', () => {
    expect(carryOverFrom(null)).toBeNull()
  })

  it('la anterior se busca por fecha, tambien entre meses y anios', () => {
    const lista = [
      quincena({ id: 'oct2', startDate: '2026-10-16', endDate: '2026-10-31' }),
      quincena({ id: 'oct1', startDate: '2026-10-01', endDate: '2026-10-15' }),
      quincena({ id: 'sep2', startDate: '2026-09-16', endDate: '2026-09-30' }),
      quincena({ id: 'dic2', startDate: '2025-12-16', endDate: '2025-12-31' }),
      quincena({ id: 'ene1', startDate: '2026-01-01', endDate: '2026-01-15' }),
    ]
    expect(previousQuincena(lista, lista[0])?.id).toBe('oct1')
    expect(previousQuincena(lista, lista[1])?.id).toBe('sep2')
    expect(previousQuincena(lista, lista[4])?.id).toBe('dic2')
    expect(previousQuincena(lista, lista[2])).toBeNull()
  })

  it('el arrastre y los topes entran a la resta', () => {
    const f = quincenaFigures({
      ...base,
      quincena: quincena(),
      previous: anterior({ status: 'CLOSED', projectedIncomeMxn: 3000, actualIncomeMxn: 0, actualExpensesMxn: 1500 }),
      categories: [categoria('cat-super', 3000)],
      expenses: [gasto('a', 8000), gasto('b', 2000, { status: 'PLANNED' })],
    })
    expect(f.carryOver).toBe(1500)
    expect(f.gross).toBe(13500)
    expect(f.capsPending).toBe(3000)
    expect(f.available).toBe(8500)
  })
})

describe('resta escrita', () => {
  it('redondea cada termino antes de restar para que cuadre', () => {
    const f = quincenaFigures({
      ...base,
      quincena: quincena({ projectedIncomeMxn: 80000.5 }),
      expenses: [gasto('a', 1206.6), gasto('b', 22500.6, { status: 'PLANNED' })],
    })
    const r = writtenSubtraction(f)
    expect(r.disponible).toBe(r.entro + r.arrastre - r.gastado - r.reservado - r.topes)
  })
})
