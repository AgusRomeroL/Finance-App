import type { ButtonHTMLAttributes, ReactNode } from 'react'

/** Spinner simple. */
export function Spinner({ className = '' }: { className?: string }) {
  return (
    <svg
      className={`animate-spin ${className}`}
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
    </svg>
  )
}

/** Estado de carga a pantalla completa dentro de un contenedor. */
export function LoadingState({ label = 'Cargando…' }: { label?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-16 text-on-surface-variant">
      <Spinner className="h-8 w-8 text-primary" />
      <p className="text-sm">{label}</p>
    </div>
  )
}

/** Estado de error (superficie tonal, sin bordes duros). */
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="flex flex-col items-center justify-center gap-4 rounded-card bg-expense/10 px-6 py-10 text-center">
      <p className="text-sm text-expense">{message}</p>
      {onRetry && (
        <button
          onClick={onRetry}
          className="rounded-full bg-expense/20 px-5 py-2.5 text-sm font-medium text-expense transition-colors hover:bg-expense/30"
        >
          Reintentar
        </button>
      )}
    </div>
  )
}

/** Estado vacío. */
export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-card bg-surface-1 px-6 py-12 text-center">
      <p className="text-sm font-medium text-on-surface">{title}</p>
      {hint && <p className="text-xs text-on-surface-variant">{hint}</p>}
    </div>
  )
}

type ButtonVariant = 'primary' | 'tonal' | 'secondary' | 'ghost'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  loading?: boolean
  children: ReactNode
}

const VARIANTS: Record<ButtonVariant, string> = {
  primary:
    'bg-primary text-on-primary hover:brightness-110 disabled:bg-primary/40 disabled:text-on-primary/70',
  tonal:
    'bg-primary-container text-on-primary-container hover:brightness-105 disabled:opacity-60',
  // Alias legado: "secondary" ahora es tonal sobre superficie.
  secondary: 'bg-surface-2 text-on-surface hover:bg-surface-3 disabled:opacity-60',
  ghost: 'bg-transparent text-primary hover:bg-primary/10 disabled:opacity-60',
}

export function Button({ variant = 'primary', loading, children, className = '', disabled, ...rest }: ButtonProps) {
  return (
    <button
      {...rest}
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center gap-2 rounded-full px-5 py-3 text-sm font-medium transition-all active:scale-[0.98] focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 disabled:cursor-not-allowed ${VARIANTS[variant]} ${className}`}
    >
      {loading && <Spinner className="h-4 w-4" />}
      {children}
    </button>
  )
}

/** Tarjeta contenedora: superficie tonal, radio 28px, sin borde. */
export function Card({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-card bg-surface-1 p-5 ${className}`}>{children}</div>
}

/** Etiqueta eyebrow (mayúsculas + tracking). */
export function Eyebrow({ children, className = '' }: { children: ReactNode; className?: string }) {
  return <p className={`eyebrow ${className}`}>{children}</p>
}

/**
 * Modal genérico (mismo estilo que el modal de edición del Historial):
 * sheet inferior en móvil, tarjeta centrada en sm+. Clic en el fondo cierra
 * (salvo `busy`, para no interrumpir una escritura en curso).
 */
export function Modal({
  title,
  onClose,
  busy,
  children,
}: {
  title: string
  onClose: () => void
  busy?: boolean
  children: ReactNode
}) {
  return (
    <div
      className="fixed inset-0 z-20 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-6"
      role="dialog"
      aria-modal="true"
      aria-label={title}
      onClick={() => (busy ? null : onClose())}
    >
      <div
        className="max-h-full w-full overflow-y-auto rounded-t-card bg-surface p-5 sm:max-w-md sm:rounded-card"
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="mb-4 font-semibold text-on-surface">{title}</h3>
        {children}
      </div>
    </div>
  )
}

/** Clase compartida de inputs/selects tonales (idéntica al Historial). */
export const fieldCls =
  'w-full rounded-2xl bg-surface-1 px-4 py-3 text-sm text-on-surface placeholder:text-on-surface-variant/60 focus:outline-none focus:ring-2 focus:ring-primary/50'

/** Campo etiquetado (eyebrow + control). */
export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="eyebrow mb-1.5 block px-1">{label}</span>
      {children}
    </label>
  )
}

/** Chip/píldora seleccionable (categorías, filtros). */
export function Chip({
  selected,
  onClick,
  children,
  className = '',
}: {
  selected?: boolean
  onClick?: () => void
  children: ReactNode
  className?: string
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={selected}
      className={`shrink-0 whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-primary/50 ${
        selected
          ? 'bg-primary-container text-on-primary-container'
          : 'bg-surface-1 text-on-surface-variant hover:bg-surface-2'
      } ${className}`}
    >
      {children}
    </button>
  )
}
