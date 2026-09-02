import axios from 'axios'
import { getAccessToken, signInRedirect } from '../../auth/oidcClient'
import { isOidcEnabled } from '../../auth/oidcConfig'
import { bootstrapDevAuth } from '../index'

export interface VersionlessTransport {
  get(path: string, options?: { params?: Record<string, string | number> }): Promise<{ data: unknown }>
  post(path: string, body: unknown): Promise<{ data: unknown }>
}

export const versionlessApi = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

versionlessApi.interceptors.request.use(async config => {
  if (isOidcEnabled()) {
    const token = await getAccessToken()
    if (token) config.headers.Authorization = `Bearer ${token}`
  } else if (import.meta.env.DEV) {
    await bootstrapDevAuth()
    const token = localStorage.getItem('dev_access_token')
    if (token) config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

versionlessApi.interceptors.response.use(
  response => response,
  async error => {
    if (
      error?.response?.status === 401
      && isOidcEnabled()
      && !window.location.pathname.startsWith('/oauth/callback')
    ) {
      sessionStorage.setItem('oidc_post_login_redirect', window.location.pathname + window.location.search)
      await signInRedirect()
    }
    return Promise.reject(error)
  },
)

export const versionlessTransport: VersionlessTransport = {
  get: (path, options) => versionlessApi.get(path, options),
  post: (path, body) => versionlessApi.post(path, body),
}
