import { z } from 'zod'
import { isOidcEnabled } from '../../auth/oidcConfig'
import { currentOidcTransportRevision, getOidcRequestBinding, handleOidcUnauthorized } from '../../auth/oidcClient'

export interface ApiClientConfig {
  baseUrl: string
  headers?: Record<string, string>
}

export interface ApiRequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  headers?: Record<string, string>
  signal?: AbortSignal
}

export type ApiResult<T> = 
  | { success: true; data: T }
  | { success: false; error: ApiError }

export interface ApiError {
  message: string
  code?: string
  status?: number
}

export async function apiRequest<T>(
  config: ApiClientConfig,
  path: string,
  schema: z.ZodSchema<T>,
  options: ApiRequestOptions = {}
): Promise<ApiResult<T>> {
  const { method = 'GET', body, headers = {}, signal } = options
  
  try {
    const requestHeaders: Record<string, string> = {
      'Content-Type': 'application/json',
      ...config.headers,
      ...headers,
    }
    const binding = isOidcEnabled() ? await getOidcRequestBinding() : null
    if (binding) {
      if (binding.retired || binding.revision !== currentOidcTransportRevision() || signal?.aborted) {
        throw new DOMException('OIDC request binding retired', 'AbortError')
      }
      // Replace any caller credential, including differently cased header names.
      for (const name of Object.keys(requestHeaders)) {
        if (name.toLowerCase() === 'authorization') delete requestHeaders[name]
      }
      if (binding.accessToken) Object.assign(requestHeaders, { Authorization: `Bearer ${binding.accessToken}` })
    }
    const response = await fetch(`${config.baseUrl}${path}`, {
      method,
      headers: requestHeaders,
      body: body ? JSON.stringify(body) : undefined,
      signal,
    })

    if (response.status === 401 && binding && !window.location.pathname.startsWith('/oauth/callback')) {
      try { await handleOidcUnauthorized(binding, signal, window.location.pathname + window.location.search) }
      catch { console.error('OIDC current-session check failed') }
    }
    if (!response.ok) {
      return {
        success: false,
        error: {
          message: `HTTP ${response.status}`,
          status: response.status,
        },
      }
    }

    const json = await response.json()
    const parsed = schema.safeParse(json)

    if (!parsed.success) {
      return {
        success: false,
        error: {
          message: 'Response validation failed',
          code: 'PARSE_ERROR',
        },
      }
    }

    return { success: true, data: parsed.data }
  } catch (error) {
    return {
      success: false,
      error: {
        message: error instanceof Error ? error.message : 'Unknown error',
      },
    }
  }
}
