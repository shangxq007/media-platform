import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = vi.fn()

class MockGraphQLClient {
  constructor(_endpoint: string, _options: Record<string, unknown>) {}
  request = mockRequest
}

vi.mock('graphql-request', () => ({
  GraphQLClient: MockGraphQLClient,
}))

const PROMPT_DETAIL_QUERY = `
  query PromptTemplateDetail($id: String!) {
    promptTemplateDetail(id: $id) {
      id
      name
      status
      currentVersion
      tags
      versions { version createdBy changelog }
      executions(limit: 20) { executionId status riskLevel costEstimate { amount currency } }
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

describe('PromptManagement loads PromptTemplateDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockRequest.mockReset()
  })

  it('loads prompt template detail from GraphQL', async () => {
    const mockData = {
      promptTemplateDetail: {
        id: 'pt-1',
        name: 'Test Template',
        status: 'ACTIVE',
        currentVersion: '1.0.0',
        tags: ['ai', 'creative'],
        versions: [
          { version: '1.0.0', createdBy: 'user-1', changelog: 'Initial version' },
        ],
        executions: [
          { executionId: 'pe-1', status: 'SUCCEEDED', riskLevel: 'LOW', costEstimate: { amount: 0.003, currency: 'USD' } },
        ],
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{ promptTemplateDetail: any }>(PROMPT_DETAIL_QUERY, { id: 'pt-1' })

    expect(result.promptTemplateDetail.id).toBe('pt-1')
    expect(result.promptTemplateDetail.name).toBe('Test Template')
    expect(result.promptTemplateDetail.status).toBe('ACTIVE')
    expect(result.promptTemplateDetail.currentVersion).toBe('1.0.0')
    expect(result.promptTemplateDetail.tags).toContain('ai')
    expect(result.promptTemplateDetail.versions).toHaveLength(1)
    expect(result.promptTemplateDetail.executions).toHaveLength(1)
  })

  it('shows GraphQL data in prompt versions', async () => {
    const mockData = {
      promptTemplateDetail: {
        id: 'pt-1',
        name: 'Versioned Template',
        status: 'ACTIVE',
        currentVersion: '2.0.0',
        tags: [],
        versions: [
          { version: '1.0.0', createdBy: 'user-1', changelog: 'Initial' },
          { version: '1.1.0', createdBy: 'user-1', changelog: 'Added variables' },
          { version: '2.0.0', createdBy: 'user-2', changelog: 'Major rewrite' },
        ],
        executions: [],
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{ promptTemplateDetail: any }>(PROMPT_DETAIL_QUERY, { id: 'pt-1' })

    expect(result.promptTemplateDetail.versions).toHaveLength(3)
    expect(result.promptTemplateDetail.versions[0].version).toBe('1.0.0')
    expect(result.promptTemplateDetail.versions[2].version).toBe('2.0.0')
    expect(result.promptTemplateDetail.currentVersion).toBe('2.0.0')
  })

  it('shows GraphQL data in executions', async () => {
    const mockData = {
      promptTemplateDetail: {
        id: 'pt-1',
        name: 'Exec Template',
        status: 'ACTIVE',
        currentVersion: '1.0.0',
        tags: [],
        versions: [],
        executions: [
          { executionId: 'pe-1', status: 'SUCCEEDED', riskLevel: 'LOW', costEstimate: { amount: 0.003, currency: 'USD' } },
          { executionId: 'pe-2', status: 'FAILED', riskLevel: 'HIGH', costEstimate: { amount: 0.0, currency: 'USD' } },
          { executionId: 'pe-3', status: 'RUNNING', riskLevel: 'MEDIUM', costEstimate: { amount: 0.001, currency: 'USD' } },
        ],
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{ promptTemplateDetail: any }>(PROMPT_DETAIL_QUERY, { id: 'pt-1' })

    expect(result.promptTemplateDetail.executions).toHaveLength(3)
    expect(result.promptTemplateDetail.executions[0].status).toBe('SUCCEEDED')
    expect(result.promptTemplateDetail.executions[1].status).toBe('FAILED')
    expect(result.promptTemplateDetail.executions[2].status).toBe('RUNNING')
  })

  it('limits executions to 20', async () => {
    const manyExecutions = Array.from({ length: 25 }, (_, i) => ({
      executionId: `pe-${i}`,
      status: 'SUCCEEDED',
      riskLevel: 'LOW',
      costEstimate: { amount: 0.001, currency: 'USD' },
    }))

    const mockData = {
      promptTemplateDetail: {
        id: 'pt-1',
        name: 'Many Executions',
        status: 'ACTIVE',
        currentVersion: '1.0.0',
        tags: [],
        versions: [],
        executions: manyExecutions.slice(0, 20),
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{ promptTemplateDetail: any }>(PROMPT_DETAIL_QUERY, { id: 'pt-1' })

    expect(result.promptTemplateDetail.executions).toHaveLength(20)
  })

  it('handles GraphQL error for PromptTemplateDetail', async () => {
    const graphqlError = {
      response: {
        errors: [
          {
            message: 'Template not found',
            extensions: {
              errorCode: 'PROMPT-404-001',
              traceId: 'trace-prompt-789',
            },
          },
        ],
      },
    }
    mockRequest.mockRejectedValueOnce(graphqlError)

    try {
      await graphqlRequest(PROMPT_DETAIL_QUERY, { id: 'nonexistent' })
      expect.unreachable('Should have thrown')
    } catch (err) {
      expect(err).toBeInstanceOf(GraphQLError)
      const gqlErr = err as InstanceType<typeof GraphQLError>
      expect(gqlErr.message).toBe('Template not found')
      expect(gqlErr.errorCode).toBe('PROMPT-404-001')
    }
  })

  it('passes template id variable to GraphQL', async () => {
    mockRequest.mockResolvedValueOnce({
      promptTemplateDetail: {
        id: 'pt-42',
        name: 'Var Test',
        status: 'ACTIVE',
        currentVersion: '1.0.0',
        tags: [],
        versions: [],
        executions: [],
      },
    })

    await graphqlRequest(PROMPT_DETAIL_QUERY, { id: 'pt-42' })

    expect(mockRequest).toHaveBeenCalledWith(PROMPT_DETAIL_QUERY, { id: 'pt-42' })
  })

  it('handles empty executions list', async () => {
    const mockData = {
      promptTemplateDetail: {
        id: 'pt-1',
        name: 'No Executions',
        status: 'ACTIVE',
        currentVersion: '1.0.0',
        tags: [],
        versions: [],
        executions: [],
      },
    }
    mockRequest.mockResolvedValueOnce(mockData)

    const result = await graphqlRequest<{ promptTemplateDetail: any }>(PROMPT_DETAIL_QUERY, { id: 'pt-1' })

    expect(result.promptTemplateDetail.executions).toEqual([])
  })
})
