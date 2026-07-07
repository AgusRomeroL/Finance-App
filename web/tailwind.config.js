/** @type {import('tailwindcss').Config} */

/** Helper: color token respaldado por variable CSS (permite /alpha de Tailwind). */
const v = (name) => `rgb(var(--c-${name}) / <alpha-value>)`

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'media',
  theme: {
    extend: {
      colors: {
        // Esquema tonal "Architectural Ledger" (seed #016E3E), definido como
        // variables CSS en src/index.css con variantes claro/oscuro por `media`.
        primary: v('primary'),
        'on-primary': v('on-primary'),
        'primary-container': v('primary-container'),
        'on-primary-container': v('on-primary-container'),
        surface: v('surface'),
        'surface-1': v('surface-1'),
        'surface-2': v('surface-2'),
        'surface-3': v('surface-3'),
        'on-surface': v('on-surface'),
        'on-surface-variant': v('on-surface-variant'),
        outline: v('outline'),
        // Semánticos financieros (ingreso verde / gasto rosa-rojo suave / alerta ámbar).
        income: v('income'),
        expense: v('expense'),
        alert: v('alert'),
        // Alias legado (verde sembrado de la app Android).
        brand: v('primary'),
      },
      borderRadius: {
        card: '28px',
        'card-sm': '24px',
      },
      fontFamily: {
        sans: [
          'system-ui',
          '-apple-system',
          '"Segoe UI"',
          'Roboto',
          '"Helvetica Neue"',
          'Arial',
          'sans-serif',
        ],
      },
    },
  },
  plugins: [],
}
