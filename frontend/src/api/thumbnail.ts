import { z } from 'zod'
import { apiRequest, type ApiClientConfig } from './core/api-client'

export const thumbnailTaskSchema = z.object({taskId:z.string(),status:z.enum(['ADMITTED','RUNNING','COMPLETED','FAILED','CANCELLED']),artifactId:z.string().nullable().optional(),failureCode:z.string().nullable().optional()})
export type ThumbnailTask = z.infer<typeof thumbnailTaskSchema>
export async function submitThumbnail(config:ApiClientConfig, tenantId:string, projectId:string, body:{sourceAssetId:string;timestampSeconds:number;imageFormat:string;idempotencyKey:string}) { return apiRequest(config,`/api/tenants/${encodeURIComponent(tenantId)}/projects/${encodeURIComponent(projectId)}/thumbnails`,thumbnailTaskSchema,{method:'POST',body}) }
export async function getThumbnail(config:ApiClientConfig, tenantId:string, projectId:string, taskId:string) { return apiRequest(config,`/api/tenants/${encodeURIComponent(tenantId)}/projects/${encodeURIComponent(projectId)}/thumbnails/${encodeURIComponent(taskId)}`,thumbnailTaskSchema) }
