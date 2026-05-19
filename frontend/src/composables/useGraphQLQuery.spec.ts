import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'
import { useGraphQLQuery } from './useGraphQLQuery'
import { graphqlRequest, GraphQLError } from '@/api/graphqlClient'

vi.mock('@/api/graphqlClient', () => ({
  graphqlRequest: vi.fn(),
  GraphQLError: class GraphQLError extends Error {
    constructor(
      message: string,
      public readonly errorCode?: string,
      public readonly traceId?: string,
      public readonly details?: unknown
    ) {
      super(message)
      this.name = 'GraphQLError'
    }
  },
}))

describe('useGraphQLQuery', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('starts with null data and not loading', () => {
    const { data, loading, error } = useGraphQLQuery({
      query: 'query { test }',
      immediate: false,
    })
    expect(data.value).toBeNull()
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('fetches data on mount when immediate is true', async () => {
    const mockData = { test: 'result' }
    vi.mocked(graphqlRequest).mockResolvedValueOnce(mockData)

    const { data, loading } = useGraphQLQuery<typeof mockData>({
      query: 'query { test }',
      immediate: true,
    })

    expect(loading.value).toBe(true)
    await nextTick()
    await nextTick()
    expect(data.value).toEqual(mockData)
    expect(loading.value).toBe(false)
  })

  it('does not fetch when immediate is false', () => {
    const { data, loading } = useGraphQLQuery({
      query: 'query { test }',
      immediate: false,
    })
    expect(loading.value).toBe(false)
    expect(data.value).toBeNull()
    expect(graphqlRequest).not.toHaveBeenCalled()
  })

  it('refetch triggers a new request', async () => {
    const mockData = { test: 'refetched' }
    vi.mocked(graphqlRequest).mockResolvedValueOnce(mockData)

    const { data, refetch } = useGraphQLQuery<typeof mockData>({
      query: 'query { test }',
      immediate: false,
    })

    expect(data.value).toBeNull()
    const result = await refetch()
    expect(result).toEqual(mockData)
    expect(data.value).toEqual(mockData)
  })

  it('sets error on GraphQL failure without fallback', async () => {
    vi.mocked(graphqlRequest).mockRejectedValueOnce(new Error('Network error'))

    const { data, error, loading } = useGraphQLQuery({
      query: 'query { test }',
      immediate: true,
    })

    await nextTick()
    await nextTick()
    expect(data.value).toBeNull()
    expect(error.value).toBeInstanceOf(Error)
    expect(error.value?.message).toBe('Network error')
    expect(loading.value).toBe(false)
  })

  it('falls back to REST on GraphQL failure', async () => {
    vi.mocked(graphqlRequest).mockRejectedValueOnce(new Error('GraphQL down'))
    const fallbackData = { rest: 'data' }
    const fallbackFn = vi.fn().mockResolvedValueOnce(fallbackData)

    const { data, error } = useGraphQLQuery<typeof fallbackData>({
      query: 'query { test }',
      fallbackFn,
      immediate: true,
    })

    await nextTick()
    await nextTick()
    expect(fallbackFn).toHaveBeenCalled()
    expect(data.value).toEqual(fallbackData)
    expect(error.value).toBeNull()
  })

  it('sets errorCode and traceId on GraphQLError', async () => {
    vi.mocked(graphqlRequest).mockRejectedValueOnce(
      new GraphQLError('Access denied', 'GRAPHQL-403-001', 'trace-123')
    )

    const { errorCode, traceId } = useGraphQLQuery({
      query: 'query { test }',
      immediate: true,
    })

    await nextTick()
    await nextTick()
    expect(errorCode.value).toBe('GRAPHQL-403-001')
    expect(traceId.value).toBe('trace-123')
  })

  it('uses transform function on data', async () => {
    vi.mocked(graphqlRequest).mockResolvedValueOnce({ raw: 'data' })

    const { data } = useGraphQLQuery<{ transformed: boolean }>({
      query: 'query { raw }',
      transform: () => ({ transformed: true }),
      immediate: true,
    })

    await nextTick()
    await nextTick()
    expect(data.value).toEqual({ transformed: true })
  })

  it('passes variables to graphqlRequest', async () => {
    vi.mocked(graphqlRequest).mockResolvedValueOnce({ ok: true })

    await useGraphQLQuery({
      query: 'query Test($id: String!) { test(id: $id) }',
      variables: { id: '123' },
      immediate: true,
    })

    expect(graphqlRequest).toHaveBeenCalledWith(
      'query Test($id: String!) { test(id: $id) }',
      { id: '123' }
    )
  })
})
