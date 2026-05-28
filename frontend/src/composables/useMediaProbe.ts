import { ref } from 'vue'
import api from '@/api/index'

export interface MediaProbeRequest {
  projectId: string
  assetId: string
  assetUri: string
}

export interface MediaProbeResult {
  found: boolean
  valid: boolean
  container: string
  durationMs: number
  width: number
  height: number
  fps: number
  videoCodec: string
  audioCodec: string
  hasAudioStream: boolean
  hasUsableAudio: boolean
  rotation: number
  bitrate: number
  isVfr: boolean
  clientExportCompatible: boolean
  normalizeRequired: boolean
  warnings: string[]
}

export function useMediaProbe() {
  const loading = ref(false)
  const error = ref<string | null>(null)
  const probeResult = ref<MediaProbeResult | null>(null)

  async function probeAsset(request: MediaProbeRequest): Promise<MediaProbeResult | null> {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.post<MediaProbeResult>('/render/media-probe', request)
      probeResult.value = data
      return data
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Media probe failed'
      error.value = msg
      return null
    } finally {
      loading.value = false
    }
  }

  async function getProbeResult(tenantId: string, assetId: string): Promise<MediaProbeResult | null> {
    loading.value = true
    error.value = null

    try {
      const { data } = await api.get<MediaProbeResult>(`/render/media-probe/${tenantId}/${assetId}`)
      probeResult.value = data
      return data.found ? data : null
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to get probe result'
      error.value = msg
      return null
    } finally {
      loading.value = false
    }
  }

  return {
    loading,
    error,
    probeResult,
    probeAsset,
    getProbeResult,
  }
}
