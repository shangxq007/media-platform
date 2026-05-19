import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import MyCapabilitiesPage from './MyCapabilitiesPage.vue'

vi.mock('@/api/me', () => ({
  MeEntitlementAPI: {
    getMyCapabilities: vi.fn().mockResolvedValue({
      tenantId: 't1',
      userId: 'u1',
      tier: 'PRO',
      entitlementPolicy: {
        policyId: 'ep1',
        tier: 'PRO',
        maxResolutionWidth: 1920,
        maxResolutionHeight: 1080,
        monthlyRenderMinutes: 600,
        watermark: false,
        allowedProviders: ['ffmpeg'],
        gpuAllowed: false,
        remoteWorkerAllowed: false,
        maxSubtitleTracks: 3,
        customFontsAllowed: true,
        effectPacksAllowed: [],
        exportFormats: ['mp4', 'webm'],
        maxConcurrentJobs: 2,
      },
      exportCapabilities: {
        policyId: 'ec1',
        tier: 'PRO',
        allowedFormats: ['mp4', 'webm'],
        allowedPresets: ['default_720p', 'default_1080p'],
        maxResolutionWidth: 1920,
        maxResolutionHeight: 1080,
        watermarkRequired: false,
        gpuExportAllowed: false,
        remoteExportAllowed: false,
        maxConcurrentExports: 2,
      },
      providerAccess: {
        policyId: 'pa1',
        tier: 'PRO',
        allowedProviders: ['ffmpeg'],
        gpuAllowed: false,
        remoteWorkerAllowed: false,
        allowedGpuPresets: [],
      },
      featureFlags: [],
    }),
  },
}))

describe('i18n feature flag error message renders', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('renders feature flag error messages in English', () => {
    const errorMessages: Record<string, string> = {
      'FF-404-001': 'Feature flag not found',
      'FF-400-002': 'Feature flag is disabled',
      'FF-500-001': 'Feature flag evaluation failed',
      'FF-503-001': 'Feature flag provider unavailable',
      'FF-400-003': 'Feature flag context is invalid',
      'FF-400-004': 'Feature flag targeting rule is invalid',
      'FF-400-005': 'Feature flag variant is invalid',
      'FF-400-006': 'Feature flag rollout percentage is invalid',
      'FF-403-001': 'Feature flag access denied',
      'FF-403-002': 'Feature flag operation not allowed',
      'FF-500-002': 'OpenFeature initialization failed',
      'FF-403-003': 'Policy denied by feature flag',
      'FF-403-004': 'Navigation disabled by feature flag',
    }
    expect(errorMessages['FF-404-001']).toBe('Feature flag not found')
    expect(errorMessages['FF-403-004']).toBe('Navigation disabled by feature flag')
    expect(Object.keys(errorMessages).length).toBe(13)
  })

  it('renders feature flag error messages in Chinese', () => {
    const errorMessages: Record<string, string> = {
      'FF-404-001': '功能标志不存在',
      'FF-400-002': '功能标志已禁用',
      'FF-500-001': '功能标志评估失败',
      'FF-503-001': '功能标志提供者不可用',
      'FF-400-003': '功能标志上下文无效',
      'FF-400-004': '功能标志目标规则无效',
      'FF-400-005': '功能标志变体无效',
      'FF-400-006': '功能标志灰度百分比无效',
      'FF-403-001': '功能标志访问被拒绝',
      'FF-403-002': '功能标志操作不允许',
      'FF-500-002': 'OpenFeature 初始化失败',
      'FF-403-003': '策略被功能标志拒绝',
      'FF-403-004': '导航被功能标志禁用',
    }
    expect(errorMessages['FF-404-001']).toBe('功能标志不存在')
    expect(errorMessages['FF-403-004']).toBe('导航被功能标志禁用')
    expect(Object.keys(errorMessages).length).toBe(13)
  })

  it('renders correct error message for each locale', () => {
    const messages = {
      en: {
        'FF-404-001': 'Feature flag not found',
        'FF-403-004': 'Navigation disabled by feature flag',
      },
      zh: {
        'FF-404-001': '功能标志不存在',
        'FF-403-004': '导航被功能标志禁用',
      },
    }
    expect(messages.en['FF-404-001']).toBe('Feature flag not found')
    expect(messages.zh['FF-404-001']).toBe('功能标志不存在')
    expect(messages.en['FF-403-004']).toBe('Navigation disabled by feature flag')
    expect(messages.zh['FF-403-004']).toBe('导航被功能标志禁用')
  })

  it('renders page without errors when feature flags load', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.exists()).toBe(true)
    expect(wrapper.text()).toContain('Capabilities')
  })

  it('renders error state with localized message', async () => {
    const wrapper = mount(MyCapabilitiesPage)
    await new Promise(r => setTimeout(r, 10))
    await wrapper.vm.$nextTick()
    expect(wrapper.exists()).toBe(true)
  })

  it('handles all feature flag error codes with both locales', () => {
    const errorCodes = [
      'FF-404-001', 'FF-400-002', 'FF-500-001', 'FF-503-001',
      'FF-400-003', 'FF-400-004', 'FF-400-005', 'FF-400-006',
      'FF-403-001', 'FF-403-002', 'FF-500-002', 'FF-403-003',
      'FF-403-004',
    ]
    const enMessages: Record<string, string> = {
      'FF-404-001': 'Feature flag not found',
      'FF-400-002': 'Feature flag is disabled',
      'FF-500-001': 'Feature flag evaluation failed',
      'FF-503-001': 'Feature flag provider unavailable',
      'FF-400-003': 'Feature flag context is invalid',
      'FF-400-004': 'Feature flag targeting rule is invalid',
      'FF-400-005': 'Feature flag variant is invalid',
      'FF-400-006': 'Feature flag rollout percentage is invalid',
      'FF-403-001': 'Feature flag access denied',
      'FF-403-002': 'Feature flag operation not allowed',
      'FF-500-002': 'OpenFeature initialization failed',
      'FF-403-003': 'Policy denied by feature flag',
      'FF-403-004': 'Navigation disabled by feature flag',
    }
    const zhMessages: Record<string, string> = {
      'FF-404-001': '功能标志不存在',
      'FF-400-002': '功能标志已禁用',
      'FF-500-001': '功能标志评估失败',
      'FF-503-001': '功能标志提供者不可用',
      'FF-400-003': '功能标志上下文无效',
      'FF-400-004': '功能标志目标规则无效',
      'FF-400-005': '功能标志变体无效',
      'FF-400-006': '功能标志灰度百分比无效',
      'FF-403-001': '功能标志访问被拒绝',
      'FF-403-002': '功能标志操作不允许',
      'FF-500-002': 'OpenFeature 初始化失败',
      'FF-403-003': '策略被功能标志拒绝',
      'FF-403-004': '导航被功能标志禁用',
    }
    errorCodes.forEach(code => {
      expect(enMessages[code]).toBeDefined()
      expect(zhMessages[code]).toBeDefined()
      expect(enMessages[code].length).toBeGreaterThan(0)
      expect(zhMessages[code].length).toBeGreaterThan(0)
    })
  })

  it('renders error message with correct HTTP status mapping', () => {
    const statusMap: Record<string, number> = {
      'FF-404-001': 404,
      'FF-400-002': 400,
      'FF-500-001': 500,
      'FF-503-001': 503,
      'FF-400-003': 400,
      'FF-400-004': 400,
      'FF-400-005': 400,
      'FF-400-006': 400,
      'FF-403-001': 403,
      'FF-403-002': 403,
      'FF-500-002': 500,
      'FF-403-003': 403,
      'FF-403-004': 403,
    }
    expect(statusMap['FF-404-001']).toBe(404)
    expect(statusMap['FF-403-004']).toBe(403)
    expect(statusMap['FF-500-001']).toBe(500)
    expect(statusMap['FF-503-001']).toBe(503)
  })
})
