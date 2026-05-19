import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MyCreditsPage from './MyCreditsPage.vue'

vi.mock('@/api/me', () => ({
  MeEntitlementAPI: {
    getCreditBalance: vi.fn().mockResolvedValue({
      walletId: 'w1',
      subjectId: 'u1',
      subjectType: 'USER',
      balance: 42.50,
      currency: 'USD',
      heldBalance: 5.00,
      lastTransactionAt: '2026-05-16T12:00:00Z',
    }),
    getCreditTransactions: vi.fn().mockResolvedValue({
      transactions: [],
      total: 0,
    }),
    topUpCredits: vi.fn().mockResolvedValue({
      transactionId: 'tx1',
      walletId: 'w1',
      type: 'TOP_UP',
      amount: 50,
      balanceAfter: 92.50,
      description: 'Credit top-up',
      createdAt: '2026-05-16T12:00:00Z',
    }),
  },
}))

describe('MyCreditsPage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state initially', () => {
    const wrapper = mount(MyCreditsPage)
    expect(wrapper.text()).toContain('Loading credits...')
  })

  it('renders credits page after loading', async () => {
    const wrapper = mount(MyCreditsPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Credits')
  })

  it('renders balance overview cards', async () => {
    const wrapper = mount(MyCreditsPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Available Balance')
    expect(wrapper.text()).toContain('Held Balance')
    expect(wrapper.text()).toContain('Total Balance')
  })

  it('renders add credits button', async () => {
    const wrapper = mount(MyCreditsPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Add Credits')
  })

  it('renders earned vs spent section', async () => {
    const wrapper = mount(MyCreditsPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Credits Earned vs Spent')
    expect(wrapper.text()).toContain('Earned')
    expect(wrapper.text()).toContain('Spent')
  })

  it('renders empty transaction state', async () => {
    const wrapper = mount(MyCreditsPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('No transactions')
  })

  it('does not show low balance warning when balance is sufficient', async () => {
    const wrapper = mount(MyCreditsPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).not.toContain('Low Balance')
  })
})
