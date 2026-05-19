import api from '../index'
import type {
  EntitlementBundle,
  TenantOverride,
  UserGrant,
  QuotaPolicy
} from '@/types'

export const EntitlementAdminAPI = {
  async getBundles(status?: string): Promise<EntitlementBundle[]> {
    const params = status ? { status } : {}
    const { data } = await api.get('/admin/entitlements/bundles', { params })
    return data
  },

  async getBundle(bundleId: string): Promise<EntitlementBundle> {
    const { data } = await api.get(`/admin/entitlements/bundles/${bundleId}`)
    return data
  },

  async createBundle(bundle: Omit<EntitlementBundle, 'bundleId' | 'createdAt' | 'updatedAt'>): Promise<EntitlementBundle> {
    const { data } = await api.post('/admin/entitlements/bundles', bundle)
    return data
  },

  async updateBundle(bundleId: string, updates: Partial<Omit<EntitlementBundle, 'bundleId' | 'createdAt' | 'updatedAt'>>): Promise<EntitlementBundle> {
    const { data } = await api.put(`/admin/entitlements/bundles/${bundleId}`, updates)
    return data
  },

  async archiveBundle(bundleId: string): Promise<void> {
    await api.post(`/admin/entitlements/bundles/${bundleId}/archive`)
  },

  async getTenantOverrides(tenantId?: string): Promise<TenantOverride[]> {
    const params = tenantId ? { tenantId } : {}
    const { data } = await api.get('/admin/entitlements/overrides', { params })
    return data
  },

  async createTenantOverride(override: Omit<TenantOverride, 'overrideId' | 'createdAt'>): Promise<TenantOverride> {
    const { data } = await api.post('/admin/entitlements/overrides', override)
    return data
  },

  async updateTenantOverride(overrideId: string, updates: Partial<Omit<TenantOverride, 'overrideId' | 'createdAt'>>): Promise<TenantOverride> {
    const { data } = await api.put(`/admin/entitlements/overrides/${overrideId}`, updates)
    return data
  },

  async deleteTenantOverride(overrideId: string): Promise<void> {
    await api.delete(`/admin/entitlements/overrides/${overrideId}`)
  },

  async getUserGrants(userId?: string): Promise<UserGrant[]> {
    const params = userId ? { userId } : {}
    const { data } = await api.get('/admin/entitlements/grants', { params })
    return data
  },

  async grantUserEntitlement(grant: Omit<UserGrant, 'grantId' | 'createdAt'>): Promise<UserGrant> {
    const { data } = await api.post('/admin/entitlements/grants', grant)
    return data
  },

  async revokeUserEntitlement(grantId: string): Promise<void> {
    await api.delete(`/admin/entitlements/grants/${grantId}`)
  },

  async getQuotaPolicies(scope?: string, scopeId?: string): Promise<QuotaPolicy[]> {
    const params: Record<string, string> = {}
    if (scope) params.scope = scope
    if (scopeId) params.scopeId = scopeId
    const { data } = await api.get('/admin/entitlements/quota-policies', { params })
    return data
  },

  async createQuotaPolicy(policy: Omit<QuotaPolicy, 'policyId'>): Promise<QuotaPolicy> {
    const { data } = await api.post('/admin/entitlements/quota-policies', policy)
    return data
  },

  async updateQuotaPolicy(policyId: string, updates: Partial<Omit<QuotaPolicy, 'policyId'>>): Promise<QuotaPolicy> {
    const { data } = await api.put(`/admin/entitlements/quota-policies/${policyId}`, updates)
    return data
  },

  async deleteQuotaPolicy(policyId: string): Promise<void> {
    await api.delete(`/admin/entitlements/quota-policies/${policyId}`)
  }
}
