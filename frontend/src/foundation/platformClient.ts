import { useQuery } from '@tanstack/react-query'
import { z } from 'zod'
import api from '../api'
import type { EffectiveAccessCatalog } from './effectiveAccess'
import { unknownAccess } from './effectiveAccess'

const ProjectSummarySchema = z.object({
  id: z.string().min(1),
  tenantId: z.string().min(1).nullable().optional(),
  name: z.string().min(1),
  description: z.string().nullish(),
  status: z.string().optional(),
  createdAt: z.string().optional(),
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
  readonly createdAt?: string
}

export interface WorkspaceHomeProjection {
  readonly workspace: WorkspaceSummary
  readonly tenantId: string | null
  readonly recentProjects: readonly ProjectSummary[]
  readonly projectedAt?: string
}

export interface PlatformClient {
  readonly workspace: {
    getHome(workspaceId: string): Promise<WorkspaceHomeProjection>
  }
  readonly effectiveAccess: {
    getCatalog(keys: readonly string[]): Promise<EffectiveAccessCatalog>
  }
}

export const platformClient: PlatformClient = {
  workspace: {
    async getHome(workspaceId) {
      const { data } = await api.get('/me/dashboard')
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
}

export const platformQueryKeys = {
  workspaceHome: (workspaceId: string) => ['platform', 'workspace', workspaceId, 'home'] as const,
  effectiveAccess: (keys: readonly string[]) => ['platform', 'effective-access', ...[...keys].sort()] as const,
}

export function useWorkspaceHome(workspaceId: string) {
  return useQuery({
    queryKey: platformQueryKeys.workspaceHome(workspaceId),
    queryFn: () => platformClient.workspace.getHome(workspaceId),
    enabled: Boolean(workspaceId),
  })
}

export function useEffectiveAccessCatalog(keys: readonly string[]) {
  return useQuery({
    queryKey: platformQueryKeys.effectiveAccess(keys),
    queryFn: () => platformClient.effectiveAccess.getCatalog(keys),
    staleTime: 0,
  })
}
