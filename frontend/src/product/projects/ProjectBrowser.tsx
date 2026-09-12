import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Badge, Button, EmptyState, Panel, PropertyRow, Search, Skeleton, Status } from '../../components/design-system'
import { AsyncStatePanel } from '../../foundation/errors'
import { useWorkspaceHome } from '../../foundation/platformClient'
import { useWorkspaceBinding } from '../../foundation/workspaceSession'
import { InteractionDialog } from '../../interaction/InteractionDialog'
import { defaultProjectBrowsing, selectRecentProjects, type ProjectBrowsing, type ProjectOrder } from './model'

export function ProjectBrowser({ workspaceId }: { workspaceId: string }) {
  const { binding, retired } = useWorkspaceBinding()
  return <BrowserSession key={`${binding?.id ?? workspaceId}:${retired}`} workspaceId={workspaceId} />
}

function BrowserSession({ workspaceId }: { workspaceId: string }) {
  const { binding } = useWorkspaceBinding()
  const home = useWorkspaceHome(workspaceId)
  const [browsing, setBrowsing] = useState(() => binding?.browsing ?? defaultProjectBrowsing())
  const [summaryOpen, setSummaryOpen] = useState(false)
  const root = useRef<HTMLDivElement>(null)
  const search = useRef<HTMLDivElement>(null)
  const latest = useRef(browsing)
  const rendered = useRef(browsing)
  if (rendered.current !== browsing) { latest.current = browsing; rendered.current = browsing }
  const projects = home.data?.recentProjects ?? []
  const visible = selectRecentProjects(projects, browsing)
  const selected = projects.find(project => project.id === browsing.selectedId)
  const statuses = [...new Set(projects.flatMap(project => project.status ? [project.status] : []))].sort()
  const update = (patch: Partial<ProjectBrowsing>) => setBrowsing({ ...latest.current, ...patch })
  const clearConditions = () => update({ query: '', status: '', order: 'created-desc', scrollTop: 0 })

  useLayoutEffect(() => {
    if (binding && !binding.getSnapshot()) binding.browsing = browsing
  }, [binding, browsing])
  useLayoutEffect(() => {
    const scroller = root.current?.closest<HTMLElement>('.ff-center-workspace')
    if (!scroller) return
    const save = () => {
      const next = { ...latest.current, scrollTop: scroller.scrollTop }
      latest.current = next
      if (binding && !binding.getSnapshot()) binding.browsing = next
    }
    scroller.addEventListener('scroll', save)
    return () => { scroller.removeEventListener('scroll', save) }
  }, [binding])
  useLayoutEffect(() => {
    if (!home.data) return
    const scroller = root.current?.closest<HTMLElement>('.ff-center-workspace')
    if (scroller) {
      scroller.scrollTop = Math.max(0, Math.min(latest.current.scrollTop, scroller.scrollHeight - scroller.clientHeight))
      latest.current = { ...latest.current, scrollTop: scroller.scrollTop }
      if (binding && !binding.getSnapshot()) binding.browsing = latest.current
    }
  }, [home.data, home.dataUpdatedAt, browsing.query, browsing.status, browsing.order, binding])
  useEffect(() => {
    if (home.data && browsing.selectedId && !selected) {
      update({ selectedId: null })
      setSummaryOpen(false)
      search.current?.querySelector('input')?.focus({ preventScroll: true })
    }
  }, [home.data, browsing.selectedId, selected])

  if (home.unavailable) return <AsyncStatePanel state="UNAVAILABLE" title="Workspace access unavailable"><p>Reopen this Workspace after signing in with current access. Previous project information has been cleared.</p></AsyncStatePanel>
  return <div ref={root}>
    <p>Search and filters apply only to returned recent projects (up to 5), not all Workspace projects.</p>
    <div className="ff-table-toolbar">
      <div ref={search}><Search label="Search recent projects" placeholder="Search recent projects" value={browsing.query} onChange={event => update({ query: event.target.value, scrollTop: 0 })} /></div>
      <label>Status<select aria-label="Status" value={browsing.status} onChange={event => update({ status: event.target.value, scrollTop: 0 })}><option value="">All statuses</option>{[...new Set([...statuses, ...(browsing.status ? [browsing.status] : [])])].map(status => <option key={status} value={status}>{status}</option>)}</select></label>
      <label>Sort recent projects<select aria-label="Sort recent projects" value={browsing.order} onChange={event => update({ order: event.target.value as ProjectOrder, scrollTop: 0 })}><option value="created-desc">Newest created first</option><option value="name-asc">Name A–Z</option><option value="name-desc">Name Z–A</option></select></label>
      <Button onClick={clearConditions}>Clear all conditions</Button>
      <Button onClick={() => void home.refetch({ cancelRefetch: true })}>Refresh</Button>
    </div>
    <p aria-label="Active browsing conditions"><Badge>{browsing.query.trim() ? `Name: ${browsing.query.trim()}` : 'Any name'}</Badge> <Badge>{browsing.status || 'All statuses'}</Badge> <Badge>{browsing.order === 'created-desc' ? 'Newest created first' : browsing.order === 'name-asc' ? 'Name A–Z' : 'Name Z–A'}</Badge></p>
    {home.isLoading ? <Skeleton label="Loading recent projects" /> : null}
    {home.isFetching && home.data ? <p role="status">Refreshing recent projects…</p> : null}
    {home.error ? <AsyncStatePanel state="ERROR" title="Recent projects could not be loaded"><p>{home.data ? 'Showing the last successful response. Refresh failed.' : 'Try loading the Workspace again.'}</p><Button onClick={() => void home.refetch()}>Retry</Button></AsyncStatePanel> : null}
    {home.data ? <>
      <p role="status">{visible.length} of {projects.length} returned recent projects</p>
      {projects.length === 0 ? <EmptyState title="No recent projects" description="The Workspace response returned no recent projects." /> : visible.length === 0 ? <><EmptyState title="No matching recent projects" description="Clear the conditions to see the returned recent projects." /><Button onClick={clearConditions}>Clear filters</Button></> : <div className="ff-card-grid">{visible.map(project => <Panel key={project.id} title={project.name} className={project.id === browsing.selectedId ? 'ff-project-card--selected' : ''}>
        <p>{project.description || 'No description available.'}</p><Status label={project.status || 'Status unavailable'} />
        <div className="ff-project-card-actions"><Button aria-label={`Summary: ${project.name}`} aria-haspopup="dialog" onClick={() => { update({ selectedId: project.id }); setSummaryOpen(true) }}>View summary</Button><Button disabled title="Workspace-to-Project resolution is not integrated (FB-GAP-001).">Open project</Button></div>
      </Panel>)}</div>}
      {selected ? <p>Selected: {selected.name} <Button onClick={() => { update({ selectedId: null }); setSummaryOpen(false) }}>Clear selection</Button></p> : null}
    </> : null}
    {summaryOpen && selected ? <InteractionDialog className="ff-project-summary" title="Project summary" onClose={() => setSummaryOpen(false)}>
      <PropertyRow label="Name">{selected.name}</PropertyRow><PropertyRow label="Description">{selected.description || 'No description available.'}</PropertyRow><PropertyRow label="Status">{selected.status || 'Unavailable'}</PropertyRow><PropertyRow label="Created">{selected.createdAt || 'Unavailable'}</PropertyRow><p>Read-only summary from recent projects. Project entry is not yet available.</p>
    </InteractionDialog> : null}
  </div>
}
