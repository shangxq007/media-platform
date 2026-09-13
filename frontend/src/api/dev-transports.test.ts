import axios from 'axios'
import { z } from 'zod'
import { afterEach, expect, it, vi } from 'vitest'

vi.mock('../auth/oidcConfig', () => ({ isOidcEnabled: () => false }))
import api from './index'
import { versionlessApi } from './app/versionless-api'
import { apiRequest } from './core/api-client'

const adapters = [api.defaults.adapter, versionlessApi.defaults.adapter]
afterEach(() => {
  api.defaults.adapter = adapters[0]; versionlessApi.defaults.adapter = adapters[1]
  delete api.defaults.headers.common.Authorization
  localStorage.clear(); sessionStorage.clear(); vi.restoreAllMocks()
})

it('bootstraps the explicit dev token once and binds it to both clients, preserving 401 errors', async () => {
  const bootstrap = vi.spyOn(axios, 'post').mockResolvedValue({ data: { accessToken: 'dev-test-token' } })
  vi.spyOn(console, 'error').mockImplementation(() => {})
  for (const client of [api, versionlessApi]) {
    client.defaults.adapter = async config => {
      expect(config.headers.Authorization).toBe('Bearer dev-test-token')
      return { status: 200, statusText: 'OK', headers: {}, config, data: { usable: true } }
    }
    expect((await client.get('/dev-request')).data).toEqual({ usable: true })
    const error = new axios.AxiosError('Dev unauthorized')
    client.defaults.adapter = async config => {
      error.config = config
      error.response = { status: 401, statusText: 'Unauthorized', headers: {}, config, data: {} }
      throw error
    }
    await expect(client.get('/dev-failure')).rejects.toBe(error)
  }
  expect(bootstrap).toHaveBeenCalledOnce()
  expect(bootstrap).toHaveBeenCalledWith('/api/dev/auth/token', { userId: 'user-1' })
  expect(localStorage.getItem('dev_access_token')).toBe('dev-test-token')
  expect(sessionStorage.getItem('oidc_post_login_redirect')).toBeNull()
})

it('preserves the fetch client explicit headers, path, body and non-OIDC error contract', async () => {
  const network = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}', { status: 401 }))
  const controller = new AbortController()
  expect(await apiRequest({ baseUrl: '/api', headers: { Authorization: 'Bearer explicit-dev' } }, '/request', z.unknown(), {
    method: 'POST', body: { value: 1 }, headers: { 'X-Request': 'test' }, signal: controller.signal,
  })).toEqual({ success: false, error: { status: 401, message: 'HTTP 401' } })
  expect(network).toHaveBeenCalledWith('/api/request', {
    method: 'POST', body: '{"value":1}', signal: controller.signal,
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer explicit-dev', 'X-Request': 'test' },
  })
  expect(sessionStorage.getItem('oidc_post_login_redirect')).toBeNull()
})
