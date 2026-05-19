import { ref } from 'vue'
import { EntitlementAPI } from '@/api'
import type { ExportValidationResult, TimelineState, ExportSettings } from '@/types'

export function useExportValidation() {
  const validationResult = ref<ExportValidationResult | null>(null)
  const isValidating = ref(false)
  const validationError = ref<string | null>(null)

  let debounceTimer: ReturnType<typeof setTimeout> | null = null

  async function validateExport(
    timeline: TimelineState,
    settings: ExportSettings,
    preset: string,
  ) {
    isValidating.value = true
    validationError.value = null

    try {
      const result = await EntitlementAPI.validateExport(
        preset,
        settings.format,
        Math.max(60, timeline.duration),
      )
      validationResult.value = result
      return result
    } catch (err: any) {
      const errorCode = err.response?.data?.errorCode || 'COMMON-500-001'
      validationError.value = `${errorCode}: Validation failed`
      validationResult.value = null
      return null
    } finally {
      isValidating.value = false
    }
  }

  function validateExportDebounced(
    timeline: TimelineState,
    settings: ExportSettings,
    preset: string,
    delay = 500,
  ) {
    if (debounceTimer) clearTimeout(debounceTimer)
    debounceTimer = setTimeout(() => {
      validateExport(timeline, settings, preset)
    }, delay)
  }

  function clearValidation() {
    validationResult.value = null
    validationError.value = null
  }

  return {
    validationResult,
    isValidating,
    validationError,
    validateExport,
    validateExportDebounced,
    clearValidation,
  }
}
