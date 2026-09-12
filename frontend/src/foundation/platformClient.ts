import { useWorkspaceBinding } from './workspaceSession'
import { useQuery } from '@tanstack/react-query'
import { z } from 'zod'
import api from '../api'
import { PublicationReadAPI, type PublicationAccountDto, type PublicationDetailRequest, type PublicationListRequest, type PublicationPostDto, type PublicationPostListDto } from '../api/publish'
import type { EffectiveAccessCatalog } from './effectiveAccess'
import { unknownAccess } from './effectiveAccess'

const ProjectSummarySchema = z.object({
  id: z.string().min(1),
  tenantId: z.string().min(1).nullable().optional(),
  name: z.string().min(1),
  description: z.string().nullish(),
  status: z.string().optional(),
  createdAt: z.string().nullish(),
})

const DashboardSchema = z.object({
  tenantId: z.string().nullable(),
  workspace: z.object({
    id: z.string().optional(),
    name: z.string().optional(),
    status: z.string().optional(),
    role: z.string().optional(),
  }),
  recentProjects: z.array(ProjectSummarySchema),
  timestamp: z.string().optional(),
})

export interface WorkspaceSummary {
  readonly id: string
  readonly name: string
  readonly status?: string
}

export interface ProjectSummary {
  readonly id: string
  readonly tenantId?: string | null
  readonly name: string
  readonly description?: string | null
  readonly status?: string
  readonly createdAt?: string | null
}

export interface WorkspaceHomeProjection {
  readonly workspace: WorkspaceSummary
  readonly tenantId: string | null
  readonly recentProjects: readonly ProjectSummary[]
  readonly projectedAt?: string
}

export interface PlatformClient {
  readonly workspace: {
    getHome(workspaceId: string, signal?: AbortSignal): Promise<WorkspaceHomeProjection>
  }
  readonly effectiveAccess: {
    getCatalog(keys: readonly string[]): Promise<EffectiveAccessCatalog>
  }
  readonly publication: PublicationReadSource
}

export interface PublicationReadSource {
  readonly origin: 'platform-authenticated' | 'fixture-verification'
  readonly owner: object
  getAccounts(projectId: string, signal: AbortSignal): Promise<PublicationAccountDto[]>
  getPosts(request: PublicationListRequest, signal: AbortSignal): Promise<PublicationPostListDto>
  getPost(request: PublicationDetailRequest, signal: AbortSignal): Promise<PublicationPostDto>
}

const publicationOwner = {}

export const platformClient: PlatformClient = {
  workspace: {
    async getHome(workspaceId, signal) {
      const { data } = await api.get('/api/me/dashboard', { baseURL: '', signal })
      const parsed = DashboardSchema.parse(data)
      if (!parsed.workspace.id || parsed.workspace.id !== workspaceId) {
        const mismatch = new Error('The requested Workspace is not available in the authenticated dashboard projection.')
        mismatch.name = 'WORKSPACE_SCOPE_NOT_AVAILABLE'
        throw mismatch
      }
      return {
        workspace: {
          id: parsed.workspace.id,
          name: parsed.workspace.name ?? 'Workspace',
          status: parsed.workspace.status,
        },
        tenantId: parsed.tenantId,
        recentProjects: parsed.recentProjects,
        projectedAt: parsed.timestamp,
      }
    },
  },
  effectiveAccess: {
    async getCatalog(keys) {
      // FB-GAP-002: there is no accepted five-factor effective-access catalog.
      // The isolated adapter is deliberately fail-closed in every environment.
      return Object.fromEntries(keys.map(key => [key, unknownAccess(key)]))
    },
  },
  publication: {
    origin: 'platform-authenticated',
    owner: publicationOwner,
    getAccounts: (projectId, signal) => PublicationReadAPI.getAccounts(projectId, signal),
    getPosts: (request, signal) => PublicationReadAPI.getPosts(request, signal),
    getPost: (request, signal) => PublicationReadAPI.getPost(request, signal),
  },
}

export const platformQueryKeys = {
  workspaceHome: (workspaceId: string) => ['platform', 'workspace', workspaceId, 'home'] as const,
  effectiveAccess: (keys: readonly string[]) => ['platform', 'effective-access', ...[...keys].sort()] as const,
}

export function isWorkspaceAccessFailure(error: unknown) {
  const failure = error as { name?: string; response?: { status?: number } }
  return failure?.name === 'WORKSPACE_SCOPE_NOT_AVAILABLE' || [401, 403, 404].includes(failure?.response?.status ?? 0)
}

export function useWorkspaceHome(workspaceId: string) {
  const { binding, retired } = useWorkspaceBinding()
  const available = !retired && binding.workspaceId === workspaceId
  const query = useQuery({
    queryKey: [...platformQueryKeys.workspaceHome(workspaceId), binding.id],
    queryFn: async ({ signal }) => {
      try {
        const data = await platformClient.workspace.getHome(workspaceId, signal)
        if (signal.aborted || binding.getSnapshot()) throw new Error('Workspace request retired')
        return data
      } catch (error) {
        if (!signal.aborted && isWorkspaceAccessFailure(error)) binding.retire()
        throw error
      }
    },
    enabled: Boolean(workspaceId) && available,
    retry: false,
    staleTime: 0,
    gcTime: 0,
  })
  return { ...query, data: available && !isWorkspaceAccessFailure(query.error) ? query.data : undefined,
    unavailable: !available || isWorkspaceAccessFailure(query.error) }
}

export function useEffectiveAccessCatalog(keys: readonly string[]) {
  return useQuery({
    queryKey: platformQueryKeys.effectiveAccess(keys),
    queryFn: () => platformClient.effectiveAccess.getCatalog(keys),
    staleTime: 0,
  })
}
