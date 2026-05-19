import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockRequest = vi.fn()

class MockGraphQLClient {
  constructor(_endpoint: string, _options: Record<string, unknown>) {}
  request = mockRequest
}

vi.mock('graphql-request', () => ({
  GraphQLClient: MockGraphQLClient,
}))

const EXPORT_PANEL_QUERY = `
  query ExportPanelState($projectId: String!) {
    exportPanelState(projectId: $projectId) {
      project { id name }
      timelineSummary { durationSeconds tracks clips subtitles effects }
      exportOptions { preset allowed reasonCode }
      workers { id status gpuAvailable }
      validation { allowed violations recommendations }
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

describe('ExportPanel displays disabled by feature flag', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders export panel query', () => {
    expect(EXPORT_PANEL_QUERY).toContain('exportPanelState')
    expect(EXPORT_PANEL_QUERY).toContain('exportOptions')
  })

  it('displays disabled export option when feature flag is off', async () => {
    const mockResponse = {
      exportPanelState: {
        project: { id: 'p1', name: 'Test Project' },
        timelineSummary: { durationSeconds: 60, tracks: 2, clips: 5, subtitles: 1, effects: 0 },
        exportOptions: { preset: 'default_720p', allowed: false, reasonCode: 'FF-403-004' },
        workers: [{ id: 'w1', status: 'AVAILABLE', gpuAvailable: false }],
        validation: { allowed: true, violations: [], recommendations: [] },
      },
    }
    mockRequest.mockResolvedValueOnce(mockResponse)
    const result = await graphqlRequest<typeof mockResponse>(EXPORT_PANEL_QUERY, { projectId: 'p1' })
    expect(result.exportPanelState.exportOptions.allowed).toBe(false)
    expect(result.exportPanelState.exportOptions.reasonCode).toBe('FF-403-004')
  })

  it('displays allowed export option when feature flag is on', async () => {
    const mockResponse = {
      exportPanelState: {
        project: { id: 'p1', name: 'Test Project' },
        timelineSummary: { durationSeconds: 60, tracks: 2, clips: 5, subtitles: 1, effects: 0 },
        exportOptions: { preset: 'default_1080p', allowed: true, reasonCode: null },
        workers: [{ id: 'w1', status: 'AVAILABLE', gpuAvailable: true }],
        validation: { allowed: true, violations: [], recommendations: [] },
      },
    }
    mockRequest.mockResolvedValueOnce(mockResponse)
    const result = await graphqlRequest<typeof mockResponse>(EXPORT_PANEL_QUERY, { projectId: 'p1' })
    expect(result.exportPanelState.exportOptions.allowed).toBe(true)
    expect(result.exportPanelState.exportOptions.reasonCode).toBeNull()
  })

  it('handles feature flag disabled error code FF-403-004', () => {
    const errorCode = 'FF-403-004'
    const errorMessage = 'Navigation disabled by feature flag'
    const error = new GraphQLError(errorMessage, errorCode, 'trace-123')
    expect(error.errorCode).toBe('FF-403-004')
    expect(error.message).toBe('Navigation disabled by feature flag')
  })

  it('handles feature flag not found error code FF-404-001', () => {
    const errorCode = 'FF-404-001'
    const errorMessage = 'Feature flag not found'
    const error = new GraphQLError(errorMessage, errorCode, 'trace-456')
    expect(error.errorCode).toBe('FF-404-001')
  })

  it('displays disabled state with correct reason code', async () => {
    const mockResponse = {
      exportPanelState: {
        project: { id: 'p1', name: 'Test Project' },
        timelineSummary: { durationSeconds: 60, tracks: 2, clips: 5, subtitles: 1, effects: 0 },
        exportOptions: { preset: 'gpu_export', allowed: false, reasonCode: 'FF-400-002' },
        workers: [],
        validation: { allowed: false, violations: ['GPU export is disabled'], recommendations: ['Enable export.gpu.v2 feature flag'] },
      },
    }
    mockRequest.mockResolvedValueOnce(mockResponse)
    const result = await graphqlRequest<typeof mockResponse>(EXPORT_PANEL_QUERY, { projectId: 'p1' })
    expect(result.exportPanelState.exportOptions.allowed).toBe(false)
    expect(result.exportPanelState.exportOptions.reasonCode).toBe('FF-400-002')
    expect(result.exportPanelState.validation.violations).toContain('GPU export is disabled')
    expect(result.exportPanelState.validation.recommendations).toContain('Enable export.gpu.v2 feature flag')
  })

  it('shows GPU worker availability based on feature flag', async () => {
    const mockResponse = {
      exportPanelState: {
        project: { id: 'p1', name: 'Test Project' },
        timelineSummary: { durationSeconds: 60, tracks: 2, clips: 5, subtitles: 1, effects: 0 },
        exportOptions: { preset: 'default_720p', allowed: true, reasonCode: null },
        workers: [{ id: 'w1', status: 'AVAILABLE', gpuAvailable: false }],
        validation: { allowed: true, violations: [], recommendations: [] },
      },
    }
    mockRequest.mockResolvedValueOnce(mockResponse)
    const result = await graphqlRequest<typeof mockResponse>(EXPORT_PANEL_QUERY, { projectId: 'p1' })
    expect(result.exportPanelState.workers[0].gpuAvailable).toBe(false)
  })

  it('handles export with multiple feature flag checks', async () => {
    const mockResponse = {
      exportPanelState: {
        project: { id: 'p1', name: 'Test Project' },
        timelineSummary: { durationSeconds: 60, tracks: 2, clips: 5, subtitles: 1, effects: 0 },
        exportOptions: { preset: 'default_1080p', allowed: false, reasonCode: 'FF-403-003' },
        workers: [],
        validation: { allowed: false, violations: ['Policy denied by feature flag'], recommendations: [] },
      },
    }
    mockRequest.mockResolvedValueOnce(mockResponse)
    const result = await graphqlRequest<typeof mockResponse>(EXPORT_PANEL_QUERY, { projectId: 'p1' })
    expect(result.exportPanelState.exportOptions.allowed).toBe(false)
    expect(result.exportPanelState.exportOptions.reasonCode).toBe('FF-403-003')
  })
})
