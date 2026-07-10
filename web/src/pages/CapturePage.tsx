import { useEffect, useMemo, useState } from 'react'
import { useHousehold } from '../context/HouseholdContext'
import { Button, Chip, EmptyState, ErrorState, Eyebrow, LoadingState } from '../components/ui'
import { createExpense, createIncome, listCategories, listMembers, listWallets } from '../lib/repository'
import { dateInputToEpochMs, epochMsToDateInput, formatMxn, youLabel } from '../lib/format'
import type {
  AttributionInput,
  CategoryWithId,
  MemberWithId,
  WalletWithId,
} from '../lib/types'

/* ---------------------------------------------------------------------------
 * Captura real (OWNER | PAYER) — espejo web de la captura Android.
 * Monto héroe + keypad (mismo patrón/px que ProposePage), toggle Gasto/Ingreso,
 * categoría con recientes + búsqueda, wallet con saldo, fecha, notas, y
 * atribución en dos dimensiones: "Beneficia a" visible con % editable
 * (stepper ±5, "Todos" equitativo) y "Pagó" colapsado (un solo miembro al
 * 100%, default el member VINCULADO al usuario o el dueño del wallet). La
 * conversión %→bps (suma exacta 10000) la hace repository.
 *
 * Modo "Planear": si la fecha del gasto es FUTURA se crea con status PLANNED
 * (no toca el saldo; se confirma desde Historial/Calendario o el teléfono).
 * Un INGRESO se registra POSTED en income_source y acredita la cuenta.
 * ------------------------------------------------------------------------- */

const KEYPAD: string[] = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', 'del']
const MAX_INT_DIGITS = 7

function pressKey(prev: string, key: string): string {
  if (key === 'del') return prev.slice(0, -1)
  if (key === '.') {
    if (prev.includes('.')) return prev
    return prev === '' ? '0.' : prev + '.'
  }
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

/** Reparto equitativo en enteros que suma exactamente 100 (los primeros absorben el residuo). */
function equalSplit(ids: string[]): Record<string, number> {
  const n = ids.length
  if (n === 0) return {}
  const base = Math.floor(100 / n)
  const remainder = 100 - base * n
  const out: Record<string, number> = {}
  ids.forEach((id, i) => {
    out[id] = base + (i < remainder ? 1 : 0)
  })
  return out
}

/* Recientes de categoría por hogar, persistidos en localStorage. */
function recentsKey(hid: string): string {
  return `web_recent_categories:${hid}`
}

function readRecents(hid: string): string[] {
  try {
    const raw = localStorage.getItem(recentsKey(hid))
    const parsed: unknown = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === 'string') : []
  } catch {
    return []
  }
}

function pushRecent(hid: string, categoryId: string): void {
  try {
    const next = [categoryId, ...readRecents(hid).filter((id) => id !== categoryId)].slice(0, 6)
    localStorage.setItem(recentsKey(hid), JSON.stringify(next))
  } catch {
    /* localStorage puede fallar (modo privado); los recientes son cosméticos. */
  }
}

interface Catalog {
  categories: CategoryWithId[]
  wallets: WalletWithId[]
  members: MemberWithId[]
}

type CaptureMode = 'expense' | 'income'

export default function CapturePage() {
  const { active, linkedMemberId, loading: hhLoading } = useHousehold()
  const hid = active?.id ?? null

  const [catalog, setCatalog] = useState<Catalog | null>(null)
  const [catalogError, setCatalogError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  // Formulario
  const [mode, setMode] = useState<CaptureMode>('expense')
  const [amount, setAmount] = useState('')
  const [concept, setConcept] = useState('')
  const [notes, setNotes] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [walletId, setWalletId] = useState('')
  const [date, setDate] = useState(() => epochMsToDateInput(Date.now()))
  const [benefShares, setBenefShares] = useState<Record<string, number>>({})
  const [payerId, setPayerId] = useState('')
  const [payerOpen, setPayerOpen] = useState(false)
  const [catSearch, setCatSearch] = useState('')
  const [recents, setRecents] = useState<string[]>([])
  // Ingreso: quién lo recibe (default: member vinculado al usuario).
  const [incomeMemberId, setIncomeMemberId] = useState('')

  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<{ kind: 'ok' | 'err'; text: string } | null>(null)

  // Carga del catálogo (categorías + wallets + miembros) y defaults.
  useEffect(() => {
    if (!hid) return
    let cancelled = false
    setCatalog(null)
    setCatalogError(null)
    Promise.all([listCategories(hid), listWallets(hid), listMembers(hid)])
      .then(([categories, wallets, members]) => {
        if (cancelled) return
        const activeMembers = members.filter((m) => m.isActive)
        const activeWallets = wallets.filter((w) => w.isActive)
        setCatalog({ categories, wallets: activeWallets, members: activeMembers })
        setRecents(readRecents(hid))
        // Defaults: reparto equitativo entre miembros activos; pagador = member
        // VINCULADO al usuario (rol v2) si existe; fallback: dueño del primer
        // wallet, primer adulto pagador o el primero.
        setBenefShares(equalSplit(activeMembers.map((m) => m.id)))
        const linked = activeMembers.find((m) => m.id === linkedMemberId)
        const defaultPayer =
          activeMembers.find((m) => m.role.toUpperCase().includes('PAYER')) ?? activeMembers[0]
        let payer = linked ?? defaultPayer
        const firstWallet = activeWallets[0]
        if (firstWallet) {
          setWalletId(firstWallet.id)
          if (
            !linked &&
            firstWallet.ownerMemberId &&
            activeMembers.some((m) => m.id === firstWallet.ownerMemberId)
          ) {
            payer = activeMembers.find((m) => m.id === firstWallet.ownerMemberId) ?? payer
          }
        }
        setPayerId(payer?.id ?? '')
        setIncomeMemberId((linked ?? activeMembers[0])?.id ?? '')
      })
      .catch((e) => {
        if (!cancelled) {
          setCatalogError(e instanceof Error ? e.message : 'No se pudo cargar el catálogo del hogar.')
        }
      })
    return () => {
      cancelled = true
    }
  }, [hid, reloadKey, linkedMemberId])

  const members = catalog?.members ?? []
  const wallets = catalog?.wallets ?? []
  const categories = catalog?.categories ?? []

  /** Chips de recientes: los usados aquí + relleno con las primeras del catálogo. */
  const recentCategories = useMemo<CategoryWithId[]>(() => {
    if (categories.length === 0) return []
    const byId = new Map(categories.map((c) => [c.id, c]))
    const picked: CategoryWithId[] = []
    for (const id of recents) {
      const c = byId.get(id)
      if (c) picked.push(c)
    }
    for (const c of categories) {
      if (picked.length >= 6) break
      if (!picked.some((p) => p.id === c.id)) picked.push(c)
    }
    return picked
  }, [categories, recents])

  const filteredCategories = useMemo<CategoryWithId[]>(() => {
    const q = catSearch.trim().toLowerCase()
    if (!q) return []
    return categories.filter((c) => c.displayName.toLowerCase().includes(q)).slice(0, 12)
  }, [categories, catSearch])

  const selectedCategory = categories.find((c) => c.id === categoryId) ?? null
  const selectedWallet = wallets.find((w) => w.id === walletId) ?? null
  const payer = members.find((m) => m.id === payerId) ?? null
  const benefTotal = members.reduce((s, m) => s + (benefShares[m.id] ?? 0), 0)

  // Modo "Planear": fecha futura (comparación de strings YYYY-MM-DD) → el
  // gasto se crea PLANNED y NO toca el saldo hasta confirmarse.
  const isFutureDate = date > epochMsToDateInput(Date.now())
  const planned = mode === 'expense' && isFutureDate

  function setShare(memberId: string, delta: number) {
    setBenefShares((prev) => {
      const current = prev[memberId] ?? 0
      const next = Math.max(0, Math.min(100, current + delta))
      return { ...prev, [memberId]: next }
    })
  }

  function selectWallet(w: WalletWithId) {
    setWalletId(w.id)
    // El pagador se autodefine al dueño del wallet (mismo comportamiento que Android).
    if (w.ownerMemberId && members.some((m) => m.id === w.ownerMemberId)) {
      setPayerId(w.ownerMemberId)
    }
  }

  /** Reset del formulario tras registrar (se conservan wallet/pagador/recibe). */
  function resetForm() {
    setAmount('')
    setConcept('')
    setNotes('')
    setCategoryId('')
    setCatSearch('')
    setDate(epochMsToDateInput(Date.now()))
    setBenefShares(equalSplit(members.map((m) => m.id)))
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault()
    if (!hid || busy) return
    const amountNum = Number.parseFloat(amount)
    if (!Number.isFinite(amountNum) || amountNum <= 0) {
      setMsg({ kind: 'err', text: 'Captura un monto mayor a cero con el teclado.' })
      return
    }
    if (!concept.trim()) {
      setMsg({ kind: 'err', text: 'Escribe un concepto.' })
      return
    }
    if (!walletId) {
      setMsg({ kind: 'err', text: mode === 'income' ? 'Selecciona la cuenta destino.' : 'Selecciona un método de pago.' })
      return
    }

    // ── Ingreso ──────────────────────────────────────────────────────────
    if (mode === 'income') {
      if (!incomeMemberId) {
        setMsg({ kind: 'err', text: 'Selecciona quién recibe el ingreso.' })
        return
      }
      setBusy(true)
      setMsg(null)
      try {
        await createIncome(hid, {
          label: concept,
          amountMxn: amountNum,
          receivedAt: dateInputToEpochMs(date),
          memberId: incomeMemberId,
          paymentMethodId: walletId,
        })
        setMsg({ kind: 'ok', text: `Ingreso registrado: ${concept.trim()} por ${formatMxn(amountNum)}.` })
        resetForm()
      } catch (err) {
        setMsg({ kind: 'err', text: err instanceof Error ? err.message : 'No se pudo registrar el ingreso.' })
      } finally {
        setBusy(false)
      }
      return
    }

    // ── Gasto (POSTED, o PLANNED si la fecha es futura) ──────────────────
    if (!categoryId) {
      setMsg({ kind: 'err', text: 'Selecciona una categoría.' })
      return
    }
    const beneficiaries: AttributionInput[] = members
      .filter((m) => (benefShares[m.id] ?? 0) > 0)
      .map((m) => ({ memberId: m.id, percent: benefShares[m.id] ?? 0 }))
    if (beneficiaries.length === 0 || benefTotal !== 100) {
      setMsg({ kind: 'err', text: `"Beneficia a" debe sumar exactamente 100% (va en ${benefTotal}%).` })
      return
    }
    if (!payerId) {
      setMsg({ kind: 'err', text: 'Selecciona quién pagó.' })
      return
    }

    setBusy(true)
    setMsg(null)
    try {
      await createExpense(
        hid,
        {
          concept,
          amountMxn: amountNum,
          occurredAt: dateInputToEpochMs(date),
          categoryId,
          paymentMethodId: walletId,
          notes: notes.trim() || undefined,
          beneficiaries,
          payers: [{ memberId: payerId, percent: 100 }],
        },
        { planned },
      )
      pushRecent(hid, categoryId)
      setRecents(readRecents(hid))
      setMsg({
        kind: 'ok',
        text: planned
          ? `Pago planeado: ${concept.trim()} por ${formatMxn(amountNum)} (se confirma en su fecha).`
          : `Gasto registrado: ${concept.trim()} por ${formatMxn(amountNum)}.`,
      })
      resetForm()
    } catch (err) {
      setMsg({ kind: 'err', text: err instanceof Error ? err.message : 'No se pudo registrar el gasto.' })
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
  if (catalogError) return <ErrorState message={catalogError} onRetry={() => setReloadKey((k) => k + 1)} />
  if (!catalog) return <LoadingState label="Cargando catálogo…" />
  if (members.length === 0) {
    return (
      <EmptyState
        title="El hogar no tiene miembros sincronizados"
        hint="Abre la app del teléfono con conexión para sincronizar los miembros."
      />
    )
  }
  if (wallets.length === 0) {
    return (
      <EmptyState
        title="No hay métodos de pago sincronizados"
        hint="Abre la app del teléfono con conexión para sincronizar tus cuentas."
      />
    )
  }

  const hasDecimals = amount.includes('.')

  return (
    <form onSubmit={submit} className="mx-auto max-w-md space-y-5 pb-8">
      {/* Toggle Gasto / Ingreso */}
      <div className="grid grid-cols-2 gap-1 rounded-full bg-surface-1 p-1" role="group" aria-label="Tipo de movimiento">
        {(
          [
            { value: 'expense', label: 'Gasto' },
            { value: 'income', label: 'Ingreso' },
          ] as const
        ).map((opt) => (
          <button
            key={opt.value}
            type="button"
            onClick={() => {
              setMode(opt.value)
              setMsg(null)
            }}
            aria-pressed={mode === opt.value}
            className={`rounded-full px-4 py-2.5 text-sm font-medium transition-colors ${
              mode === opt.value
                ? opt.value === 'income'
                  ? 'bg-income/15 text-income'
                  : 'bg-primary-container text-on-primary-container'
                : 'text-on-surface-variant hover:bg-surface-2'
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>

      {/* Monto héroe + keypad (mismo patrón que ProposePage) */}
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

      {/* Concepto */}
      <Field label="Concepto">
        <input
          value={concept}
          onChange={(e) => setConcept(e.target.value)}
          placeholder={mode === 'income' ? 'ej. Nómina, Aguinaldo, Venta' : 'ej. Súper, Gasolina, Colegiatura'}
          className={inputCls}
        />
      </Field>

      {/* Categoría: recientes + búsqueda (solo gasto) */}
      {mode === 'expense' && (
      <div>
        <Eyebrow className="mb-2 px-1">Categoría</Eyebrow>
        <div className="no-scrollbar -mx-4 flex gap-2 overflow-x-auto px-4 pb-2">
          {recentCategories.map((c) => (
            <Chip key={c.id} selected={categoryId === c.id} onClick={() => setCategoryId(c.id)}>
              {c.displayName}
            </Chip>
          ))}
        </div>
        <input
          value={catSearch}
          onChange={(e) => setCatSearch(e.target.value)}
          placeholder="Buscar categoría…"
          aria-label="Buscar categoría"
          className={inputCls}
        />
        {filteredCategories.length > 0 && (
          <ul className="mt-2 max-h-56 space-y-1 overflow-y-auto">
            {filteredCategories.map((c) => (
              <li key={c.id}>
                <button
                  type="button"
                  onClick={() => {
                    setCategoryId(c.id)
                    setCatSearch('')
                  }}
                  className={`w-full rounded-2xl px-4 py-2.5 text-left text-sm transition-colors ${
                    categoryId === c.id
                      ? 'bg-primary-container text-on-primary-container'
                      : 'bg-surface-1 text-on-surface hover:bg-surface-2'
                  }`}
                >
                  {c.displayName}
                </button>
              </li>
            ))}
          </ul>
        )}
        {catSearch.trim() !== '' && filteredCategories.length === 0 && (
          <p className="mt-2 px-1 text-xs text-on-surface-variant">Sin coincidencias.</p>
        )}
        {selectedCategory && (
          <p className="mt-2 px-1 text-xs text-on-surface-variant">
            Seleccionada: <span className="font-semibold text-on-surface">{selectedCategory.displayName}</span>
          </p>
        )}
      </div>
      )}

      {/* Wallet / método de pago con saldo */}
      <div>
        <Eyebrow className="mb-2 px-1">{mode === 'income' ? 'Cuenta destino' : 'Método de pago'}</Eyebrow>
        <ul className="space-y-1.5">
          {wallets.map((w) => {
            const selected = walletId === w.id
            return (
              <li key={w.id}>
                <button
                  type="button"
                  onClick={() => selectWallet(w)}
                  aria-pressed={selected}
                  className={`flex w-full items-center justify-between gap-3 rounded-2xl px-4 py-3 text-left transition-colors ${
                    selected
                      ? 'bg-primary-container text-on-primary-container'
                      : 'bg-surface-1 text-on-surface hover:bg-surface-2'
                  }`}
                >
                  <span className="min-w-0">
                    <span className="block truncate text-sm font-medium">{w.displayName}</span>
                    {w.last4 && <span className="block text-xs opacity-70">•••• {w.last4}</span>}
                  </span>
                  <span className="tnum shrink-0 text-sm font-semibold">
                    {formatMxn(w.currentBalanceMxn)}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      </div>

      {/* Fecha */}
      <Field label="Fecha">
        <input type="date" value={date} onChange={(e) => setDate(e.target.value)} className={inputCls} />
      </Field>
      {planned && (
        <p className="rounded-2xl bg-alert/10 px-4 py-3 text-xs font-medium text-alert" role="status">
          Fecha futura: se registrará como <span className="font-semibold">pago planeado</span> (PLANNED).
          No afecta el saldo hasta que lo confirmes en Historial o Calendario.
        </p>
      )}

      {/* Notas (opcional, solo gasto — income_source no tiene campo de notas) */}
      {mode === 'expense' && (
        <Field label="Notas (opcional)">
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={2}
            placeholder="Detalle opcional del gasto…"
            className={`${inputCls} resize-none`}
          />
        </Field>
      )}

      {/* Recibe (solo ingreso): member obligatorio para el sync de Android */}
      {mode === 'income' && (
        <div className="rounded-card bg-surface-1 p-4">
          <Eyebrow className="mb-3">Recibe</Eyebrow>
          <div className="no-scrollbar -mx-1 flex gap-2 overflow-x-auto px-1">
            {members.map((m) => (
              <Chip key={m.id} selected={incomeMemberId === m.id} onClick={() => setIncomeMemberId(m.id)}>
                {youLabel(m.displayName, m.id, linkedMemberId)}
              </Chip>
            ))}
          </div>
        </div>
      )}

      {/* Beneficia a: % editable por miembro (solo gasto) */}
      {mode === 'expense' && (
      <div className="rounded-card bg-surface-1 p-4">
        <div className="mb-3 flex items-center justify-between gap-2">
          <Eyebrow>Beneficia a</Eyebrow>
          <div className="flex items-center gap-2">
            <span
              className={`tnum text-xs font-semibold ${
                benefTotal === 100 ? 'text-income' : 'text-expense'
              }`}
            >
              {benefTotal}%
            </span>
            <button
              type="button"
              onClick={() => setBenefShares(equalSplit(members.map((m) => m.id)))}
              className="rounded-full bg-surface-2 px-3 py-1.5 text-xs font-medium text-on-surface transition-colors hover:bg-surface-3"
            >
              Todos
            </button>
          </div>
        </div>
        <ul className="space-y-2">
          {members.map((m) => {
            const pct = benefShares[m.id] ?? 0
            return (
              <li key={m.id} className="flex items-center justify-between gap-3">
                <p className={`min-w-0 truncate text-sm ${pct > 0 ? 'font-medium text-on-surface' : 'text-on-surface-variant'}`}>
                  {youLabel(m.displayName, m.id, linkedMemberId)}
                </p>
                <div className="flex shrink-0 items-center gap-1.5">
                  <StepperButton label={`Quitar 5% a ${m.displayName}`} onClick={() => setShare(m.id, -5)} disabled={pct <= 0}>
                    −
                  </StepperButton>
                  <span className="tnum w-11 text-center text-sm font-semibold text-on-surface">{pct}%</span>
                  <StepperButton label={`Sumar 5% a ${m.displayName}`} onClick={() => setShare(m.id, 5)} disabled={pct >= 100}>
                    +
                  </StepperButton>
                </div>
              </li>
            )
          })}
        </ul>
        {benefTotal !== 100 && (
          <p className="mt-2 text-xs font-medium text-expense">La suma debe ser exactamente 100%.</p>
        )}
      </div>
      )}

      {/* Pagó: colapsado, un miembro al 100% (solo gasto) */}
      {mode === 'expense' && (
      <div className="rounded-card bg-surface-1 p-4">
        <button
          type="button"
          onClick={() => setPayerOpen((o) => !o)}
          aria-expanded={payerOpen}
          className="flex w-full items-center justify-between gap-2"
        >
          <Eyebrow>Pagó</Eyebrow>
          <span className="text-sm font-medium text-on-surface">
            {payer ? youLabel(payer.displayName, payer.id, linkedMemberId) : '—'} · 100%
            <span className="ml-2 text-xs text-on-surface-variant">{payerOpen ? '▲' : '▼'}</span>
          </span>
        </button>
        {payerOpen && (
          <div className="no-scrollbar -mx-1 mt-3 flex gap-2 overflow-x-auto px-1">
            {members.map((m) => (
              <Chip key={m.id} selected={payerId === m.id} onClick={() => setPayerId(m.id)}>
                {youLabel(m.displayName, m.id, linkedMemberId)}
              </Chip>
            ))}
          </div>
        )}
        {selectedWallet?.ownerMemberId && selectedWallet.ownerMemberId === payerId && (
          <p className="mt-2 text-xs text-on-surface-variant">
            Definido por el dueño de {selectedWallet.displayName}.
          </p>
        )}
      </div>
      )}

      <Button type="submit" loading={busy} className="w-full py-3.5">
        {mode === 'income' ? 'Registrar ingreso' : planned ? 'Planear pago' : 'Registrar gasto'}
      </Button>

      {msg && (
        <p
          className={`rounded-2xl px-4 py-3 text-sm ${
            msg.kind === 'ok'
              ? 'bg-primary-container/60 text-on-primary-container'
              : 'bg-expense/10 text-expense'
          }`}
          role="alert"
        >
          {msg.text}
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

function StepperButton({
  label,
  onClick,
  disabled,
  children,
}: {
  label: string
  onClick: () => void
  disabled?: boolean
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-2 text-base font-medium text-on-surface transition-all hover:bg-surface-3 active:scale-95 disabled:opacity-40"
    >
      {children}
    </button>
  )
}
