import { useQuery } from '@tanstack/react-query'
import api from './index'
import { safeApiCall, type SafeApiResult } from './safeApiCall'
import { z } from 'zod'
import { RenderJobSummary, RenderWorkspaceScope } from '../contracts/app/render-job'

export type { RenderWorkspaceScope }

// --- API ---

function requireValidated<T>(result: SafeApiResult<T>): T {
  if (result.success) return result.data
  const error = new Error(result.error.message)
  error.name = result.error.code
  throw error
}

export const RenderJobsAPI = {
  async getWorkspaceScope(): Promise<RenderWorkspaceScope | null> {
    const result = await safeApiCall(
      RenderWorkspaceScope,
      () => api.get('/me/dashboard').then(r => r.data),
      'RenderJobs.getWorkspaceScope'
    )
    return requireValidated(result)
  },
}

// --- Hooks ---

export function useRenderWorkspaceScope() {
  return useQuery({
    queryKey: ['render-workspace-scope'],
    queryFn: () => RenderJobsAPI.getWorkspaceScope(),
    staleTime: 60_000,
  })
}

// Authorized platform Render reads. Legacy Product browsing above is a separate contract.
const id = z.string().trim().min(1)
export const RenderProject = z.object({
  id, tenantId: id, name: z.string(), description: z.string().nullable(),
  status: z.string().min(1), createdAt: z.string().datetime({ offset: true }).nullable(),
}).strict()
export type RenderProject = z.infer<typeof RenderProject>
export type RenderReadSource = typeof renderReadSource
function invalidScope(): never { throw new Error('Render response scope could not be verified') }
const part = (value: string) => encodeURIComponent(id.parse(value))
const jobsPath = (tenant: string, project: string) => `/api/tenants/${part(tenant)}/projects/${part(project)}/render-jobs`
async function get(path: string, signal: AbortSignal) {
  // Same authenticated Axios transport; HTTP failures remain intact (including status).
  const { data } = await api.get(path, { baseURL: '', signal })
  signal.throwIfAborted()
  return data
}
export const renderReadSource = {
  async tenant(signal: AbortSignal): Promise<string> {
    // Dashboard establishes current tenant only. It never supplies selectable Projects.
    return z.object({ tenantId: id }).parse(await get('/api/me/dashboard', signal)).tenantId
  },
  async projects(tenant: string, signal: AbortSignal): Promise<RenderProject[]> {
    const rows = RenderProject.array().parse(await get(`/api/identity/tenants/${part(tenant)}/projects`, signal))
    if (rows.some(row => row.tenantId !== tenant) || new Set(rows.map(row => row.id)).size !== rows.length) invalidScope()
    return rows
  },
  async project(tenant: string, project: string, signal: AbortSignal): Promise<RenderProject> {
    const row = RenderProject.parse(await get(`/api/identity/projects/${part(project)}`, signal))
    if (row.tenantId !== tenant || row.id !== project) invalidScope()
    return row
  },
  async jobs(tenant: string, project: string, signal: AbortSignal): Promise<RenderJobSummary[]> {
    const rows = RenderJobSummary.array().parse(await get(jobsPath(tenant, project), signal))
    if (rows.some(row => row.projectId !== project) || new Set(rows.map(row => row.id)).size !== rows.length) invalidScope()
    return rows
  },
  async job(tenant: string, project: string, job: string, signal: AbortSignal): Promise<RenderJobSummary> {
    const row = RenderJobSummary.parse(await get(`${jobsPath(tenant, project)}/${part(job)}`, signal))
    if (row.projectId !== project || row.id !== job) invalidScope()
    return row
  },
}
export const renderHttpStatus = (error: unknown): number | undefined =>
  (error as { response?: { status?: number } } | null)?.response?.status
