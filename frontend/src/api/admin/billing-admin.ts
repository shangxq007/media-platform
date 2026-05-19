import api from '../index'
import type {
  BillingPlan,
  PricingRule,
  UsageRecord,
  CreditWallet,
  BillingQuote,
  Invoice
} from '@/types'

export const BillingAdminAPI = {
  async getBillingPlans(): Promise<BillingPlan[]> {
    const { data } = await api.get('/admin/billing/plans')
    return data
  },

  async getBillingPlan(planId: string): Promise<BillingPlan> {
    const { data } = await api.get(`/admin/billing/plans/${planId}`)
    return data
  },

  async createBillingPlan(plan: Omit<BillingPlan, 'planId' | 'createdAt' | 'updatedAt'>): Promise<BillingPlan> {
    const { data } = await api.post('/admin/billing/plans', plan)
    return data
  },

  async updateBillingPlan(planId: string, updates: Partial<Omit<BillingPlan, 'planId' | 'createdAt' | 'updatedAt'>>): Promise<BillingPlan> {
    const { data } = await api.put(`/admin/billing/plans/${planId}`, updates)
    return data
  },

  async archiveBillingPlan(planId: string): Promise<void> {
    await api.post(`/admin/billing/plans/${planId}/archive`)
  },

  async getPricingRules(tier?: string): Promise<PricingRule[]> {
    const params = tier ? { tier } : {}
    const { data } = await api.get('/admin/billing/pricing', { params })
    return data
  },

  async createPricingRule(rule: Omit<PricingRule, 'ruleId'>): Promise<PricingRule> {
    const { data } = await api.post('/admin/billing/pricing', rule)
    return data
  },

  async updatePricingRule(ruleId: string, updates: Partial<Omit<PricingRule, 'ruleId'>>): Promise<PricingRule> {
    const { data } = await api.put(`/admin/billing/pricing/${ruleId}`, updates)
    return data
  },

  async deletePricingRule(ruleId: string): Promise<void> {
    await api.delete(`/admin/billing/pricing/${ruleId}`)
  },

  async getUsageRecords(tenantId?: string, page = 0, size = 50): Promise<{ records: UsageRecord[]; total: number }> {
    const params: Record<string, string | number> = { page, size }
    if (tenantId) params.tenantId = tenantId
    const { data } = await api.get('/admin/billing/usage', { params })
    return data
  },

  async getRatedUsage(tenantId?: string, page = 0, size = 50): Promise<{ records: UsageRecord[]; total: number }> {
    const params: Record<string, string | number> = { page, size }
    if (tenantId) params.tenantId = tenantId
    const { data } = await api.get('/admin/billing/usage/rated', { params })
    return data
  },

  async getCreditWallets(subjectType?: string, subjectId?: string): Promise<CreditWallet[]> {
    const params: Record<string, string> = {}
    if (subjectType) params.subjectType = subjectType
    if (subjectId) params.subjectId = subjectId
    const { data } = await api.get('/admin/billing/credits', { params })
    return data
  },

  async adminTopUpCredits(walletId: string, amount: number, description: string): Promise<CreditWallet> {
    const { data } = await api.post(`/admin/billing/credits/${walletId}/topup`, { amount, description })
    return data
  },

  async getBillingQuotes(tenantId?: string): Promise<BillingQuote[]> {
    const params = tenantId ? { tenantId } : {}
    const { data } = await api.get('/admin/billing/quotes', { params })
    return data
  },

  async createBillingQuote(tenantId: string, tier: string): Promise<BillingQuote> {
    const { data } = await api.post('/admin/billing/quotes', { tenantId, tier })
    return data
  },

  async getInvoices(tenantId?: string, status?: string): Promise<Invoice[]> {
    const params: Record<string, string> = {}
    if (tenantId) params.tenantId = tenantId
    if (status) params.status = status
    const { data } = await api.get('/admin/billing/invoices', { params })
    return data
  },

  async getInvoicePreview(tenantId: string): Promise<Invoice> {
    const { data } = await api.post('/admin/billing/invoices/preview', { tenantId })
    return data
  },

  async issueInvoice(invoiceId: string): Promise<Invoice> {
    const { data } = await api.post(`/admin/billing/invoices/${invoiceId}/issue`)
    return data
  },

  async voidInvoice(invoiceId: string): Promise<void> {
    await api.post(`/admin/billing/invoices/${invoiceId}/void`)
  }
}
