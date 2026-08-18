import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';
import { fileURLToPath } from 'node:url';

const backendUrl = process.env.SUMBOOKLM_BACKEND_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: 'target/dist',
    emptyOutDir: true,
    sourcemap: false,
  },
  server: {
    port: 5173,
    host: true,
    proxy: {
      '/api': { target: backendUrl, changeOrigin: true },
      '/v3/api-docs': { target: backendUrl, changeOrigin: true },
    },
  },
});
