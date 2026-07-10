import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon.svg', 'apple-touch-icon.png'],
      manifest: {
        name: 'Presupuesto Familiar',
        short_name: 'Presupuesto',
        description: 'Presupuesto familiar quincenal — consulta y propuestas para colaboradores',
        lang: 'es-MX',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        // Coherente con el theme-color de index.html: verde sembrado en claro,
        // superficie clara (--c-surface #F5FBF3) como fondo del splash.
        theme_color: '#016E3E',
        background_color: '#F5FBF3',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'maskable-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Precache del app shell completo (JS/CSS/HTML/iconos del build).
        globPatterns: ['**/*.{js,css,html,svg,png,ico,woff2}'],
        // SPA: cualquier navegación cae al index.html precacheado.
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/__/],
        // NO se cachean firestore.googleapis.com ni identitytoolkit.googleapis.com:
        // Workbox solo intercepta lo que declara runtimeCaching, y aquí no se
        // declara ninguna ruta — el SDK de Firebase gestiona su propia
        // persistencia (persistentLocalCache en src/lib/firebase.ts).
        // Tampoco hay Google Fonts que cachear (la web usa el stack de sistema).
        runtimeCaching: [],
      },
    }),
  ],
  build: {
    outDir: 'dist',
  },
})
