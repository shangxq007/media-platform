import api from './index'

export const PromptAPI = {
  async listTemplates(status?: string) {
    const params = status ? { status } : {}
    const { data } = await api.get('/prompts/templates', { params })
    return data
  },

  async getTemplate(templateId: string) {
    const { data } = await api.get(`/prompts/templates/${templateId}`)
    return data
  },

  async createTemplate(template: {
    name: string
    description?: string
    category?: string
    tags?: string[]
    owner?: string
    schemaVersion?: string
  }) {
    const { data } = await api.post('/prompts/templates', template)
    return data
  },

  async updateTemplate(templateId: string, updates: {
    name?: string
    description?: string
    category?: string
    tags?: string[]
  }) {
    const { data } = await api.put(`/prompts/templates/${templateId}`, updates)
    return data
  },

  async getVersions(templateId: string) {
    const { data } = await api.get(`/prompts/templates/${templateId}/versions`)
    return data
  },

  async createVersion(templateId: string, version: {
    templateBody: string
    variableSchemaJson?: string
    changelog?: string
    createdBy?: string
  }) {
    const { data } = await api.post(`/prompts/templates/${templateId}/versions`, version)
    return data
  },

  async rollback(templateId: string, targetVersion: string) {
    const { data } = await api.post(`/prompts/templates/${templateId}/rollback`, { targetVersion })
    return data
  },

  async deprecateTemplate(templateId: string) {
    const { data } = await api.post(`/prompts/templates/${templateId}/deprecate`)
    return data
  },

  async render(templateId: string, request: {
    promptVersion?: string
    variables?: Record<string, unknown>
    dryRun?: boolean
  }) {
    const { data } = await api.post(`/prompts/templates/${templateId}/render`, request)
    return data
  },

  async validate(templateId: string) {
    const { data } = await api.post(`/prompts/templates/${templateId}/validate`)
    return data
  },

  async analyzeRisk(request: {
    content: string
    variables?: Record<string, unknown>
    tenantId?: string
    userId?: string
    environment?: string
    category?: string
  }) {
    const { data } = await api.post('/prompts/risk/analyze', request)
    return data
  },

  async startExecution(request: {
    templateId: string
    promptVersion?: string
    tenantId?: string
    userId?: string
    modelProvider?: string
    modelName?: string
    inputVariables?: Record<string, unknown>
    relatedPromptFile?: string
    relatedManifestEntry?: string
  }) {
    const { data } = await api.post('/prompts/executions', request)
    return data
  },

  async listExecutions(templateId?: string) {
    const params = templateId ? { templateId } : {}
    const { data } = await api.get('/prompts/executions', { params })
    return data
  },

  async getExecution(executionId: string) {
    const { data } = await api.get(`/prompts/executions/${executionId}`)
    return data
  },

  async evaluateExecution(executionId: string, evaluation: {
    evaluatorUserId: string
    acceptanceCriteriaMet: boolean
    documentationUpdated: boolean
    manifestUpdated: boolean
    testsPass: boolean
    hasHighRiskChanges: boolean
    hasHumanReviewItems: boolean
    hasScopeCreep: boolean
    hasFalseClaims: boolean
  }) {
    const { data } = await api.post(`/prompts/executions/${executionId}/evaluate`, evaluation)
    return data
  },

  async completeExecution(executionId: string, outputSummary: string) {
    const { data } = await api.post(`/prompts/executions/${executionId}/complete`, { outputSummary })
    return data
  },

  async failExecution(executionId: string, errorCode: string, errorDetails?: string) {
    const { data } = await api.post(`/prompts/executions/${executionId}/fail`, { errorCode, errorDetails })
    return data
  },

  async archiveTemplate(templateId: string) {
    const { data } = await api.post(`/prompts/templates/${templateId}/archive`)
    return data
  },

  async scanFiles(fileContents: string[], fileNames: string[]) {
    const { data } = await api.post('/prompts/files/scan', { fileContents, fileNames })
    return data
  },

  async importFile(content: string, fileName: string, owner: string) {
    const { data } = await api.post('/prompts/files/import', { content, fileName, owner })
    return data
  },

  async validateManifest(manifest: Record<string, unknown>) {
    const { data } = await api.post('/prompts/manifest/validate', manifest)
    return data
  }
}
