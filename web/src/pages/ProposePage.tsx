import { useEffect, useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { useHousehold } from '../context/HouseholdContext'
import { Button, Card, EmptyState, LoadingState } from '../components/ui'
import { createProposal, listCategories, listMyProposals } from '../lib/repository'
import { dateInputToEpochMs, epochMsToDateInput, formatDate, formatMxn } from '../lib/format'
import type { CategoryWithId, ProposalKind, ProposalWithId } from '../lib/types'

export default function ProposePage() {
  const { user } = useAuth()
  const { active, loading: hhLoading } = useHousehold()

  const [kind, setKind] = useState<ProposalKind>('EXPENSE')
  const [concept, setConcept] = useState('')
  const [amount, setAmount] = useState('')
  const [date, setDate] = useState(() => epochMsToDateInput(Date.now()))
  const [categoryId, setCategoryId] = useState('')
  const [note, setNote] = useState('')

  const [categories, setCategories] = useState<CategoryWithId[]>([])
  const [myProposals, setMyProposals] = useState<ProposalWithId[]>([])
  const [loadingSide, setLoadingSide] = useState(false)
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null)

  const hid = active?.id ?? null

  useEffect(() => {
    if (!hid || !user) return
    let cancelled = false
    setLoadingSide(true)
    Promise.all([listCategories(hid), listMyProposals(hid, user.uid)])
      .then(([cats, props]) => {
        if (cancelled) return
        setCategories(cats)
        setMyProposals(props)
      })
      .catch(() => {
        /* No bloqueamos el formulario si falla el catálogo. */
      })
      .finally(() => {
        if (!cancelled) setLoadingSide(false)
      })
    return () => {
      cancelled = true
    }
  }, [hid, user])

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!hid || !user) return
    const amountNum = Number.parseFloat(amount)
    if (!concept.trim() || !Number.isFinite(amountNum) || amountNum <= 0) {
      setMsg({ kind: 'err', text: 'Escribe un concepto y un monto válido.' })
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
      setConcept('')
      setAmount('')
      setNote('')
      setCategoryId('')
      const refreshed = await listMyProposals(hid, user.uid)
      setMyProposals(refreshed)
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

  return (
    <div className="space-y-6">
      <Card>
        <h2 className="mb-4 text-lg font-semibold text-gray-900">Proponer un movimiento</h2>

        <form onSubmit={submit} className="space-y-4">
          <div>
            <label className="mb-1.5 block text-xs font-medium text-gray-600">Tipo</label>
            <div className="grid grid-cols-2 gap-2">
              <TypeToggle
                active={kind === 'EXPENSE'}
                onClick={() => setKind('EXPENSE')}
                label="Gasto"
                hint="Ya ocurrió"
              />
              <TypeToggle
                active={kind === 'FUTURE_PAYMENT'}
                onClick={() => setKind('FUTURE_PAYMENT')}
                label="Pago futuro"
                hint="Por venir"
              />
            </div>
          </div>

          <Field label="Concepto">
            <input
              value={concept}
              onChange={(e) => setConcept(e.target.value)}
              placeholder="ej. Colegiatura, Cine, Súper"
              className={inputCls}
            />
          </Field>

          <div className="grid grid-cols-2 gap-3">
            <Field label="Monto (MXN)">
              <input
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                inputMode="decimal"
                placeholder="0.00"
                className={inputCls}
              />
            </Field>
            <Field label="Fecha">
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                className={inputCls}
              />
            </Field>
          </div>

          <Field label="Categoría (opcional)">
            <select
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              className={inputCls}
              disabled={loadingSide && categories.length === 0}
            >
              <option value="">Sin categoría</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.displayName}
                </option>
              ))}
            </select>
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

          <Button type="submit" loading={busy} className="w-full">
            Enviar propuesta
          </Button>

          {msg && (
            <p
              className={`rounded-lg px-3 py-2 text-xs ${
                msg.kind === 'ok' ? 'bg-green-50 text-green-700' : 'bg-red-50 text-red-700'
              }`}
              role="alert"
            >
              {msg.text}
            </p>
          )}
        </form>
      </Card>

      <section>
        <h3 className="mb-3 text-sm font-semibold text-gray-900">Mis propuestas</h3>
        {loadingSide && myProposals.length === 0 ? (
          <LoadingState label="Cargando…" />
        ) : myProposals.length === 0 ? (
          <EmptyState title="Todavía no has enviado propuestas" />
        ) : (
          <ul className="space-y-2">
            {myProposals.map((p) => (
              <li
                key={p.id}
                className="flex items-center justify-between gap-3 rounded-xl border border-gray-200 bg-white px-4 py-3"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-medium text-gray-900">{p.concept}</p>
                  <p className="text-xs text-gray-500">
                    {p.kind === 'EXPENSE' ? 'Gasto' : 'Pago futuro'} · {formatDate(p.occurredAt)}
                  </p>
                </div>
                <div className="shrink-0 text-right">
                  <p className="text-sm font-semibold text-gray-900">{formatMxn(p.amountMxn)}</p>
                  <span className="text-xs font-medium text-amber-600">Pendiente</span>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}

const inputCls =
  'w-full rounded-lg border border-gray-300 px-3 py-2.5 text-sm focus:border-brand focus:outline-none focus:ring-2 focus:ring-brand/30'

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1.5 block text-xs font-medium text-gray-600">{label}</span>
      {children}
    </label>
  )
}

function TypeToggle({
  active,
  onClick,
  label,
  hint,
}: {
  active: boolean
  onClick: () => void
  label: string
  hint: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-lg border px-3 py-2.5 text-left transition-colors ${
        active ? 'border-brand bg-brand/5' : 'border-gray-300 bg-white hover:border-gray-400'
      }`}
    >
      <span className={`block text-sm font-medium ${active ? 'text-brand' : 'text-gray-800'}`}>
        {label}
      </span>
      <span className="block text-xs text-gray-500">{hint}</span>
    </button>
  )
}
