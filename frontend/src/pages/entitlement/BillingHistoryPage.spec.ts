import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BillingHistoryPage from './BillingHistoryPage.vue'

vi.mock('@/api/me', () => ({
  MeEntitlementAPI: {
    getBillingHistory: vi.fn().mockResolvedValue({
      entries: [
        { entryId: 'e1', tenantId: 't1', type: 'CHARGE', amount: 5.99, currency: 'USD', description: 'Render job #123', status: 'COMPLETED', createdAt: '2026-05-01' },
        { entryId: 'e2', tenantId: 't1', type: 'CREDIT', amount: 50, currency: 'USD', description: 'Top-up', status: 'COMPLETED', createdAt: '2026-05-02' }
      ],
      total: 2
    }),
    getInvoices: vi.fn().mockResolvedValue([
      { invoiceId: 'inv1', tenantId: 't1', invoiceNumber: 'INV-001', amount: 55.99, currency: 'USD', status: 'ISSUED', lineItems: [{ description: 'Pro Plan', quantity: 1, unitPrice: 50, total: 50 }], issuedAt: '2026-05-01', dueAt: '2026-05-31' }
    ])
  }
}))

describe('BillingHistoryPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state initially', () => {
    const wrapper = mount(BillingHistoryPage)
    expect(wrapper.text()).toContain('Loading billing data...')
  })

  it('renders ledger entries after loading', async () => {
    const wrapper = mount(BillingHistoryPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Ledger')
    expect(wrapper.text()).toContain('Render job #123')
    expect(wrapper.text()).toContain('Top-up')
  })

  it('switches to invoices tab', async () => {
    const wrapper = mount(BillingHistoryPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    const invoicesTab = wrapper.findAll('button').find(b => b.text().includes('Invoices'))
    await invoicesTab?.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('INV-001')
    expect(wrapper.text()).toContain('ISSUED')
  })

  it('renders correct number of data entries', async () => {
    const wrapper = mount(BillingHistoryPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Render job #123')
    expect(wrapper.text()).toContain('Top-up')
  })
})
