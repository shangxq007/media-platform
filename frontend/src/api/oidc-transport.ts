import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { isOidcEnabled } from '../auth/oidcConfig'
import { getOidcRequestBinding, currentOidcTransportRevision, handleOidcUnauthorized, type OidcRequestBinding } from '../auth/oidcClient'

// Both Axios transports retain the binding on the config actually dispatched.
// Session retirement and expiry coalescing remain exclusively in oidcClient.
const oidcRequests = new WeakMap<object, OidcRequestBinding>()

export async function bindOidcRequest(config: InternalAxiosRequestConfig): Promise<void> {
  const binding = await getOidcRequestBinding()
  if (binding.retired || binding.revision !== currentOidcTransportRevision() || config.signal?.aborted) {
    throw new axios.CanceledError('OIDC request binding retired')
  }
  if (binding.accessToken) config.headers.Authorization = `Bearer ${binding.accessToken}`
  else delete config.headers.Authorization
  oidcRequests.set(config, binding)
}

export async function handleOidcResponseError(error: AxiosError): Promise<void> {
  if (error?.response?.status !== 401 || !isOidcEnabled() || window.location.pathname.startsWith('/oauth/callback')) return
  const binding = error.config && oidcRequests.get(error.config)
  if (binding) {
    try { await handleOidcUnauthorized(binding, error.config?.signal, window.location.pathname + window.location.search) }
    catch { console.error('OIDC current-session check failed') }
  }
}
