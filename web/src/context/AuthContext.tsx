import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import {
  getRedirectResult,
  onAuthStateChanged,
  signInWithPopup,
  signInWithRedirect,
  signOut as fbSignOut,
  type User,
  type UserCredential,
} from 'firebase/auth'
import { auth, googleProvider } from '../lib/firebase'
import { upsertUser } from '../lib/repository'

interface AuthContextValue {
  user: User | null
  loading: boolean
  signInWithGoogle: () => Promise<void>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

/**
 * Heurística de webview embebido (apps in-app browser: WhatsApp, Instagram,
 * FB, Gmail…). En esos entornos `signInWithPopup` suele fallar o quedarse
 * colgado, así que se va directo a redirect.
 */
function isLikelyEmbeddedWebView(): boolean {
  const ua = navigator.userAgent
  return (
    /\bwv\b/.test(ua) || // Android WebView marker
    /FBAN|FBAV|Instagram|Line\/|Twitter|GSA\//i.test(ua)
  )
}

/** Errores de popup que ameritan reintentar con redirect en vez de fallar. */
function shouldFallbackToRedirect(code: string): boolean {
  return (
    code === 'auth/popup-blocked' ||
    code === 'auth/cancelled-popup-request' ||
    code === 'auth/operation-not-supported-in-this-environment'
  )
}

/**
 * Crea/actualiza users/{uid} tras un login exitoso, SIN convertir un fallo de
 * escritura en un fallo de login: si el popup/redirect ya autenticó al usuario
 * pero el upsert falla (reglas, red), se loguea y se continúa — el usuario SÍ
 * inició sesión y el doc se reintentará en el siguiente login.
 */
async function upsertUserSafe(user: User): Promise<void> {
  try {
    await upsertUser(user)
  } catch (e) {
    console.error('[auth] Login exitoso pero upsertUser falló (se continúa):', e)
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    // Cierra el ciclo del flujo por redirect (móvil/webview): al volver a la
    // app tras signInWithRedirect, aquí llega la credencial. Un error aquí no
    // bloquea la app (onAuthStateChanged sigue mandando el estado real).
    getRedirectResult(auth)
      .then((cred: UserCredential | null) => {
        if (cred?.user) void upsertUserSafe(cred.user)
      })
      .catch((e) => {
        console.error('[auth] getRedirectResult falló:', e)
      })

    const unsub = onAuthStateChanged(auth, (u) => {
      setUser(u)
      setLoading(false)
    })
    return unsub
  }, [])

  async function signInWithGoogle() {
    // Webview embebido: el popup no es viable — redirect directo.
    if (isLikelyEmbeddedWebView()) {
      await signInWithRedirect(auth, googleProvider)
      return // la página navega; el resultado llega vía getRedirectResult.
    }

    let cred: UserCredential
    try {
      cred = await signInWithPopup(auth, googleProvider)
    } catch (e) {
      const code = (e as { code?: string }).code ?? ''
      if (shouldFallbackToRedirect(code)) {
        await signInWithRedirect(auth, googleProvider)
        return
      }
      throw e
    }
    // Login OK: el upsert del perfil NO debe reportarse como fallo de login.
    await upsertUserSafe(cred.user)
  }

  async function signOut() {
    await fbSignOut(auth)
  }

  return (
    <AuthContext.Provider value={{ user, loading, signInWithGoogle, signOut }}>
      {children}
    </AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de <AuthProvider>')
  return ctx
}
