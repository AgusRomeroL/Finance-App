import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useHousehold } from '../context/HouseholdContext'
import { Button, Chip, EmptyState, Eyebrow, LoadingState } from '../components/ui'
import { createProposal, listCategories } from '../lib/repository'
import { dateInputToEpochMs, epochMsToDateInput } from '../lib/format'
import type { CategoryWithId, ProposalKind } from '../lib/types'

/* ---------------------------------------------------------------------------
 * Teclado numérico en pantalla — el monto es un string crudo ("123.45") que
 * se construye tecla a tecla: dígitos, un solo ".", máx. 2 decimales, borrar.
 * ------------------------------------------------------------------------- */
const KEYPAD: string[] = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'del']
const MAX_INT_DIGITS = 7

function pressKey(prev: string, key: string): string {
  if (key === 'del') return prev.slice(0, -1)
  if (key === '.') {
    if (prev.includes('.')) return prev
    return prev === '' ? '0.' : prev + '.'
  }
  // Dígito.
  const [intPart = '', decPart] = prev.split('.')
  if (decPart !== undefined) {
    if (decPart.length >= 2) return prev
    return prev + key
  }
  if (intPart.length >= MAX_INT_DIGITS) return prev
  if (prev === '0') return key
  return prev + key
}

/** Presenta el string crudo con separador de miles, sin perder lo tecleado. */
function displayAmount(raw: string): string {
  if (!raw) return '0'
  const [intPart, decPart] = raw.split('.')
  const grouped = Number(intPart === '' ? '0' : intPart).toLocaleString('es-MX')
  return decPart !== undefined ? `${grouped}.${decPart}` : grouped
}

export default function ProposePage() {
  const { user } = useAuth()
  const { active, loading: hhLoading } = useHousehold()

  const [kind, setKind] = useState<ProposalKind>('EXPENSE')
  const [amount, setAmount] = useState('')
  const [concept, setConcept] = useState('')
  const [date, setDate] = useState(() => epochMsToDateInput(Date.now()))
  const [categoryId, setCategoryId] = useState('')
  const [note, setNote] = useState('')

  const [categories, setCategories] = useState<CategoryWithId[]>([])
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null)

  const hid = active?.id ?? null

  useEffect(() => {
    if (!hid) return
    let cancelled = false
    listCategories(hid)
      .then((cats) => {
        if (!cancelled) setCategories(cats)
      })
      .catch(() => {
        /* No bloqueamos el formulario si falla el catálogo. */
      })
    return () => {
      cancelled = true
    }
  }, [hid])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!hid || !user) return
    const amountNum = Number.parseFloat(amount)
    if (!Number.isFinite(amountNum) || amountNum <= 0) {
      setMsg({ kind: 'err', text: 'Captura un monto mayor a cero con el teclado.' })
      return
    }
    if (!concept.trim()) {
      setMsg({ kind: 'err', text: 'Escribe un concepto.' })
      return
    }
    setBusy(true)
    setMsg(null)
    try {
      const proposedByName = user.displayName ?? user.email ?? 'Colaborador'
      await createProposal(hid, user, proposedByName, {
        kind,
        concept,
        amountMxn: amountNum,
        occurredAt: dateInputToEpochMs(date),
        categoryId: categoryId || undefined,
        note: note || undefined,
      })
      setMsg({ kind: 'ok', text: 'Propuesta enviada. El titular la revisará en la app.' })
      setAmount('')
      setConcept('')
      setNote('')
      setCategoryId('')
      setDate(epochMsToDateInput(Date.now()))
    } catch (err) {
      setMsg({ kind: 'err', text: err instanceof Error ? err.message : 'No se pudo enviar la propuesta.' })
    } finally {
      setBusy(false)
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

  const hasDecimals = amount.includes('.')

  return (
    <form onSubmit={submit} className="mx-auto max-w-md space-y-5 pb-8">
      {/* Tipo de movimiento */}
      <div className="flex justify-center gap-2" role="group" aria-label="Tipo de movimiento">
        <Chip selected={kind === 'EXPENSE'} onClick={() => setKind('EXPENSE')}>
          Gasto
        </Chip>
        <Chip selected={kind === 'FUTURE_PAYMENT'} onClick={() => setKind('FUTURE_PAYMENT')}>
          Pago futuro
        </Chip>
      </div>

      {/* Monto héroe */}
      <div className="rounded-card bg-surface-1 px-5 pb-5 pt-6 text-center">
        <Eyebrow>Monto MXN</Eyebrow>
        <p
          className="tnum mt-2 min-h-[3.5rem] break-all text-5xl font-light leading-tight text-on-surface"
          aria-live="polite"
          aria-label={`Monto: ${displayAmount(amount)} pesos`}
        >
          <span className="mr-1 align-top text-2xl font-normal text-on-surface-variant">$</span>
          {displayAmount(amount)}
          {amount.endsWith('.') && <span className="text-on-surface-variant">.</span>}
        </p>

        {/* Teclado numérico 3×4 */}
        <div className="mt-4 grid grid-cols-3 gap-2">
          {KEYPAD.map((key) => (
            <button
              key={key}
              type="button"
              onClick={() => setAmount((prev) => pressKey(prev, key))}
              disabled={key === '.' && hasDecimals}
              aria-label={key === 'del' ? 'Borrar' : key === '.' ? 'Punto decimal' : key}
              className="tnum flex h-14 items-center justify-center rounded-full bg-surface-2 text-xl font-medium text-on-surface transition-all hover:bg-surface-3 active:scale-95 disabled:opacity-40 sm:h-16"
            >
              {key === 'del' ? (
                <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" aria-hidden="true">
                  <path
                    d="M9 5h9a2 2 0 012 2v10a2 2 0 01-2 2H9l-5.3-6.4a1 1 0 010-1.2L9 5z"
                    stroke="currentColor"
                    strokeWidth="1.7"
                    strokeLinejoin="round"
                  />
                  <path
                    d="M12.5 9.5l5 5m0-5l-5 5"
                    stroke="currentColor"
                    strokeWidth="1.7"
                    strokeLinecap="round"
                  />
                </svg>
              ) : (
                key
              )}
            </button>
          ))}
        </div>
      </div>

      {/* Categoría: chips scrolleables del hogar */}
      {categories.length > 0 && (
        <div>
          <Eyebrow className="mb-2 px-1">Categoría</Eyebrow>
          <div className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4 pb-1">
            <Chip selected={categoryId === ''} onClick={() => setCategoryId('')}>
              Sin categoría
            </Chip>
            {categories.map((c) => (
              <Chip key={c.id} selected={categoryId === c.id} onClick={() => setCategoryId(c.id)}>
                {c.displayName}
              </Chip>
            ))}
          </div>
        </div>
      )}

      {/* Concepto / fecha / nota */}
      <div className="space-y-3">
        <Field label="Concepto">
          <input
            value={concept}
            onChange={(e) => setConcept(e.target.value)}
            placeholder="ej. Colegiatura, Cine, Súper"
            className={inputCls}
          />
        </Field>
        <Field label="Fecha">
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className={inputCls} />
        </Field>
        <Field label="Nota (opcional)">
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            rows={2}
            placeholder="Detalle para el titular"
            className={inputCls}
          />
        </Field>
      </div>

      <Button type="submit" loading={busy} className="w-full py-3.5">
        Enviar propuesta
      </Button>

      {msg && (
        <p
          className={`rounded-2xl px-4 py-3 text-sm ${
            msg.kind === 'ok' ? 'bg-primary-container/60 text-on-primary-container' : 'bg-expense/10 text-expense'
          }`}
          role="alert"
        >
          {msg.text}
          {msg.kind === 'ok' && (
            <>
              {' '}
              <Link to="/mis-propuestas" className="font-semibold underline underline-offset-2">
                Ver mis propuestas
              </Link>
            </>
          )}
        </p>
      )}
    </form>
  )
}

const inputCls =
  'w-full rounded-2xl bg-surface-1 px-4 py-3 text-sm text-on-surface placeholder:text-on-surface-variant/60 focus:outline-none focus:ring-2 focus:ring-primary/50'

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="eyebrow mb-1.5 block px-1">{label}</span>
      {children}
    </label>
  )
}
