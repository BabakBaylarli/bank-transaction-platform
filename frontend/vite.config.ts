import path from 'node:path';
import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// In development the three services run on separate ports; the proxy makes the
// browser see a single origin (exactly like Caddy does in production) - no CORS needed.
const proxy = {
  '/api/v1/search': 'http://localhost:8083',
  '/api/v1/processing': 'http://localhost:8082',
  '/api': 'http://localhost:8081',
};

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': path.resolve(__dirname, './src') },
  },
  server: { port: 5173, proxy },
  preview: { port: 4173, proxy },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router-dom'],
          charts: ['recharts'],
        },
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setup.ts'],
    css: false,
  },
});
