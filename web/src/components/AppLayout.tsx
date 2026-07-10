import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useHousehold } from '../context/HouseholdContext'
import { ROLE_LABELS } from '../lib/types'

interface NavItem {
  to: string
  label: string
  end: boolean
}

/**
 * Nav de ACCESO COMPLETO (OWNER "Dueño" | PAYER "Administrador"): administra
 * el presupuesto real desde la web. Cuentas/Analíticas llegan en la ola 2 —
 * no se listan enlaces muertos.
 */
const NAV_FULL: NavItem[] = [
  { to: '/panel', label: 'Panel', end: false },
  { to: '/capturar', label: 'Capturar', end: false },
  { to: '/calendario', label: 'Calendario', end: false },
  { to: '/ledger', label: 'Historial', end: false },
  { to: '/', label: 'Grupos', end: true },
]

/**
 * Nav del colaborador (MEMBER). SIN "Resumen" (/dashboard): el colaborador no
 * debe ver el estado financiero del hogar desde la web — solo propone gastos y
 * consulta sus propias propuestas. App.tsx además redirige /dashboard →
 * /proponer si un MEMBER navega ahí a mano.
 */
const NAV_MEMBER: NavItem[] = [
  { to: '/', label: 'Grupos', end: true },
  { to: '/proponer', label: 'Proponer', end: false },
  { to: '/mis-propuestas', label: 'Mis propuestas', end: false },
]

/** Mientras el rol carga (o no hay hogar activo) solo se ofrece Grupos. */
const NAV_MINIMAL: NavItem[] = [{ to: '/', label: 'Grupos', end: true }]

/** Isotipo del "Architectural Ledger" (tres líneas de asiento contable). */
function BrandMark({ className = 'h-10 w-10' }: { className?: string }) {
  return (
    <div
      className={`flex shrink-0 items-center justify-center rounded-full bg-primary-container text-on-primary-container ${className}`}
    >
      <svg viewBox="0 0 32 32" className="h-5 w-5" aria-hidden="true">
        <path
          d="M11 9h10M11 16h10M11 23h6"
          stroke="currentColor"
          strokeWidth="2.6"
          strokeLinecap="round"
        />
      </svg>
    </div>
  )
}

export default function AppLayout() {
  const { user, signOut } = useAuth()
  const { active, myRole } = useHousehold()

  const nav =
    myRole === 'OWNER' || myRole === 'PAYER' ? NAV_FULL : myRole === 'MEMBER' ? NAV_MEMBER : NAV_MINIMAL
  const roleLabel = myRole ? ROLE_LABELS[myRole] : null

  return (
    <div className="flex min-h-full">
      {/* ── Sidebar persistente (solo escritorio lg+) ─────────────────────── */}
      <aside
        className="sticky top-0 hidden h-screen w-60 shrink-0 flex-col bg-surface-1 lg:flex"
        aria-label="Navegación principal"
      >
        {/* Branding: hogar activo + rol */}
        <div className="flex items-center gap-3 px-5 pb-4 pt-6">
          <BrandMark />
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold text-on-surface">
              {active?.name ?? 'Presupuesto Familiar'}
            </p>
            {roleLabel && <p className="truncate text-xs text-on-surface-variant">{roleLabel}</p>}
          </div>
        </div>

        <nav className="flex flex-1 flex-col gap-1 overflow-y-auto px-3 py-2">
          {nav.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                `rounded-full px-4 py-2.5 text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-primary-container text-on-primary-container'
                    : 'text-on-surface-variant hover:bg-surface-2'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        {/* Usuario + salir */}
        <div className="px-5 pb-6 pt-3">
          {user?.email && (
            <p className="mb-2 truncate text-xs text-on-surface-variant" title={user.email}>
              {user.email}
            </p>
          )}
          <button
            onClick={() => void signOut()}
            className="w-full rounded-full bg-surface-2 px-4 py-2.5 text-xs font-medium text-on-surface transition-colors hover:bg-surface-3"
          >
            Salir
          </button>
        </div>
      </aside>

      {/* ── Columna de contenido ──────────────────────────────────────────── */}
      <div className="flex min-h-full min-w-0 flex-1 flex-col">
        {/* Header móvil/tablet: EXACTAMENTE la experiencia actual (<lg). */}
        <header className="sticky top-0 z-10 bg-surface/90 backdrop-blur lg:hidden">
          <div className="mx-auto flex max-w-3xl items-center justify-between gap-3 px-4 pb-1 pt-3">
            <div className="flex min-w-0 items-center gap-3">
              <BrandMark />
              <div className="min-w-0">
                <p className="truncate text-sm font-semibold text-on-surface">
                  {active?.name ?? 'Presupuesto Familiar'}
                </p>
                {user && <p className="truncate text-xs text-on-surface-variant">{user.email}</p>}
              </div>
            </div>
            <button
              onClick={() => void signOut()}
              className="shrink-0 rounded-full px-4 py-2 text-xs font-medium text-on-surface-variant transition-colors hover:bg-surface-2"
            >
              Salir
            </button>
          </div>

          <nav
            className="no-scrollbar mx-auto flex max-w-3xl gap-2 overflow-x-auto px-4 py-2"
            aria-label="Navegación principal"
          >
            {nav.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  `shrink-0 whitespace-nowrap rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                    isActive
                      ? 'bg-primary-container text-on-primary-container'
                      : 'text-on-surface-variant hover:bg-surface-1'
                  }`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
        </header>

        <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-5 lg:max-w-6xl lg:px-10 lg:py-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
