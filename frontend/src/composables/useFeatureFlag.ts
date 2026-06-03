import { ref, computed, onMounted } from 'vue'
import { FeatureFlagAPI } from '@/api/admin/feature-flags'
import type { FeatureFlagEvaluationResult } from '@/api/admin/feature-flags'

export interface FeatureFlagMap {
  [flagKey: string]: boolean
}

export interface FeatureFlagDetail {
  flagKey: string
  enabled: boolean
  reason: string
  variant?: string
}

const EXPORT_FLAG_KEYS = [
  'export.gpu.v2',
  'export.remoteWorker.enabled',
  'export.providerRouting.v2',
  'export.newPresetSelector.enabled',
  'export.client.enabled',
] as const

const EDITOR_FLAG_KEYS = [
  'editor.newTimeline.enabled',
  'editor.demoProject.enabled',
  'editor.subtitlePanel.v2',
  'editor.effectChain.v2',
  'editor.effectTaxonomy.enabled',
] as const

const PROMPT_FLAG_KEYS = [
  'prompt.management.enabled',
  'prompt.riskReview.enabled',
  'prompt.executionCostPreview.enabled',
  'prompt.manifestPanel.enabled',
] as const

const EXTENSION_FLAG_KEYS = [
  'extension.platform.enabled',
  'extension.wasmRuntime.enabled',
  'extension.jsRuntime.enabled',
  'extension.pythonRuntime.enabled',
  'extension.grayRelease.enabled',
] as const

const MONITORING_FLAG_KEYS = [
  'graphql.queryAggregation.enabled',
  'graphql.adminDashboard.enabled',
  'monitoring.openReplay.enabled',
  'monitoring.sentryReplay.enabled',
  'feedback.userReport.enabled',
] as const

export const ALL_SCENE_FLAG_KEYS = [
  ...EXPORT_FLAG_KEYS,
  ...EDITOR_FLAG_KEYS,
  ...PROMPT_FLAG_KEYS,
  ...EXTENSION_FLAG_KEYS,
  ...MONITORING_FLAG_KEYS,
] as const

export type ExportFlagKey = (typeof EXPORT_FLAG_KEYS)[number]
export type EditorFlagKey = (typeof EDITOR_FLAG_KEYS)[number]
export type PromptFlagKey = (typeof PROMPT_FLAG_KEYS)[number]
export type ExtensionFlagKey = (typeof EXTENSION_FLAG_KEYS)[number]
export type MonitoringFlagKey = (typeof MONITORING_FLAG_KEYS)[number]

export function useFeatureFlag(options?: {
  flagKeys?: string[]
  immediate?: boolean
}) {
  const flagMap = ref<FeatureFlagMap>({})
  const flagDetails = ref<Map<string, FeatureFlagDetail>>(new Map())
  const loading = ref(false)
  const error = ref<string | null>(null)

  const keys = computed(() => options?.flagKeys || [])

  async function evaluateFlag(flagKey: string): Promise<FeatureFlagEvaluationResult | null> {
    try {
      const result = await FeatureFlagAPI.evaluateFeatureFlag(flagKey, {})
      return result
    } catch {
      return null
    }
  }

  async function refresh() {
    if (keys.value.length === 0) return
    loading.value = true
    error.value = null
    try {
      const newMap: FeatureFlagMap = {}
      const newDetails = new Map<string, FeatureFlagDetail>()
      const results = await Promise.allSettled(keys.value.map(k => evaluateFlag(k)))
      for (let i = 0; i < keys.value.length; i++) {
        const key = keys.value[i]
        const result = results[i]
        if (result.status === 'fulfilled' && result.value) {
          newMap[key] = result.value.enabled
          newDetails.set(key, {
            flagKey: key,
            enabled: result.value.enabled,
            reason: result.value.reason,
            variant: result.value.variant,
          })
        } else {
          newMap[key] = false
          newDetails.set(key, { flagKey: key, enabled: false, reason: 'Evaluation failed' })
        }
      }
      flagMap.value = newMap
      flagDetails.value = newDetails
    } catch (e: any) {
      error.value = e.message || 'Failed to evaluate feature flags'
    } finally {
      loading.value = false
    }
  }

  function isEnabled(flagKey: string): boolean {
    return flagMap.value[flagKey] ?? false
  }

  function getDetail(flagKey: string): FeatureFlagDetail | undefined {
    return flagDetails.value.get(flagKey)
  }

  function getDisabledReason(flagKey: string): string {
    const detail = flagDetails.value.get(flagKey)
    if (!detail) return 'Feature flag not evaluated'
    if (detail.enabled) return ''
    return detail.reason || 'This feature is currently disabled'
  }

  if (options?.immediate !== false && keys.value.length > 0) {
    onMounted(refresh)
  }

  return {
    flagMap,
    flagDetails,
    loading,
    error,
    refresh,
    isEnabled,
    getDetail,
    getDisabledReason,
  }
}

export function useExportFeatureFlags() {
  return useFeatureFlag({ flagKeys: [...EXPORT_FLAG_KEYS] })
}

export function useEditorFeatureFlags() {
  return useFeatureFlag({ flagKeys: [...EDITOR_FLAG_KEYS] })
}

export function usePromptFeatureFlags() {
  return useFeatureFlag({ flagKeys: [...PROMPT_FLAG_KEYS] })
}

export function useExtensionFeatureFlags() {
  return useFeatureFlag({ flagKeys: [...EXTENSION_FLAG_KEYS] })
}

export function useMonitoringFeatureFlags() {
  return useFeatureFlag({ flagKeys: [...MONITORING_FLAG_KEYS] })
}
