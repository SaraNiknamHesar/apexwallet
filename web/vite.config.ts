import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// This file runs in Node, not in the browser bundle, so the launcher passes the ports in as env vars.
declare const process: { env: Record<string, string | undefined> }

const apiTarget = process.env.APEX_API_URL ?? 'http://localhost:8080'
const webPort = Number(process.env.WEB_PORT ?? 3000)

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: webPort,
    strictPort: true,
    proxy: {
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },
})
