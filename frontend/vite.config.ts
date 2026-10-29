import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 로컬 개발(npm run dev) 시 /api 요청을 Spring Boot로 프록시한다.
// Docker 환경에서는 nginx가 같은 역할을 한다 (frontend/nginx.conf).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8081',
    },
  },
});
