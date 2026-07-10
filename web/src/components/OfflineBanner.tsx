import { useEffect, useState } from 'react'

/**
 * Franja discreta que aparece pegada al borde inferior cuando el navegador
 * pierde la conexión. Las escrituras siguen funcionando: Firestore las encola
 * en el cache persistente (ver src/lib/firebase.ts) y las sube al reconectar.
 *
 * Montaje: se renderiza como HERMANO de <App /> desde src/main.tsx (fuera del
 * router), para no tocar App.tsx ni AppLayout.tsx. Es position:fixed, así que
 * no participa del layout del árbol de la app. Si en el futuro se prefiere
 * dentro del layout, basta moverlo al final de AppLayout.
 */
export default function OfflineBanner() {
  const [online, setOnline] = useState<boolean>(() => navigator.onLine)

  useEffect(() => {
    const handleOnline = () => setOnline(true)
    const handleOffline = () => setOnline(false)
    window.addEventListener('online', handleOnline)
    window.addEventListener('offline', handleOffline)
    return () => {
      window.removeEventListener('online', handleOnline)
      window.removeEventListener('offline', handleOffline)
    }
  }, [])

  if (online) return null

  return (
    <div
      role="status"
      aria-live="polite"
      className="fixed inset-x-0 bottom-0 z-50 bg-surface-3/95 px-4 pt-2 text-center text-xs font-medium text-on-surface backdrop-blur"
      style={{ paddingBottom: 'max(0.5rem, env(safe-area-inset-bottom))' }}
    >
      Sin conexión — los cambios se guardarán al reconectar
    </div>
  )
}
