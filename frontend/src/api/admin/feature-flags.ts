import api from '../index'

export interface AdminFeatureFlag {
  flagKey: string
  displayName: string
  enabled: boolean
  scope: string
  targetTier: string
  description: string
}

export interface FeatureFlagOverview {
  module?: string
  status?: string
  description?: string
  unleashEnabled?: boolean
  policyCount?: number
}

export interface FeatureFlagDefinition {
  flagKey: string
  name: string
  description: string
  type: 'BOOLEAN' | 'STRING' | 'NUMBER' | 'JSON'
  defaultValue: string
  variants: FeatureFlagVariant[]
  targetingRules: FeatureFlagTargetingRule[]
  owner: string
  tags: string[]
  enabled: boolean
  createdAt?: string
  updatedAt?: string
}

export interface FeatureFlagVariant {
  key: string
  value: string
}

export interface FeatureFlagTargetingRule {
  ruleId?: string
  priority: number
  name: string
  conditions: FeatureFlagCondition[]
  percentage: number
  variantKey?: string
  startAt?: string
  endAt?: string
}

export interface FeatureFlagCondition {
  attribute: string
  operator: 'EQUALS' | 'IN' | 'NOT_IN' | 'GT' | 'LT' | 'GTE' | 'LTE' | 'CONTAINS'
  value: string
}

export interface FeatureFlagEvaluationContext {
  tenant?: string
  workspace?: string
  user?: string
  role?: string
  group?: string
  tier?: string
  region?: string
  requestSource?: string
  environment?: string
}

export interface FeatureFlagEvaluationResult {
  flagKey: string
  enabled: boolean
  variant?: string
  matchedRule?: string
  reason: string
  steps: FeatureFlagEvaluationStep[]
}

export interface FeatureFlagEvaluationStep {
  step: string
  result: string
  detail?: string
}

export interface FeatureFlagEvaluationLogEntry {
  id?: string
  timestamp?: string
  flagKey: string
  tenant?: string
  workspace?: string
  user?: string
  context?: Record<string, string>
  result: string
  variant?: string
  matchedRule?: string
}

export const FeatureFlagAPI = {
  async getCapabilities(tenantId?: string, userId?: string) {
    const headers: Record<string, string> = {}
    if (tenantId) headers['X-Tenant-ID'] = tenantId
    if (userId) headers['X-User-ID'] = userId
    const { data } = await api.get('/entitlements/me/capabilities', { headers })
    return data
  },

  async getEntitlements(tenantId: string) {
    const { data } = await api.get(`/tenants/${tenantId}/entitlements`)
    return data
  },

  async getPolicyGovernanceOverview(): Promise<FeatureFlagOverview> {
    const { data } = await api.get('/policy/governance/overview')
    return data
  },

  async listFeatureFlags(): Promise<FeatureFlagDefinition[]> {
    const { data } = await api.get('/admin/feature-flags')
    return data
  },

  async getFeatureFlag(flagKey: string): Promise<FeatureFlagDefinition> {
    const { data } = await api.get(`/admin/feature-flags/${flagKey}`)
    return data
  },

  async createFeatureFlag(flag: Omit<FeatureFlagDefinition, 'createdAt' | 'updatedAt'>): Promise<FeatureFlagDefinition> {
    const { data } = await api.post('/admin/feature-flags', flag)
    return data
  },

  async updateFeatureFlag(flagKey: string, updates: Partial<FeatureFlagDefinition>): Promise<FeatureFlagDefinition> {
    const { data } = await api.put(`/admin/feature-flags/${flagKey}`, updates)
    return data
  },

  async archiveFeatureFlag(flagKey: string): Promise<void> {
    await api.post(`/admin/feature-flags/${flagKey}/archive`)
  },

  async enableFeatureFlag(flagKey: string): Promise<void> {
    await api.post(`/admin/feature-flags/${flagKey}/enable`)
  },

  async disableFeatureFlag(flagKey: string): Promise<void> {
    await api.post(`/admin/feature-flags/${flagKey}/disable`)
  },

  async evaluateFeatureFlag(flagKey: string, context: FeatureFlagEvaluationContext): Promise<FeatureFlagEvaluationResult> {
    const { data } = await api.post(`/admin/feature-flags/${flagKey}/evaluate`, context)
    return data
  },

  async getEvaluationLogs(params?: {
    flagKey?: string
    tenant?: string
    workspace?: string
    user?: string
    result?: string
    page?: number
    size?: number
  }): Promise<{ entries: FeatureFlagEvaluationLogEntry[]; total: number }> {
    const { data } = await api.get('/admin/feature-flags/evaluation-logs', { params })
    return data
  },

  async getFeatureFlagSummary(): Promise<{
    total: number
    active: number
    beta: number
    recentChanges: Array<{ flagKey: string; change: string; timestamp: string }>
  }> {
    const { data } = await api.get('/admin/feature-flags/summary')
    return data
  },
}
