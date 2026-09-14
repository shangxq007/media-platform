import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { isOidcEnabled } from '../auth/oidcConfig'
import { getOidcRequestBinding, currentOidcTransportRevision, handleOidcUnauthorized, type OidcRequestBinding } from '../auth/oidcClient'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** Preserve the original canonical credential across a multi-request operation. */
    expectedOidcBinding?: object
  }
}

// Both Axios transports retain the binding on the config actually dispatched.
// Session retirement and expiry coalescing remain exclusively in oidcClient.
const oidcRequests = new WeakMap<object, OidcRequestBinding>()
class BindingExpectation {}

/** Opaque marker survives Axios config merging without putting credentials in config metadata. */
export function expectOidcRequestBinding(binding: OidcRequestBinding): object {
  const marker = Object.freeze(new BindingExpectation())
  oidcRequests.set(marker, binding)
  return marker
}

export async function bindOidcRequest(config: InternalAxiosRequestConfig): Promise<void> {
  const binding = await getOidcRequestBinding()
  const expected = config.expectedOidcBinding && oidcRequests.get(config.expectedOidcBinding)
  if (binding.retired || binding.revision !== currentOidcTransportRevision() || config.signal?.aborted
    || (config.expectedOidcBinding && (!expected || expected.revision !== binding.revision || expected.credential !== binding.credential))) {
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
