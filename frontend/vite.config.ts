import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: false,
      workbox: {
        globPatterns: ['**/*.{js,css,html,ico,png,svg}'],
        runtimeCaching: []
      }
    })
  ],
  server: {
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.{test,spec}.{ts,tsx}'],
    // Multi-step tests (typing across several fields, opening an antd Modal, awaiting a
    // network round trip) comfortably clear vitest's 5000ms default locally, but under
    // --coverage's v8 instrumentation on a loaded CI runner they've intermittently tipped
    // over it — different tests each run, the signature of a shared timing margin rather
    // than a broken test. 10s keeps a real hang failing fast while giving CI headroom.
    testTimeout: 10000,
    coverage: {
      provider: 'v8',
      all: true,
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/**/*.{test,spec}.{ts,tsx}',
        'src/**/*.d.ts',
        'src/main.tsx',
        'src/vite-env.d.ts',
        'src/test/**',
        'src/types/**',
      ],
      reporter: ['text-summary', 'text', 'html', 'json-summary'],
      // Nested under `thresholds` per Vitest 1.x — flat keys here are silently ignored
      // and were never actually enforcing a gate on `pnpm test:coverage`.
      thresholds: {
        lines: 80,
        statements: 80,
        branches: 75,
        functions: 55,
      },
    },
  }
})
