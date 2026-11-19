import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The managed Replit workflow supplies PORT and BASE_PATH. Keep local Vite
// defaults available so the imported project also runs outside Replit.
const port = Number(process.env.PORT || 5173);
if (!Number.isInteger(port) || port < 1 || port > 65535) {
  throw new Error('PORT must be a valid TCP port.');
}

export default defineConfig({
  plugins: [react()],
  base: process.env.BASE_PATH || '/',
  // Use a modern browser target compatible with the workspace's secured
  // esbuild version and Supabase's current browser client.
  build: { target: 'es2022' },
  esbuild: { target: 'es2022' },
  optimizeDeps: { esbuildOptions: { target: 'es2022' } },
  server: {
    host: '0.0.0.0',
    port,
    strictPort: true,
    allowedHosts: true,
  },
  preview: {
    host: '0.0.0.0',
    port,
    strictPort: true,
    allowedHosts: true,
  },
});
