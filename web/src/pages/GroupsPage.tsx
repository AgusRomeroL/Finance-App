import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { useHousehold } from '../context/HouseholdContext'
import { Button, Card, EmptyState, ErrorState, LoadingState } from '../components/ui'
import { createHousehold, joinByCode } from '../lib/repository'
import { ROLE_LABELS } from '../lib/types'

export default function GroupsPage() {
  const { user } = useAuth()
  const { households, active, loading, error, reload, selectHousehold } = useHousehold()

  return (
    <div className="space-y-6">
      <section>
        <h2 className="mb-3 px-1 text-lg font-semibold text-on-surface">Mis grupos</h2>
        {loading ? (
          <LoadingState label="Cargando tus grupos…" />
        ) : error ? (
          <ErrorState message={error} onRetry={() => void reload()} />
        ) : households.length === 0 ? (
          <EmptyState
            title="Aún no perteneces a ningún grupo"
            hint="Crea uno nuevo o únete con un código de invitación."
          />
        ) : (
          <ul className="space-y-2">
            {households.map((h) => {
              const isActive = active?.id === h.id
              return (
                <li key={h.id}>
                  <button
                    onClick={() => void selectHousehold(h.id)}
                    className={`flex w-full items-center justify-between rounded-card-sm px-5 py-4 text-left transition-colors ${
                      isActive
                        ? 'bg-primary-container text-on-primary-container'
                        : 'bg-surface-1 hover:bg-surface-2'
                    }`}
                  >
                    <div className="min-w-0">
                      <p className="truncate font-medium">{h.name}</p>
                      <p className={`text-xs ${isActive ? 'opacity-75' : 'text-on-surface-variant'}`}>
                        {ROLE_LABELS[h.role]} · {h.currency}
                      </p>
                    </div>
                    {isActive && (
                      <span className="shrink-0 rounded-full bg-primary px-3 py-1 text-xs font-medium text-on-primary">
                        Activo
                      </span>
                    )}
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </section>

      {user && (
        <>
          <JoinGroup onJoined={reload} />
          <CreateGroup uid={user.uid} onCreated={reload} />
        </>
      )}
    </div>
  )
}

const inputCls =
  'flex-1 rounded-full bg-surface-2 px-4 py-3 text-sm text-on-surface placeholder:text-on-surface-variant/60 focus:outline-none focus:ring-2 focus:ring-primary/50'

function JoinGroup({ onJoined }: { onJoined: () => Promise<void> }) {
  const { user } = useAuth()
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!user) return
    setBusy(true)
    setMsg(null)
    try {
      const displayName = user.displayName ?? user.email ?? 'Colaborador'
      const result = await joinByCode(user, code, displayName)
      setMsg({ kind: 'ok', text: `Te uniste como ${ROLE_LABELS[result.role].toLowerCase()}.` })
      setCode('')
      await onJoined()
    } catch (err) {
      setMsg({ kind: 'err', text: err instanceof Error ? err.message : 'No se pudo unir al grupo.' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <h3 className="mb-1 font-semibold text-on-surface">Unirse con código</h3>
      <p className="mb-3 text-xs text-on-surface-variant">
        Pide al titular su código de invitación (8 letras o números).
      </p>
      <form onSubmit={submit} className="flex flex-col gap-2 sm:flex-row">
        <input
          value={code}
          onChange={(e) => setCode(e.target.value)}
          placeholder="Código de invitación (ej. 7QK9M2ZP)"
          className={inputCls}
          autoComplete="off"
          spellCheck={false}
        />
        <Button type="submit" loading={busy} disabled={!code.trim()}>
          Unirme
        </Button>
      </form>
      {msg && <Feedback msg={msg} />}
    </Card>
  )
}

function CreateGroup({ uid, onCreated }: { uid: string; onCreated: () => Promise<void> }) {
  const { user } = useAuth()
  const { selectHousehold } = useHousehold()
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null)

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!user) return
    setBusy(true)
    setMsg(null)
    try {
      const displayName = user.displayName ?? user.email ?? 'Titular'
      const hid = await createHousehold(uid, name, displayName)
      setName('')
      await onCreated()
      await selectHousehold(hid)
      setMsg({ kind: 'ok', text: 'Grupo creado. Ya es tu grupo activo.' })
    } catch (err) {
      setMsg({ kind: 'err', text: err instanceof Error ? err.message : 'No se pudo crear el grupo.' })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card>
      <h3 className="mb-3 font-semibold text-on-surface">Crear un grupo nuevo</h3>
      <form onSubmit={submit} className="flex flex-col gap-2 sm:flex-row">
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Nombre del hogar (ej. Casa Romero)"
          className={inputCls}
        />
        <Button type="submit" loading={busy} disabled={!name.trim()}>
          Crear
        </Button>
      </form>
      {msg && <Feedback msg={msg} />}
    </Card>
  )
}

function Feedback({ msg }: { msg: { kind: 'ok' | 'err'; text: string } }) {
  return (
    <p
      className={`mt-3 rounded-2xl px-4 py-2.5 text-xs ${
        msg.kind === 'ok'
          ? 'bg-primary-container/60 text-on-primary-container'
          : 'bg-expense/10 text-expense'
      }`}
      role="alert"
    >
      {msg.text}
    </p>
  )
}
