import type { Role } from './types'

/**
 * Acceso por rol en la web, en un solo sitio y sin React: lo consumen el guard
 * de rutas (App.tsx), la barra de navegacion (AppLayout.tsx) y las pruebas.
 *
 *   - OWNER ("Dueno") y PAYER ("Administrador") tienen acceso completo: el
 *     escritorio administra el presupuesto real.
 *   - MEMBER ("Colaborador") solo propone y consulta sus propuestas. No ve el
 *     resumen financiero del hogar; App.tsx ademas redirige /dashboard a
 *     /proponer si navega ahi a mano.
 *   - Sin rol (cargando, o sin hogar activo) solo se ofrece Grupos.
 */
export interface NavItem {
  to: string
  label: string
  end: boolean
}

export const NAV_FULL: readonly NavItem[] = [
  { to: '/panel', label: 'Panel', end: false },
  { to: '/capturar', label: 'Capturar', end: false },
  { to: '/calendario', label: 'Calendario', end: false },
  { to: '/ledger', label: 'Historial', end: false },
  { to: '/cuentas', label: 'Cuentas', end: false },
  { to: '/deudas', label: 'Deudas', end: false },
  { to: '/analiticas', label: 'Analíticas', end: false },
  { to: '/', label: 'Grupos', end: true },
]

export const NAV_MEMBER: readonly NavItem[] = [
  { to: '/', label: 'Grupos', end: true },
  { to: '/proponer', label: 'Proponer', end: false },
  { to: '/mis-propuestas', label: 'Mis propuestas', end: false },
]

export const NAV_MINIMAL: readonly NavItem[] = [{ to: '/', label: 'Grupos', end: true }]

/** Rutas que exigen acceso completo (las envuelve RequireFullAccess). */
export const FULL_ACCESS_ROUTES: readonly string[] = [
  '/panel',
  '/capturar',
  '/calendario',
  '/ledger',
  '/cuentas',
  '/deudas',
  '/analiticas',
]

/** Rutas del colaborador, abiertas a cualquier miembro con hogar activo. */
export const MEMBER_ROUTES: readonly string[] = ['/proponer', '/mis-propuestas']

export function hasFullAccess(role: Role | null | undefined): boolean {
  return role === 'OWNER' || role === 'PAYER'
}

export function navForRole(role: Role | null | undefined): readonly NavItem[] {
  if (hasFullAccess(role)) return NAV_FULL
  if (role === 'MEMBER') return NAV_MEMBER
  return NAV_MINIMAL
}

/** A donde se lleva a cada rol tras entrar: el resumen financiero nunca al colaborador. */
export function landingFor(role: Role | null | undefined): string {
  if (hasFullAccess(role)) return '/panel'
  if (role === 'MEMBER') return '/proponer'
  return '/'
}

/** Si un rol puede abrir una ruta. `/dashboard` sigue la misma regla que el panel. */
export function canVisit(role: Role | null | undefined, path: string): boolean {
  if (path === '/') return true
  if (FULL_ACCESS_ROUTES.includes(path) || path === '/dashboard') return hasFullAccess(role)
  if (MEMBER_ROUTES.includes(path)) return role === 'MEMBER' || hasFullAccess(role)
  return false
}
