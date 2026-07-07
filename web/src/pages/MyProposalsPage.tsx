import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useHousehold } from '../context/HouseholdContext'
import { EmptyState, ErrorState, Eyebrow, LoadingState } from '../components/ui'
import { listMyProposals } from '../lib/repository'
import { formatDate, formatMxn } from '../lib/format'
import type { ProposalWithId } from '../lib/types'

/* ---------------------------------------------------------------------------
 * Mis propuestas + saldo "me deben".
 * El flujo: el colaborador pagó con SU dinero; cuando el titular ACEPTA la
 * propuesta queda debiéndole el monto, hasta que la marque reembolsada
 * (reimbursedAt, escrito por la app Android).
 * ------------------------------------------------------------------------- */

interface StatusInfo {
  label: string
  cls: string
}

function statusInfo(p: ProposalWithId): StatusInfo {
  if (p.reimbursedAt) return { label: 'Reembolsada', cls: 'bg-income/20 text-income' }
  switch (p.status) {
    case 'ACCEPTED':
      return { label: 'Aceptada', cls: 'bg-primary-container text-on-primary-container' }
    case 'REJECTED':
      return { label: 'Rechazada', cls: 'bg-expense/10 text-expense' }
    default:
      return { label: 'Pendiente', cls: 'bg-alert/20 text-alert' }
  }
}

export default function MyProposalsPage() {
  const { user } = useAuth()
  const { active, loading: hhLoading } = useHousehold()
  const hid = active?.id ?? null

  const [proposals, setProposals] = useState<ProposalWithId[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    if (!hid || !user) return
    let cancelled = false
    setProposals(null)
    setError(null)
    listMyProposals(hid, user.uid)
      .then((rows) => {
        if (!cancelled) setProposals(rows)
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'No se pudieron cargar tus propuestas.')
      })
    return () => {
      cancelled = true
    }
  }, [hid, user, reloadKey])

  /** "Me deben" = ACCEPTED sin reembolsar. */
  const owed = useMemo(() => {
    if (!proposals) return 0
    return proposals
      .filter((p) => p.status === 'ACCEPTED' && !p.reimbursedAt)
      .reduce((sum, p) => sum + (p.amountMxn ?? 0), 0)
  }, [proposals])

  if (hhLoading) return <LoadingState />
  if (!hid) {
    return (
      <EmptyState
        title="Selecciona un grupo primero"
        hint="Ve a la pestaña Grupos y elige (o crea) un grupo activo."
      />
    )
  }
  if (error) return <ErrorState message={error} onRetry={() => setReloadKey((k) => k + 1)} />
  if (!proposals) return <LoadingState label="Cargando tus propuestas…" />

  return (
    <div className="space-y-6">
      {/* Saldo héroe: me deben */}
      <div className="rounded-card bg-primary-container px-6 pb-6 pt-5 text-on-primary-container">
        <Eyebrow className="!text-on-primary-container/70">Me deben</Eyebrow>
        <p className="tnum mt-1 text-4xl font-light leading-tight sm:text-5xl">{formatMxn(owed)}</p>
        <p className="mt-2 text-sm opacity-80">
          Suma de tus propuestas aceptadas que el titular aún no te reembolsa.
        </p>
      </div>

      <section>
        <h2 className="mb-3 px-1 text-sm font-semibold text-on-surface">Mis propuestas</h2>
        {proposals.length === 0 ? (
          <EmptyState
            title="Todavía no has enviado propuestas"
            hint="Captura la primera desde la pestaña Proponer."
          />
        ) : (
          <ul className="space-y-2">
            {proposals.map((p) => {
              const st = statusInfo(p)
              return (
                <li key={p.id} className="rounded-card-sm bg-surface-1 px-4 py-3.5">
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-on-surface">{p.concept}</p>
                      <p className="mt-0.5 text-xs text-on-surface-variant">
                        {p.kind === 'EXPENSE' ? 'Gasto' : 'Pago futuro'} · {formatDate(p.occurredAt)}
                      </p>
                    </div>
                    <div className="shrink-0 text-right">
                      <p className="tnum text-sm font-semibold text-on-surface">{formatMxn(p.amountMxn)}</p>
                      <span
                        className={`mt-1 inline-block rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${st.cls}`}
                      >
                        {st.label}
                      </span>
                    </div>
                  </div>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      <p className="pb-4 text-center text-xs text-on-surface-variant/70">
        ¿Falta algo?{' '}
        <Link to="/proponer" className="font-medium text-primary underline-offset-2 hover:underline">
          Proponer un movimiento
        </Link>
      </p>
    </div>
  )
}
