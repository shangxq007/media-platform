import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = vi.fn()

class MockGraphQLClient {
  constructor(_endpoint: string, _options: Record<string, unknown>) {}
  request = mockRequest
}

vi.mock('graphql-request', () => ({
  GraphQLClient: MockGraphQLClient,
}))

const { graphqlRequest, GraphQLError } = await import('./graphqlClient')

describe('graphqlRequest', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockReset()
  })

  it('returns data on successful query', async () => {
    const mockData = { meOverview: { id: '1', displayName: 'Test User' } }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<typeof mockData>('query MeOverview { meOverview { id displayName } }')
    expect(result).toEqual(mockData)
  })

  it('throws GraphQLError with errorCode on GraphQL errors', async () => {
    const graphqlError = {
      response: {
        errors: [
          {
            message: 'Access denied',
            extensions: {
              errorCode: 'GRAPHQL-403-001',
              traceId: 'trace-123',
              details: { field: 'meOverview' },
            },
          },
        ],
      },
    }
    mockRequest.mockRejectedValueOnce(graphqlError)

    try {
      await graphqlRequest('query { meOverview { id } }')
      expect.unreachable('Should have thrown')
    } catch (err) {
      expect(err).toBeInstanceOf(GraphQLError)
      const gqlErr = err as InstanceType<typeof GraphQLError>
      expect(gqlErr.message).toBe('Access denied')
      expect(gqlErr.errorCode).toBe('GRAPHQL-403-001')
      expect(gqlErr.traceId).toBe('trace-123')
      expect(gqlErr.details).toEqual({ field: 'meOverview' })
    }
  })

  it('re-throws non-GraphQL errors as-is', async () => {
    const networkError = new Error('Network failure')
    mockRequest.mockRejectedValueOnce(networkError)

    await expect(graphqlRequest('query { test }')).rejects.toThrow('Network failure')
  })

  it('passes variables to the client', async () => {
    mockRequest.mockResolvedValueOnce({ data: 'ok' })

    await graphqlRequest('query Test($id: String!) { test(id: $id) }', { id: '123' })
    expect(mockRequest).toHaveBeenCalledWith(
      'query Test($id: String!) { test(id: $id) }',
      { id: '123' }
    )
  })
})

describe('GraphQLError', () => {
  it('is an instance of Error', () => {
    const err = new GraphQLError('test error')
    expect(err).toBeInstanceOf(Error)
    expect(err).toBeInstanceOf(GraphQLError)
  })

  it('stores errorCode, traceId, and details', () => {
    const err = new GraphQLError('test', 'CODE-001', 'trace-abc', { foo: 'bar' })
    expect(err.message).toBe('test')
    expect(err.errorCode).toBe('CODE-001')
    expect(err.traceId).toBe('trace-abc')
    expect(err.details).toEqual({ foo: 'bar' })
    expect(err.name).toBe('GraphQLError')
  })

  it('has optional fields undefined by default', () => {
    const err = new GraphQLError('test')
    expect(err.errorCode).toBeUndefined()
    expect(err.traceId).toBeUndefined()
    expect(err.details).toBeUndefined()
  })
})
