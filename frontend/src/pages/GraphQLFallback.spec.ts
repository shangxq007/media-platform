import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = vi.fn()
const mockRestGet = vi.fn()

class MockGraphQLClient {
  constructor(_endpoint: string, _options: Record<string, unknown>) {}
  request = mockRequest
}

vi.mock('graphql-request', () => ({
  GraphQLClient: MockGraphQLClient,
}))

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

async function restFallback<T>(path: string): Promise<T> {
  return mockRestGet(path) as Promise<T>
}

async function fetchWithFallback<T>(
  query: string,
  variables: Record<string, unknown>,
  fallbackPath: string
): Promise<{ data: T; source: 'graphql' | 'rest' }> {
  try {
    const data = await graphqlRequest<T>(query, variables)
    return { data, source: 'graphql' }
  } catch {
    const data = await restFallback<T>(fallbackPath)
    return { data, source: 'rest' }
  }
}

describe('fallback behavior when GraphQL fails', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockReset()
    mockRestGet.mockReset()
  })

  it('uses GraphQL when available', async () => {
    const graphqlData = { meOverview: { id: '1', displayName: 'GraphQL User' } }
    mockRequest.mockResolvedValueOnce(graphqlData)

    const result = await fetchWithFallback<typeof graphqlData>(
      'query { meOverview { id displayName } }',
      {},
      '/api/v1/me/overview'
    )

    expect(result.source).toBe('graphql')
    expect(result.data).toEqual(graphqlData)
    expect(mockRestGet).not.toHaveBeenCalled()
  })

  it('falls back to REST when GraphQL fails with network error', async () => {
    mockRequest.mockRejectedValueOnce(new Error('Network failure'))
    const restData = { meOverview: { id: '1', displayName: 'REST User' } }
    mockRestGet.mockResolvedValueOnce(restData)

    const result = await fetchWithFallback<typeof restData>(
      'query { meOverview { id displayName } }',
      {},
      '/api/v1/me/overview'
    )

    expect(result.source).toBe('rest')
    expect(result.data).toEqual(restData)
    expect(mockRestGet).toHaveBeenCalledWith('/api/v1/me/overview')
  })

  it('falls back to REST when GraphQL returns errors', async () => {
    mockRequest.mockRejectedValueOnce({
      response: {
        errors: [{ message: 'Internal error', extensions: { errorCode: 'COMMON-500-001' } }],
      },
    })
    const restData = { exportPanelState: { project: { id: 'p1', name: 'REST Project' } } }
    mockRestGet.mockResolvedValueOnce(restData)

    const result = await fetchWithFallback<typeof restData>(
      'query ExportPanelState($id: String!) { exportPanelState(projectId: $id) { project { id name } } }',
      { id: 'p1' },
      '/api/v1/projects/p1/export-panel'
    )

    expect(result.source).toBe('rest')
    expect(result.data).toEqual(restData)
  })

  it('falls back to REST when GraphQL returns access denied', async () => {
    mockRequest.mockRejectedValueOnce({
      response: {
        errors: [{
          message: 'Access denied: requires ADMIN or DASHBOARD_ADMIN role',
          extensions: { errorCode: 'GRAPHQL-403-001', traceId: 'trace-123' },
        }],
      },
    })
    const restData = { adminDashboard: { renderStats: { submitted: 0, completed: 0, failed: 0 } } }
    mockRestGet.mockResolvedValueOnce(restData)

    const result = await fetchWithFallback<typeof restData>(
      'query AdminDashboard { adminDashboard { renderStats { submitted } } }',
      {},
      '/api/v1/admin/dashboard'
    )

    expect(result.source).toBe('rest')
    expect(result.data).toEqual(restData)
  })

  it('falls back to REST for ExportPanelState', async () => {
    mockRequest.mockRejectedValueOnce(new Error('GraphQL endpoint unavailable'))
    const restData = {
      exportPanelState: {
        project: { id: 'proj-1', name: 'Fallback Project' },
        timelineSummary: { durationSeconds: 3600, tracks: 4, clips: 12, subtitles: 0, effects: 0 },
        exportOptions: [],
        workers: [],
        validation: { allowed: true, violations: [], recommendations: [] },
      },
    }
    mockRestGet.mockResolvedValueOnce(restData)

    const result = await fetchWithFallback<typeof restData>(
      'query ExportPanelState($id: String!) { exportPanelState(projectId: $id) { project { id name } } }',
      { id: 'proj-1' },
      '/api/v1/projects/proj-1/export-panel'
    )

    expect(result.source).toBe('rest')
    expect(result.data.exportPanelState.project.name).toBe('Fallback Project')
  })

  it('falls back to REST for PromptTemplateDetail', async () => {
    mockRequest.mockRejectedValueOnce(new Error('GraphQL down'))
    const restData = {
      promptTemplateDetail: {
        id: 'pt-1',
        name: 'Fallback Template',
        status: 'ACTIVE',
        currentVersion: '1.0.0',
        tags: [],
        versions: [],
        executions: [],
      },
    }
    mockRestGet.mockResolvedValueOnce(restData)

    const result = await fetchWithFallback<typeof restData>(
      'query PromptTemplateDetail($id: String!) { promptTemplateDetail(id: $id) { id name status } }',
      { id: 'pt-1' },
      '/api/v1/prompts/templates/pt-1'
    )

    expect(result.source).toBe('rest')
    expect(result.data.promptTemplateDetail.name).toBe('Fallback Template')
  })

  it('falls back to REST for AdminDashboard', async () => {
    mockRequest.mockRejectedValueOnce(new Error('Service unavailable'))
    const restData = {
      adminDashboard: {
        renderStats: { submitted: 5, completed: 3, failed: 2, avgDurationSeconds: 60 },
        providerHealth: [],
        billingSummary: { usageAmount: { amount: 50, currency: 'USD' }, estimatedRevenue: { amount: 60, currency: 'USD' } },
        feedbackSummary: { openIssues: 2, criticalIssues: 0, linkedRenderJobs: 1, linkedPromptExecutions: 0, replayLinked: false },
        extensionSummary: { installed: 2, enabled: 1, highRisk: 0, sandboxJobsRunning: 0 },
      },
    }
    mockRestGet.mockResolvedValueOnce(restData)

    const result = await fetchWithFallback<typeof restData>(
      'query AdminDashboard { adminDashboard { renderStats { submitted } } }',
      {},
      '/api/v1/admin/dashboard'
    )

    expect(result.source).toBe('rest')
    expect(result.data.adminDashboard.renderStats.submitted).toBe(5)
  })

  it('tracks that fallback was used', async () => {
    mockRequest.mockRejectedValueOnce(new Error('GraphQL failure'))
    mockRestGet.mockResolvedValueOnce({ data: 'from-rest' })

    const result = await fetchWithFallback('query { test }', {}, '/api/v1/test')

    expect(result.source).toBe('rest')
  })

  it('does not call REST when GraphQL succeeds', async () => {
    mockRequest.mockResolvedValueOnce({ data: 'from-graphql' })

    const result = await fetchWithFallback('query { test }', {}, '/api/v1/test')

    expect(result.source).toBe('graphql')
    expect(mockRestGet).not.toHaveBeenCalled()
  })

  it('calls REST with correct fallback path', async () => {
    mockRequest.mockRejectedValueOnce(new Error('fail'))
    mockRestGet.mockResolvedValueOnce({})

    await fetchWithFallback('query { test }', {}, '/api/v1/custom/endpoint')

    expect(mockRestGet).toHaveBeenCalledTimes(1)
    expect(mockRestGet).toHaveBeenCalledWith('/api/v1/custom/endpoint')
  })

  it('handles MeOverview fallback', async () => {
    mockRequest.mockRejectedValueOnce(new Error('GraphQL unavailable'))
    const restData = {
      meOverview: {
        id: 'user-1',
        displayName: 'Fallback User',
        currentTenant: { id: 't1', name: 'Tenant', tier: 'FREE' },
        currentWorkspace: null,
        capabilities: [],
        navigation: [
          { routeKey: 'editor', path: '/', title: 'Editor', visible: true, enabled: true },
        ],
        billing: null,
      },
    }
    mockRestGet.mockResolvedValueOnce(restData)

    const result = await fetchWithFallback<typeof restData>(
      'query MeOverview { meOverview { id displayName } }',
      {},
      '/api/v1/me/overview'
    )

    expect(result.source).toBe('rest')
    expect(result.data.meOverview.displayName).toBe('Fallback User')
    expect(result.data.meOverview.navigation).toHaveLength(1)
  })
})
