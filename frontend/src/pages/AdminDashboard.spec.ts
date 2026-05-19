import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = vi.fn()

class MockGraphQLClient {
  constructor(_endpoint: string, _options: Record<string, unknown>) {}
  request = mockRequest
}

vi.mock('graphql-request', () => ({
  GraphQLClient: MockGraphQLClient,
}))

const ADMIN_DASHBOARD_QUERY = `
  query AdminDashboard($range: String = "7d") {
    adminDashboard(range: $range) {
      renderStats { submitted completed failed avgDurationSeconds }
      providerHealth { providerKey status latencyMs errorRate }
      billingSummary { usageAmount { amount currency } estimatedRevenue { amount currency } }
      feedbackSummary { openIssues criticalIssues linkedRenderJobs linkedPromptExecutions replayLinked }
      extensionSummary { installed enabled highRisk sandboxJobsRunning }
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

describe('AdminDashboard handles access denied', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockReset()
  })

  it('loads admin dashboard for admin user', async () => {
    const mockData = {
      adminDashboard: {
        renderStats: { submitted: 10, completed: 8, failed: 2, avgDurationSeconds: 120 },
        providerHealth: [
          { providerKey: 'javacv', status: 'HEALTHY', latencyMs: 50, errorRate: 0.01 },
        ],
        billingSummary: {
          usageAmount: { amount: 100.0, currency: 'USD' },
          estimatedRevenue: { amount: 120.0, currency: 'USD' },
        },
        feedbackSummary: { openIssues: 5, criticalIssues: 1, linkedRenderJobs: 3, linkedPromptExecutions: 2, replayLinked: false },
        extensionSummary: { installed: 3, enabled: 2, highRisk: 0, sandboxJobsRunning: 1 },
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{
      adminDashboard: {
        renderStats: { submitted: number; completed: number; failed: number; avgDurationSeconds: number }
        providerHealth: Array<{ providerKey: string; status: string; latencyMs: number; errorRate: number }>
        billingSummary: { usageAmount: { amount: number; currency: string }; estimatedRevenue: { amount: number; currency: string } }
        feedbackSummary: { openIssues: number; criticalIssues: number; linkedRenderJobs: number; linkedPromptExecutions: number; replayLinked: boolean }
        extensionSummary: { installed: number; enabled: number; highRisk: number; sandboxJobsRunning: number }
      }
    }>(ADMIN_DASHBOARD_QUERY, { range: '7d' })

    expect(result.adminDashboard.renderStats.submitted).toBe(10)
    expect(result.adminDashboard.renderStats.completed).toBe(8)
    expect(result.adminDashboard.renderStats.failed).toBe(2)
    expect(result.adminDashboard.providerHealth).toHaveLength(1)
    expect(result.adminDashboard.extensionSummary.installed).toBe(3)
  })

  it('shows access denied error for non-admin user', async () => {
    const graphqlError = {
      response: {
        errors: [
          {
            message: 'Access denied: requires ADMIN or DASHBOARD_ADMIN role',
            extensions: {
              errorCode: 'GRAPHQL-403-001',
              traceId: 'trace-admin-123',
            },
          },
        ],
      },
    }
    mockRequest.mockRejectedValueOnce(graphqlError)

    try {
      await graphqlRequest(ADMIN_DASHBOARD_QUERY, { range: '7d' })
      expect.unreachable('Should have thrown')
    } catch (err) {
      expect(err).toBeInstanceOf(GraphQLError)
      const gqlErr = err as InstanceType<typeof GraphQLError>
      expect(gqlErr.message).toContain('Access denied')
      expect(gqlErr.errorCode).toBe('GRAPHQL-403-001')
      expect(gqlErr.traceId).toBe('trace-admin-123')
    }
  })

  it('shows error message in UI for non-admin', async () => {
    const graphqlError = {
      response: {
        errors: [
          {
            message: 'Access denied: requires ADMIN or DASHBOARD_ADMIN role',
            extensions: {
              errorCode: 'GRAPHQL-403-001',
              traceId: 'trace-admin-456',
            },
          },
        ],
      },
    }
    mockRequest.mockRejectedValueOnce(graphqlError)

    let caughtError: GraphQLError | null = null
    try {
      await graphqlRequest(ADMIN_DASHBOARD_QUERY, { range: '7d' })
    } catch (err) {
      caughtError = err as GraphQLError
    }

    expect(caughtError).not.toBeNull()
    expect(caughtError!.errorCode).toBe('GRAPHQL-403-001')
    expect(caughtError!.message).toContain('requires ADMIN')
  })

  it('renders error code in UI', async () => {
    const graphqlError = {
      response: {
        errors: [
          {
            message: 'Access denied: requires ADMIN or DASHBOARD_ADMIN role',
            extensions: {
              errorCode: 'GRAPHQL-403-001',
              traceId: 'trace-admin-789',
            },
          },
        ],
      },
    }
    mockRequest.mockRejectedValueOnce(graphqlError)

    let caughtErrorCode: string | undefined
    try {
      await graphqlRequest(ADMIN_DASHBOARD_QUERY, { range: '7d' })
    } catch (err) {
      caughtErrorCode = (err as GraphQLError).errorCode
    }

    expect(caughtErrorCode).toBe('GRAPHQL-403-001')
  })

  it('handles member role access denied', async () => {
    const graphqlError = {
      response: {
        errors: [
          {
            message: 'Access denied: requires ADMIN or DASHBOARD_ADMIN role',
            extensions: {
              errorCode: 'GRAPHQL-403-001',
              traceId: 'trace-member-denied',
            },
          },
        ],
      },
    }
    mockRequest.mockRejectedValueOnce(graphqlError)

    try {
      await graphqlRequest(ADMIN_DASHBOARD_QUERY, { range: '7d' })
      expect.unreachable('Should have thrown')
    } catch (err) {
      const gqlErr = err as GraphQLError
      expect(gqlErr.errorCode).toBe('GRAPHQL-403-001')
    }
  })

  it('handles viewer role access denied', async () => {
    const graphqlError = {
      response: {
        errors: [
          {
            message: 'Access denied: requires ADMIN or DASHBOARD_ADMIN role',
            extensions: {
              errorCode: 'GRAPHQL-403-001',
              traceId: 'trace-viewer-denied',
            },
          },
        ],
      },
    }
    mockRequest.mockRejectedValueOnce(graphqlError)

    try {
      await graphqlRequest(ADMIN_DASHBOARD_QUERY, { range: '7d' })
      expect.unreachable('Should have thrown')
    } catch (err) {
      const gqlErr = err as GraphQLError
      expect(gqlErr.errorCode).toBe('GRAPHQL-403-001')
    }
  })

  it('passes range variable to admin dashboard query', async () => {
    mockRequest.mockResolvedValueOnce({
      adminDashboard: {
        renderStats: { submitted: 0, completed: 0, failed: 0, avgDurationSeconds: 0 },
        providerHealth: [],
        billingSummary: { usageAmount: { amount: 0, currency: 'USD' }, estimatedRevenue: { amount: 0, currency: 'USD' } },
        feedbackSummary: { openIssues: 0, criticalIssues: 0, linkedRenderJobs: 0, linkedPromptExecutions: 0, replayLinked: false },
        extensionSummary: { installed: 0, enabled: 0, highRisk: 0, sandboxJobsRunning: 0 },
      },
    })

    await graphqlRequest(ADMIN_DASHBOARD_QUERY, { range: '30d' })

    expect(mockRequest).toHaveBeenCalledWith(ADMIN_DASHBOARD_QUERY, { range: '30d' })
  })
})
