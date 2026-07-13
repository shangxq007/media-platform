import { z } from 'zod'

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
    const response = await fetch(`${config.baseUrl}${path}`, {
      method,
      headers: {
        'Content-Type': 'application/json',
        ...config.headers,
        ...headers,
      },
      body: body ? JSON.stringify(body) : undefined,
      signal,
    })

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
