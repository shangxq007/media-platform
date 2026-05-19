import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MyUsagePage from './MyUsagePage.vue'

vi.mock('@/api/me', () => ({
  MeEntitlementAPI: {
    getUsageSummary: vi.fn().mockResolvedValue({
      tenantId: 't1',
      userId: 'u1',
      period: '2026-05',
      renderMinutesUsed: 120,
      renderMinutesLimit: 600,
      storageGbUsed: 5.2,
      storageGbLimit: 50,
      apiCallsUsed: 340,
      apiCallsLimit: 10000,
      exportsUsed: 8,
      exportsLimit: 100,
      lastUpdatedAt: '2026-05-16T12:00:00Z',
    }),
  },
}))

describe('MyUsagePage', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders loading state initially', () => {
    const wrapper = mount(MyUsagePage)
    expect(wrapper.text()).toContain('Loading usage data...')
  })

  it('renders usage page after loading', async () => {
    const wrapper = mount(MyUsagePage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Usage')
  })

  it('renders period selector tabs', async () => {
    const wrapper = mount(MyUsagePage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Current Month')
    expect(wrapper.text()).toContain('Last Month')
    expect(wrapper.text()).toContain('Custom Range')
  })

  it('renders summary metric cards', async () => {
    const wrapper = mount(MyUsagePage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('of 600 minutes')
    expect(wrapper.text()).toContain('of 50 GB')
    expect(wrapper.text()).toContain('of 10000 calls')
  })

  it('renders usage data values', async () => {
    const wrapper = mount(MyUsagePage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('120')
    expect(wrapper.text()).toContain('5.2')
    expect(wrapper.text()).toContain('340')
  })

  it('renders exports usage', async () => {
    const wrapper = mount(MyUsagePage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('of 100 exports')
  })

  it('renders all quota items', async () => {
    const wrapper = mount(MyUsagePage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Render Minutes')
    expect(wrapper.text()).toContain('Storage')
    expect(wrapper.text()).toContain('API Calls')
    expect(wrapper.text()).toContain('Exports')
  })

  it('renders page header with title', async () => {
    const wrapper = mount(MyUsagePage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain('Usage')
  })
})
