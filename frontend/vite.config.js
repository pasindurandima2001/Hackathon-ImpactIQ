import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  test: {
    environment: 'jsdom',
    include: ['src/**/*.test.{js,jsx}'],
    css: false,
  },
  server: {
    proxy: {
      // VITE_PROXY_TARGET lets a second dev server point at an isolated test backend.
      '/api': process.env.VITE_PROXY_TARGET || 'http://localhost:8080',
    },
  },
})
