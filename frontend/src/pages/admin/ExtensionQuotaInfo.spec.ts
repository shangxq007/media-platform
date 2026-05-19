import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ExtensionQuotaInfo from './ExtensionQuotaInfo.vue'

describe('ExtensionQuotaInfo', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  const mockQuota = {
    extensionKey: 'test-ext',
    executionQuota: 100,
    executionsUsed: 30,
    executionsRemaining: 70,
    estimatedCost: 0.0025,
    currency: 'USD',
    riskLevel: 'LOW' as const
  }

  it('renders quota metrics', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: { quota: mockQuota }
    })
    expect(wrapper.text()).toContain('100')
    expect(wrapper.text()).toContain('30')
    expect(wrapper.text()).toContain('70')
    expect(wrapper.text()).toContain('$0.0025')
  })

  it('renders risk badge with risk level', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: { quota: mockQuota }
    })
    expect(wrapper.text()).toContain('LOW Risk')
  })

  it('shows elevated warning for HIGH risk', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: { quota: { ...mockQuota, riskLevel: 'HIGH' as const } }
    })
    expect(wrapper.text()).toContain('Elevated risk level')
  })

  it('shows elevated warning for CRITICAL risk', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: { quota: { ...mockQuota, riskLevel: 'CRITICAL' as const } }
    })
    expect(wrapper.text()).toContain('Elevated risk level')
  })

  it('does not show warning for LOW risk', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: { quota: mockQuota }
    })
    expect(wrapper.text()).not.toContain('Elevated risk level')
  })

  it('shows loading state', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: { quota: mockQuota, loading: true }
    })
    expect(wrapper.findAll('.animate-pulse').length).toBeGreaterThan(0)
  })

  it('calculates usage percentage correctly', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: { quota: { ...mockQuota, executionsUsed: 50, executionQuota: 100 } }
    })
    expect(wrapper.text()).toContain('50%')
  })
})
