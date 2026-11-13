import { createContext, useContext, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { isWorkspaceAccessFailure, platformClient, useWorkspaceHome } from './platformClient'
import { useWorkspaceBinding } from './workspaceSession'
import type { ProjectRef } from './references'

export type ProjectContextStatus = 'LOADING' | 'RESOLVED' | 'BLOCKED' | 'ERROR'

export interface ProjectContextValue {
  readonly workspaceId: string
  readonly tenantId: string | null
  readonly projectId: string
  readonly project: ProjectRef
  readonly projectName?: string
  readonly status: ProjectContextStatus
  readonly reason: string
}

const ProjectContext = createContext<ProjectContextValue | null>(null)

export function ProjectContextProvider({ workspaceId, projectId, resolveProject = false, children }: {
  workspaceId: string
  projectId: string
  resolveProject?: boolean
  children: ReactNode
}) {
  const { binding, retired } = useWorkspaceBinding()
  const home = useWorkspaceHome(workspaceId, !resolveProject)
  const scoped = useQuery({
    queryKey: ['platform', 'workspace', workspaceId, 'project', projectId, binding.id],
    queryFn: async ({ signal }) => {
      try {
        const data = await platformClient.project.getContext(workspaceId, projectId, signal)
        if (signal.aborted || binding.getSnapshot()) throw new Error('Project context request retired')
        return data
      } catch (error) {
        if (!signal.aborted && isWorkspaceAccessFailure(error)) binding.retire()
        throw error
      }
    },
    enabled: resolveProject && !retired && binding.workspaceId === workspaceId && Boolean(projectId),
    retry: false,
    staleTime: 0,
    gcTime: 0,
  })
  const candidate = home.data?.recentProjects.find(project => project.id === projectId)
  const status: ProjectContextStatus = resolveProject
    ? scoped.data ? 'RESOLVED' : scoped.isLoading ? 'LOADING' : scoped.error ? 'ERROR' : 'BLOCKED'
    : home.isLoading ? 'LOADING' : home.error ? 'ERROR' : 'BLOCKED'
  const reason = resolveProject
    ? scoped.data ? 'Workspace and Project scope verified by authenticated server reads.'
      : 'Authenticated Workspace and Project scope unavailable.'
    : home.error ? 'Workspace context could not be loaded without disclosing resource existence.'
      : 'Project context is visible as route metadata, but the server cannot yet verify the Workspace-to-Project relationship (FB-GAP-001). Shell-level inferred commands remain disabled; focused gateways must validate every read and operation on the server.'

  return (
    <ProjectContext.Provider value={{
      workspaceId,
      tenantId: resolveProject ? scoped.data?.tenantId ?? null : home.data?.tenantId ?? null,
      projectId,
      project: { kind: 'PROJECT', id: projectId, label: resolveProject ? scoped.data?.name : candidate?.name },
      projectName: resolveProject ? scoped.data?.name : candidate?.name,
      status,
      reason,
    }}>
      {children}
    </ProjectContext.Provider>
  )
}

export function useProjectContext(): ProjectContextValue {
  const context = useContext(ProjectContext)
  if (!context) throw new Error('useProjectContext must be used inside ProjectContextProvider')
  return context
}
