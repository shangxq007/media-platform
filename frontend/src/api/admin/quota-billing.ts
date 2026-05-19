import api from '../index'

export interface QuotaBucket {
  key?: string
  limit?: number
  used?: number
  remaining?: number
  unit?: string
}

export interface TenantUsage {
  tenantId?: string
  renderMinutes?: number
  storageGb?: number
  apiCalls?: number
  period?: string
}

export interface BillingState {
  subjectId?: string
  balance?: number
  currency?: string
  status?: string
  lastInvoiceAt?: string
}

export const QuotaBillingAPI = {
  async getQuota(tenantId: string): Promise<QuotaBucket[]> {
    const { data } = await api.get(`/tenants/${tenantId}/quota`)
    return data
  },
  async getUsage(tenantId: string): Promise<TenantUsage> {
    const { data } = await api.get(`/tenants/${tenantId}/usage`)
    return data
  },
  async resetQuota(tenantId: string): Promise<void> {
    await api.post(`/tenants/${tenantId}/quota/reset`)
  },
  async getBillingState(subjectId: string): Promise<BillingState> {
    const { data } = await api.get(`/billing/subjects/${subjectId}`)
    return data
  },
  async confirmPayment(paymentId: string): Promise<void> {
    await api.post('/payments/confirm', { paymentId })
  },
  async createCheckoutSession(tenantId: string, plan: string): Promise<{ sessionId: string; url?: string }> {
    const { data } = await api.post('/commerce/checkout-sessions', { tenantId, plan })
    return data
  },
  async cancelCheckoutSession(sessionId: string): Promise<void> {
    await api.post(`/commerce/checkout-sessions/${sessionId}/cancel`)
  },
  async getRecentCommerceEvents(tenantId: string): Promise<unknown[]> {
    const { data } = await api.get(`/commerce/events/recent/${tenantId}`)
    return data
  },
  async getTotalRevenue(tenantId: string): Promise<{ total: number; currency?: string }> {
    const { data } = await api.get(`/commerce/revenue/${tenantId}`)
    return data
  },
}
