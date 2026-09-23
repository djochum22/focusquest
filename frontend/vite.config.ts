import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: { port: 5173, strictPort: true }, // the backend's CORS allow-list expects :5173
  test: { environment: 'jsdom' },
})
