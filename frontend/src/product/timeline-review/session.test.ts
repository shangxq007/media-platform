import { AxiosError } from 'axios'
import { afterEach, expect, it, vi } from 'vitest'
const auth = vi.hoisted(() => ({ retire: vi.fn(), redirect: vi.fn() }))
vi.mock('../../auth/oidcConfig', () => ({ isOidcEnabled: () => true }))
vi.mock('../../auth/oidcClient', () => ({ getAccessToken: async () => 'test-only-token', signInRedirect: auth.redirect, retireOidcSession: auth.retire }))
import api from '../../api'
afterEach(() => vi.restoreAllMocks())
it('retires private OIDC consumers immediately on a real Axios 401 before a pending redirect completes', async () => {
  let finish!: () => void
  auth.redirect.mockImplementation(() => new Promise<void>(resolve => { finish = resolve }))
  const original = api.defaults.adapter
  api.defaults.adapter = async config => { throw new AxiosError('Expired', undefined, config, undefined, { status: 401, statusText: 'Expired', headers: {}, config, data: {} }) }
  vi.spyOn(console, 'error').mockImplementation(() => {})
  try {
    const pending = api.get('/api/render/projects/p/timeline/reviews', { baseURL: '' }).catch(error => error)
    await vi.waitFor(() => expect(auth.redirect).toHaveBeenCalledOnce())
    expect(auth.retire).toHaveBeenCalledOnce()
    finish(); expect((await pending).response.status).toBe(401)
  } finally { api.defaults.adapter = original }
})
