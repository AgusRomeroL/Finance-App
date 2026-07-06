import { initializeApp } from 'firebase/app'
import { getAuth, GoogleAuthProvider } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'

/**
 * Config web de Firebase del proyecto `finance-app-abdf9` (el mismo que la app
 * Android). Los valores por defecto se extrajeron de app/google-services.json;
 * son config pública de cliente, no secretos. Se pueden sobrescribir vía .env
 * (ver .env.example).
 *
 * OJO: `appId` por defecto es el de la app Android. Para producción registra
 * una app Web en la consola y define VITE_FIREBASE_APP_ID con el appId web
 * (formato 1:318390629591:web:XXXX). Auth por popup y Firestore funcionan igual,
 * pero conviene tener el appId web correcto para Analytics y el registro.
 */
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY ?? 'AIzaSyBPv01mYVEpURyvHilB982y7ILiFiF2zjw',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN ?? 'finance-app-abdf9.firebaseapp.com',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID ?? 'finance-app-abdf9',
  storageBucket:
    import.meta.env.VITE_FIREBASE_STORAGE_BUCKET ?? 'finance-app-abdf9.firebasestorage.app',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '318390629591',
  // Placeholder: appId de Android. Reemplaza con el appId WEB en .env para prod.
  appId:
    import.meta.env.VITE_FIREBASE_APP_ID ??
    '1:318390629591:android:0c48524ea7923757c0ec92',
}

export const app = initializeApp(firebaseConfig)
export const auth = getAuth(app)
export const db = getFirestore(app)
export const googleProvider = new GoogleAuthProvider()
