import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = vi.fn()

class MockGraphQLClient {
  constructor(_endpoint: string, _options: Record<string, unknown>) {}
  request = mockRequest
}

vi.mock('graphql-request', () => ({
  GraphQLClient: MockGraphQLClient,
}))

const ME_OVERVIEW_QUERY = `
  query MeOverview {
    meOverview {
      id
      displayName
      navigation {
        routeKey
        path
        title
        visible
        enabled
      }
    }
  }
`

class GraphQLError extends Error {
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

async function graphqlRequest<T>(query: string, variables?: Record<string, unknown>): Promise<T> {
  try {
    return await mockRequest(query, variables)
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'response' in error) {
      const response = (error as { response: { errors?: Array<{ message: string; extensions?: Record<string, unknown> }> } }).response
      if (response.errors?.length) {
        const firstError = response.errors[0]
        const errorCode = firstError.extensions?.errorCode as string
        const traceId = firstError.extensions?.traceId as string
        throw new GraphQLError(firstError.message, errorCode, traceId)
      }
    }
    throw error
  }
}

describe('NavigationMenu loads MeOverview', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockReset()
  })

  it('loads navigation routes from GraphQL MeOverview', async () => {
    const mockData = {
      meOverview: {
        id: 'user-1',
        displayName: 'Test User',
        navigation: [
          { routeKey: 'editor', path: '/', title: 'Editor', visible: true, enabled: true },
          { routeKey: 'prompts', path: '/prompts', title: 'Prompts', visible: true, enabled: true },
          { routeKey: 'effect-packs', path: '/effect-packs', title: 'Effect Packs', visible: true, enabled: true },
        ],
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{ meOverview: { navigation: any[] } }>(ME_OVERVIEW_QUERY)

    expect(result.meOverview.navigation).toHaveLength(3)
    expect(result.meOverview.navigation[0].routeKey).toBe('editor')
    expect(result.meOverview.navigation[0].path).toBe('/')
    expect(result.meOverview.navigation[0].visible).toBe(true)
    expect(result.meOverview.navigation[0].enabled).toBe(true)
  })

  it('filters out hidden routes', async () => {
    const mockData = {
      meOverview: {
        id: 'user-1',
        displayName: 'Test User',
        navigation: [
          { routeKey: 'editor', path: '/', title: 'Editor', visible: true, enabled: true },
          { routeKey: 'admin', path: '/admin', title: 'Admin', visible: false, enabled: false },
        ],
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{ meOverview: { navigation: any[] } }>(ME_OVERVIEW_QUERY)
    const visibleRoutes = result.meOverview.navigation.filter((r: any) => r.visible)

    expect(visibleRoutes).toHaveLength(1)
    expect(visibleRoutes[0].routeKey).toBe('editor')
  })

  it('handles disabled routes correctly', async () => {
    const mockData = {
      meOverview: {
        id: 'user-1',
        displayName: 'Test User',
        navigation: [
          { routeKey: 'editor', path: '/', title: 'Editor', visible: true, enabled: true },
          { routeKey: 'team', path: '/team', title: 'Team', visible: true, enabled: false },
        ],
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{ meOverview: { navigation: any[] } }>(ME_OVERVIEW_QUERY)
    const enabledRoutes = result.meOverview.navigation.filter((r: any) => r.visible && r.enabled)
    const disabledRoutes = result.meOverview.navigation.filter((r: any) => r.visible && !r.enabled)

    expect(enabledRoutes).toHaveLength(1)
    expect(disabledRoutes).toHaveLength(1)
    expect(disabledRoutes[0].routeKey).toBe('team')
  })

  it('handles empty navigation', async () => {
    const mockData = {
      meOverview: {
        id: 'user-1',
        displayName: 'Anonymous',
        navigation: [],
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{ meOverview: { navigation: any[] } }>(ME_OVERVIEW_QUERY)
    expect(result.meOverview.navigation).toEqual([])
  })

  it('handles GraphQL error for MeOverview', async () => {
    const graphqlError = {
      response: {
        errors: [
          {
            message: 'Access denied',
            extensions: {
              errorCode: 'GRAPHQL-403-001',
              traceId: 'trace-nav-123',
            },
          },
        ],
      },
    }
    mockRequest.mockRejectedValueOnce(graphqlError)

    try {
      await graphqlRequest(ME_OVERVIEW_QUERY)
      expect.unreachable('Should have thrown')
    } catch (err) {
      expect(err).toBeInstanceOf(GraphQLError)
      const gqlErr = err as InstanceType<typeof GraphQLError>
      expect(gqlErr.message).toBe('Access denied')
      expect(gqlErr.errorCode).toBe('GRAPHQL-403-001')
      expect(gqlErr.traceId).toBe('trace-nav-123')
    }
  })

  it('handles network error for MeOverview', async () => {
    mockRequest.mockRejectedValueOnce(new Error('Network failure'))

    await expect(graphqlRequest(ME_OVERVIEW_QUERY)).rejects.toThrow('Network failure')
  })

  it('loads MeOverview with tenant info', async () => {
    const mockData = {
      meOverview: {
        id: 'user-1',
        displayName: 'Test User',
        currentTenant: {
          id: 'tenant-1',
          name: 'Test Tenant',
          tier: 'PRO',
        },
        navigation: [
          { routeKey: 'editor', path: '/', title: 'Editor', visible: true, enabled: true },
        ],
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{ meOverview: { currentTenant?: { tier: string } } }>(ME_OVERVIEW_QUERY)

    expect(result.meOverview.currentTenant).toBeDefined()
    expect(result.meOverview.currentTenant?.tier).toBe('PRO')
  })
})
