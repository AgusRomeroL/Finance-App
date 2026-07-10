import { useEffect, useMemo, useState } from 'react'
import { useHousehold } from '../context/HouseholdContext'
import { Button, EmptyState, ErrorState, LoadingState } from '../components/ui'
import {
  confirmPlanned,
  deleteExpense,
  listCategories,
  listExpenseAttributions,
  listExpensesByQuincena,
  listMembers,
  listQuincenas,
  listWallets,
  updateExpense,
} from '../lib/repository'
import { dateInputToEpochMs, epochMsToDateInput, formatDate, formatMxn } from '../lib/format'
import type {
  Attribution,
  CategoryWithId,
  ExpenseWithId,
  MemberWithId,
  QuincenaWithId,
  WalletWithId,
} from '../lib/types'

/* ---------------------------------------------------------------------------
 * Historial (/ledger) — OWNER | PAYER.
 *
 * Selector de quincena (todas, desc) + gastos POSTED y PLANNED de la quincena
 * con filtros en CLIENTE (categoría / miembro / texto), tabla en escritorio
 * (lg+) y cards en móvil. Acciones por fila: Confirmar (PLANNED → POSTED con
 * descuento del wallet), Editar (monto/concepto/categoría/fecha/notas — las
 * atribuciones existentes se reenvían tal cual porque updateExpense REEMPLAZA
 * la subcolección) y Eliminar (lápida + reversión de saldo, con confirmación).
 *
 * Las atribuciones de cada gasto se cargan en paralelo al cambiar de quincena:
 * alimentan el filtro por miembro (participa en cualquiera de los dos roles) y
 * el reenvío en la edición. Volúmenes por quincena chicos (decenas de docs).
 * ------------------------------------------------------------------------- */

interface CatalogData {
  quincenas: QuincenaWithId[]
  categories: CategoryWithId[]
  members: MemberWithId[]
  wallets: WalletWithId[]
}

interface EditDraft {
  expense: ExpenseWithId
  amount: string
  concept: string
  categoryId: string
  date: string
  notes: string
}

export default function LedgerPage() {
  const { active, loading: hhLoading } = useHousehold()
  const hid = active?.id ?? null

  const [catalog, setCatalog] = useState<CatalogData | null>(null)
  const [catalogError, setCatalogError] = useState<string | null>(null)
  const [catalogKey, setCatalogKey] = useState(0)

  const [quincenaId, setQuincenaId] = useState('')
  const [expenses, setExpenses] = useState<ExpenseWithId[] | null>(null)
  const [attribsByExpense, setAttribsByExpense] = useState<Map<string, Attribution[]>>(new Map())
  const [listError, setListError] = useState<string | null>(null)
  const [listKey, setListKey] = useState(0)

  // Filtros en cliente
  const [filterCategory, setFilterCategory] = useState('')
  const [filterMember, setFilterMember] = useState('')
  const [filterText, setFilterText] = useState('')

  // Acciones
  const [edit, setEdit] = useState<EditDraft | null>(null)
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)

  // ── Catálogo (quincenas + categorías + miembros + wallets), una vez ──────
  useEffect(() => {
    if (!hid) return
    let cancelled = false
    setCatalog(null)
    setCatalogError(null)
    Promise.all([listQuincenas(hid), listCategories(hid), listMembers(hid), listWallets(hid)])
      .then(([quincenas, categories, members, wallets]) => {
        if (cancelled) return
        setCatalog({ quincenas, categories, members, wallets })
        // Default: la quincena ACTIVE; si no hay, la más reciente.
        const activeQ = quincenas.find((q) => q.status === 'ACTIVE') ?? quincenas[0]
        setQuincenaId((prev) => (prev && quincenas.some((q) => q.id === prev) ? prev : activeQ?.id ?? ''))
      })
      .catch((e) => {
        if (!cancelled) setCatalogError(e instanceof Error ? e.message : 'No se pudo cargar el historial.')
      })
    return () => {
      cancelled = true
    }
  }, [hid, catalogKey])

  // ── Gastos + atribuciones de la quincena seleccionada ────────────────────
  useEffect(() => {
    if (!hid || !quincenaId) return
    let cancelled = false
    setExpenses(null)
    setListError(null)
    ;(async () => {
      try {
        const rows = await listExpensesByQuincena(hid, quincenaId)
        const attribLists = await Promise.all(
          rows.map((e) => listExpenseAttributions(hid, e.id).catch(() => [] as Attribution[])),
        )
        if (cancelled) return
        const map = new Map<string, Attribution[]>()
        rows.forEach((e, i) => map.set(e.id, attribLists[i] ?? []))
        setAttribsByExpense(map)
        setExpenses(rows)
      } catch (e) {
        if (!cancelled) setListError(e instanceof Error ? e.message : 'No se pudieron cargar los movimientos.')
      }
    })()
    return () => {
      cancelled = true
    }
  }, [hid, quincenaId, listKey])

  const categories = catalog?.categories ?? []
  const members = catalog?.members ?? []
  const catMap = useMemo(() => new Map(categories.map((c) => [c.id, c.displayName])), [categories])
  const walletMap = useMemo(
    () => new Map((catalog?.wallets ?? []).map((w) => [w.id, w.displayName])),
    [catalog],
  )

  const filtered = useMemo<ExpenseWithId[]>(() => {
    const text = filterText.trim().toLowerCase()
    return (expenses ?? []).filter((e) => {
      if (filterCategory && e.categoryId !== filterCategory) return false
      if (filterMember) {
        const attribs = attribsByExpense.get(e.id) ?? []
        if (!attribs.some((a) => a.memberId === filterMember)) return false
      }
      if (text) {
        const haystack = `${e.concept} ${e.notes ?? ''}`.toLowerCase()
        if (!haystack.includes(text)) return false
      }
      return true
    })
  }, [expenses, attribsByExpense, filterCategory, filterMember, filterText])

  const totalFiltered = filtered.reduce((s, e) => s + (e.amountMxn ?? 0), 0)

  function reloadList() {
    setConfirmDeleteId(null)
    setListKey((k) => k + 1)
  }

  function openEdit(e: ExpenseWithId) {
    setActionError(null)
    if (!e.paymentMethodId) {
      setActionError('Este gasto no tiene método de pago sincronizado; edítalo desde el teléfono.')
      return
    }
    setEdit({
      expense: e,
      amount: String(e.amountMxn),
      concept: e.concept,
      categoryId: e.categoryId,
      date: epochMsToDateInput(e.occurredAt),
      notes: e.notes ?? '',
    })
  }

  async function onSaveEdit() {
    if (!hid || !edit || busyId) return
    const e = edit.expense
    const amountNum = Number.parseFloat(edit.amount)
    if (!Number.isFinite(amountNum) || amountNum <= 0) {
      setActionError('El monto debe ser mayor que cero.')
      return
    }
    if (!edit.concept.trim()) {
      setActionError('El concepto no puede estar vacío.')
      return
    }
    // Las atribuciones existentes se reenvían tal cual (bps → % exacto):
    // updateExpense reemplaza la subcolección completa.
    const attribs = attribsByExpense.get(e.id) ?? []
    const beneficiaries = attribs
      .filter((a) => a.role === 'BENEFICIARY')
      .map((a) => ({ memberId: a.memberId, percent: a.shareBps / 100 }))
    const payers = attribs
      .filter((a) => a.role === 'PAYER')
      .map((a) => ({ memberId: a.memberId, percent: a.shareBps / 100 }))
    if (beneficiaries.length === 0 || payers.length === 0) {
      setActionError('Este gasto no tiene atribuciones sincronizadas; edítalo desde el teléfono.')
      return
    }
    setBusyId(e.id)
    setActionError(null)
    try {
      await updateExpense(hid, e.id, {
        concept: edit.concept,
        amountMxn: amountNum,
        occurredAt: dateInputToEpochMs(edit.date),
        categoryId: edit.categoryId,
        paymentMethodId: e.paymentMethodId ?? '',
        notes: edit.notes.trim() || undefined,
        beneficiaries,
        payers,
      })
      setEdit(null)
      reloadList()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'No se pudo guardar el gasto.')
    } finally {
      setBusyId(null)
    }
  }

  async function onDelete(expenseId: string) {
    if (!hid || busyId) return
    setBusyId(expenseId)
    setActionError(null)
    try {
      await deleteExpense(hid, expenseId)
      reloadList()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'No se pudo eliminar el gasto.')
    } finally {
      setBusyId(null)
    }
  }

  async function onConfirmPlanned(expenseId: string) {
    if (!hid || busyId) return
    setBusyId(expenseId)
    setActionError(null)
    try {
      await confirmPlanned(hid, expenseId)
      reloadList()
    } catch (err) {
      setActionError(err instanceof Error ? err.message : 'No se pudo confirmar el pago.')
    } finally {
      setBusyId(null)
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
  if (catalogError) return <ErrorState message={catalogError} onRetry={() => setCatalogKey((k) => k + 1)} />
  if (!catalog) return <LoadingState label="Cargando historial…" />
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
          <h2 className="px-1 text-lg font-semibold text-on-surface">Historial</h2>
          <p className="px-1 text-xs text-on-surface-variant">
            Movimientos registrados y planeados por quincena.
          </p>
        </div>
        {expenses && (
          <p className="tnum px-1 text-xs font-semibold text-on-surface-variant">
            {filtered.length} movimiento{filtered.length === 1 ? '' : 's'} · −{formatMxn(totalFiltered)}
          </p>
        )}
      </div>

      {/* Selector de quincena + filtros */}
      <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
        <label className="block">
          <span className="eyebrow mb-1.5 block px-1">Quincena</span>
          <select
            value={quincenaId}
            onChange={(e) => setQuincenaId(e.target.value)}
            className={selectCls}
          >
            {catalog.quincenas.map((q) => (
              <option key={q.id} value={q.id}>
                {q.label || q.id}
                {q.status === 'ACTIVE' ? ' · activa' : ''}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="eyebrow mb-1.5 block px-1">Categoría</span>
          <select
            value={filterCategory}
            onChange={(e) => setFilterCategory(e.target.value)}
            className={selectCls}
          >
            <option value="">Todas</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.displayName}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="eyebrow mb-1.5 block px-1">Miembro</span>
          <select
            value={filterMember}
            onChange={(e) => setFilterMember(e.target.value)}
            className={selectCls}
          >
            <option value="">Todos</option>
            {members.map((m) => (
              <option key={m.id} value={m.id}>
                {m.displayName}
              </option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="eyebrow mb-1.5 block px-1">Buscar</span>
          <input
            value={filterText}
            onChange={(e) => setFilterText(e.target.value)}
            placeholder="Concepto o notas…"
            className={selectCls}
          />
        </label>
      </div>

      {actionError && (
        <p className="rounded-2xl bg-expense/10 px-4 py-3 text-sm text-expense" role="alert">
          {actionError}
        </p>
      )}

      {listError ? (
        <ErrorState message={listError} onRetry={() => setListKey((k) => k + 1)} />
      ) : !expenses ? (
        <LoadingState label="Cargando movimientos…" />
      ) : filtered.length === 0 ? (
        <EmptyState
          title="Sin movimientos"
          hint={
            expenses.length > 0
              ? 'Ningún movimiento coincide con los filtros.'
              : 'Esta quincena no tiene movimientos registrados ni planeados.'
          }
        />
      ) : (
        <>
          {/* ── Tabla (escritorio lg+) ─────────────────────────────────── */}
          <div className="hidden overflow-x-auto rounded-card bg-surface-1 lg:block">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="text-on-surface-variant">
                  <th className="px-4 py-3 font-semibold">Fecha</th>
                  <th className="px-4 py-3 font-semibold">Concepto</th>
                  <th className="px-4 py-3 font-semibold">Categoría</th>
                  <th className="px-4 py-3 font-semibold">Cuenta</th>
                  <th className="px-4 py-3 text-right font-semibold">Monto</th>
                  <th className="px-4 py-3 font-semibold">Estado</th>
                  <th className="px-4 py-3 text-right font-semibold">
                    <span className="sr-only">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((e) => {
                  const busy = busyId === e.id
                  const confirming = confirmDeleteId === e.id
                  return (
                    <tr key={e.id} className="border-t border-surface-2/70">
                      <td className="tnum whitespace-nowrap px-4 py-3 text-on-surface-variant">
                        {formatDate(e.occurredAt)}
                      </td>
                      <td className="max-w-[16rem] px-4 py-3">
                        <p className="truncate font-medium text-on-surface">{e.concept}</p>
                        {e.notes && <p className="truncate text-xs text-on-surface-variant">{e.notes}</p>}
                      </td>
                      <td className="max-w-[10rem] truncate px-4 py-3 text-on-surface-variant">
                        {catMap.get(e.categoryId) ?? 'Sin categoría'}
                      </td>
                      <td className="max-w-[10rem] truncate px-4 py-3 text-on-surface-variant">
                        {(e.paymentMethodId && walletMap.get(e.paymentMethodId)) ?? '—'}
                      </td>
                      <td className="tnum whitespace-nowrap px-4 py-3 text-right font-semibold text-expense">
                        −{formatMxn(e.amountMxn)}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={e.status} />
                      </td>
                      <td className="whitespace-nowrap px-4 py-3 text-right">
                        {confirming ? (
                          <span className="inline-flex items-center gap-2">
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => void onDelete(e.id)}
                              className="rounded-full bg-expense px-3 py-1.5 text-xs font-semibold text-surface transition-all active:scale-95 disabled:opacity-60"
                            >
                              {busy ? 'Eliminando…' : 'Eliminar'}
                            </button>
                            <button
                              type="button"
                              disabled={busy}
                              onClick={() => setConfirmDeleteId(null)}
                              className="rounded-full bg-surface-2 px-3 py-1.5 text-xs font-medium text-on-surface hover:bg-surface-3"
                            >
                              Cancelar
                            </button>
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5">
                            {e.status === 'PLANNED' && (
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => void onConfirmPlanned(e.id)}
                                className="rounded-full bg-primary-container px-3 py-1.5 text-xs font-semibold text-on-primary-container transition-all active:scale-95 disabled:opacity-60"
                              >
                                {busy ? 'Confirmando…' : 'Confirmar'}
                              </button>
                            )}
                            <RowIconButton label={`Editar ${e.concept}`} onClick={() => openEdit(e)}>
                              <EditIcon />
                            </RowIconButton>
                            <RowIconButton
                              label={`Eliminar ${e.concept}`}
                              danger
                              onClick={() => setConfirmDeleteId(e.id)}
                            >
                              <TrashIcon />
                            </RowIconButton>
                          </span>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          {/* ── Cards (móvil / tablet <lg) ─────────────────────────────── */}
          <ul className="space-y-2 lg:hidden">
            {filtered.map((e) => {
              const busy = busyId === e.id
              const confirming = confirmDeleteId === e.id
              return (
                <li key={e.id} className="rounded-card-sm bg-surface-1 px-4 py-3.5">
                  <div className="flex items-center justify-between gap-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-on-surface">{e.concept}</p>
                      <p className="mt-0.5 text-xs text-on-surface-variant">
                        {catMap.get(e.categoryId) ?? 'Sin categoría'} · {formatDate(e.occurredAt)}
                        {e.paymentMethodId && walletMap.get(e.paymentMethodId)
                          ? ` · ${walletMap.get(e.paymentMethodId)}`
                          : ''}
                      </p>
                      {e.notes && <p className="mt-0.5 truncate text-xs text-on-surface-variant/80">{e.notes}</p>}
                    </div>
                    <div className="shrink-0 text-right">
                      <p className="tnum text-sm font-semibold text-expense">−{formatMxn(e.amountMxn)}</p>
                      {e.status === 'PLANNED' && (
                        <div className="mt-1 flex justify-end">
                          <StatusBadge status={e.status} />
                        </div>
                      )}
                    </div>
                  </div>
                  {confirming ? (
                    <div className="mt-3 flex items-center justify-between gap-2 rounded-2xl bg-expense/10 px-3.5 py-2.5">
                      <p className="text-xs font-medium text-expense">
                        {e.status === 'POSTED'
                          ? '¿Eliminar y revertir el saldo del wallet?'
                          : '¿Eliminar este pago planeado?'}
                      </p>
                      <div className="flex shrink-0 gap-2">
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void onDelete(e.id)}
                          className="rounded-full bg-expense px-3.5 py-1.5 text-xs font-semibold text-surface transition-all active:scale-95 disabled:opacity-60"
                        >
                          {busy ? 'Eliminando…' : 'Eliminar'}
                        </button>
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => setConfirmDeleteId(null)}
                          className="rounded-full bg-surface-2 px-3.5 py-1.5 text-xs font-medium text-on-surface hover:bg-surface-3"
                        >
                          Cancelar
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="mt-2.5 flex items-center justify-end gap-1.5">
                      {e.status === 'PLANNED' && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => void onConfirmPlanned(e.id)}
                          className="rounded-full bg-primary-container px-3.5 py-1.5 text-xs font-semibold text-on-primary-container transition-all active:scale-95 disabled:opacity-60"
                        >
                          {busy ? 'Confirmando…' : 'Confirmar'}
                        </button>
                      )}
                      <RowIconButton label={`Editar ${e.concept}`} onClick={() => openEdit(e)}>
                        <EditIcon />
                      </RowIconButton>
                      <RowIconButton
                        label={`Eliminar ${e.concept}`}
                        danger
                        onClick={() => setConfirmDeleteId(e.id)}
                      >
                        <TrashIcon />
                      </RowIconButton>
                    </div>
                  )}
                </li>
              )
            })}
          </ul>
        </>
      )}

      {/* ── Modal de edición ────────────────────────────────────────────── */}
      {edit && (
        <div
          className="fixed inset-0 z-20 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-6"
          role="dialog"
          aria-modal="true"
          aria-label={`Editar ${edit.expense.concept}`}
          onClick={() => (busyId ? null : setEdit(null))}
        >
          <div
            className="max-h-full w-full overflow-y-auto rounded-t-card bg-surface p-5 sm:max-w-md sm:rounded-card"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="mb-4 flex items-center justify-between gap-2">
              <h3 className="font-semibold text-on-surface">Editar movimiento</h3>
              <StatusBadge status={edit.expense.status} />
            </div>
            <div className="space-y-3">
              <label className="block">
                <span className="eyebrow mb-1.5 block px-1">Monto MXN</span>
                <input
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  step="0.01"
                  value={edit.amount}
                  onChange={(e) => setEdit((d) => (d ? { ...d, amount: e.target.value } : d))}
                  className={selectCls}
                />
              </label>
              <label className="block">
                <span className="eyebrow mb-1.5 block px-1">Concepto</span>
                <input
                  value={edit.concept}
                  onChange={(e) => setEdit((d) => (d ? { ...d, concept: e.target.value } : d))}
                  className={selectCls}
                />
              </label>
              <label className="block">
                <span className="eyebrow mb-1.5 block px-1">Categoría</span>
                <select
                  value={edit.categoryId}
                  onChange={(e) => setEdit((d) => (d ? { ...d, categoryId: e.target.value } : d))}
                  className={selectCls}
                >
                  {!categories.some((c) => c.id === edit.categoryId) && (
                    <option value={edit.categoryId}>Sin categoría</option>
                  )}
                  {categories.map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.displayName}
                    </option>
                  ))}
                </select>
              </label>
              <label className="block">
                <span className="eyebrow mb-1.5 block px-1">Fecha</span>
                <input
                  type="date"
                  value={edit.date}
                  onChange={(e) => setEdit((d) => (d ? { ...d, date: e.target.value } : d))}
                  className={selectCls}
                />
              </label>
              <label className="block">
                <span className="eyebrow mb-1.5 block px-1">Notas (opcional)</span>
                <textarea
                  rows={2}
                  value={edit.notes}
                  onChange={(e) => setEdit((d) => (d ? { ...d, notes: e.target.value } : d))}
                  className={`${selectCls} resize-none`}
                />
              </label>
              {actionError && (
                <p className="rounded-2xl bg-expense/10 px-4 py-2.5 text-xs text-expense" role="alert">
                  {actionError}
                </p>
              )}
              <div className="flex justify-end gap-2 pt-1">
                <Button
                  type="button"
                  variant="secondary"
                  disabled={busyId !== null}
                  onClick={() => setEdit(null)}
                >
                  Cancelar
                </Button>
                <Button type="button" loading={busyId === edit.expense.id} onClick={() => void onSaveEdit()}>
                  Guardar
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

const selectCls =
  'w-full rounded-2xl bg-surface-1 px-4 py-3 text-sm text-on-surface placeholder:text-on-surface-variant/60 focus:outline-none focus:ring-2 focus:ring-primary/50'

function StatusBadge({ status }: { status: string }) {
  if (status === 'PLANNED') {
    return (
      <span className="inline-flex items-center rounded-full bg-alert/15 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide text-alert">
        Planeado
      </span>
    )
  }
  return (
    <span className="inline-flex items-center rounded-full bg-surface-2 px-2.5 py-1 text-[11px] font-semibold uppercase tracking-wide text-on-surface-variant">
      Registrado
    </span>
  )
}

function RowIconButton({
  label,
  onClick,
  danger,
  children,
}: {
  label: string
  onClick: () => void
  danger?: boolean
  children: React.ReactNode
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={`flex h-8 w-8 items-center justify-center rounded-full text-on-surface-variant transition-colors ${
        danger ? 'hover:bg-expense/10 hover:text-expense' : 'hover:bg-surface-2 hover:text-on-surface'
      }`}
    >
      {children}
    </button>
  )
}

function EditIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" aria-hidden="true">
      <path
        d="M4 20h4L19.5 8.5a2.1 2.1 0 00-3-3L5 17v3zM14 7l3 3"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function TrashIcon() {
  return (
    <svg viewBox="0 0 24 24" className="h-[18px] w-[18px]" fill="none" aria-hidden="true">
      <path
        d="M5 7h14M10 4h4M9 7v12m6-12v12M7 7l1 13a1 1 0 001 1h6a1 1 0 001-1l1-13"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
