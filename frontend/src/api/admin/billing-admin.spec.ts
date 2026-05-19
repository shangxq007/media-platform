import { describe, it, expect, vi, beforeEach } from 'vitest'
import { BillingAdminAPI } from './billing-admin'
import api from '../index'

vi.mock('../index', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } }
  }
}))

describe('BillingAdminAPI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('getBillingPlans calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await BillingAdminAPI.getBillingPlans()
    expect(api.get).toHaveBeenCalledWith('/admin/billing/plans')
  })

  it('createBillingPlan posts correctly', async () => {
    const plan = { name: 'Pro', tier: 'PRO', description: '', monthlyPrice: 29, annualPrice: 290, currency: 'USD', trialDays: 14, isActive: true, features: [], quota: {} as any }
    vi.mocked(api.post).mockResolvedValueOnce({ data: { planId: 'p1', ...plan } })
    const result = await BillingAdminAPI.createBillingPlan(plan)
    expect(api.post).toHaveBeenCalledWith('/admin/billing/plans', plan)
    expect(result.planId).toBe('p1')
  })

  it('getPricingRules calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await BillingAdminAPI.getPricingRules('PRO')
    expect(api.get).toHaveBeenCalledWith('/admin/billing/pricing', { params: { tier: 'PRO' } })
  })

  it('getUsageRecords with pagination', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: { records: [], total: 0 } })
    await BillingAdminAPI.getUsageRecords('t1', 0, 50)
    expect(api.get).toHaveBeenCalledWith('/admin/billing/usage', { params: { tenantId: 't1', page: 0, size: 50 } })
  })

  it('getCreditWallets calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await BillingAdminAPI.getCreditWallets('TENANT', 't1')
    expect(api.get).toHaveBeenCalledWith('/admin/billing/credits', { params: { subjectType: 'TENANT', subjectId: 't1' } })
  })

  it('adminTopUpCredits posts correctly', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { walletId: 'w1', balance: 150 } })
    await BillingAdminAPI.adminTopUpCredits('w1', 50, 'Test top-up')
    expect(api.post).toHaveBeenCalledWith('/admin/billing/credits/w1/topup', { amount: 50, description: 'Test top-up' })
  })

  it('getBillingQuotes calls correct endpoint', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: [] })
    await BillingAdminAPI.getBillingQuotes('t1')
    expect(api.get).toHaveBeenCalledWith('/admin/billing/quotes', { params: { tenantId: 't1' } })
  })

  it('getInvoicePreview posts correctly', async () => {
    vi.mocked(api.post).mockResolvedValueOnce({ data: { invoiceId: 'inv1', amount: 99 } })
    await BillingAdminAPI.getInvoicePreview('t1')
    expect(api.post).toHaveBeenCalledWith('/admin/billing/invoices/preview', { tenantId: 't1' })
  })

  it('deletePricingRule calls delete', async () => {
    vi.mocked(api.delete).mockResolvedValueOnce({})
    await BillingAdminAPI.deletePricingRule('pr1')
    expect(api.delete).toHaveBeenCalledWith('/admin/billing/pricing/pr1')
  })
})
