import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react-swc'
import { fileURLToPath, URL } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('.', import.meta.url))

// Dev-only backend the vite dev server proxies to.
const devBackendTarget = 'http://localhost:8088'
// Dev-only browser path the dev server rewrites to the secret-protected backend path.
const devAuthProxyPath = '/dev-auth/token'
const devAuthBackendPath = '/api/dev/auth/token'
const devAuthHeader = 'X-Dev-Auth-Secret'

export default defineConfig(({ mode }) => {
  // AUTH-DEV-AUTH-CALLER-ADAPT-001: DEV_AUTH_SECRET is read here, in the Node config, and is only
  // ever attached by the dev-server proxy below. It is deliberately NOT VITE_-prefixed, so Vite
  // never exposes it through import.meta.env — it can never be inlined into the browser bundle.
  // Dev setup: put `DEV_AUTH_SECRET=...` in frontend/.env.local (gitignored) or export it in the
  // shell that runs `npm run dev`. See docs/development/dev-auth.md.
  const frontendEnv = loadEnv(mode, root, '')
  const repositoryEnv = loadEnv(mode, path.resolve(root, '..'), '')
  const devAuthSecret = (frontendEnv.DEV_AUTH_SECRET ?? repositoryEnv.DEV_AUTH_SECRET ?? '').trim()
  if (!devAuthSecret) {
    // Fail closed: the proxy forwards without the header and the backend answers 401.
    console.warn(
      `[dev-auth] DEV_AUTH_SECRET is not set; ${devAuthBackendPath} will reject dev token requests. ` +
        'Set DEV_AUTH_SECRET in the environment (or frontend/.env.local) to enable dev bootstrap.'
    )
  }

  return {
    root,
    plugins: [react()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 3000,
      proxy: {
        // Dev-only transport: the browser calls this path, the dev server rewrites it to the
        // backend dev-auth endpoint and injects the secret header server-side.
        [devAuthProxyPath]: {
          target: devBackendTarget,
          changeOrigin: true,
          rewrite: () => devAuthBackendPath,
          headers: devAuthSecret ? { [devAuthHeader]: devAuthSecret } : undefined
        },
        '/api': {
          target: devBackendTarget,
          changeOrigin: true
        }
      }
    },
    build: {
      outDir: path.resolve(root, '../platform-app/src/main/resources/static'),
      emptyOutDir: true,
      assetsDir: 'assets',
      sourcemap: false
    }
  }
})
