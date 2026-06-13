import api from './index'
import { safeApiCall, safeApiCallList } from './safeApiCall'
import { AssetSchema, type Asset } from './contracts'

export type { Asset }

export const AssetAPI = {
  async listByProject(projectId: string): Promise<Asset[]> {
    const result = await safeApiCallList(
      AssetSchema,
      () => api.get(`/projects/${projectId}/assets`).then(r => r.data),
      `Asset.listByProject(${projectId})`
    )
    return result.success ? result.data : []
  },

  async get(projectId: string, assetId: string): Promise<Asset | null> {
    const result = await safeApiCall(
      AssetSchema,
      () => api.get(`/projects/${projectId}/assets/${assetId}`).then(r => r.data),
      `Asset.get(${projectId}, ${assetId})`
    )
    return result.success ? result.data : null
  },

  async getPreviewUrl(projectId: string, assetId: string): Promise<string> {
    try {
      const { data } = await api.get(`/projects/${projectId}/assets/${assetId}/preview-url`)
      return data?.previewUrl ?? ''
    } catch {
      return ''
    }
  },

  async register(projectId: string, payload: {
    storageKey: string
    mediaType: string
    filename?: string
    sizeBytes?: number
    checksum?: string
    durationMs?: number
    width?: number
    height?: number
  }): Promise<Asset | null> {
    const result = await safeApiCall(
      AssetSchema,
      () => api.post(`/projects/${projectId}/assets/register`, payload).then(r => r.data),
      `Asset.register(${projectId})`
    )
    return result.success ? result.data : null
  },
}
