import { useMemo, useState, type ReactNode } from 'react'
import { useLocation, useParams } from '@tanstack/react-router'
import { ProductAppShell } from '../components/app-shell/AppShell'
import { Badge, Button, EmptyState, Input, Panel, PropertyRow, Search, Skeleton, Status } from '../components/design-system'
import { AsyncStatePanel, classifyPlatformError } from '../foundation/errors'
import { AccessStatus, getEffectiveAccess } from '../foundation/effectiveAccess'
import { ProjectContextProvider, useProjectContext } from '../foundation/projectContext'
import { useEffectiveAccessCatalog, useWorkspaceHome } from '../foundation/platformClient'
import { getSurface, surfaceRegistry, type SurfaceId } from '../foundation/surfaceRegistry'

function routeParams(): { workspaceId?: string; projectId?: string } {
  return useParams({ strict: false }) as { workspaceId?: string; projectId?: string }
}

export function PageHeading({ eyebrow, title, description, actions }: { eyebrow: string; title: string; description: string; actions?: ReactNode }) {
  return <header className="ff-page-heading"><div><span>{eyebrow}</span><h1>{title}</h1><p>{description}</p></div>{actions ? <div className="ff-page-actions">{actions}</div> : null}</header>
}

export function RootLandingPage() {
  return <ProductAppShell surfaceId="workspace"><div className="ff-page"><PageHeading eyebrow="Workspace entry" title="Choose a workspace" description="The application never invents a default Workspace. Continue from an authenticated, server-projected workspace link." /><AsyncStatePanel state="UNAVAILABLE" title="Workspace chooser not integrated"><p>A canonical last-workspace or workspace-list projection is required before this page can redirect.</p></AsyncStatePanel></div></ProductAppShell>
}

export function WorkspaceHomePage() {
  const { workspaceId = '' } = routeParams()
  const home = useWorkspaceHome(workspaceId)
  const access = useEffectiveAccessCatalog(['project.create'])
  const createAccess = getEffectiveAccess(access.data, 'project.create')
  return (
    <ProductAppShell surfaceId="workspace" workspaceId={workspaceId}>
      <div className="ff-page">
        <PageHeading eyebrow="Workspace" title={home.data?.workspace.name ?? 'Workspace home'} description="Projects, recent work, and creation entry points from server-authoritative projections." actions={<Button disabled={createAccess.status !== 'AVAILABLE'} title={createAccess.explanation}>Create</Button>} />
        {home.isLoading ? <Skeleton label="Loading Workspace home" /> : null}
        {home.error ? <AsyncStatePanel state="ERROR" title="Workspace unavailable"><p>{classifyPlatformError(home.error).message}</p></AsyncStatePanel> : null}
        {home.data ? <>
          <AccessStatus entry={createAccess} />
          <section className="ff-dashboard-grid" aria-label="Workspace summary">
            <Panel title="Recent projects">
              {home.data.recentProjects.length ? <ul className="ff-project-list">{home.data.recentProjects.map(project => <li key={project.id}><div><strong>{project.name}</strong><span>{project.status ?? 'Status not projected'}</span></div><Button disabled title="Workspace-to-Project resolution is not integrated (FB-GAP-001).">Open</Button></li>)}</ul> : <EmptyState title="No recent projects" description="The accepted dashboard projection returned no recent Projects." />}
              <a className="ff-text-link" href={`/w/${encodeURIComponent(workspaceId)}/projects`}>View project list</a>
            </Panel>
            <Panel title="Recent assets"><EmptyState title="Asset projection unavailable" description="A scoped redacted media and Artifact list is required (FB-GAP-004)." /></Panel>
            <Panel title="Activity"><EmptyState title="No integrated activity feed" description="No Workspace-scoped typed activity projection is currently consumed." /></Panel>
            <Panel title="Recent renders / jobs"><EmptyState title="Scope selection required" description="Render APIs are Project-scoped. This Home surface does not guess a Project from recency." /></Panel>
            <Panel title="Pinned / favorites"><EmptyState title="Not supported" description="The accepted API does not expose pinned or favorite Projects." /></Panel>
            <Panel title="Templates / recipes"><EmptyState title="Availability unknown" description="Recipe discovery requires a typed server access and catalog projection." /></Panel>
          </section>
        </> : null}
      </div>
    </ProductAppShell>
  )
}

export function ProjectListPage() {
  const { workspaceId = '' } = routeParams()
  const [query, setQuery] = useState('')
  const home = useWorkspaceHome(workspaceId)
  const projects = useMemo(() => home.data?.recentProjects.filter(project => project.name.toLowerCase().includes(query.toLowerCase())) ?? [], [home.data, query])
  return <ProductAppShell surfaceId="workspace" workspaceId={workspaceId}><div className="ff-page"><PageHeading eyebrow="Workspace" title="Projects" description="Recent Project projections for this authenticated Workspace. Opening remains fail-closed until scoped resolution exists." /><Search value={query} onChange={event => setQuery(event.target.value)} label="Search projects" />{home.isLoading ? <Skeleton /> : home.error ? <AsyncStatePanel state="ERROR" title="Projects unavailable"><p>The authenticated Workspace projection could not be loaded.</p></AsyncStatePanel> : projects.length ? <div className="ff-card-grid">{projects.map(project => <Panel key={project.id} title={project.name}><p>{project.description || 'No description projected.'}</p><Status label={project.status ?? 'Status unknown'} /><Button disabled title="FB-GAP-001">Open project</Button></Panel>)}</div> : <EmptyState title="No projects found" description={query ? 'No projected Project matches this search.' : 'No recent Projects were returned.'} />}</div></ProductAppShell>
}

export function ProjectFrame({ surfaceId, children }: { surfaceId: SurfaceId; children: ReactNode }) {
  const { workspaceId = '', projectId = '' } = routeParams()
  const scopeKey = `${workspaceId}\u0000${projectId}`
  return <ProjectContextProvider key={scopeKey} workspaceId={workspaceId} projectId={projectId}><ProjectFrameInner surfaceId={surfaceId}>{children}</ProjectFrameInner></ProjectContextProvider>
}

function ProjectFrameInner({ surfaceId, children }: { surfaceId: SurfaceId; children: ReactNode }) {
  const project = useProjectContext()
  return <ProductAppShell surfaceId={surfaceId} workspaceId={project.workspaceId} project={project}><div className="ff-page ff-project-surface"><div className="ff-context-warning" role="note"><Badge tone="warning">{project.status}</Badge><span>{project.reason}</span></div>{children}</div></ProductAppShell>
}

export function ProjectOverviewPage() {
  return <ProjectFrame surfaceId="project-overview"><ProjectOverviewContent /></ProjectFrame>
}
function ProjectOverviewContent() {
  const project = useProjectContext()
  const launchers = surfaceRegistry.filter(surface => surface.projectScoped && surface.id !== 'project-overview' && surface.maturity !== 'HIDDEN')
  return <><PageHeading eyebrow="Project" title={project.projectName ?? project.projectId} description="One canonical Project, presented across multiple surfaces without duplicating Project or Timeline identity." /><section className="ff-card-grid"><Panel title="Identity"><PropertyRow label="Project ID"><code>{project.projectId}</code></PropertyRow><PropertyRow label="Workspace route context"><code>{project.workspaceId}</code></PropertyRow></Panel>{['Revisions', 'Assets', 'Renders', 'Review projection', 'Activity'].map(label => <Panel key={label} title={label}><EmptyState title="Not loaded" description="Project scope cannot be server-verified yet, so this projection remains unavailable." /></Panel>)}</section><Panel title="Open another surface"><div className="ff-launcher-grid">{launchers.map(surface => <a key={surface.id} href={surface.buildRoute(project)}><strong>{surface.displayName}</strong><Badge tone={surface.maturity === 'PREVIEW' ? 'warning' : 'neutral'}>{surface.maturity}</Badge></a>)}</div></Panel></>
}

function BlockedCommand({ label, gap = 'FB-GAP-003' }: { label: string; gap?: string }) {
  return <Button disabled title={`Canonical application command unavailable (${gap}).`}>{label}</Button>
}

export const workflowNodeCategories = ['OPERATION', 'RENDER', 'REVIEW', 'WAIT', 'CONDITION', 'AGENT', 'INTEGRATION'] as const
export function WorkflowPage() {
  return <ProjectFrame surfaceId="workflow"><PageHeading eyebrow="Creative · Workflow" title="Process composer" description="Node categories are presentation metadata over canonical Workflow definitions and executions." actions={<BlockedCommand label="Invoke workflow" gap="FB-GAP-002" />} /><div className="ff-graph-shell"><Panel title="Node palette"><ul>{workflowNodeCategories.map(category => <li key={category}><Badge>{category}</Badge></li>)}</ul></Panel><Panel title="Workflow graph"><EmptyState title="No definition loaded" description="Select a server-projected Workflow version. Invocation remains blocked without effective access." /></Panel><Panel title="Validation"><Status label="Not validated" tone="warning" /><p>No client-side graph is treated as workflow process truth.</p></Panel></div></ProjectFrame>
}

export type AgentActionState = 'REQUEST' | 'RESOLVED_PLAN' | 'PREVIEW' | 'AUTHORIZATION' | 'RESULT'
export function AgentPage() {
  const states: readonly [AgentActionState, string][] = [['REQUEST', 'Conversation'], ['RESOLVED_PLAN', 'Plan'], ['PREVIEW', 'Preview'], ['AUTHORIZATION', 'Authorization'], ['RESULT', 'Execution / result']]
  return <ProjectFrame surfaceId="agent"><PageHeading eyebrow="Creative · Agent Studio" title="Agent workspace" description="Requests, resolved plans, previews, authorization, and results are visibly distinct. An Agent cannot mutate canonical state directly." actions={<BlockedCommand label="Authorize action" gap="FB-GAP-002/003" />} /><div className="ff-agent-grid"><Panel title="Conversation"><EmptyState title="No conversation" description="Free-form content is not logged by frontend telemetry." /></Panel><Panel title="Context & referenced objects"><p>Only typed safe references can be attached.</p></Panel>{states.slice(1).map(([state, label]) => <Panel key={state} title={label}><Badge tone={state === 'AUTHORIZATION' ? 'warning' : 'neutral'}>{state}</Badge><p>No server projection is available.</p></Panel>)}</div></ProjectFrame>
}

export function ProductionPage() {
  const [filter, setFilter] = useState('')
  return <ProjectFrame surfaceId="production"><PageHeading eyebrow="Production management" title="Production overview" description="A projection/control surface that owns neither Timeline nor Workflow." /><div className="ff-table-toolbar"><Search value={filter} onChange={event => setFilter(event.target.value)} label="Search production items" /><Input aria-label="Filter status" value="NOT_INTEGRATED" readOnly /><Button disabled>Sort</Button></div><Panel title="Shots, assets, and tasks"><EmptyState title="NOT_INTEGRATED" description="Canonical Shot, Scene, Task, assignment, milestone, and workload projections do not exist in the accepted application API (FB-GAP-007)." /></Panel><Panel title="Review and render status"><EmptyState title="Projection unavailable" description="No client-side joins are used to reconstruct production truth." /></Panel></ProjectFrame>
}

const operationsCards = [
  ['Renders', 'Scoped render list/detail exists; richer actions and Artifact linkage remain partial.', '/operations/renders', 'PARTIAL'],
  ['Executions', 'Typed coherent execution detail is missing.', undefined, 'API_GAP'],
  ['Workers / runtimes', 'No unified authorized projection is integrated.', undefined, 'API_GAP'],
  ['Devices', 'No typed operations device list is integrated.', undefined, 'API_GAP'],
  ['Providers', 'Provider identity is projected only where backed; no client eligibility decisions.', undefined, 'API_GAP'],
  ['Artifacts', 'Scoped redacted Artifact list is missing.', undefined, 'API_GAP'],
  ['Storage', 'Existing narrow storage health UI remains a legacy diagnostic.', '/operations/storage', 'FOUNDATION'],
  ['Incidents', 'No incident projection is integrated.', undefined, 'API_GAP'],
] as const

export function OperationsOverviewPage() {
  const [query, setQuery] = useState('')
  const cards = operationsCards.filter(([label]) => label.toLowerCase().includes(query.toLowerCase()))
  return <ProductAppShell surfaceId="operations"><div className="ff-page"><PageHeading eyebrow="Platform operations" title="Operations overview" description="Query and control-plane projections over canonical Render, Execution, Worker, Runtime, Provider, Artifact, and Storage owners." /><Search label="Search operations areas" value={query} onChange={event => setQuery(event.target.value)} /><div className="ff-card-grid">{cards.map(([title, description, href, status]) => <Panel key={title} title={title}><Badge tone={status === 'API_GAP' ? 'warning' : 'info'}>{status}</Badge><p>{description}</p>{href ? <a className="ff-text-link" href={href}>Open</a> : <Button disabled>Not integrated</Button>}</Panel>)}</div></div></ProductAppShell>
}

export function OperationsProjectionPage() {
  const location = useLocation()
  const area = location.pathname.endsWith('/storage') ? 'Storage' : 'Renders'
  return <ProductAppShell surfaceId="operations"><div className="ff-page"><PageHeading eyebrow="Platform operations" title={area} description="Only a narrow, honest foundation route is exposed; unsupported detail tabs remain absent." /><div className="ff-table-toolbar"><Search label={`Search ${area}`} /><Button disabled>Filter</Button><Button disabled>Sort</Button></div><AsyncStatePanel state="UNAVAILABLE" title={`${area} projection requires explicit scope`}><p>{area === 'Renders' ? 'The accepted Render API is Project-scoped. Operations does not guess a Project or reconstruct a global list.' : 'The existing storage health diagnostic has not yet been migrated to a typed authorized operations projection.'}</p></AsyncStatePanel></div></ProductAppShell>
}

const adminAreas = ['organization', 'members', 'workspaces', 'roles', 'security', 'billing', 'entitlements', 'usage', 'quota', 'policies', 'audit'] as const
const developerAreas = ['capabilities', 'plugins', 'providers', 'integrations', 'mcp', 'api-keys', 'webhooks', 'agents', 'recipes'] as const

export function ManagementFoundationPage() {
  const location = useLocation()
  const developer = location.pathname.startsWith('/developer/')
  const pathParts = location.pathname.split('/').filter(Boolean)
  const section = pathParts[pathParts.length - 1] ?? (developer ? 'capabilities' : 'organization')
  const areas = developer ? developerAreas : adminAreas
  const surfaceId = developer ? 'developer' : 'admin'
  const scopeMessage = developer ? 'Developer discovery requires server-projected effective access.' : 'Administration is scoped to the current authorized organization; no universal super-admin is assumed.'
  return <ProductAppShell surfaceId={surfaceId}><div className="ff-page"><PageHeading eyebrow={developer ? 'Developer foundation' : 'Organization administration'} title={section.replace(/-/g, ' ')} description={scopeMessage} /><nav className="ff-section-nav" aria-label={`${developer ? 'Developer' : 'Admin'} sections`}>{areas.map(area => <a key={area} aria-current={area === section ? 'page' : undefined} href={`/${developer ? 'developer' : 'admin'}/${area}`}>{area.replace(/-/g, ' ')}</a>)}</nav><Panel title="Integration status"><Badge tone="warning">FOUNDATION / NOT_INTEGRATED</Badge><p>This route is intentionally an honest maturity placeholder. It does not infer access from UI role, plan name, or provider presence.</p>{section === 'api-keys' ? <p>Secrets are never displayed after creation; creation is unavailable without a canonical command.</p> : null}<Button disabled title="Effective access and application command required">Configure</Button></Panel></div></ProductAppShell>
}

export function HiddenCreativeFoundationPage() {
  const location = useLocation()
  const surfaceId: SurfaceId = location.pathname.endsWith('/script')
    ? 'screenplay'
    : location.pathname.endsWith('/recipe')
      ? 'recipe'
      : 'storyboard'
  const surface = getSurface(surfaceId)
  return <ProjectFrame surfaceId={surfaceId}><PageHeading eyebrow="Creative foundation" title={surface.displayName} description="This semantic route is registered for deep-link integrity but remains hidden from normal navigation at HIDDEN maturity." /><AsyncStatePanel state="UNAVAILABLE" title="Surface not yet integrated"><p>No canonical authoring command is available. This route does not create shadow media or Timeline state.</p></AsyncStatePanel></ProjectFrame>
}
