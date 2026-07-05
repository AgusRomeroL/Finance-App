import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './context/AuthContext'
import { HouseholdProvider } from './context/HouseholdContext'
import { LoadingState } from './components/ui'
import AppLayout from './components/AppLayout'
import LoginPage from './pages/LoginPage'
import GroupsPage from './pages/GroupsPage'
import DashboardPage from './pages/DashboardPage'
import ProposePage from './pages/ProposePage'

function AuthedApp() {
  return (
    <HouseholdProvider>
      <Routes>
        <Route element={<AppLayout />}>
          <Route index element={<GroupsPage />} />
          <Route path="dashboard" element={<DashboardPage />} />
          <Route path="proponer" element={<ProposePage />} />
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
