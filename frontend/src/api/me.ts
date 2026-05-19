import api from './index'
import type {
  MyCapabilities,
  UsageSummary,
  BillingLedgerEntry,
  Invoice,
  CreditWallet,
  CreditTransaction,
  SubscriptionPlan,
  UpgradeOption,
  EntitlementExplanation
} from '@/types'

export const MeEntitlementAPI = {
  async getMyCapabilities(): Promise<MyCapabilities> {
    const { data } = await api.get('/entitlements/me/capabilities')
    return data
  },

  async getUsageSummary(): Promise<UsageSummary> {
    const { data } = await api.get('/entitlements/me/usage')
    return data
  },

  async getBillingHistory(page = 0, size = 20): Promise<{ entries: BillingLedgerEntry[]; total: number }> {
    const { data } = await api.get('/billing/me/history', { params: { page, size } })
    return data
  },

  async getInvoices(): Promise<Invoice[]> {
    const { data } = await api.get('/billing/me/invoices')
    return data
  },

  async getInvoice(invoiceId: string): Promise<Invoice> {
    const { data } = await api.get(`/billing/me/invoices/${invoiceId}`)
    return data
  },

  async getCreditBalance(): Promise<CreditWallet> {
    const { data } = await api.get('/billing/me/credits')
    return data
  },

  async getCreditTransactions(page = 0, size = 20): Promise<{ transactions: CreditTransaction[]; total: number }> {
    const { data } = await api.get('/billing/me/credits/transactions', { params: { page, size } })
    return data
  },

  async getCurrentPlan(): Promise<SubscriptionPlan> {
    const { data } = await api.get('/billing/me/plan')
    return data
  },

  async getUpgradeOptions(): Promise<UpgradeOption[]> {
    const { data } = await api.get('/billing/me/upgrades')
    return data
  },

  async getEntitlementExplanation(featureKey: string): Promise<EntitlementExplanation> {
    const { data } = await api.get(`/entitlements/me/explain/${featureKey}`)
    return data
  },

  async topUpCredits(amount: number, currency = 'USD'): Promise<CreditTransaction> {
    const { data } = await api.post('/billing/me/credits/topup', { amount, currency })
    return data
  },

  async submitPayment(paymentId: string): Promise<void> {
    await api.post('/payments/confirm', { paymentId })
  }
}
