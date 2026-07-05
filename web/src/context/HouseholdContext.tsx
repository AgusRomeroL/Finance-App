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
  getUserDoc,
  listUserHouseholds,
  setActiveHousehold as persistActiveHousehold,
} from '../lib/repository'
import type { HouseholdWithId } from '../lib/types'

interface HouseholdContextValue {
  households: HouseholdWithId[]
  active: HouseholdWithId | null
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
      value={{ households, active, loading, error, reload, selectHousehold }}
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
