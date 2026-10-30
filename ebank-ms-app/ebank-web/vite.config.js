import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The gateway is the single entry point (port 9999).
// /api/accounts/**      -> account-service
// /api/transactions/**  -> transaction-service
// /api/transfers/**     -> transaction-service
// /api/chat/**          -> ebank-bot
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:9999',
        changeOrigin: true,
      },
    },
  },
});
