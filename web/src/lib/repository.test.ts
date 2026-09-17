import { describe, expect, it } from 'vitest'
import {
  camelToSnake,
  epochToMxDateStr,
  isTombstoned,
  mirrorSnake,
  normCategory,
  normExpense,
  normIncome,
  normInstallmentPlan,
  normLoan,
  normMember,
  normQuincena,
  normSavingsGoal,
  normWallet,
  parseInviteCode,
  pick,
  pickNum,
  pickStr,
  toDateStr,
} from './repository'

/**
 * El contrato Firestore tiene dos dialectos: los docs que escribe Android van
 * en camelCase y los que sembro scripts/seed_firebase.py en snake_case. La web
 * lee con fallback dual y escribe camelCase, espejando snake_case solo si el
 * doc ya lo traia.
 */
describe('lectura con fallback dual', () => {
  it('pick prefiere camelCase y cae a snake_case', () => {
    expect(pick({ amountMxn: 10, amount_mxn: 20 }, 'amountMxn', 'amount_mxn')).toBe(10)
    expect(pick({ amount_mxn: 20 }, 'amountMxn', 'amount_mxn')).toBe(20)
    expect(pick({}, 'amountMxn', 'amount_mxn')).toBeUndefined()
  })

  it('un null en camelCase no tapa el valor snake_case', () => {
    expect(pick({ amountMxn: null, amount_mxn: 20 }, 'amountMxn', 'amount_mxn')).toBe(20)
    expect(pick({ amountMxn: null, amount_mxn: null }, 'amountMxn', 'amount_mxn')).toBeUndefined()
  })

  it('pickNum solo acepta numeros finitos y pickStr solo cadenas', () => {
    expect(pickNum({ x: '12' }, 'x', 'x')).toBeUndefined()
    expect(pickNum({ x: Number.NaN }, 'x', 'x')).toBeUndefined()
    expect(pickNum({ x: 12.5 }, 'x', 'x')).toBe(12.5)
    expect(pickNum({ x: 0 }, 'x', 'x')).toBe(0)
    expect(pickStr({ x: 12 }, 'x', 'x')).toBeUndefined()
    expect(pickStr({ x: 'a' }, 'x', 'x')).toBe('a')
  })
})

describe('espejo snake_case al escribir', () => {
  it('camelToSnake convierte cada mayuscula', () => {
    expect(camelToSnake('currentBalanceMxn')).toBe('current_balance_mxn')
    expect(camelToSnake('last4')).toBe('last4')
    expect(camelToSnake('updatedAt')).toBe('updated_at')
  })

  it('mirrorSnake duplica solo los campos que el doc existente traia en snake_case', () => {
    const existing = { current_balance_mxn: 100, display_name: 'BBVA', last4: '1234' }
    const out = mirrorSnake(existing, { currentBalanceMxn: 250, creditLimitMxn: 9000, last4: '5678' })
    expect(out).toEqual({
      currentBalanceMxn: 250,
      current_balance_mxn: 250,
      creditLimitMxn: 9000,
      last4: '5678',
    })
  })

  it('mirrorSnake no inventa claves snake_case en un doc camelCase', () => {
    expect(mirrorSnake({ currentBalanceMxn: 1 }, { currentBalanceMxn: 2 })).toEqual({ currentBalanceMxn: 2 })
  })
})

describe('normalizadores de documentos', () => {
  const seedExpense = {
    concept: 'Gasolina',
    amount_mxn: 850,
    category_id: 'cat-gas',
    quincena_id: 'q-2026-09-FIRST',
    occurred_at: 1_757_000_000_000,
    payment_method_id: 'w-bbva',
    status: 'POSTED',
    household_id: 'default_household',
  }
  const androidExpense = {
    concept: 'Gasolina',
    amountMxn: 850,
    categoryId: 'cat-gas',
    quincenaId: 'q-2026-09-FIRST',
    occurredAt: 1_757_000_000_000,
    paymentMethodId: 'w-bbva',
    status: 'POSTED',
    householdId: 'default_household',
    updatedAt: 5,
  }

  it('un gasto sembrado y uno de Android normalizan igual', () => {
    const a = normExpense('e1', seedExpense)
    const b = normExpense('e1', androidExpense)
    expect(a).toEqual({ ...b, updatedAt: 0 })
    expect(a.amountMxn).toBe(850)
    expect(a.quincenaId).toBe('q-2026-09-FIRST')
    expect(a.notes).toBeNull()
    expect(a.deletedAt).toBeUndefined()
  })

  it('los defaults de un gasto vacio no rompen', () => {
    const e = normExpense('e0', {})
    expect(e.amountMxn).toBe(0)
    expect(e.status).toBe('POSTED')
    expect(e.updatedAt).toBe(0)
  })

  it('una lapida se reconoce en los dos dialectos', () => {
    expect(isTombstoned(normExpense('e', { ...androidExpense, deletedAt: 10 }))).toBe(true)
    expect(isTombstoned(normExpense('e', { ...seedExpense, deleted_at: 10 }))).toBe(true)
    expect(isTombstoned(normExpense('e', { ...seedExpense, deleted_at: 0 }))).toBe(false)
    expect(isTombstoned(normExpense('e', androidExpense))).toBe(false)
  })

  it('miembro, cuenta y categoria con fallback dual y defaults', () => {
    const m = normMember('m', { display_name: 'Norma', role: 'PAYER_ADULT', short_aliases: '["norma"]' })
    expect(m.displayName).toBe('Norma')
    expect(m.isActive).toBe(true)
    expect(m.shortAliases).toBe('["norma"]')
    expect(normMember('m', {}).displayName).toBe('(sin nombre)')

    const w = normWallet('w', { displayName: 'BBVA', kind: 'DEBIT_ACCOUNT', current_balance_mxn: 1500, isActive: false })
    expect(w.currentBalanceMxn).toBe(1500)
    expect(w.isActive).toBe(false)
    expect(w.creditLimitMxn).toBeUndefined()

    const c = normCategory('c', { display_name: 'Despensa', code: 'FOOD.DESPENSA', sort_order: 3 })
    expect(c.sortOrder).toBe(3)
    expect(c.kind).toBe('EXPENSE')
    expect(normCategory('c', {}).sortOrder).toBe(0)
  })

  it('quincena: los limites sobreviven como cadena ISO o como epoch legado', () => {
    const q = normQuincena('q', { year: 2026, month: 9, half: 'FIRST', start_date: '2026-09-01', end_date: '2026-09-15', status: 'ACTIVE' })
    expect(q.startDate).toBe('2026-09-01')
    expect(q.projectedIncomeMxn).toBe(0)
    expect(toDateStr('2026-09-01T00:00:00')).toBe('2026-09-01')
    expect(toDateStr('2026')).toBeNull()
    expect(toDateStr(0)).toBeNull()
    // 2026-09-01T03:00Z es todavia el 31 de agosto en Mexico.
    expect(toDateStr(Date.UTC(2026, 8, 1, 3, 0))).toBe('2026-08-31')
    expect(epochToMxDateStr(Date.UTC(2026, 8, 1, 12, 0))).toBe('2026-09-01')
  })

  it('hoja de balance: meta, prestamo, plan MSI e ingreso', () => {
    expect(normSavingsGoal('g', { name: 'Vacaciones', target_mxn: 10000, current_mxn: 2500 })).toMatchObject({
      name: 'Vacaciones', targetMxn: 10000, currentMxn: 2500, targetDate: null, linkedPaymentMethodId: null,
    })
    expect(normLoan('l', {}).id).toBe('l')
    expect(normInstallmentPlan('p', { display_name: 'Mercado Libre 12 MSI', total_installments: 12 })).toMatchObject({
      displayName: 'Mercado Libre 12 MSI', totalInstallments: 12,
    })
    expect(normIncome('i', { quincena_id: 'q1', member_id: 'm1', amount_mxn: 20000 })).toMatchObject({
      quincenaId: 'q1', memberId: 'm1', amountMxn: 20000, status: 'PLANNED',
    })
  })
})

describe('codigo de invitacion legacy', () => {
  it('parte por el ultimo punto y exige ocho caracteres A-Z0-9', () => {
    expect(parseInviteCode('default_household.7qx4k2ab')).toEqual({ hid: 'default_household', code: '7QX4K2AB' })
    expect(parseInviteCode('con.puntos.7QX4K2AB')).toEqual({ hid: 'con.puntos', code: '7QX4K2AB' })
    expect(parseInviteCode('7QX4K2AB')).toBeNull()
    expect(parseInviteCode('hid.corto')).toBeNull()
    expect(parseInviteCode('hid.')).toBeNull()
    expect(parseInviteCode('.7QX4K2AB')).toBeNull()
  })
})
