var _a;
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
// Proxies every backend-owned path to the ingestion service so the browser
// sees one origin. That keeps the GitHub OAuth session cookie working
// without any CORS dance in dev, and mirrors how prod should be fronted.
var BACKEND_TARGET = (_a = process.env.BACKEND_URL) !== null && _a !== void 0 ? _a : 'http://localhost:8080';
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
