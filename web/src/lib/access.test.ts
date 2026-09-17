import { describe, expect, it } from 'vitest'
import {
  FULL_ACCESS_ROUTES,
  MEMBER_ROUTES,
  NAV_FULL,
  NAV_MEMBER,
  NAV_MINIMAL,
  canVisit,
  hasFullAccess,
  landingFor,
  navForRole,
} from './access'
import { ROLE_LABELS, normalizeRole } from './types'

describe('rutas por rol', () => {
  it('OWNER y PAYER tienen acceso completo; MEMBER y sin rol no', () => {
    expect(hasFullAccess('OWNER')).toBe(true)
    expect(hasFullAccess('PAYER')).toBe(true)
    expect(hasFullAccess('MEMBER')).toBe(false)
    expect(hasFullAccess(null)).toBe(false)
    expect(hasFullAccess(undefined)).toBe(false)
  })

  it('la barra de navegacion depende del rol', () => {
    expect(navForRole('OWNER')).toBe(NAV_FULL)
    expect(navForRole('PAYER')).toBe(NAV_FULL)
    expect(navForRole('MEMBER')).toBe(NAV_MEMBER)
    expect(navForRole(null)).toBe(NAV_MINIMAL)
  })

  it('el colaborador nunca ve el resumen financiero en su barra', () => {
    const rutas = NAV_MEMBER.map((i) => i.to)
    expect(rutas).not.toContain('/dashboard')
    expect(rutas).not.toContain('/panel')
    for (const r of FULL_ACCESS_ROUTES) expect(rutas).not.toContain(r)
    expect(rutas).toEqual(['/', '/proponer', '/mis-propuestas'])
  })

  it('la barra completa cubre todas las rutas de acceso completo mas Grupos', () => {
    const rutas = NAV_FULL.map((i) => i.to)
    for (const r of FULL_ACCESS_ROUTES) expect(rutas).toContain(r)
    expect(rutas).toContain('/')
    expect(NAV_FULL.find((i) => i.to === '/')?.end).toBe(true)
  })

  it('la ruta de llegada tras entrar', () => {
    expect(landingFor('OWNER')).toBe('/panel')
    expect(landingFor('PAYER')).toBe('/panel')
    expect(landingFor('MEMBER')).toBe('/proponer')
    expect(landingFor(null)).toBe('/')
  })

  it('canVisit refleja el guard de App.tsx', () => {
    for (const r of FULL_ACCESS_ROUTES) {
      expect(canVisit('OWNER', r)).toBe(true)
      expect(canVisit('PAYER', r)).toBe(true)
      expect(canVisit('MEMBER', r)).toBe(false)
      expect(canVisit(null, r)).toBe(false)
    }
    expect(canVisit('MEMBER', '/dashboard')).toBe(false)
    expect(canVisit('OWNER', '/dashboard')).toBe(true)
    for (const r of MEMBER_ROUTES) {
      expect(canVisit('MEMBER', r)).toBe(true)
      expect(canVisit('OWNER', r)).toBe(true)
      expect(canVisit(null, r)).toBe(false)
    }
    expect(canVisit(null, '/')).toBe(true)
    expect(canVisit('OWNER', '/no-existe')).toBe(false)
  })
})

describe('normalizacion del rol del wire', () => {
  it('acepta el alias legacy COLLABORATOR y cae a MEMBER por defecto', () => {
    expect(normalizeRole('OWNER')).toBe('OWNER')
    expect(normalizeRole('payer')).toBe('PAYER')
    expect(normalizeRole('COLLABORATOR')).toBe('MEMBER')
    expect(normalizeRole('MEMBER')).toBe('MEMBER')
    expect(normalizeRole('')).toBe('MEMBER')
    expect(normalizeRole(undefined)).toBe('MEMBER')
    expect(normalizeRole('ADMIN')).toBe('MEMBER')
  })

  it('cada rol tiene etiqueta en espanol', () => {
    expect(ROLE_LABELS.OWNER).toBe('Dueño')
    expect(ROLE_LABELS.PAYER).toBe('Administrador')
    expect(ROLE_LABELS.MEMBER).toBe('Colaborador')
  })
})
