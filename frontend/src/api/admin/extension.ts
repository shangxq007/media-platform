import api from '../index'

export interface ExtensionInfo {
  key: string
  name?: string
  version?: string
  status?: string
  description?: string
  author?: string
}

export interface ExtensionVersion {
  version?: string
  createdAt?: string
  status?: string
}

export interface ExtensionAuditEvent {
  id?: string
  eventType?: string
  extensionKey?: string
  details?: string
  createdAt?: string
}

export interface RoutingRule {
  id?: string
  extensionKey?: string
  ruleType?: string
  config?: Record<string, unknown>
}

export interface ResourceLimits {
  concurrency?: number
  queueSize?: number
  memoryMb?: number
  timeoutSeconds?: number
}

export const ExtensionAPI = {
  async listCatalog(): Promise<ExtensionInfo[]> {
    const { data } = await api.get('/extensions/catalog')
    return data
  },
  async getExtension(key: string): Promise<ExtensionInfo> {
    const { data } = await api.get(`/extensions/${key}`)
    return data
  },
  async getVersionHistory(key: string): Promise<ExtensionVersion[]> {
    const { data } = await api.get(`/extensions/${key}/history`)
    return data
  },
  async executeExtension(key: string, params?: Record<string, unknown>): Promise<unknown> {
    const { data } = await api.post(`/extensions/${key}/execute`, params || {})
    return data
  },
  async unloadExtension(key: string): Promise<void> {
    await api.delete(`/extensions/${key}`)
  },
  async rollbackExtension(key: string, targetVersion: string): Promise<void> {
    await api.post(`/extensions/${key}/rollback`, { targetVersion })
  },
  async createRollbackPoint(key: string): Promise<void> {
    await api.post(`/extensions/${key}/rollback-point`)
  },
  async getResourceLimits(key: string): Promise<ResourceLimits> {
    const { data } = await api.get(`/extensions/${key}/resource-limits`)
    return data
  },
  async getRoutingRules(key: string): Promise<RoutingRule[]> {
    const { data } = await api.get(`/extensions/${key}/routing-rules`)
    return data
  },
  async createRoutingRule(key: string, rule: Partial<RoutingRule>): Promise<void> {
    await api.post(`/extensions/${key}/routing-rules`, rule)
  },
  async getAuditEvents(key: string): Promise<ExtensionAuditEvent[]> {
    const { data } = await api.get(`/extensions/${key}/audit-events`)
    return data
  },
  async getRecentAuditEvents(): Promise<ExtensionAuditEvent[]> {
    const { data } = await api.get('/extensions/audit-events/recent')
    return data
  },
  async listCliTools(): Promise<{ toolKey: string; name?: string }[]> {
    const { data } = await api.get('/extensions/cli-tools')
    return data
  },
  async runCliTool(toolKey: string, args?: Record<string, unknown>): Promise<unknown> {
    const { data } = await api.post(`/extensions/cli-tools/${toolKey}/run`, args || {})
    return data
  },
}
