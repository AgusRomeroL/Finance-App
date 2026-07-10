import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App'
import OfflineBanner from './components/OfflineBanner'

const rootEl = document.getElementById('root')
if (!rootEl) throw new Error('No se encontró el elemento #root')

createRoot(rootEl).render(
  <StrictMode>
    <App />
    {/* Hermano de <App /> (fuera del router) para no tocar App.tsx. */}
    <OfflineBanner />
  </StrictMode>,
)
