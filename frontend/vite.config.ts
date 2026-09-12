import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Proxies every backend-owned path to the ingestion service so the browser
// sees one origin. That keeps the GitHub OAuth session cookie working
// without any CORS dance in dev, and mirrors how prod should be fronted.
const BACKEND_TARGET = process.env.BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/debug': { target: BACKEND_TARGET, changeOrigin: true },
      '/api': { target: BACKEND_TARGET, changeOrigin: true },
      '/home': { target: BACKEND_TARGET, changeOrigin: true },
      '/health': { target: BACKEND_TARGET, changeOrigin: true },
      '/oauth2': { target: BACKEND_TARGET, changeOrigin: true },
      '/login': { target: BACKEND_TARGET, changeOrigin: true },
      '/logout': { target: BACKEND_TARGET, changeOrigin: true },
    },
  },
});
