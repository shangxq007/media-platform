import { ref } from 'vue'
import api from '@/api/index'

export interface AutoCaptionsRequest {
  projectId: string
  assetId: string
  audioUri: string
  language?: string
  maxSegmentDurationMs?: number
  fontFamily?: string
  fontSize?: number
  fontColor?: string
  positionX?: number
  positionY?: number
}

export interface CaptionOverlay {
  id: string
  text: string
  startTime: number
  duration: number
  fontFamily: string
  fontSize: number
  color: string
}

export interface AutoCaptionsResult {
  projectId: string
  success: boolean
  segmentCount?: number
  overlays?: CaptionOverlay[]
  error?: string
}

export function useAutoCaptions() {
  const loading = ref(false)
  const error = ref<string | null>(null)
  const result = ref<AutoCaptionsResult | null>(null)

  async function generateCaptions(request: AutoCaptionsRequest): Promise<AutoCaptionsResult | null> {
    loading.value = true
    error.value = null
    result.value = null

    try {
      const { data } = await api.post<AutoCaptionsResult>('/render/auto-captions', request)
      result.value = data
      if (!data.success) {
        error.value = data.error || 'Auto captions failed'
      }
      return data
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Auto captions request failed'
      error.value = msg
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    result,
    generateCaptions,
  }
}
