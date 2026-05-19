import api from './index'

export interface NlqPreviewResponse {
  previewId: string
  question: string
  intent: string
  datasets: string[]
  sqlDraft: string
  sqlExplanation: string
  parameters: Record<string, unknown>
  safety: {
    safe: boolean
    violations: string[]
    riskLevel: string
    requiresReview: boolean
  }
  accessDecision: string
  riskLevel: string
  requiresConfirmation: boolean
  chartSuggestions: string[]
  warnings: string[]
}

export interface NlqExecuteResponse {
  queryId: string
  columns: string[]
  rows: Record<string, unknown>[]
  rowCount: number
  truncated: boolean
  durationMs: number
  summary: string
  chartSuggestions: Array<{
    chartType: string
    xField: string
    yField: string
    groupField: string
    title: string
    reason: string
  }>
  warnings: string[]
}

export interface DatasetInfo {
  datasetKey: string
  name: string
  description: string
  viewName: string
  module: string
  enabled: boolean
  tenantScoped: boolean
  workspaceScoped: boolean
  userScoped: boolean
  maxRows: number
  maxLookbackDays: number
  sensitivityLevel: string
}

export interface ReportInfo {
  reportId: string
  tenantId: string
  workspaceId: string
  name: string
  description: string
  createdBy: string
  visibility: string
  createdAt: string
  updatedAt: string
  archived: boolean
}

export interface ReportExecutionResponse {
  executionId: string
  reportId: string
  status: string
  rowCount: number
  durationMs: number
  errorCode: string | null
  createdAt: string
}

export const NlqAPI = {
  async preview(question: string, workspaceId?: string, userId?: string,
      roles?: string[], permissions?: string[]): Promise<NlqPreviewResponse> {
    const { data } = await api.post('/analytics/nlq/preview', {
      question, userId, workspaceId, roles, permissions,
    })
    return data
  },

  async execute(sql: string, question?: string, workspaceId?: string,
      userId?: string, maxRows?: number, confirmed?: boolean): Promise<NlqExecuteResponse> {
    const { data } = await api.post('/analytics/nlq/execute', {
      sql, question, userId, workspaceId, maxRows, confirmed,
    })
    return data
  },

  async explain(question: string, workspaceId?: string, userId?: string,
      roles?: string[], permissions?: string[]): Promise<unknown> {
    const { data } = await api.post('/analytics/nlq/explain', {
      question, userId, workspaceId, roles, permissions,
    })
    return data
  },

  async chartSuggestions(columns: string[], rows: Record<string, unknown>[],
      userId?: string): Promise<unknown> {
    const { data } = await api.post('/analytics/nlq/chart-suggestions', {
      userId, columns, rows,
    })
    return data
  },

  async listDatasets(workspaceId?: string, userId?: string,
      _roles?: string[]): Promise<{ datasets: DatasetInfo[]; total: number }> {
    const params: Record<string, string> = {}
    if (workspaceId) params.workspaceId = workspaceId
    if (userId) params.userId = userId
    const { data } = await api.get('/analytics/nlq/datasets', { params })
    return data
  },

  async getDataset(datasetKey: string): Promise<{ dataset: DatasetInfo }> {
    const { data } = await api.get(`/analytics/nlq/datasets/${datasetKey}`)
    return data
  },
}

export const ReportAPI = {
  async createReport(report: Omit<ReportInfo, 'reportId' | 'createdAt' | 'updatedAt' | 'archived'>): Promise<ReportInfo> {
    const { data } = await api.post('/analytics/reports', report)
    return data
  },

  async listReports(tenantId?: string, workspaceId?: string): Promise<{ reports: ReportInfo[]; total: number }> {
    const params: Record<string, string> = {}
    if (tenantId) params.tenantId = tenantId
    if (workspaceId) params.workspaceId = workspaceId
    const { data } = await api.get('/analytics/reports', { params })
    return data
  },

  async getReport(reportId: string): Promise<ReportInfo> {
    const { data } = await api.get(`/analytics/reports/${reportId}`)
    return data
  },

  async updateReport(reportId: string, updates: Partial<ReportInfo>): Promise<ReportInfo> {
    const { data } = await api.put(`/analytics/reports/${reportId}`, updates)
    return data
  },

  async executeReport(reportId: string, userId?: string, tenantId?: string,
      workspaceId?: string): Promise<ReportExecutionResponse> {
    const { data } = await api.post(`/analytics/reports/${reportId}/execute`, null, {
      params: { userId, tenantId, workspaceId },
    })
    return data
  },

  async archiveReport(reportId: string): Promise<{ reportId: string; archived: boolean }> {
    const { data } = await api.post(`/analytics/reports/${reportId}/archive`)
    return data
  },
}
