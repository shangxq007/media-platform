import { Outlet, useParams } from '@tanstack/react-router'

import { WorkspaceSessionProvider } from '../foundation/workspaceSession'

export default function RootLayout() {
  const { workspaceId, projectId } = useParams({ strict: false }) as { workspaceId?: string; projectId?: string }
  return (
    <div className="ff-root" data-theme="dark">
      <WorkspaceSessionProvider workspaceId={workspaceId} projectId={projectId}><Outlet /></WorkspaceSessionProvider>
    </div>
  )
}
