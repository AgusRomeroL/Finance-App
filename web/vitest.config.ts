import { defineConfig } from 'vitest/config'

// Pruebas minimas de la web (Fase 7): normalizacion dual camelCase/snake_case
// del repositorio y rutas por rol. Corren en Node sin DOM; el SDK de Firebase
// se inicializa al importar el repositorio pero no toca la red.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
  },
})
