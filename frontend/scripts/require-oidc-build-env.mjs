// Fail-closed guard for an OIDC-aware production frontend build.
//
// The committed bundle is built WITHOUT VITE_OIDC_* so it never embeds a test issuer.
// A production build that wants build-time OIDC values (for hosting without the
// backend /config.js endpoint) must use `npm run build:oidc`, which runs this guard.
//
// Exit codes: 0 = build may proceed; 1 = refused.
import { loadEnv } from 'vite'

const root = process.cwd()
const env = loadEnv('production', root, '')
const read = name => (process.env[name] ?? env[name] ?? '').trim()

const issuer = read('VITE_OIDC_ISSUER')
const clientId = read('VITE_OIDC_CLIENT_ID')
const allowNoOidc = read('ALLOW_NO_OIDC_BUILD') === '1'
const allowTest = read('ALLOW_TEST_OIDC_BUILD') === '1'

const fail = message => {
  console.error(`[build:oidc] REFUSED: ${message}`)
  process.exit(1)
}

if (allowNoOidc) {
  if (issuer || clientId) fail('ALLOW_NO_OIDC_BUILD=1 but VITE_OIDC_* values are present')
  console.log('[build:oidc] no-OIDC build requested; the bundle will rely on /config.js')
  process.exit(0)
}

if (!issuer) fail('VITE_OIDC_ISSUER is required (or set ALLOW_NO_OIDC_BUILD=1)')
if (!clientId) fail('VITE_OIDC_CLIENT_ID is required (or set ALLOW_NO_OIDC_BUILD=1)')

if (!allowTest) {
  const lowered = issuer.toLowerCase()
  const loopback = ['localhost', '127.0.0.1', '::1', '0.0.0.0', '.local', 'test.', 'example.']
  if (loopback.some(marker => lowered.includes(marker))) {
    fail(`issuer "${issuer}" looks like a test/local value; set ALLOW_TEST_OIDC_BUILD=1 only for local E2E`)
  }
  if (!lowered.startsWith('https://')) {
    fail(`issuer "${issuer}" must use https in a production build`)
  }
  if (/^f8[-.]/.test(clientId) || clientId === 'f8-local') {
    fail(`client id "${clientId}" looks like the local validation audience`)
  }
}

console.log(`[build:oidc] issuer=${issuer} clientId=${clientId}`)
