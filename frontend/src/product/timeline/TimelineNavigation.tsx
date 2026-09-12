import { createContext, useContext, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { Badge, Button, Input, PropertyRow } from '../../components/design-system'
import { InteractionDialog } from '../../interaction/InteractionDialog'
import { SelectionActionBar } from '../../interaction/InteractionShell'
import { useInteractionStore, useSelection, useSurfaceAdapter } from '../../interaction/SelectionContext'
import { useTranslation } from '../../localization'
import { accessKey, clipsAtTime, compareTime, navigationAccess, parseNavigationSnapshot, scopeKey, selectionId, targetKey, timeDifference, type NavigationClip, type NavigationSnapshot, type NavigationTarget, type TimelineNavigationSource } from './navigation'
import './timeline-navigation.css'

const SourceContext = createContext<TimelineNavigationSource | undefined>(undefined)
/** Explicit isolated verification host. Ordinary routes have no source fallback. */
export function TimelineNavigationProvider({ source, children }: { source: TimelineNavigationSource; children: ReactNode }) {
  return <SourceContext.Provider value={source}>{children}</SourceContext.Provider>
}
// Explicit collection access keeps local bookkeeping distinct from transport calls.
const objectIdentityNumbers = new WeakMap<object, number>()
let identitySequence = 0
function identity(value: object): number {
  if (!objectIdentityNumbers.has(value)) objectIdentityNumbers.set(value, ++identitySequence)
  return objectIdentityNumbers['get'](value)!
}
interface Props {
  target: NavigationTarget | null
  workspaceId: string
  projectId: string
  tenantId: string | null
  loading?: boolean
  source?: TimelineNavigationSource
}
export function TimelineNavigation(props: Props) {
  const provided = useContext(SourceContext), store = useInteractionStore()
  const source = props.source ?? provided
  const key = JSON.stringify([identity(store), props.workspaceId, props.projectId, props.tenantId, targetKey(props.target), source && [identity(source.adapter), scopeKey(source.scope), accessKey(source)]])
  return <NavigationSession key={key} {...props} source={source} />
}
function NavigationSession({ target, workspaceId, projectId, tenantId, loading: headLoading, source }: Props) {
  const { t } = useTranslation()
  const text = (key: string) => t(`timeline.nav.${key}`)
  const store = useInteractionStore(), selection = useSelection()
  const permitted = Boolean(source && target && navigationAccess(source) && source.scope.projectId === projectId && target.projectId === projectId && source.scope.workspaceId === workspaceId && source.scope.tenantId === tenantId)
  type Status = 'loading' | 'ready' | 'unavailable' | 'error' | 'stale' | 'restricted'
  const [status, setStatus] = useState<Status>(permitted ? 'loading' : source && target ? 'restricted' : 'unavailable')
  const [snapshot, setSnapshot] = useState<NavigationSnapshot | null>(null)
  const [query, setQuery] = useState(''), [position, setPosition] = useState<string | null>(null), [input, setInput] = useState('')
  const [timeError, setTimeError] = useState(false), [locationResult, setLocationResult] = useState<number | null>(null)
  const [detailOpen, setDetailOpen] = useState<{ lifetime: object; id: string } | null>(null)
  const committedDetail = useRef<object | null>(null)
  const live = useRef(false), generation = useRef(0), controller = useRef<AbortController | null>(null)
  const lifetime = useRef(selection.lifetime)
  const region = useRef<HTMLElement>(null), list = useRef<HTMLDivElement>(null)
  const objectButtons = useRef(new Map<string, HTMLButtonElement>())
  const savedScroll = useRef(0), restoreSelection = useRef<readonly string[]>([])
  const currentSnapshot = useRef(snapshot)
  const owns = () => live.current && lifetime.current === store.getSnapshot().lifetime
  const allObjects = snapshot?.tracks.flatMap(track => [
    { id: selectionId('track', track.id), kind: 'LANE' as const, title: track.name || track.id, synthetic: true },
    ...track.clips.map(clip => ({ id: selectionId('clip', clip.id), kind: 'CLIP' as const, title: clip.name || clip.id, synthetic: true })),
  ]) ?? []
  useSurfaceAdapter({ objects: () => allObjects, supports: ['reveal'], handle: action => {
    if (!owns() || action.type !== 'reveal') return false
    const button = objectButtons.current['get'](action.targetId)
    if (!button) return false
    button.scrollIntoView?.({ block: 'nearest' }); button.focus(); return true
  } })
  useLayoutEffect(() => {
    live.current = true; lifetime.current = store.getSnapshot().lifetime
    return () => { live.current = false; ++generation.current; controller.current?.abort() }
  }, [store])
  useLayoutEffect(() => { currentSnapshot.current = snapshot }, [snapshot])
  const retired = lifetime.current !== selection.lifetime
  useLayoutEffect(() => {
    if (retired) { ++generation.current; controller.current?.abort(); setSnapshot(null); setDetailOpen(null) }
  }, [retired])
  useLayoutEffect(() => {
    if (!snapshot || !owns()) return
    if (list.current) list.current.scrollTop = savedScroll.current
    const ids = restoreSelection.current.filter(id => allObjects.some(object => object.id === id))
    restoreSelection.current = []
    if (ids.length) store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids })
  }, [snapshot])

  async function load() {
    if (!owns() || !permitted || !source || !target || headLoading) return
    const epoch = ++generation.current
    controller.current?.abort()
    const abort = new AbortController(); controller.current = abort
    savedScroll.current = list.current?.scrollTop ?? savedScroll.current
    restoreSelection.current = store.getSnapshot().selectedObjects.map(object => object.id)
    setSnapshot(null); setDetailOpen(null); setStatus('loading'); setLocationResult(null)
    const request = { scope: source.scope, target, requestId: `navigation-${epoch}` }
    try {
      const input = await source.adapter.read(request, abort.signal)
      if (!owns() || abort.signal.aborted || epoch !== generation.current) return
      const result = parseNavigationSnapshot(input, request)
      if (result.status === 'ok') {
        if (result.timeBasis === 'unknown' || (position !== null && result.bounds && (compareTime(position, result.bounds.start) < 0 || compareTime(position, result.bounds.end) > 0))) { setPosition(null); setInput('') }
        setSnapshot(result); setStatus('ready')
      }
      else { restoreSelection.current = []; setStatus(result.status) }
    } catch {
      if (owns() && !abort.signal.aborted && epoch === generation.current) { restoreSelection.current = []; setStatus('error') }
    }
  }
  useEffect(() => {
    if (headLoading) { ++generation.current; controller.current?.abort(); setSnapshot(null); setDetailOpen(null); return }
    void load()
  }, [headLoading])

  const selectedId = selection.primarySelectedObject?.id
  const selectedTrack = snapshot?.tracks.find(track => selectionId('track', track.id) === selectedId)
  const selectedClip = snapshot?.tracks.flatMap(track => track.clips).find(clip => selectionId('clip', clip.id) === selectedId)
  const selected = selectedClip ?? selectedTrack
  const ownsSelection = () => owns() && currentSnapshot.current === snapshot && store.getSnapshot().revision === selection.revision && store.getSnapshot().primarySelectedObject?.id === selectedId
  const currentDetail = detailOpen && detailOpen.lifetime === selection.lifetime && detailOpen.id === selectedId && selection.inspectorOpen && selected && !headLoading && !retired ? detailOpen : null
  if (detailOpen && !currentDetail) setDetailOpen(null)
  useLayoutEffect(() => { committedDetail.current = currentDetail; return () => { committedDetail.current = null } }, [currentDetail])
  useLayoutEffect(() => {
    if (!currentDetail) return
    return store.subscribe(() => {
      const current = store.getSnapshot()
      if (current.lifetime !== currentDetail.lifetime || current.primarySelectedObject?.id !== currentDetail.id || !current.inspectorOpen) {
        committedDetail.current = null
        setDetailOpen(null)
      }
    })
  }, [store, currentDetail])
  const detail = currentDetail && selected
  const hadDetail = useRef(false)
  useEffect(() => {
    if (hadDetail.current && !detail && !selected) region.current?.focus()
    hadDetail.current = Boolean(detail)
  }, [detail, selected])
  const select = (id: string) => {
    if (!owns() || currentSnapshot.current !== snapshot) return
    store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [id] })
  }
  function inspect() {
    if (!ownsSelection() || !selected) return
    store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'inspect', open: true }); setDetailOpen({ lifetime: selection.lifetime, id: selectedId! })
  }
  function locate(value: string) {
    if (!owns() || !snapshot || currentSnapshot.current !== snapshot || snapshot.timeBasis !== 'exact-seconds') return
    try {
      if (compareTime(value, '0') < 0 || (snapshot.bounds && (compareTime(value, snapshot.bounds.start) < 0 || compareTime(value, snapshot.bounds.end) > 0))) throw new Error('Outside bounds')
      setPosition(value); setInput(value); setTimeError(false)
      const matches = clipsAtTime(snapshot, value)
      setLocationResult(matches.length)
      const clip = matches[0]
      if (clip) { setQuery(''); select(selectionId('clip', clip.id)) }
    } catch { setTimeError(true) }
  }
  function locateClip(clip: NavigationClip | undefined) {
    if (!clip?.timelineRange || !ownsSelection()) return
    locate(clip.timelineRange.start)
    // Overlapping clips keep the explicitly requested selection.
    setQuery(''); select(selectionId('clip', clip.id))
  }
  useLayoutEffect(() => {
    if (locationResult !== null && selectedId && !query) objectButtons.current['get'](selectedId)?.scrollIntoView?.({ block: 'nearest' })
  }, [locationResult, selectedId, query])
  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey || event.nativeEvent.isComposing || !owns() || currentSnapshot.current !== snapshot) return
    const button = (event.target as HTMLElement).closest<HTMLButtonElement>('[data-navigation-object]')
    if (!button) return
    if (event.key === 'Escape') { event.preventDefault(); store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] }); return }
    if (event.key === 'i') { event.preventDefault(); select(button.dataset.navigationObject!); store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'inspect', open: true }); setDetailOpen({ lifetime: lifetime.current, id: button.dataset.navigationObject! }); return }
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return
    event.preventDefault()
    const items = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('[data-navigation-object]'))
    const index = items.indexOf(button)
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 : Math.max(0, Math.min(items.length - 1, index + (event.key === 'ArrowDown' ? 1 : -1)))
    items[next]?.focus(); if (items[next]) select(items[next].dataset.navigationObject!)
  }
  const filtered = snapshot?.tracks.map(track => ({ ...track, clips: track.clips.filter(clip => `${track.name ?? ''} ${track.id} ${track.type ?? ''} ${clip.name ?? ''} ${clip.id} ${clip.type ?? ''}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())) }))
    .filter(track => track.clips.length || `${track.name ?? ''} ${track.id} ${track.type ?? ''}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())) ?? []
  const objectButton = (kind: 'track' | 'clip', object: { id: string; name?: string; type?: string }) => {
    const id = selectionId(kind, object.id)
    return <button type="button" data-navigation-object={id} ref={element => { if (element) objectButtons.current.set(id, element); else objectButtons.current['delete'](id) }} aria-pressed={selectedId === id} title={object.name || object.id} onClick={() => select(id)}><span>{object.name || object.id}</span><small>{object.type || text('unknownType')}</small></button>
  }
  const displayStatus = retired ? 'restricted' : headLoading ? 'loading' : status
  return <section ref={region} tabIndex={-1} className="ff-timeline-navigation" aria-label={text('region')}>
    <p>{text('limitation')}</p>
    {target && !retired ? <dl className="ff-navigation-identity"><dt>{text('project')}</dt><dd>{target.projectId}</dd><dt>{text('timeline')}</dt><dd>{target.timelineId}</dd><dt>{text('revision')}</dt><dd>{target.revisionId}</dd></dl> : null}
    {source ? <Badge tone="warning">{text('verification')}</Badge> : null}
    <p role="status">{text(displayStatus)}</p>
    {!source ? <p>{text('gap')}</p> : null}
    {permitted && !retired ? <Button disabled={Boolean(headLoading) || status === 'loading'} onClick={() => void load()}>{text(status === 'ready' ? 'refresh' : 'retry')}</Button> : null}
    {snapshot && displayStatus === 'ready' ? <>
      <p>{text(snapshot.completeness === 'bounded' ? 'bounded' : 'complete')}</p>
      <label>{text('filter')}<Input value={query} onChange={event => { if (owns()) setQuery(event.target.value) }} /></label>
      <form className="ff-navigation-locate" onSubmit={event => { event.preventDefault(); locate(input) }}>
        <label>{text('time')}<Input value={input} disabled={snapshot.timeBasis !== 'exact-seconds'} onChange={event => { if (owns()) setInput(event.target.value) }} aria-invalid={timeError} /></label>
        <Button type="submit" disabled={snapshot.timeBasis !== 'exact-seconds'}>{text('locate')}</Button>
        <Button type="button" disabled={snapshot.timeBasis !== 'exact-seconds' || !selectedClip?.timelineRange} onClick={() => locateClip(selectedClip)}>{text('locateClip')}</Button>
      </form>
      <p>{text(snapshot.timeBasis === 'exact-seconds' ? 'seconds' : 'unknownTime')}</p>
      {snapshot.bounds ? <p>{text('bounds')}: {snapshot.bounds.start} → {snapshot.bounds.end}</p> : <p>{text('unknownBounds')}</p>}
      <output aria-label={text('position')}>{position ?? text('unset')}</output>
      {timeError ? <p role="alert">{text('timeError')}</p> : null}
      {locationResult !== null ? <p role="status">{text('locatedCount')}: {locationResult}</p> : null}
      <SelectionActionBar />
      <div className="ff-navigation-actions"><Button disabled={!selected} onClick={inspect}>{text('inspect')}</Button><Button disabled={!selected} onClick={() => { if (ownsSelection()) store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] }) }}>{text('clear')}</Button></div>
      <p className="ff-canvas-guidance">{text('keys')}</p>
      <div ref={list} className="ff-navigation-list" aria-label={text('list')} onKeyDown={onKeyDown}>
        {!snapshot.tracks.length ? <p>{text(snapshot.completeness === 'complete' ? 'empty' : 'emptyLoaded')}</p> : !filtered.length ? <p>{text('noMatch')}</p> : filtered.map(track => <section key={track.id}>{objectButton('track', track)}
          {!track.clips.length ? <p>{text(snapshot.tracks.find(item => item.id === track.id)?.clips.length ? 'noMatch' : snapshot.completeness === 'complete' ? 'emptyTrack' : 'emptyLoadedTrack')}</p> : <ul>{track.clips.map(clip => <li key={clip.id}>{objectButton('clip', clip)}{clip.timelineRange ? <small>{clip.timelineRange.start} → {clip.timelineRange.end}</small> : null}</li>)}</ul>}
        </section>)}
      </div>
      {detail ? <InteractionDialog title={text('details')} closeLabel={text('close')} className="ff-navigation-detail" onClose={() => { if (ownsSelection() && committedDetail.current === detailOpen) setDetailOpen(null) }}>
        <PropertyRow label={text('name')}>{selected.name ?? text('notSupplied')}</PropertyRow>
        <PropertyRow label={text('id')}>{selected.id}</PropertyRow>
        <PropertyRow label={text('type')}>{selected.type ?? text('notSupplied')}</PropertyRow>
        <PropertyRow label={text('revision')}>{target?.revisionId}</PropertyRow>
        {selectedClip ? <><PropertyRow label={text('track')}>{selectedClip.trackId}</PropertyRow>
          {selectedClip.version ? <PropertyRow label={text('version')}>{selectedClip.version}</PropertyRow> : null}
          {selectedClip.timelineRange ? <><PropertyRow label={text('range')}>{selectedClip.timelineRange.start} → {selectedClip.timelineRange.end}</PropertyRow>{snapshot.timeBasis === 'exact-seconds' ? <PropertyRow label={text('duration')}>{timeDifference(selectedClip.timelineRange.end, selectedClip.timelineRange.start)}</PropertyRow> : null}</> : null}
          {selectedClip.sourceRange ? <PropertyRow label={text('sourceRange')}>{selectedClip.sourceRange.start} → {selectedClip.sourceRange.end}</PropertyRow> : null}
          {selectedClip.source ? Object.entries(selectedClip.source).map(([key, value]) => <PropertyRow key={key} label={text(key)}>{value}</PropertyRow>) : null}
        </> : null}
        <p>{text('metadataOnly')}</p>
      </InteractionDialog> : null}
    </> : null}
  </section>
}
