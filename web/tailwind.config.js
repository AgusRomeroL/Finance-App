/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Verde sembrado de la app Android (#016E3E) como marca base.
        brand: {
          DEFAULT: '#016E3E',
          light: '#0a8a52',
          dark: '#014d2b',
        },
        income: '#0a8a52',
        expense: '#c0392b',
      },
      fontFamily: {
        sans: ['"Google Sans"', 'system-ui', 'Roboto', 'Helvetica', 'Arial', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
