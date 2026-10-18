import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // Read .env from the repo root (two folders up), the same file docker-compose and the backend use,
  // so there is only one .env to fill in. Vite only exposes the variables that start with VITE_.
  envDir: '../..',
});
