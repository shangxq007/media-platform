import { useCallback, useLayoutEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { renderHttpStatus, renderReadSource, type RenderReadSource } from '../api/render-jobs'
import { ProductAppShell } from '../components/app-shell/AppShell'
import { Button, Panel } from '../components/design-system'
import { JobDetail } from '../components/render-jobs/JobDetail'
import { RENDER_JOB_STATUSES } from '../contracts/app/render-job'
import { useWorkspaceBinding } from '../foundation/workspaceSession'
import { InteractionDialog } from '../interaction/InteractionDialog'
import { SelectionProvider, useInteractionStore, useSelection, useSurfaceAdapter } from '../interaction/SelectionContext'

let nextLifetime = 0
const identities = new WeakMap<object, number>()
function identity(source: object) { if (!identities.has(source)) identities.set(source, ++nextLifetime); return identities.get(source)! }
const options = { retry: false, staleTime: 0, gcTime: 0, refetchOnWindowFocus: false, refetchOnReconnect: false } as const
type Reader = <T>(signal: AbortSignal, action: () => Promise<T>) => Promise<T>

export function RenderPage() {
  return <ProductAppShell surfaceId="operations"><RenderBrowser /></ProductAppShell>
}

/** The normal route has exactly one authenticated source; test sources are explicit props only. */
export function RenderBrowser({ source = renderReadSource }: { source?: RenderReadSource }) {
  const { binding, retired } = useWorkspaceBinding()
  const [epoch, setEpoch] = useState(0)
  return retired ? <div className="ff-page"><h1>Render jobs</h1><p role="alert">Authentication is unavailable. Sign in and reopen Render jobs.</p></div>
    : <RenderSession key={`${binding.id}:${identity(source)}:${epoch}`} source={source} reload={() => setEpoch(value => value + 1)} />
}

function RenderSession({ source, reload }: { source: RenderReadSource; reload: () => void }) {
  const { binding } = useWorkspaceBinding()
  const client = useQueryClient()
  const [lifetime] = useState(() => ++nextLifetime)
  const [prefix] = useState(() => ['platform', 'render', binding.id, identity(source), lifetime] as const)
  const live = useRef(false), stopped = useRef(false)
  const [denied, setDenied] = useState<number | null>(null)
  const [projectId, setProjectId] = useState('')
  useLayoutEffect(() => {
    live.current = true
    const clear = () => { void client.cancelQueries({ queryKey: prefix }); client.removeQueries({ queryKey: prefix }) }
    const unsubscribe = binding.subscribe(clear)
    return () => { live.current = false; unsubscribe(); clear() }
  }, [binding, client, prefix])
  const read: Reader = useCallback(async (signal, action) => {
    try {
      signal.throwIfAborted()
      if (!live.current || stopped.current || binding.getSnapshot()) throw new DOMException('Read retired', 'AbortError')
      const result = await action()
      signal.throwIfAborted()
      if (!live.current || stopped.current || binding.getSnapshot()) throw new DOMException('Read retired', 'AbortError')
      return result
    } catch (error) {
      const status = renderHttpStatus(error)
      if (!signal.aborted && live.current && !stopped.current && (status === 401 || status === 403)) {
        stopped.current = true; setDenied(status)
        void client.cancelQueries({ queryKey: prefix }); client.removeQueries({ queryKey: prefix })
        if (status === 401) binding.retire()
      }
      throw error
    }
  }, [binding, client, prefix])
  const discovery = useQuery({ ...options, queryKey: [...prefix, 'projects'], enabled: denied === null,
    queryFn: ({ signal }) => read(signal, async () => {
      const tenant = await source.tenant(signal); signal.throwIfAborted()
      return { tenant, projects: await source.projects(tenant, signal) }
    }) })
  const scope = !denied && !discovery.isFetching && !discovery.error ? discovery.data : undefined
  return <section className="ff-page ff-render" aria-label="Render job browser">
    <header className="ff-page-heading"><div><span>Operations · Read only</span><h1>Render jobs</h1><p>Choose an authorized Project to browse its returned jobs.</p></div></header>
    {denied ? <><p role="alert">Render access denied. Previous Project and job data have been cleared.</p><Button onClick={reload}>Reload authorized projects</Button></>
      : discovery.isFetching ? <p role="status">Loading authorized projects…</p>
        : discovery.error ? <><p role="alert">Could not read authorized projects.</p><Button onClick={reload}>Retry project discovery</Button></>
          : scope ? <>
            <div className="ff-table-toolbar"><label>Project<select aria-label="Project" value={projectId} onChange={event => setProjectId(event.target.value)}>
              <option value="">Choose a Project</option>{scope.projects.map(project => <option key={project.id} value={project.id}>{project.name || project.id}</option>)}
            </select></label><Button onClick={reload}>Refresh projects</Button></div>
            {!scope.projects.length ? <p role="status">No authorized projects were returned.</p> : !projectId ? <p>Select a Project to read its Render jobs.</p> : null}
            {projectId && scope.projects.some(project => project.id === projectId) ? <SelectionProvider key={`${scope.tenant}:${projectId}`} scope={{ surfaceId: 'operations', workspaceId: binding.workspaceId, projectId }}>
              <ProjectJobs source={source} tenant={scope.tenant} projectId={projectId} prefix={prefix} read={read} />
            </SelectionProvider> : null}
          </> : null}
  </section>
}

function ProjectJobs({ source, tenant, projectId, prefix, read }: { source: RenderReadSource; tenant: string; projectId: string; prefix: readonly unknown[]; read: Reader }) {
  const client = useQueryClient(), store = useInteractionStore(), selection = useSelection()
  const [key] = useState(() => [...prefix, tenant, projectId])
  const [detail, setDetail] = useState<string | null>(null)
  const [filter, setFilter] = useState('ALL')
  const [search, setSearch] = useState('')
  const [descending, setDescending] = useState(false)
  const query = useQuery({ ...options, queryKey: [...key, 'list'], queryFn: ({ signal }) => read(signal, async () => {
    const project = await source.project(tenant, projectId, signal); signal.throwIfAborted()
    return { project, jobs: await source.jobs(tenant, projectId, signal) }
  }) })
  const data = !query.isFetching && !query.error ? query.data : undefined
  const needle = search.trim().toLowerCase()
  const jobs = (data?.jobs ?? []).filter(job => (filter === 'ALL' || job.status === filter)
    && [job.id, job.profile, job.timelineSnapshotId].some(value => value.toLowerCase().includes(needle)))
    .sort((a, b) => (a.id < b.id ? -1 : a.id > b.id ? 1 : 0) * (descending ? -1 : 1))
  useSurfaceAdapter({ objects: () => jobs.map(job => ({ id: job.id, kind: 'RENDER_JOB' as const, title: job.id })), supports: [], handle: () => false })
  useLayoutEffect(() => () => { void client.cancelQueries({ queryKey: key }); client.removeQueries({ queryKey: key }) }, [client, key])
  const close = () => { setDetail(null); store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'inspect', open: false }) }
  return <Panel title="Returned Render jobs">
    <Button onClick={() => { close(); void query.refetch() }} disabled={query.isFetching}>Refresh jobs</Button>
    {query.isFetching ? <p role="status">Resolving Project and loading jobs…</p> : query.error ? <><p role="alert">{renderHttpStatus(query.error) === 404 ? 'Project not found in the current scope.' : 'Could not read Render jobs.'}</p><Button onClick={() => void query.refetch()}>Retry job read</Button></> : null}
    {data ? <><p>Filtering applies to returned records only. The API does not provide pagination or an inventory completeness guarantee.</p>
      <div className="ff-table-toolbar">
        <label>Search returned jobs<input className="ff-input" type="search" value={search} onChange={event => { close(); setSearch(event.target.value) }} /></label>
        <label>Status<select aria-label="Status" value={filter} onChange={event => { close(); setFilter(event.target.value) }}><option value="ALL">All statuses</option>{RENDER_JOB_STATUSES.map(status => <option key={status} value={status}>{status}</option>)}</select></label>
        <label>Sort by job ID<select aria-label="Sort by job ID" value={descending ? 'descending' : 'ascending'} onChange={event => setDescending(event.target.value === 'descending')}><option value="ascending">Ascending</option><option value="descending">Descending</option></select></label>
      </div><p role="status">Showing {jobs.length} of {data.jobs.length} returned jobs.</p>
      {!data.jobs.length ? <p role="status">No Render jobs were returned for this authorized Project.</p> : !jobs.length ? <p role="status">No returned jobs match these filters.</p> : <ul className="space-y-2">{jobs.map(job => <li key={job.id}><Button aria-pressed={selection.primarySelectedObject?.id === job.id} onClick={() => {
        store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [job.id] }); store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'inspect', open: true }); setDetail(job.id)
      }}>Inspect {job.id}</Button> <span>{job.profile} · {job.status}</span></li>)}</ul>}
    </> : null}
    {detail && data ? <InteractionDialog title="Render job details" onClose={close} className="w-[min(40rem,calc(100vw-2rem))] max-h-[76vh] overflow-y-auto rounded-xl border border-gray-700 bg-gray-950 p-4 shadow-xl [&_.ff-dialog-heading]:mb-4 [&_.ff-dialog-heading]:flex [&_.ff-dialog-heading]:justify-between"><RenderDetail key={detail} source={source} tenant={tenant} projectId={projectId} jobId={detail} prefix={key} read={read} missing={() => {
      store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] })
      client.setQueryData<typeof query.data>([...key, 'list'], value => value ? { ...value, jobs: value.jobs.filter(job => job.id !== detail) } : value)
    }} /></InteractionDialog> : null}
  </Panel>
}

function RenderDetail({ source, tenant, projectId, jobId, prefix, read, missing }: { source: RenderReadSource; tenant: string; projectId: string; jobId: string; prefix: readonly unknown[]; read: Reader; missing: () => void }) {
  const query = useQuery({ ...options, queryKey: [...prefix, 'detail', jobId], queryFn: ({ signal }) => read(signal, () => source.job(tenant, projectId, jobId, signal)) })
  const notified = useRef(false)
  useLayoutEffect(() => { if (renderHttpStatus(query.error) === 404 && !notified.current) { notified.current = true; missing() } }, [query.error, missing])
  return <>{query.isFetching ? <p role="status">Loading Render job detail…</p> : query.error ? <p role="alert">{renderHttpStatus(query.error) === 404 ? 'This Render job is no longer available in this Project.' : 'Could not read Render job detail.'}</p> : query.data ? <JobDetail job={query.data} /> : null}
    <Button onClick={() => void query.refetch()} disabled={query.isFetching}>{query.error ? 'Retry detail read' : 'Refresh detail'}</Button>
    <p>Progress, times, failure details and Artifact availability are not provided by this read contract.</p></>
}
