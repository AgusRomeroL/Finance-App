import { useState } from 'react'
import { useAuth } from '../context/AuthContext'
import { Button } from '../components/ui'

export default function LoginPage() {
  const { signInWithGoogle } = useAuth()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSignIn() {
    setLoading(true)
    setError(null)
    try {
      await signInWithGoogle()
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e)
      if (msg.includes('popup-closed-by-user') || msg.includes('cancelled-popup-request')) {
        setError('Cancelaste el inicio de sesión.')
      } else if (msg.includes('configuration-not-found') || msg.includes('operation-not-allowed')) {
        setError(
          'El proveedor de Google no está habilitado en Firebase Authentication. Actívalo en la consola.',
        )
      } else {
        setError('No se pudo iniciar sesión. Intenta de nuevo.')
      }
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center bg-gradient-to-b from-brand/5 to-gray-50 px-4">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex flex-col items-center text-center">
          <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-brand text-white shadow-lg">
            <svg viewBox="0 0 32 32" className="h-9 w-9" aria-hidden="true">
              <path
                d="M11 9h10M11 16h10M11 23h6"
                stroke="currentColor"
                strokeWidth="2.4"
                strokeLinecap="round"
              />
            </svg>
          </div>
          <h1 className="text-2xl font-bold text-gray-900">Presupuesto Familiar</h1>
          <p className="mt-2 text-sm text-gray-600">
            Portal de colaboradores. Inicia sesión para participar en el presupuesto de tu hogar.
          </p>
        </div>

        <div className="rounded-2xl border border-gray-200 bg-white p-6 shadow-sm">
          <Button onClick={handleSignIn} loading={loading} className="w-full">
            {!loading && (
              <svg className="h-5 w-5" viewBox="0 0 24 24" aria-hidden="true">
                <path
                  fill="#FFF"
                  d="M22.5 12.2c0-.7-.1-1.4-.2-2H12v3.8h5.9a5 5 0 01-2.2 3.3v2.7h3.6c2.1-2 3.2-4.9 3.2-7.8z"
                />
                <path
                  fill="#FFF"
                  d="M12 23c2.9 0 5.4-1 7.2-2.6l-3.6-2.7c-1 .7-2.3 1.1-3.6 1.1-2.8 0-5.1-1.9-6-4.4H2.3v2.8A11 11 0 0012 23z"
                />
                <path fill="#FFF" d="M6 14.3a6.6 6.6 0 010-4.3V7.2H2.3a11 11 0 000 9.9L6 14.3z" />
                <path
                  fill="#FFF"
                  d="M12 5.4c1.6 0 3 .5 4.1 1.6l3.1-3.1A11 11 0 0012 1a11 11 0 00-9.7 6.2L6 10c.9-2.6 3.2-4.6 6-4.6z"
                />
              </svg>
            )}
            Continuar con Google
          </Button>

          {error && (
            <p className="mt-4 rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700" role="alert">
              {error}
            </p>
          )}
        </div>

        <p className="mt-6 text-center text-xs text-gray-400">
          Solo lectura y propuestas. Los cambios los aprueba el titular desde la app.
        </p>
      </div>
    </div>
  )
}
