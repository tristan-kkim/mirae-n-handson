import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    // modern/api 에 CORS 설정이 없을 때의 우회 경로:
    // VITE_API_BASE="" 로 두면 fetch 가 상대 경로(/api/...)를 쓰고 이 프록시가 8080 으로 넘긴다.
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    include: ['src/**/*.test.{ts,tsx}'],
  },
});
