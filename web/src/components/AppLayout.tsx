import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useHousehold } from '../context/HouseholdContext'

const NAV = [
  { to: '/', label: 'Grupos', end: true },
  { to: '/dashboard', label: 'Resumen', end: false },
  { to: '/proponer', label: 'Proponer', end: false },
  { to: '/mis-propuestas', label: 'Mis propuestas', end: false },
]

export default function AppLayout() {
  const { user, signOut } = useAuth()
  const { active } = useHousehold()

  return (
    <div className="flex min-h-full flex-col">
      <header className="sticky top-0 z-10 bg-surface/90 backdrop-blur">
        <div className="mx-auto flex max-w-3xl items-center justify-between gap-3 px-4 pb-1 pt-3">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-primary-container text-on-primary-container">
              <svg viewBox="0 0 32 32" className="h-5 w-5" aria-hidden="true">
                <path
                  d="M11 9h10M11 16h10M11 23h6"
                  stroke="currentColor"
                  strokeWidth="2.6"
                  strokeLinecap="round"
                />
              </svg>
            </div>
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
          {NAV.map((item) => (
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

      <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-5">
        <Outlet />
      </main>
    </div>
  )
}
