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

/**
 * Guard de las rutas del TITULAR: mientras el rol carga muestra loading; un
 * COLLABORATOR es redirigido a Proponer (ya no tiene Resumen); sin hogar
 * activo se pide elegir uno.
 */
function RequireOwner({ children }: { children: ReactNode }) {
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
  if (myRole === 'COLLABORATOR') return <Navigate to="/proponer" replace />
  if (myRole !== 'OWNER') return <LoadingState label="Verificando tu rol…" />
  return <>{children}</>
}

/**
 * Guard de /dashboard (Resumen): el COLLABORATOR ya no debe ver el resumen
 * financiero del hogar — se le redirige a Proponer. El OWNER conserva el
 * acceso (navegación manual), igual que antes.
 *
 * TODO(rules-collaborator): esto solo oculta la UI. Falta endurecer las
 * reglas de Firestore para que el COLLABORATOR tampoco pueda LEER
 * expenses/wallets/quincenas por API directa (hoy `isMember` le concede
 * lectura de todos los datos del hogar en firestore.rules).
 */
function DashboardRoute() {
  const { myRole, loading } = useHousehold()
  if (loading) return <LoadingState />
  if (myRole === 'COLLABORATOR') return <Navigate to="/proponer" replace />
  return <DashboardPage />
}

/**
 * Redirección post-login: una sola vez por sesión, si el usuario entró en "/"
 * y su rol en el hogar activo resulta OWNER, se le lleva a /panel. Un
 * COLLABORATOR se queda en "/" (Grupos) y navega a Proponer / Mis propuestas,
 * y la navegación manual posterior a Grupos nunca se secuestra.
 */
function PostLoginRedirect() {
  const { myRole, loading } = useHousehold()
  const location = useLocation()
  const navigate = useNavigate()
  const done = useRef(false)

  useEffect(() => {
    if (done.current || loading || myRole === null) return
    done.current = true
    if (myRole === 'OWNER' && location.pathname === '/') {
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
          {/* Rutas del titular (OWNER) */}
          <Route
            path="panel"
            element={
              <RequireOwner>
                <OwnerDashboardPage />
              </RequireOwner>
            }
          />
          <Route
            path="capturar"
            element={
              <RequireOwner>
                <CapturePage />
              </RequireOwner>
            }
          />
          <Route
            path="calendario"
            element={
              <RequireOwner>
                <CalendarPage />
              </RequireOwner>
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
