import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// M03 联调：/api 转发到 Gateway（默认本机 8080；后端在 VM 时用 VITE_GATEWAY_TARGET 覆盖）。
const gatewayTarget = process.env.VITE_GATEWAY_TARGET ?? 'http://127.0.0.1:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174,
    host: true,
    proxy: {
      '/api': {
        target: gatewayTarget,
        changeOrigin: true,
      },
    },
  },
})
