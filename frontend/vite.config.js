import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 개발 서버(5173)에서 /api 로 시작하는 요청을 Spring Boot(8080)로 전달한다.
    // 브라우저 입장에서는 같은 출처 요청이므로 백엔드에 CORS 설정이 필요 없다.
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
