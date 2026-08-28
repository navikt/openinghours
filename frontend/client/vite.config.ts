import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Dev: klienten snakker aldri direkte med backend. Alt går via BFF-en på 3000,
// som er den eneste som kjenner API-nøkkelen.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:3000', changeOrigin: true },
      '/me': { target: 'http://localhost:3000', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
  },
});
