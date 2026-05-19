import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MyBillingPage from './MyBillingPage.vue'

vi.mock('@/api/me', () => ({
  MeEntitlementAPI: {
    getCurrentPlan: vi.fn().mockResolvedValue({
      planId: 'p1',
      name: 'Pro Plan',
      tier: 'PRO',
      description: 'Professional tier with advanced features',
      monthlyPrice: 29.99,
      annualPrice: 299.99,
      currency: 'USD',
      includedQuota: {
        renderMinutes: 600,
        storageGb: 50,
        apiCalls: 10000,
        exports: 100,
        subtitleTracks: 5,
        maxResolution: '1080p',
        gpuAllowed: false,
        remoteWorkerAllowed: false,
        customFontsAllowed: true,
      },
      features: ['HD Export', 'Custom Fonts', 'Priority Support'],
      isActive: true,
    }),
    getBillingHistory: vi.fn().mockResolvedValue({
      entries: [
        { id: 'le1', type: 'CHARGE', amount: 29.99, currency: 'USD', status: 'COMPLETED', description: 'Monthly subscription', createdAt: '2026-05-01T00:00:00Z' },
      ],
      total: 1,
    }),
    getInvoices: vi.fn().mockResolvedValue([
      { id: 'inv1', invoiceNumber: 'INV-001', status: 'PAID', amount: 29.99, currency: 'USD', issuedAt: '2026-05-01', dueAt: '2026-05-15' },
    ]),
  },
}))

describe('MyBillingPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state initially', () => {
    const wrapper = mount(MyBillingPage)
    expect(wrapper.text()).toContain('Loading billing data...')
  })

  it('renders billing page after loading', async () => {
    const wrapper = mount(MyBillingPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Billing')
  })

  it('renders current plan card', async () => {
    const wrapper = mount(MyBillingPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Current Plan')
    expect(wrapper.text()).toContain('Pro Plan')
  })

  it('renders billing summary with plan details', async () => {
    const wrapper = mount(MyBillingPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('29.99')
    expect(wrapper.text()).toContain('USD')
  })

  it('renders billing history entries', async () => {
    const wrapper = mount(MyBillingPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Monthly subscription')
  })

  it('renders invoices section', async () => {
    const wrapper = mount(MyBillingPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('INV-001')
  })

  it('renders plan description', async () => {
    const wrapper = mount(MyBillingPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Professional tier with advanced features')
  })

  it('renders active status badge', async () => {
    const wrapper = mount(MyBillingPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Active')
  })

  it('renders annual price option', async () => {
    const wrapper = mount(MyBillingPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('299.99')
  })
})
