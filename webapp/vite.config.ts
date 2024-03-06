import { ConfigEnv, defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import svgr from 'vite-plugin-svgr';

// https://vitejs.dev/config/
export default defineConfig((mode: ConfigEnv) => {
  // eslint-disable-next-line @typescript-eslint/ban-ts-comment
  // @ts-ignore
  const env = loadEnv(mode.mode, process.cwd());
  const baseUrl = env.VITE_BASE_URL
  const proxyPath = env.VITE_PROXY_PATH;
  return {
    plugins: [react(), svgr()],
    server: {
      proxy: {
        [proxyPath]: {
          target: baseUrl,
          changeOrigin: true,
          rewrite: (path) => path.replace(/^\/api/, '')
        },
      }
    }
  }
})
