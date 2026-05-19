import api from '../index'

export interface ABACPolicy {
  policyId: string
  name: string
  code: string
  description: string
  status: 'ACTIVE' | 'DRAFT' | 'ARCHIVED'
  versionCount: number
  rules: PolicyRule[]
  createdAt?: string
  updatedAt?: string
}

export interface PolicyRule {
  ruleId?: string
  name: string
  effect: 'ALLOW' | 'DENY' | 'REQUIRE_REVIEW' | 'DEGRADE' | 'WARN'
  priority: number
  conditions: PolicyCondition[]
  featureFlagConditions: PolicyFeatureFlagCondition[]
  status: 'ACTIVE' | 'INACTIVE'
}

export interface PolicyCondition {
  attribute: string
  operator: 'EQUALS' | 'IN' | 'NOT_IN' | 'GT' | 'LT' | 'GTE' | 'LTE' | 'CONTAINS' | 'EXISTS'
  value: string
}

export interface PolicyFeatureFlagCondition {
  flagKey: string
  expectedValue: string
}

export interface PolicySimulationRequest {
  policyCode: string
  context: PolicySimulationContext
}

export interface PolicySimulationContext {
  user?: string
  role?: string
  tenant?: string
  workspace?: string
  resource?: string
  action?: string
  tier?: string
  region?: string
}

export interface PolicySimulationResult {
  decision: 'ALLOW' | 'DENY' | 'REQUIRE_REVIEW' | 'DEGRADE' | 'WARN'
  explanation: string
  matchedRules: MatchedPolicyRule[]
  featureFlagResults: PolicyFeatureFlagResult[]
  decisionChain: DecisionChainStep[]
}

export interface MatchedPolicyRule {
  ruleId: string
  ruleName: string
  effect: string
  matchedConditions: string[]
}

export interface PolicyFeatureFlagResult {
  flagKey: string
  evaluated: boolean
  result: string
}

export interface DecisionChainStep {
  step: string
  decision: string
  detail?: string
}

export interface PolicySummary {
  total: number
  active: number
  recentChanges: Array<{ policyCode: string; change: string; timestamp: string }>
}

export const PolicyAdminAPI = {
  async listPolicies(): Promise<ABACPolicy[]> {
    const { data } = await api.get('/admin/policies')
    return data
  },

  async getPolicy(policyId: string): Promise<ABACPolicy> {
    const { data } = await api.get(`/admin/policies/${policyId}`)
    return data
  },

  async createPolicy(policy: Omit<ABACPolicy, 'policyId' | 'createdAt' | 'updatedAt'>): Promise<ABACPolicy> {
    const { data } = await api.post('/admin/policies', policy)
    return data
  },

  async updatePolicy(policyId: string, updates: Partial<ABACPolicy>): Promise<ABACPolicy> {
    const { data } = await api.put(`/admin/policies/${policyId}`, updates)
    return data
  },

  async archivePolicy(policyId: string): Promise<void> {
    await api.post(`/admin/policies/${policyId}/archive`)
  },

  async simulatePolicy(request: PolicySimulationRequest): Promise<PolicySimulationResult> {
    const { data } = await api.post('/admin/policies/simulate', request)
    return data
  },

  async getPolicySummary(): Promise<PolicySummary> {
    const { data } = await api.get('/admin/policies/summary')
    return data
  },
}
