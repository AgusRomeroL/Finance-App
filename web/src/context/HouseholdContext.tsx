import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { useAuth } from './AuthContext'
import {
  getMyRole,
  getUserDoc,
  listUserHouseholds,
  setActiveHousehold as persistActiveHousehold,
} from '../lib/repository'
import type { HouseholdWithId, Role } from '../lib/types'

interface HouseholdContextValue {
  households: HouseholdWithId[]
  active: HouseholdWithId | null
  /**
   * Rol REAL del usuario en el hogar activo, leído de households/{hid}/roles/{uid}
   * (fuente canónica; el espejo users/{uid}/households se usa como valor
   * optimista mientras carga). La UI bifurca con esto: OWNER captura gastos
   * reales, COLLABORATOR solo lee y propone. null = sin hogar activo o sin rol.
   */
  myRole: Role | null
  loading: boolean
  error: string | null
  reload: () => Promise<void>
  selectHousehold: (hid: string) => Promise<void>
}

const HouseholdContext = createContext<HouseholdContextValue | undefined>(undefined)

export function HouseholdProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const [households, setHouseholds] = useState<HouseholdWithId[]>([])
  const [activeId, setActiveId] = useState<string | null>(null)
  const [myRole, setMyRole] = useState<Role | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    if (!user) return
    setLoading(true)
    setError(null)
    try {
      const [list, userDoc] = await Promise.all([listUserHouseholds(user.uid), getUserDoc(user.uid)])
      setHouseholds(list)
      const preferred = userDoc?.activeHouseholdId
      const valid = preferred && list.some((h) => h.id === preferred) ? preferred : list[0]?.id ?? null
      setActiveId(valid)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'No se pudieron cargar tus grupos.')
    } finally {
      setLoading(false)
    }
  }, [user])

  useEffect(() => {
    if (user) {
      void reload()
    } else {
      setHouseholds([])
      setActiveId(null)
      setLoading(false)
    }
  }, [user, reload])

  // Rol en el hogar activo: valor optimista desde el espejo (households[].role)
  // y verificación contra el doc canónico roles/{uid}. Se recarga al cambiar de
  // hogar activo o de usuario.
  useEffect(() => {
    if (!user || !activeId) {
      setMyRole(null)
      return
    }
    let cancelled = false
    const mirror = households.find((h) => h.id === activeId)?.role ?? null
    setMyRole(mirror)
    getMyRole(activeId, user.uid)
      .then((role) => {
        if (!cancelled) setMyRole(role ?? mirror)
      })
      .catch(() => {
        // Silencioso: nos quedamos con el valor del espejo.
      })
    return () => {
      cancelled = true
    }
  }, [user, activeId, households])

  const selectHousehold = useCallback(
    async (hid: string) => {
      setActiveId(hid)
      if (user) {
        try {
          await persistActiveHousehold(user.uid, hid)
        } catch {
          // Silencioso: la selección local ya se aplicó.
        }
      }
    },
    [user],
  )

  const active = useMemo(
    () => households.find((h) => h.id === activeId) ?? null,
    [households, activeId],
  )

  return (
    <HouseholdContext.Provider
      value={{ households, active, myRole, loading, error, reload, selectHousehold }}
    >
      {children}
    </HouseholdContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useHousehold(): HouseholdContextValue {
  const ctx = useContext(HouseholdContext)
  if (!ctx) throw new Error('useHousehold debe usarse dentro de <HouseholdProvider>')
  return ctx
}
