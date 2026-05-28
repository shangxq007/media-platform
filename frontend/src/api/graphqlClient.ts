import { GraphQLClient } from 'graphql-request'
import { isOidcEnabled } from '@/auth/oidcConfig'
import { getAccessToken } from '@/auth/oidcClient'

const endpoint = import.meta.env.VITE_GRAPHQL_ENDPOINT || '/graphql'

export const graphqlClient = new GraphQLClient(endpoint, {
  headers: {
    'Content-Type': 'application/json',
  },
  credentials: 'include',
  requestMiddleware: async (request) => {
    const headers: Record<string, string> = { ...request.headers as Record<string, string> }
    if (isOidcEnabled()) {
      const token = await getAccessToken()
      if (token) {
        headers['Authorization'] = `Bearer ${token}`
      }
    } else if (import.meta.env.DEV) {
      const devToken = localStorage.getItem('dev_access_token')
      if (devToken) {
        headers['Authorization'] = `Bearer ${devToken}`
      }
    }
    return { ...request, headers }
  },
})

export async function graphqlRequest<T>(query: string, variables?: Record<string, unknown>): Promise<T> {
  try {
    return await graphqlClient.request<T>(query, variables)
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'response' in error) {
      const response = (error as { response: { errors?: Array<{ message: string; extensions?: Record<string, unknown> }> } }).response
      if (response.errors?.length) {
        const firstError = response.errors[0]
        const errorCode = firstError.extensions?.errorCode as string
        const traceId = firstError.extensions?.traceId as string
        const details = firstError.extensions?.details
        throw new GraphQLError(firstError.message, errorCode, traceId, details)
      }
    }
    throw error
  }
}

export class GraphQLError extends Error {
  constructor(
    message: string,
    public readonly errorCode?: string,
    public readonly traceId?: string,
    public readonly details?: unknown
  ) {
    super(message)
    this.name = 'GraphQLError'
  }
}
