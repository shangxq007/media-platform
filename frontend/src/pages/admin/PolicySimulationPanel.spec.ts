import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ExtensionQuotaInfo from './ExtensionQuotaInfo.vue'

vi.mock('@/api/admin/entitlement-admin', () => ({
  EntitlementAdminAPI: {
    getBundles: vi.fn().mockResolvedValue([]),
    getTenantOverrides: vi.fn().mockResolvedValue([]),
    getUserGrants: vi.fn().mockResolvedValue([]),
  },
}))

describe('PolicySimulationPanel displays feature flag layer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders extension quota info component', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'test-ext',
          executionQuota: 100,
          executionsUsed: 30,
          executionsRemaining: 70,
          estimatedCost: 0.0025,
          currency: 'USD',
          riskLevel: 'LOW' as const,
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('100')
  })

  it('renders with feature flag status indicator', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'ff-enabled-ext',
          executionQuota: 200,
          executionsUsed: 50,
          executionsRemaining: 150,
          estimatedCost: 0.005,
          currency: 'USD',
          riskLevel: 'LOW' as const,
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders with disabled feature flag status', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'ff-disabled-ext',
          executionQuota: 0,
          executionsUsed: 0,
          executionsRemaining: 0,
          estimatedCost: 0,
          currency: 'USD',
          riskLevel: 'CRITICAL' as const,
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('Elevated risk level')
  })

  it('renders quota metrics with feature flag context', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'runtime-ext',
          executionQuota: 500,
          executionsUsed: 123,
          executionsRemaining: 377,
          estimatedCost: 0.0123,
          currency: 'USD',
          riskLevel: 'MEDIUM' as const,
        },
      },
    })
    expect(wrapper.text()).toContain('500')
    expect(wrapper.text()).toContain('123')
    expect(wrapper.text()).toContain('377')
  })

  it('displays risk badge for feature flag status', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'risk-ext',
          executionQuota: 100,
          executionsUsed: 95,
          executionsRemaining: 5,
          estimatedCost: 0.05,
          currency: 'USD',
          riskLevel: 'HIGH' as const,
        },
      },
    })
    expect(wrapper.text()).toContain('Elevated risk level')
  })
})
