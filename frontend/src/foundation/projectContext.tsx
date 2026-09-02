import { createContext, useContext, type ReactNode } from 'react'
import { useWorkspaceHome } from './platformClient'
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

export function ProjectContextProvider({ workspaceId, projectId, children }: {
  workspaceId: string
  projectId: string
  children: ReactNode
}) {
  const home = useWorkspaceHome(workspaceId)
  const candidate = home.data?.recentProjects.find(project => project.id === projectId)
  const status: ProjectContextStatus = home.isLoading ? 'LOADING' : home.error ? 'ERROR' : 'BLOCKED'
  const reason = home.error
    ? 'Workspace context could not be loaded without disclosing resource existence.'
    : 'Project context is visible as route metadata, but the server cannot yet verify the Workspace-to-Project relationship (FB-GAP-001). Shell-level inferred commands remain disabled; focused gateways must validate every read and operation on the server.'

  return (
    <ProjectContext.Provider value={{
      workspaceId,
      tenantId: home.data?.tenantId ?? null,
      projectId,
      project: { kind: 'PROJECT', id: projectId, label: candidate?.name },
      projectName: candidate?.name,
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
