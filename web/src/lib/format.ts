/** Utilidades de formato para MXN y fechas (America/Mexico_City). */

const MXN = new Intl.NumberFormat('es-MX', {
  style: 'currency',
  currency: 'MXN',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

export function formatMxn(amount: number): string {
  if (!Number.isFinite(amount)) return '$0.00'
  return MXN.format(amount)
}

const DATE_FMT = new Intl.DateTimeFormat('es-MX', {
  timeZone: 'America/Mexico_City',
  day: '2-digit',
  month: 'short',
  year: 'numeric',
})

/** Recibe epoch millis, devuelve fecha legible en zona MX. */
export function formatDate(epochMs: number): string {
  if (!Number.isFinite(epochMs)) return '—'
  return DATE_FMT.format(new Date(epochMs))
}

/** Convierte un value de <input type="date"> (YYYY-MM-DD) a epoch millis (mediodía MX para evitar corrimientos). */
export function dateInputToEpochMs(value: string): number {
  // Interpretamos la fecha a mediodía local para evitar saltos de día por TZ.
  const [y, m, d] = value.split('-').map(Number)
  return new Date(y, (m ?? 1) - 1, d ?? 1, 12, 0, 0).getTime()
}

/** epoch millis -> YYYY-MM-DD para prellenar <input type="date">. */
export function epochMsToDateInput(epochMs: number): string {
  const dt = new Date(epochMs)
  const y = dt.getFullYear()
  const m = String(dt.getMonth() + 1).padStart(2, '0')
  const d = String(dt.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
