import { GraphQLClient } from 'graphql-request'

const endpoint = import.meta.env.VITE_GRAPHQL_ENDPOINT || '/graphql'

export const graphqlClient = new GraphQLClient(endpoint, {
  headers: {
    'Content-Type': 'application/json',
  },
  credentials: 'include',
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
