import { z } from 'zod'
import { versionlessApi as api } from './app/versionless-api'
import { sessionBoundRequest as sessionRequest } from './oidc-transport'

export const Delivery = z.object({
  deliveryId: z.string().min(1),
  projectId: z.string().min(1),
  sourceRenderJobId: z.string().min(1),
  timelineRevisionId: z.string().nullable(),
  artifactId: z.string().min(1),
  status: z.literal('READY'),
  createdAt: z.string(),
  resultPath: z.string().startsWith('/api/projects/'),
})
export type Delivery = z.infer<typeof Delivery>
export interface CreateDelivery { sourceRenderJobId: string; artifactId?: string }
const root = (projectId: string) => `/projects/${encodeURIComponent(projectId)}/deliveries`

/** Formal authorized delivery records; no outbound transfer or storage locator. */
export const DeliveryAPI = {
  async create(projectId: string, request: CreateDelivery, idempotencyKey: string, signal?: AbortSignal): Promise<Delivery> {
    const data = await sessionRequest(c => api.post(root(projectId), request, { ...c, headers: { 'Idempotency-Key': idempotencyKey } }), signal)
    return Delivery.parse(data)
  },
  async get(projectId: string, deliveryId: string, signal?: AbortSignal): Promise<Delivery> {
    const data = await sessionRequest(c => api.get(`${root(projectId)}/${encodeURIComponent(deliveryId)}`, c), signal)
    return Delivery.parse(data)
  },
  async list(projectId: string, limit = 50, signal?: AbortSignal): Promise<Delivery[]> {
    const data = await sessionRequest(c => api.get(root(projectId), { ...c, params: { limit } }), signal)
    return z.array(Delivery).parse(data)
  },
  async download(projectId: string, deliveryId: string, signal?: AbortSignal): Promise<Blob> {
    const data = await sessionRequest(c => api.get<Blob>(`${root(projectId)}/${encodeURIComponent(deliveryId)}/result`, { ...c, responseType: 'blob' }), signal)
    return data
  },
}
