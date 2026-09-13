import { useCallback, useLayoutEffect, useRef, useState } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ProductAppShell } from '../../components/app-shell/AppShell'
import { Button, Panel, PropertyRow } from '../../components/design-system'
import { useWorkspaceHome } from '../../foundation/platformClient'
import { useWorkspaceBinding } from '../../foundation/workspaceSession'
import { InteractionDialog } from '../../interaction/InteractionDialog'
import { httpStatus, READ_LIMIT, timelineReviewSource, type Revision, type ReviewDetail, type TimelineReviewSource } from './api'
import { browsingDefaults, filterReviews, statusLabel, type TimelineBrowsing } from './model'
import './timeline-review.css'

const options = { retry: false, staleTime: 0, gcTime: 0, refetchOnWindowFocus: false, refetchOnReconnect: false } as const
let nextLifetime = 0
type Reader = <T>(signal: AbortSignal, action: () => Promise<T>) => Promise<T>
function errorMessage(error: unknown) {
  switch (httpStatus(error)) {
    case 401: return 'Your session has expired. Sign in and reopen history.'
    case 403: return 'Access denied. Previously loaded information has been cleared.'
    case 404: return 'This resource is no longer available in the current scope.'
    default: return 'The read failed. Check your connection and retry.'
  }
}
export function TimelineHistoryPage() {
  const { workspaceId = '' } = useParams({ strict: false }) as { workspaceId?: string }
  return <ProductAppShell surfaceId="workspace" workspaceId={workspaceId}><div className="ff-page">
    <Link className="ff-text-link" to="/w/$workspaceId/projects" params={{ workspaceId }}>Back to projects</Link>
    <header className="ff-page-heading"><div><h1>Revision history and reviews</h1><p>Read-only browsing. Choose an authorized project to begin.</p></div></header>
    <TimelineBrowser workspaceId={workspaceId} />
  </div></ProductAppShell>
}
export function TimelineBrowser({ workspaceId, source = timelineReviewSource }: { workspaceId: string; source?: TimelineReviewSource }) {
  const { binding, retired } = useWorkspaceBinding()
  const home = useWorkspaceHome(workspaceId)
  if (retired || home.unavailable) return <p role="alert">Workspace access is unavailable. Sign in and reopen history.</p>
  if (home.isFetching) return <p role="status">Checking Workspace access…</p>
  if (home.error) return <><p role="alert">{errorMessage(home.error)}</p><Button onClick={() => void home.refetch()}>Retry Workspace read</Button></>
  if (!home.data?.tenantId) return <p role="alert">The authenticated Workspace did not provide a tenant. History is unavailable.</p>
  return <BrowserSession key={`${binding.id}:${home.data.tenantId}`} tenant={home.data.tenantId} source={source} />
}
function BrowserSession({ tenant, source }: { tenant: string; source: TimelineReviewSource }) {
  const { binding } = useWorkspaceBinding()
  const client = useQueryClient()
  const [prefix] = useState(() => ['platform', 'timeline-review', binding.id, tenant, ++nextLifetime])
  const [browsing, setBrowsing] = useState(() => binding.timelineBrowsing?.tenant === tenant ? binding.timelineBrowsing : browsingDefaults(tenant))
  const [denied, setDenied] = useState<number | null>(null)
  const live = useRef(false), stopped = useRef(false)
  useLayoutEffect(() => {
    live.current = true
    const clear = () => { void client.cancelQueries({ queryKey: prefix }); client.removeQueries({ queryKey: prefix }) }
    const unsubscribe = binding.subscribe(clear)
    return () => { live.current = false; unsubscribe(); clear() }
  }, [binding, client, prefix])
  const update = useCallback((patch: Partial<TimelineBrowsing>) => setBrowsing(previous => {
    const next = { ...previous, ...patch }
    if (!binding.getSnapshot() && !stopped.current) binding.timelineBrowsing = next
    return next
  }), [binding])
  const read: Reader = useCallback(async (signal, action) => {
    const check = () => { signal.throwIfAborted(); if (!live.current || stopped.current || binding.getSnapshot()) throw new DOMException('Read retired', 'AbortError') }
    try { check(); const result = await action(); check(); return result }
    catch (error) {
      const status = httpStatus(error)
      if (!signal.aborted && live.current && !stopped.current && (status === 401 || status === 403)) {
        stopped.current = true; binding.timelineBrowsing = null; setBrowsing(browsingDefaults(tenant)); setDenied(status)
        void client.cancelQueries({ queryKey: prefix }); client.removeQueries({ queryKey: prefix })
        if (status === 401) binding.retire()
      }
      throw error
    }
  }, [binding, client, prefix, tenant])
  const discovery = useQuery({ ...options, queryKey: [...prefix, 'projects'], enabled: denied === null,
    queryFn: ({ signal }) => read(signal, () => source.projects(tenant, signal)) })
  const projects = !discovery.isFetching && !discovery.error && !denied ? discovery.data : undefined
  useLayoutEffect(() => {
    if (projects && browsing.project && !projects.some(project => project.id === browsing.project)) update(browsingDefaults(tenant))
  }, [projects, browsing.project, tenant, update])
  if (denied) return <p role="alert">{errorMessage({ response: { status: denied } })} Return to projects and reopen history to recheck access.</p>
  return <section className="ff-timeline-review" aria-label="Revision and review browser">
    {discovery.isFetching ? <p role="status">Loading authorized projects…</p> : discovery.error ? <p role="alert">{errorMessage(discovery.error)}</p> : null}
    <Button disabled={discovery.isFetching} onClick={() => void discovery.refetch()}>{discovery.error ? 'Retry project discovery' : 'Refresh projects'}</Button>
    {projects ? <><label>Project<select aria-label="Project" value={browsing.project} onChange={event => update(browsingDefaults(tenant, event.target.value))}>
      <option value="">Choose a project</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name || project.id}</option>)}
    </select></label>
      {!projects.length ? <p role="status">No authorized projects were returned.</p> : !browsing.project ? <p>Select a project to browse history and reviews.</p> : null}
      {projects.some(project => project.id === browsing.project) ? <ProjectHistory key={browsing.project} {...{ source, tenant, browsing, update, prefix, read }} /> : null}
    </> : null}
  </section>
}
function ProjectHistory({ source, tenant, browsing, update, prefix, read }: { source: TimelineReviewSource; tenant: string; browsing: TimelineBrowsing; update: (patch: Partial<TimelineBrowsing>) => void; prefix: unknown[]; read: Reader }) {
  const client = useQueryClient()
  const [key] = useState(() => [...prefix, browsing.project, ++nextLifetime])
  const [reviewId, setReviewId] = useState<string | null>(null)
  const [notice, setNotice] = useState('')
  const root = useRef<HTMLDivElement>(null)
  const query = useQuery({ ...options, queryKey: [...key, 'history'], queryFn: ({ signal }) => read(signal, async () => {
    await source.project(tenant, browsing.project, signal); signal.throwIfAborted()
    const [revisions, reviews, head] = await Promise.all([source.revisions(browsing.project, signal), source.reviews(browsing.project, signal), source.head(browsing.project, signal)])
    return { revisions, reviews, head }
  }) })
  const data = !query.isFetching && !query.error ? query.data : undefined
  useLayoutEffect(() => () => { void client.cancelQueries({ queryKey: key }); client.removeQueries({ queryKey: key }) }, [client, key])
  useLayoutEffect(() => {
    if (httpStatus(query.error) === 404) { update({ revision: '', query: '', status: '', scrollTop: 0 }); setReviewId(null) }
  }, [query.error, update])
  useLayoutEffect(() => {
    if (data && browsing.revision && !data.revisions.some(row => row.id === browsing.revision)) update({ revision: '' })
  }, [data, browsing.revision, update])
  useLayoutEffect(() => {
    const scroller = root.current?.closest<HTMLElement>('.ff-center-workspace')
    if (!scroller || !data) return
    scroller.scrollTop = browsing.scrollTop
    const save = () => update({ scrollTop: scroller.scrollTop })
    scroller.addEventListener('scroll', save)
    return () => scroller.removeEventListener('scroll', save)
    // Restore once per authoritative response; scrolling must not restart restoration.
  }, [data, update])
  const reviews = filterReviews(data?.reviews ?? [], browsing.query, browsing.status)
  const selected = data?.revisions.find(row => row.id === browsing.revision)
  return <div ref={root}>
    {notice ? <p role="alert">{notice}</p> : null}
    <Button disabled={query.isFetching} onClick={() => { setReviewId(null); void query.refetch() }}>{query.error ? 'Retry history read' : 'Refresh history and reviews'}</Button>
    {query.isFetching ? <p role="status">Loading revision history and reviews…</p> : query.error ? <p role="alert">{errorMessage(query.error)}</p> : null}
    {data ? <>
      <p>Showing up to {READ_LIMIT} revisions and {READ_LIMIT} reviews returned by the server. Filters search only these returned reviews; this is not complete project history.</p>
      <div className="ff-timeline-review-grid"><Panel title="Revision history">
        {!data.revisions.length ? <p role="status">No revisions were returned for this project.</p> : <ul>{data.revisions.map(row => <li key={row.id}>
          <Button aria-pressed={row.id === browsing.revision} onClick={() => update({ revision: row.id })}>Revision {row.revisionNumber} · {row.message || row.id}</Button>
          {data.head?.id === row.id ? <strong> Current head</strong> : null}
        </li>)}</ul>}
        {selected ? <RevisionSummary revision={selected} /> : <p>Select a revision to read its summary.</p>}
      </Panel><Panel title="Reviews">
        <label>Search returned reviews<input type="search" value={browsing.query} onChange={event => update({ query: event.target.value, scrollTop: 0 })} /></label>
        <label>Review status<select aria-label="Review status" value={browsing.status} onChange={event => update({ status: event.target.value, scrollTop: 0 })}><option value="">All statuses</option>{[...new Set([...data.reviews.map(row => row.status), ...(browsing.status ? [browsing.status] : [])])].map(status => <option key={status} value={status}>{statusLabel(status)}</option>)}</select></label>
        <Button onClick={() => update({ query: '', status: '', scrollTop: 0 })}>Clear review filters</Button>
        <p role="status">{reviews.length} of {data.reviews.length} returned reviews</p>
        {!data.reviews.length ? <p>No reviews were returned for this project.</p> : !reviews.length ? <p>No returned reviews match these filters.</p> : <ul>{reviews.map(row => <li key={row.reviewId}>
          <Button aria-haspopup="dialog" onClick={() => setReviewId(row.reviewId)}>{row.title || row.reviewId}</Button><p>{statusLabel(row.status)} · Revision {data.revisions.find(revision => revision.id === row.revisionId)?.revisionNumber ?? row.revisionId}</p>
        </li>)}</ul>}
      </Panel></div>
    </> : null}
    {reviewId && data ? <InteractionDialog title="Review details" onClose={() => setReviewId(null)} className="ff-timeline-review ff-timeline-review-dialog">
      <Detail key={reviewId} {...{ source, read, reviewId }} project={browsing.project} prefix={key} revisions={data.revisions} missing={() => {
        setNotice('This resource is no longer available in the current scope.'); setReviewId(null)
        client.setQueryData<typeof query.data>([...key, 'history'], previous => previous ? { ...previous, reviews: previous.reviews.filter(row => row.reviewId !== reviewId) } : previous)
      }} />
    </InteractionDialog> : null}
  </div>
}
function RevisionSummary({ revision: row }: { revision: Revision }) {
  const s = row.changeSummary
  return <section aria-label="Revision summary"><h3>Revision {row.revisionNumber}</h3>
    <PropertyRow label="Revision ID">{row.id}</PropertyRow><PropertyRow label="Message">{row.message || 'No message'}</PropertyRow>
    <PropertyRow label="Source">{row.source}</PropertyRow><PropertyRow label="Author">{row.authorUserId ?? 'Unavailable'}</PropertyRow><PropertyRow label="Created">{row.createdAt ?? 'Unavailable'}</PropertyRow>
    <PropertyRow label="Labels">{row.labels.join(', ') || 'None'}</PropertyRow><PropertyRow label="Parent revision">{row.parentRevisionId ?? 'None'}</PropertyRow>
    {row.isMerge ? <p>Merge revision · Base: {row.mergeBaseRevisionId ?? 'Unavailable'} · Parents: {row.mergeParentRevisionIds ?? 'Unavailable'}</p> : null}
    <p>{row.patchOpCount} patch operations</p>{s.supported ? <ul><li>Tracks: {s.tracksAdded} added, {s.tracksRemoved} removed, {s.tracksModified} modified</li><li>Clips: {s.clipsAdded} added, {s.clipsRemoved} removed, {s.clipsModified} modified</li><li>Assets: {s.assetsAdded} added, {s.assetsRemoved} removed</li></ul> : <p>Change summary unavailable for this revision.</p>}
  </section>
}
function Detail({ source, project, reviewId, prefix, read, revisions, missing }: { source: TimelineReviewSource; project: string; reviewId: string; prefix: unknown[]; read: Reader; revisions: Revision[]; missing: () => void }) {
  const query = useQuery({ ...options, queryKey: [...prefix, 'review', reviewId], queryFn: ({ signal }) => read(signal, () => source.detail(project, reviewId, signal)) })
  const notified = useRef(false)
  useLayoutEffect(() => { if (httpStatus(query.error) === 404 && !notified.current) { notified.current = true; missing() } }, [query.error, missing])
  return <><Button disabled={query.isFetching} onClick={() => void query.refetch()}>{query.error ? 'Retry review detail' : 'Refresh review detail'}</Button>
    {query.isFetching ? <p role="status">Loading review detail…</p> : query.error ? <p role="alert">{errorMessage(query.error)}</p> : query.data ? <ReviewContent detail={query.data} revisions={revisions} /> : null}</>
}
function ReviewContent({ detail: d, revisions }: { detail: ReviewDetail; revisions: Revision[] }) {
  const revision = revisions.find(row => row.id === d.review.revisionId)
  return <><h3>{d.review.title || d.review.reviewId}</h3><p>{d.review.description || 'No description'}</p>
    <PropertyRow label="Review ID">{d.review.reviewId}</PropertyRow><PropertyRow label="Revision">{revision ? `Revision ${revision.revisionNumber} · ` : ''}{d.review.revisionId}</PropertyRow>
    {revision?.message ? <p>{revision.message}</p> : null}<PropertyRow label="Status">{statusLabel(d.review.status)}</PropertyRow><PropertyRow label="Author">{d.review.authorUserId ?? 'Unavailable'}</PropertyRow><PropertyRow label="Created">{d.review.createdAt ?? 'Unavailable'}</PropertyRow><PropertyRow label="Updated">{d.review.updatedAt ?? 'Unavailable'}</PropertyRow>
    <h3>Merge guard</h3><p>{d.mergeGuard.canMerge ? 'Guard allows merge' : 'Guard blocks merge'}</p><p>{d.mergeGuard.reason ?? 'No reason returned.'}</p><p>Advisory backend state only. This browser does not perform merges or open the editor.</p>
    <h3>Comments</h3>{!d.comments.length ? <p>No comments.</p> : <ul>{d.comments.map(row => <li key={row.commentId}><p>{row.content}</p><p>Author: {row.authorUserId ?? 'Unavailable'} · {row.createdAt ?? 'Date unavailable'}</p><p>Revision: {row.revisionId ?? 'Unspecified'} · Thread: {row.threadId ?? 'Unthreaded'} · Entity: {row.entityRef ?? 'Unspecified'}</p></li>)}</ul>}
    <h3>Threads</h3>{!d.threads.length ? <p>No threads.</p> : <ul>{d.threads.map(row => <li key={row.threadId}>{row.threadId} · {statusLabel(row.status)}<p>Entity: {row.entityRef ?? 'Unspecified'} · Difference: {row.diffId ?? 'Unspecified'} · {row.createdAt ?? 'Date unavailable'}</p></li>)}</ul>}
    <h3>Decisions</h3>{!d.decisions.length ? <p>No decisions.</p> : <ul>{d.decisions.map(row => <li key={row.decisionId}>{statusLabel(row.decision)} · {row.reviewerUserId ?? 'Reviewer unavailable'} · {row.createdAt ?? 'Date unavailable'}</li>)}</ul>}
  </>
}
