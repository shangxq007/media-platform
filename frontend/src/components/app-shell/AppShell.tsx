import { Link } from '@tanstack/react-router'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { Badge, Breadcrumb, Button, CommandPalette, IconButton, ResizablePanel } from '../design-system'
import { commandRegistry, getCommandAvailability, getShortcut, type ShortcutOverrides } from '../../foundation/commandRegistry'
import { useEffectiveAccessCatalog } from '../../foundation/platformClient'
import { surfaceRegistry, getSurface, type SurfaceId } from '../../foundation/surfaceRegistry'
import type { ProjectContextValue } from '../../foundation/projectContext'
import { useTranslation } from '../../localization'

export function WorkspaceHeader({ workspaceId, project }: { workspaceId?: string; project?: ProjectContextValue }) {
  return (
    <header className="ff-workspace-header">
      <Link className="ff-brand" to={workspaceId ? "/w/$workspaceId/home" : "/"} params={{ workspaceId: workspaceId ?? "" }} aria-label="Media Platform home"><span aria-hidden="true">MP</span><strong>Media Platform</strong></Link>
      <Breadcrumb renderLink={item => <Link to="/w/$workspaceId/home" params={{ workspaceId: workspaceId ?? "" }}>{item.label}</Link>} items={[
        ...(workspaceId ? [{ label: workspaceId, href: `/w/${encodeURIComponent(workspaceId)}/home` }] : []),
        ...(project ? [{ label: project.projectName ?? project.projectId }] : []),
      ]} />
      <div className="ff-header-actions"><IconButton label="Open activity" icon="◴" /><IconButton label="Open notifications" icon="●" /><IconButton label="Open help" icon="?" /></div>
    </header>
  )
}

export function GlobalNavigation({ workspaceId }: { workspaceId?: string }) {
  return <nav className="ff-global-nav" aria-label="Global navigation"><Link to={workspaceId ? "/w/$workspaceId/home" : "/"} params={{ workspaceId: workspaceId ?? "" }}>Workspace</Link><Link to="/operations/overview">Operations</Link><Link to="/admin/organization">Admin</Link><Link to="/developer/capabilities">Developer</Link></nav>
}

export function ProjectNavigation({ project }: { project: ProjectContextValue }) {
  return <nav className="ff-project-nav" aria-label="Project navigation"><Link to="/w/$workspaceId/projects/$projectId/overview" params={project}>Overview</Link><Link to="/w/$workspaceId/projects/$projectId/review" params={project}>Review</Link><Link to="/w/$workspaceId/projects/$projectId/production" params={project}>Production</Link></nav>
}

export function SurfaceSwitcher({ project, currentSurfaceId }: { project: ProjectContextValue; currentSurfaceId: SurfaceId }) {
  const surfaces = surfaceRegistry.filter(surface => surface.projectScoped && surface.maturity !== 'HIDDEN')
  return <nav className="ff-surface-switcher" aria-label="Project surface switcher">{surfaces.map(surface => <Link key={surface.id} to={surface.buildRoute(project)} aria-current={surface.id === currentSurfaceId ? 'page' : undefined}>{surface.displayName}<Badge tone={surface.maturity === 'PREVIEW' ? 'warning' : 'neutral'}>{surface.maturity}</Badge></Link>)}</nav>
}

export function AssetBrowserHost() {
  return <div className="ff-host-state"><strong>Assets</strong><p>Scoped artifact and media projections are not integrated. No storage coordinates are used.</p></div>
}
export function InspectorHost() {
  return <div className="ff-host-state"><strong>Inspector</strong><p>Select a canonical reference to inspect safe projected properties.</p></div>
}
export function CenterWorkspace({ children }: { children: ReactNode }) { return <main id="main-content" className="ff-center-workspace" tabIndex={-1}>{children}</main> }
export function BottomPanel() { return <div className="ff-host-state"><strong>Timeline / activity</strong><p>Canonical command results will appear here when integrated.</p></div> }
export function ActivityPanel() { return <aside className="ff-activity-panel" aria-label="Activity panel"><strong>Activity</strong><p>No scoped activity projection is available for this view.</p></aside> }

export function ProductAppShell({ surfaceId, workspaceId, project, children, shortcutOverrides = {} }: {
  surfaceId: SurfaceId
  workspaceId?: string
  project?: ProjectContextValue
  children: ReactNode
  shortcutOverrides?: ShortcutOverrides
}) {
  const { t } = useTranslation()
  const surface = getSurface(surfaceId)
  const accessKeys = useMemo(() => commandRegistry.flatMap(command => command.requiredAccessKey ? [command.requiredAccessKey] : []), [])
  const access = useEffectiveAccessCatalog(accessKeys)
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [leftVisible, setLeftVisible] = useState(surface.shellRegions['asset-browser'] !== 'HIDDEN')
  const [rightVisible, setRightVisible] = useState(surface.shellRegions.inspector !== 'HIDDEN')
  const [bottomVisible, setBottomVisible] = useState(surface.shellRegions['bottom-panel'] !== 'HIDDEN')

  useEffect(() => {
    const listener = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); setPaletteOpen(true) }
    }
    window.addEventListener('keydown', listener)
    return () => window.removeEventListener('keydown', listener)
  }, [])

  const paletteActions = commandRegistry.map(command => {
    const availability = getCommandAvailability(command, { surfaceId, hasResolvedProject: project?.status === 'RESOLVED', accessCatalog: access.data })
    return { id: command.id, label: command.label, shortcut: getShortcut(command, shortcutOverrides), disabledReason: availability.available ? undefined : availability.reason, onSelect: () => { void command.execute(); setPaletteOpen(false) } }
  })

  return (
    <div className="ff-app-shell" data-surface={surfaceId}>
      <a className="skip-link" href="#main-content">Skip to main content</a>
      <WorkspaceHeader workspaceId={workspaceId} project={project} />
      <GlobalNavigation workspaceId={workspaceId} />
      {project ? <ProjectNavigation project={project} /> : null}
      {project ? <SurfaceSwitcher project={project} currentSurfaceId={surfaceId} /> : null}
      <div className="ff-shell-toolbar" aria-label="Panel controls">
        {surface.shellRegions['asset-browser'] !== 'HIDDEN' ? <Button variant="ghost" aria-pressed={leftVisible} onClick={() => setLeftVisible(value => !value)}>Toggle asset browser</Button> : null}
        {surface.shellRegions.inspector !== 'HIDDEN' ? <Button variant="ghost" aria-pressed={rightVisible} onClick={() => setRightVisible(value => !value)}>Toggle inspector</Button> : null}
        {surface.shellRegions['bottom-panel'] !== 'HIDDEN' ? <Button variant="ghost" aria-pressed={bottomVisible} onClick={() => setBottomVisible(value => !value)}>Toggle bottom panel</Button> : null}
        <Button variant="ghost" onClick={() => setPaletteOpen(true)}>{t('shell.commands')} <kbd>⌘K</kbd></Button>
      </div>
      <div className="ff-shell-body">
        {leftVisible ? <ResizablePanel title="Asset browser" side="left"><AssetBrowserHost /></ResizablePanel> : null}
        <div className="ff-shell-center"><CenterWorkspace>{children}</CenterWorkspace>{bottomVisible ? <ResizablePanel title="Bottom panel" side="bottom" initialSize={160} min={120} max={360}><BottomPanel /></ResizablePanel> : null}</div>
        {rightVisible ? <ResizablePanel title="Inspector" side="right"><InspectorHost /></ResizablePanel> : null}
      </div>
      {surface.shellRegions['activity-panel'] !== 'HIDDEN' ? <ActivityPanel /> : null}
      <CommandPalette open={paletteOpen} actions={paletteActions} onClose={() => setPaletteOpen(false)} />
    </div>
  )
}
