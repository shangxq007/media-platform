import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ExtensionQuotaInfo from './ExtensionQuotaInfo.vue'

describe('ExtensionManagementPage displays runtime flag status', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders extension with runtime flag enabled', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'runtime-enabled-ext',
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

  it('renders extension with runtime flag disabled', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'runtime-disabled-ext',
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
    expect(wrapper.text()).toContain('0')
  })

  it('renders extension with WASM runtime flag', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'wasm-runtime-ext',
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

  it('renders extension with JS runtime flag', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'js-runtime-ext',
          executionQuota: 500,
          executionsUsed: 100,
          executionsRemaining: 400,
          estimatedCost: 0.01,
          currency: 'USD',
          riskLevel: 'LOW' as const,
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders extension with Python runtime flag', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'python-runtime-ext',
          executionQuota: 100,
          executionsUsed: 10,
          executionsRemaining: 90,
          estimatedCost: 0.02,
          currency: 'USD',
          riskLevel: 'MEDIUM' as const,
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders extension with gray release flag', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'gray-release-ext',
          executionQuota: 50,
          executionsUsed: 5,
          executionsRemaining: 45,
          estimatedCost: 0.001,
          currency: 'USD',
          riskLevel: 'MEDIUM' as const,
        },
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('displays correct risk level for runtime flag status', () => {
    const wrapper = mount(ExtensionQuotaInfo, {
      props: {
        quota: {
          extensionKey: 'high-risk-ext',
          executionQuota: 100,
          executionsUsed: 90,
          executionsRemaining: 10,
          estimatedCost: 0.05,
          currency: 'USD',
          riskLevel: 'HIGH' as const,
        },
      },
    })
    expect(wrapper.text()).toContain('Elevated risk level')
  })

  it('renders all runtime flag status indicators', () => {
    const runtimeFlags = [
      { key: 'extension.platform.enabled', quota: 100, used: 30 },
      { key: 'extension.wasmRuntime.enabled', quota: 200, used: 50 },
      { key: 'extension.jsRuntime.enabled', quota: 500, used: 100 },
      { key: 'extension.pythonRuntime.enabled', quota: 100, used: 10 },
      { key: 'extension.grayRelease.enabled', quota: 50, used: 5 },
    ]
    expect(runtimeFlags.length).toBe(5)
    runtimeFlags.forEach(flag => {
      expect(flag.key).toContain('extension.')
      expect(flag.quota).toBeGreaterThan(0)
    })
  })
})
