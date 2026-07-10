import { useEffect, useRef, type ReactNode } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import { HouseholdProvider, useHousehold } from './context/HouseholdContext'
import { EmptyState, LoadingState } from './components/ui'
import AppLayout from './components/AppLayout'
import LoginPage from './pages/LoginPage'
import GroupsPage from './pages/GroupsPage'
import DashboardPage from './pages/DashboardPage'
import ProposePage from './pages/ProposePage'
import MyProposalsPage from './pages/MyProposalsPage'
import OwnerDashboardPage from './pages/OwnerDashboardPage'
import CapturePage from './pages/CapturePage'
import CalendarPage from './pages/CalendarPage'
import LedgerPage from './pages/LedgerPage'
import AccountsPage from './pages/AccountsPage'
import DebtsPage from './pages/DebtsPage'
import AnalyticsPage from './pages/AnalyticsPage'

/**
 * Guard de las rutas de ACCESO COMPLETO (rol v2): pasan OWNER ("Dueño") y
 * PAYER ("Administrador" — escribe el ledger igual que el dueño). Un MEMBER
 * (colaborador) es redirigido a Proponer; sin hogar activo se pide elegir uno.
 */
function RequireFullAccess({ children }: { children: ReactNode }) {
  const { active, myRole, loading } = useHousehold()
  if (loading) return <LoadingState />
  if (!active) {
    return (
      <EmptyState
        title="Selecciona un grupo primero"
        hint="Ve a la pestaña Grupos y elige (o crea) un grupo activo."
      />
    )
  }
  if (myRole === 'MEMBER') return <Navigate to="/proponer" replace />
  if (myRole !== 'OWNER' && myRole !== 'PAYER') return <LoadingState label="Verificando tu rol…" />
  return <>{children}</>
}

/**
 * Guard de /dashboard (Resumen): el MEMBER no debe ver el resumen financiero
 * del hogar — se le redirige a Proponer. OWNER y PAYER conservan el acceso
 * (navegación manual), igual que antes.
 *
 * TODO(rules-collaborator): esto solo oculta la UI. Falta endurecer las
 * reglas de Firestore para que el MEMBER tampoco pueda LEER
 * expenses/wallets/quincenas por API directa (hoy `isMember` le concede
 * lectura de todos los datos del hogar en firestore.rules).
 */
function DashboardRoute() {
  const { myRole, loading } = useHousehold()
  if (loading) return <LoadingState />
  if (myRole === 'MEMBER') return <Navigate to="/proponer" replace />
  return <DashboardPage />
}

/**
 * Redirección post-login: una sola vez por sesión, si el usuario entró en "/"
 * y su rol en el hogar activo resulta OWNER o PAYER, se le lleva a /panel. Un
 * MEMBER se queda en "/" (Grupos) y navega a Proponer / Mis propuestas, y la
 * navegación manual posterior a Grupos nunca se secuestra.
 */
function PostLoginRedirect() {
  const { myRole, loading } = useHousehold()
  const location = useLocation()
  const navigate = useNavigate()
  const done = useRef(false)

  useEffect(() => {
    if (done.current || loading || myRole === null) return
    done.current = true
    if ((myRole === 'OWNER' || myRole === 'PAYER') && location.pathname === '/') {
      navigate('/panel', { replace: true })
    }
  }, [myRole, loading, location.pathname, navigate])

  return null
}

function AuthedApp() {
  return (
    <HouseholdProvider>
      <PostLoginRedirect />
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<GroupsPage />} />
          {/* Rutas de acceso completo (OWNER | PAYER) */}
          <Route
            path="panel"
            element={
              <RequireFullAccess>
                <OwnerDashboardPage />
              </RequireFullAccess>
            }
          />
          <Route
            path="capturar"
            element={
              <RequireFullAccess>
                <CapturePage />
              </RequireFullAccess>
            }
          />
          <Route
            path="calendario"
            element={
              <RequireFullAccess>
                <CalendarPage />
              </RequireFullAccess>
            }
          />
          <Route
            path="ledger"
            element={
              <RequireFullAccess>
                <LedgerPage />
              </RequireFullAccess>
            }
          />
          <Route
            path="cuentas"
            element={
              <RequireFullAccess>
                <AccountsPage />
              </RequireFullAccess>
            }
          />
          <Route
            path="deudas"
            element={
              <RequireFullAccess>
                <DebtsPage />
              </RequireFullAccess>
            }
          />
          <Route
            path="analiticas"
            element={
              <RequireFullAccess>
                <AnalyticsPage />
              </RequireFullAccess>
            }
          />
          {/* Rutas del colaborador */}
          <Route path="dashboard" element={<DashboardRoute />} />
          <Route path="proponer" element={<ProposePage />} />
          <Route path="mis-propuestas" element={<MyProposalsPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </HouseholdProvider>
  )
}

function Gate() {
  const { user, loading } = useAuth()
  if (loading) {
    return (
      <div className="flex min-h-full items-center justify-center">
        <LoadingState label="Iniciando…" />
      </div>
    )
  }
  return user ? <AuthedApp /> : <LoginPage />
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Gate />
      </BrowserRouter>
    </AuthProvider>
  )
}
