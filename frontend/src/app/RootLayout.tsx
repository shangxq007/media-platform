import { Outlet, useParams } from '@tanstack/react-router'

import { WorkspaceSessionProvider } from '../foundation/workspaceSession'

export default function RootLayout() {
  const { workspaceId } = useParams({ strict: false }) as { workspaceId?: string }
  return (
    <div className="ff-root" data-theme="dark">
      <WorkspaceSessionProvider workspaceId={workspaceId}><Outlet /></WorkspaceSessionProvider>
    </div>
  )
}
