import { z } from 'zod'
import api from './index'
import { apiRequest, type ApiClientConfig } from './core/api-client'

export const thumbnailExecutionSchema = z.object({
  executionId: z.string(), generation: z.number().int().positive(),
  state: z.enum(['ADMITTED', 'RUNNING', 'RETRYING', 'COMPLETED', 'FAILED', 'CANCELLED']),
  artifactId: z.string().nullable(), failureCode: z.string().nullable(), retryBlocked: z.boolean(),
})
export type ThumbnailExecution = z.infer<typeof thumbnailExecutionSchema>
export const thumbnailAssetSchema = z.object({ id: z.string(), mediaType: z.string(), filename: z.string().nullish(), sizeBytes: z.number().nullish() })
export type ThumbnailAsset = z.infer<typeof thumbnailAssetSchema>
const route = (tenant: string, project: string) => `/api/tenants/${encodeURIComponent(tenant)}/projects/${encodeURIComponent(project)}/thumbnails`
export function listThumbnailAssets(config: ApiClientConfig, project: string, signal?: AbortSignal) {
  return apiRequest(config, `/api/projects/${encodeURIComponent(project)}/assets`, z.array(thumbnailAssetSchema), { signal })
}
export function submitThumbnail(config: ApiClientConfig, tenant: string, project: string, body: { sourceAssetId: string; timestampSeconds: number; imageFormat: string; idempotencyKey: string }) {
  return apiRequest(config, route(tenant, project), thumbnailExecutionSchema, { method: 'POST', body })
}
export function getThumbnail(config: ApiClientConfig, tenant: string, project: string, execution: string) {
  return apiRequest(config, `${route(tenant, project)}/${encodeURIComponent(execution)}`, thumbnailExecutionSchema)
}
export function cancelThumbnail(config: ApiClientConfig, tenant: string, project: string, execution: string, generation: number) {
  return apiRequest(config, `${route(tenant, project)}/${encodeURIComponent(execution)}/cancel`, thumbnailExecutionSchema, { method: 'POST', body: { generation } })
}
export function retryThumbnail(config: ApiClientConfig, tenant: string, project: string, execution: string, generation: number) {
  return apiRequest(config, `${route(tenant, project)}/${encodeURIComponent(execution)}/retry`, thumbnailExecutionSchema, { method: 'POST', body: { generation } })
}
export async function readThumbnailImage(config: ApiClientConfig, tenant: string, project: string, execution: string, signal?: AbortSignal): Promise<Blob> {
  // Reuse the canonical OIDC transport. Never place credentials in an <img> or download URL.
  const { data } = await api.get(`${route(tenant, project)}/${encodeURIComponent(execution)}/image`, {
    baseURL: config.baseUrl, headers: config.headers, responseType: 'blob', signal,
  })
  if (!(data instanceof Blob) || !['image/jpeg', 'image/png'].includes(data.type)) throw new Error('Image content unavailable')
  return data
}
