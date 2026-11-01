import { initializeApp } from 'firebase/app'
import { getAuth, GoogleAuthProvider } from 'firebase/auth'
import {
  getFirestore,
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  type Firestore,
} from 'firebase/firestore'

/**
 * Config web de Firebase del proyecto `finance-app-abdf9` (el mismo que la app
 * Android). Son config pública de cliente, no secretos, así que van embebidos:
 * el build de producción (scripts/web/deploy.ps1) no necesita ningún .env. Un
 * .env (ver .env.example) solo sirve para apuntar a otro proyecto.
 *
 * `appId` es el de la app WEB "Presupuesto Web" (registrada el 2026-07-09 con
 * scripts/admin/firebase_setup.py y confirmada ACTIVE el 2026-10-31). Hasta la
 * Fase 8 caía al appId de Android, que el SDK JS acepta pero registra mal.
 */
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY ?? 'AIzaSyBPv01mYVEpURyvHilB982y7ILiFiF2zjw',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN ?? 'finance-app-abdf9.firebaseapp.com',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID ?? 'finance-app-abdf9',
  storageBucket:
    import.meta.env.VITE_FIREBASE_STORAGE_BUCKET ?? 'finance-app-abdf9.firebasestorage.app',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '318390629591',
  appId: import.meta.env.VITE_FIREBASE_APP_ID ?? '1:318390629591:web:860f1bcec6aff331c0ec92',
}

export const app = initializeApp(firebaseConfig)
export const auth = getAuth(app)

/**
 * Firestore con persistencia offline (IndexedDB), coherente con el
 * offline-first de la app Android: las lecturas se sirven desde el cache local
 * sin red y las escrituras se encolan y suben solas al reconectar.
 * `persistentMultipleTabManager` coordina el cache entre pestañas abiertas.
 * Si el navegador no soporta IndexedDB (o la inicialización falla), se cae al
 * cache en memoria por defecto de `getFirestore`; la app sigue funcionando
 * online, solo sin persistencia entre sesiones.
 */
function createFirestore(): Firestore {
  try {
    return initializeFirestore(app, {
      localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
    })
  } catch (err) {
    console.warn('Firestore: sin persistencia offline, usando cache en memoria', err)
    return getFirestore(app)
  }
}

export const db = createFirestore()
export const googleProvider = new GoogleAuthProvider()
