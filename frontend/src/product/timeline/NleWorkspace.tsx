import { useEffect, useReducer, useRef, useState, type ChangeEvent, type KeyboardEvent } from 'react'
import { operationGateway as defaultOperationGateway } from '../../api/app/operation.gateway'
import { timelineQueryGateway as defaultTimelineQueryGateway } from '../../api/app/timeline-query.gateway'
import { assetGateway as defaultAssetGateway } from '../../api/app/asset.gateway'
import { capabilityGateway as defaultCapabilityGateway } from '../../api/app/capability.gateway'
import { Badge, Button, EmptyState, Input, Panel, PropertyRow, Skeleton, Status } from '../../components/design-system'
import { useProjectContext } from '../../foundation/projectContext'
import { PageHeading, ProjectFrame } from '../../surfaces/FoundationPages'
import { initialTimelineEditorState, timelineEditorReducer } from './editor-state'
import type {
  AddMediaClipCommandDraft,
  AssetGateway,
  CanonicalHeadReference,
  CapabilityGateway,
  GatewayFailure,
  GatewayResult,
  OperationGateway,
  RevisionDetail,
  RevisionListEntry,
  TimelineQueryGateway,
} from './gateways'
import { SemanticDiff } from './SemanticDiff'
import {
  ADD_MEDIA_CLIP_DEFINITION,
  ADD_MEDIA_CLIP_PRESENTATION_VERSION,
  ADD_MEDIA_CLIP_PREVIEW_OPERATION,
  applyCommandId,
  artifactId,
  clipId,
  contentHash,
  exactMediaTime,
  mediaAssetId,
  mediaStreamId,
  projectId,
  trackId,
} from './types'

interface ManualFields {
  trackId: string
  clipId: string
  mediaAssetId: string
  mediaStreamId: string
  artifactId: string
  contentDigest: string
  sourceStart: string
  sourceEnd: string
  timelineStart: string
  timelineEnd: string
  rateNumerator: string
  rateDenominator: string
  direction: '' | 'FORWARD' | 'REVERSE'
}

const emptyFields: ManualFields = {
  trackId: '', clipId: '', mediaAssetId: '', mediaStreamId: '', artifactId: '', contentDigest: '',
  sourceStart: '', sourceEnd: '', timelineStart: '', timelineEnd: '', rateNumerator: '', rateDenominator: '', direction: '',
}

const fieldLabels: readonly [keyof Omit<ManualFields, 'direction'>, string][] = [
  ['trackId', 'Track ID'], ['clipId', 'Clip ID'], ['mediaAssetId', 'Media Asset ID'],
  ['mediaStreamId', 'Media Stream ID'], ['artifactId', 'Artifact ID'], ['contentDigest', 'Source content digest'],
  ['sourceStart', 'Source start (exact rational)'], ['sourceEnd', 'Source end (exact rational)'],
  ['timelineStart', 'Timeline start (exact rational)'], ['timelineEnd', 'Timeline end (exact rational)'],
  ['rateNumerator', 'Rate numerator'], ['rateDenominator', 'Rate denominator'],
]

function buildDraft(fields: ManualFields, head: CanonicalHeadReference): AddMediaClipCommandDraft {
  const numerator = Number(fields.rateNumerator)
  const denominator = Number(fields.rateDenominator)
  if (!Number.isSafeInteger(numerator) || numerator <= 0 || !Number.isSafeInteger(denominator) || denominator <= 0) {
    throw new Error('Rate numerator and denominator must be positive safe integers.')
  }
  if (!fields.direction) throw new Error('Playback direction is required.')
  return Object.freeze({
    baseRevisionId: head.revisionId,
    baseContentHash: head.contentHash,
    trackId: trackId(fields.trackId),
    clipId: clipId(fields.clipId),
    mediaAssetId: mediaAssetId(fields.mediaAssetId),
    mediaStreamId: mediaStreamId(fields.mediaStreamId),
    artifactId: artifactId(fields.artifactId),
    contentDigest: contentHash(fields.contentDigest),
    sourceStart: exactMediaTime(fields.sourceStart),
    sourceEnd: exactMediaTime(fields.sourceEnd),
    timelineStart: exactMediaTime(fields.timelineStart),
    timelineEnd: exactMediaTime(fields.timelineEnd),
    rateNumerator: numerator,
    rateDenominator: denominator,
    direction: fields.direction,
  })
}

export async function loadExplicitRevisionSelection(
  queryGateway: TimelineQueryGateway,
  head: CanonicalHeadReference,
  revisions: readonly RevisionListEntry[],
  selectedId: string,
): Promise<GatewayResult<RevisionDetail>> {
  const selectedRevision = revisions.find(item => item.id === selectedId)
  if (!selectedRevision) {
    return {
      ok: false,
      code: 'VALIDATION',
      message: `Requested revision ${selectedId} is not present in the authoritative history projection.`,
      details: [],
      retryable: false,
    }
  }
  return queryGateway.getRevision(head.projectId, selectedRevision.id)
}

function readbackFailure(code: 'UNAVAILABLE' | 'VALIDATION', message: string): GatewayFailure {
  return { ok: false, code, message, details: [], retryable: code === 'UNAVAILABLE' }
}

function PreviewList({ title, items }: { title: string; items: readonly string[] }) {
  return <><h4>{title}</h4>{items.length ? <ul>{items.map((item, index) => <li key={`${index}-${item}`}>{item}</li>)}</ul> : <p>None.</p>}</>
}

function FrozenDraft({ draft }: { draft: AddMediaClipCommandDraft }) {
  const values: readonly [string, string | number][] = [
    ['Base revision', draft.baseRevisionId],
    ['Base content hash', draft.baseContentHash],
    ['Track ID', draft.trackId],
    ['Clip ID', draft.clipId],
    ['Media Asset ID', draft.mediaAssetId],
    ['Media Stream ID', draft.mediaStreamId],
    ['Artifact ID', draft.artifactId],
    ['Source content digest', draft.contentDigest],
    ['Source start', draft.sourceStart],
    ['Source end', draft.sourceEnd],
    ['Timeline start', draft.timelineStart],
    ['Timeline end', draft.timelineEnd],
    ['Rate numerator', draft.rateNumerator],
    ['Rate denominator', draft.rateDenominator],
    ['Direction', draft.direction],
  ]
  return <section aria-label="Exact frozen submitted draft"><h4>Exact frozen submitted draft</h4>{values.map(([label, value]) => <PropertyRow key={label} label={label}><code>{value}</code></PropertyRow>)}</section>
}

function EditorPhaseNotice({ phase, readbackRetrying, onRetryReadback }: {
  phase: ReturnType<typeof timelineEditorReducer>['phase']
  readbackRetrying: boolean
  onRetryReadback: () => void
}) {
  if (phase.kind === 'StaleBase') return <div className="ff-operation-notice ff-operation-notice--danger" role="alert"><strong>{phase.failure.code}</strong><p>Canonical state was not changed. Reload HEAD and explicitly preview again.</p></div>
  if (phase.kind === 'Conflict') return <div className="ff-operation-notice ff-operation-notice--danger" role="alert"><strong>{phase.failure.code}</strong><p>The confirmed preview is invalid. A new preview is required; apply is not retried.</p></div>
  if (phase.kind === 'Unauthorized') return <div className="ff-operation-notice ff-operation-notice--danger" role="alert"><strong>{phase.failure.code}</strong><p>{phase.failure.message} Canonical state was not changed.</p></div>
  if (phase.kind === 'AppliedRevision') return <>
    <div className="ff-operation-notice ff-operation-notice--success" role="status"><strong>{phase.accepted.status}</strong><p>{phase.accepted.status === 'APPLIED' ? `Server accepted revision ${phase.accepted.newRevisionId}.` : 'Server accepted a semantic no-op; HEAD remains unchanged.'}</p></div>
    {phase.readback === 'PENDING' ? <div className="ff-operation-notice" role="status"><strong>READBACK</strong><p>Refreshing authoritative HEAD and revision history…</p></div> : null}
    {typeof phase.readback === 'object' ? <div className="ff-operation-notice ff-operation-notice--danger" role="status"><strong>{phase.readback.code}</strong><p>{phase.readback.message}</p><Button disabled={readbackRetrying} onClick={onRetryReadback}>{readbackRetrying ? 'Retrying authoritative readback…' : 'Retry authoritative readback'}</Button></div> : null}
  </>
  if (phase.kind === 'Unavailable') return <div className="ff-operation-notice" role="status"><strong>UNAVAILABLE</strong><p>{phase.reason}</p></div>
  return <Status label={phase.kind} tone={phase.kind.startsWith('Pending') ? 'warning' : 'info'} />
}

function TimelinePresentation({ playhead, playing, selectedTrackId, ghost, onPlayhead, onPlaying, onSelectTrack }: {
  playhead: number
  playing: boolean
  selectedTrackId: string | null
  ghost: ManualFields | null
  onPlayhead: (value: number) => void
  onPlaying: (value: boolean) => void
  onSelectTrack: (value: string | null) => void
}) {
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.target !== event.currentTarget) return
    if (event.key === 'ArrowLeft') { event.preventDefault(); onPlayhead(Math.max(0, playhead - 1)) }
    if (event.key === 'ArrowRight') { event.preventDefault(); onPlayhead(playhead + 1) }
    if (event.key === ' ') { event.preventDefault(); onPlaying(!playing) }
    if (event.key === 'Escape') onSelectTrack(null)
  }
  return (
    <div className="ff-nle-stage">
      <div className="ff-preview-host"><Badge>Presentation only</Badge><strong>{playing ? 'Playing preview shell' : 'Preview paused'}</strong><p>No media is decoded without a safe projected access surface.</p></div>
      <div className="ff-playback-controls" aria-label="Timeline playback controls">
        <Button onClick={() => onPlayhead(Math.max(0, playhead - 1))}>Previous step</Button>
        <Button aria-pressed={playing} onClick={() => onPlaying(!playing)}>{playing ? 'Pause' : 'Play'}</Button>
        <Button onClick={() => onPlayhead(playhead + 1)}>Next step</Button>
        <output aria-live="polite">Playhead {playhead}</output>
      </div>
      <div className="ff-canonical-timeline" role="application" aria-label="Timeline presentation; use left and right arrows to move, space to play or pause, and escape to clear selection" tabIndex={0} onKeyDown={onKeyDown}>
        <div className="ff-timeline-ruler" aria-hidden="true"><span>0</span><span>5</span><span>10</span><span>15</span><i style={{ left: `${Math.min(100, playhead * 5)}%` }} /></div>
        {['V1', 'A1'].map(id => <button key={id} type="button" className="ff-canonical-track" aria-pressed={selectedTrackId === id} onClick={() => onSelectTrack(id)}><span>{id}</span><span>Server entities are shown only when projected.</span>{id === 'V1' && ghost?.timelineStart && ghost.timelineEnd ? <i className="ff-draft-ghost">Draft ghost {ghost.timelineStart} → {ghost.timelineEnd}</i> : null}</button>)}
      </div>
    </div>
  )
}

export function NlePage() {
  return <ProjectFrame surfaceId="nle"><NleWorkspace /></ProjectFrame>
}

export function NleWorkspace({
  queryGateway = defaultTimelineQueryGateway,
  commandGateway = defaultOperationGateway,
  sourceGateway = defaultAssetGateway,
  capabilityGateway = defaultCapabilityGateway,
}: {
  queryGateway?: TimelineQueryGateway
  commandGateway?: OperationGateway
  sourceGateway?: AssetGateway
  capabilityGateway?: CapabilityGateway
}) {
  const project = useProjectContext()
  const [state, dispatch] = useReducer(timelineEditorReducer, initialTimelineEditorState)
  const [fields, setFields] = useState<ManualFields>(emptyFields)
  const [loading, setLoading] = useState(true)
  const [queryMessage, setQueryMessage] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  const [readbackRetrying, setReadbackRetrying] = useState(false)
  const [applyOwnershipRelease, setApplyOwnershipRelease] = useState(0)
  const [stableCommandId, setStableCommandId] = useState<ReturnType<typeof applyCommandId> | null>(null)
  const [assetBoundary, setAssetBoundary] = useState('No safe media browser projection supplies a complete canonical source pin.')
  const [capabilityBoundary, setCapabilityBoundary] = useState('Operation capability is UNKNOWN until the focused gateway responds.')
  const mounted = useRef(true)
  const requestGeneration = useRef({ head: 0, selection: 0, preview: 0, apply: 0 })
  const applyInFlight = useRef(false)
  const deferredNavigationHeadLoad = useRef(false)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      requestGeneration.current.head += 1
      requestGeneration.current.selection += 1
      requestGeneration.current.preview += 1
      requestGeneration.current.apply += 1
    }
  }, [])

  const ownsRequest = (kind: 'head' | 'selection' | 'preview' | 'apply', generation: number) => (
    mounted.current && requestGeneration.current[kind] === generation
  )

  const loadHead = async (reason: 'NAVIGATION' | 'ORDINARY' = 'ORDINARY') => {
    const authoritativeTransitionOwned = applyInFlight.current
      || state.phase.kind === 'PendingApply'
      || (state.phase.kind === 'AppliedRevision' && state.phase.readback !== 'VERIFIED')
    if (authoritativeTransitionOwned) {
      if (reason === 'NAVIGATION') deferredNavigationHeadLoad.current = true
      return
    }
    const generation = ++requestGeneration.current.head
    requestGeneration.current.selection += 1
    requestGeneration.current.preview += 1
    requestGeneration.current.apply += 1
    setLoading(true)
    setQueryMessage(null)
    try {
      const requestedProject = projectId(project.projectId)
      const [head, history] = await Promise.all([queryGateway.getHead(requestedProject), queryGateway.listRevisions(requestedProject)])
      if (!ownsRequest('head', generation)) return
      if (!head.ok) { dispatch({ type: 'UNAVAILABLE', reason: `${head.code}: ${head.message}` }); return }
      if (!history.ok) { dispatch({ type: 'UNAVAILABLE', reason: `${history.code}: ${history.message}` }); return }
      dispatch({ type: 'HEAD_LOADED', head: head.value, revisions: history.value })
      setConfirmed(false)
      setStableCommandId(null)
    } catch (error) {
      if (!ownsRequest('head', generation)) return
      dispatch({ type: 'UNAVAILABLE', reason: error instanceof Error ? error.message : 'Project identity is invalid.' })
    } finally {
      if (ownsRequest('head', generation)) setLoading(false)
    }
  }

  useEffect(() => { void loadHead('NAVIGATION') }, [project.projectId, queryGateway])

  useEffect(() => {
    const authoritativeTransitionOwned = applyInFlight.current
      || state.phase.kind === 'PendingApply'
      || (state.phase.kind === 'AppliedRevision' && state.phase.readback !== 'VERIFIED')
    if (!deferredNavigationHeadLoad.current || authoritativeTransitionOwned) return
    deferredNavigationHeadLoad.current = false
    void loadHead()
  }, [state.phase, project.projectId, queryGateway, applyOwnershipRelease])

  useEffect(() => {
    let active = true
    const loadBoundaries = async () => {
      try {
        const requestedProject = projectId(project.projectId)
        const [assets, capability] = await Promise.all([
          sourceGateway.listSourcePins(requestedProject),
          capabilityGateway.getOperationCapability(requestedProject),
        ])
        if (!active) return
        setAssetBoundary(assets.ok ? `${assets.value.length} canonical source pins projected.` : `${assets.code}: ${assets.message}`)
        setCapabilityBoundary(capability.ok ? `${capability.value.state}: ${capability.value.reason}` : `${capability.code}: ${capability.message}`)
      } catch (error) {
        if (active) setCapabilityBoundary(error instanceof Error ? error.message : 'Capability boundary unavailable.')
      }
    }
    void loadBoundaries()
    return () => { active = false }
  }, [project.projectId, sourceGateway, capabilityGateway])

  const selectRevision = async (selectedId: string) => {
    const selectionLocked = applyInFlight.current
      || state.phase.kind === 'PendingApply'
      || (state.phase.kind === 'AppliedRevision' && state.phase.readback !== 'VERIFIED')
    if (!state.server.head || selectionLocked) return
    const generation = ++requestGeneration.current.selection
    const request = { purpose: 'USER_SELECTION' as const, generation }
    const requestedHead = state.server.head
    const requestedRevisions = state.server.revisions
    dispatch({ type: 'REVISION_SELECTION_STARTED', request })
    setQueryMessage('Loading explicit revision…')
    const detail = await loadExplicitRevisionSelection(queryGateway, requestedHead, requestedRevisions, selectedId)
    if (!ownsRequest('selection', generation)) return
    if (!detail.ok) { setQueryMessage(`${detail.code}: ${detail.message}`); return }
    dispatch({ type: 'REVISION_LOADED', request, detail: detail.value })
    if (detail.value.revision.id !== requestedHead.revisionId) {
      const comparison = await queryGateway.compare(requestedHead.projectId, detail.value.revision.id, requestedHead.revisionId)
      if (!ownsRequest('selection', generation)) return
      if (comparison.ok) dispatch({ type: 'COMPARISON_LOADED', request, comparison: comparison.value })
      else setQueryMessage(`${comparison.code}: ${comparison.message}`)
    }
    if (ownsRequest('selection', generation)) setQueryMessage(null)
  }

  const changeField = (key: keyof ManualFields) => (event: ChangeEvent<HTMLInputElement | HTMLSelectElement>) => {
    setFields(current => ({ ...current, [key]: event.target.value }))
    setFormError(null)
    setConfirmed(false)
    setStableCommandId(null)
    requestGeneration.current.preview += 1
    dispatch({ type: 'DRAFT_CHANGED', command: null })
  }

  const preview = async () => {
    if (!state.server.head || !project.tenantId) return
    const generation = ++requestGeneration.current.preview
    try {
      const draft = buildDraft(fields, state.server.head)
      dispatch({ type: 'DRAFT_CHANGED', command: draft })
      dispatch({ type: 'PREVIEW_STARTED' })
      setFormError(null)
      const result = await commandGateway.previewAddMediaClip(project.tenantId, state.server.head.projectId, draft)
      if (!ownsRequest('preview', generation)) return
      if (!result.ok) { dispatch({ type: 'OPERATION_FAILED', failure: result }); return }
      dispatch({ type: 'PREVIEW_ACCEPTED', preview: result.value })
      setStableCommandId(applyCommandId(`apply-${crypto.randomUUID()}`))
      setConfirmed(false)
    } catch (error) {
      if (ownsRequest('preview', generation)) setFormError(error instanceof Error ? error.message : 'The manual source pin is invalid.')
    }
  }

  const verifyAuthoritativeApplyReadback = async (
    generation: number,
    requestedProject: CanonicalHeadReference['projectId'],
  ) => {
    try {
      const [headReadback, historyReadback] = await Promise.all([
        queryGateway.getHead(requestedProject),
        queryGateway.listRevisions(requestedProject),
      ])
      if (!ownsRequest('apply', generation)) return
      if (!headReadback.ok) {
        dispatch({ type: 'APPLY_READBACK_FAILED', failure: readbackFailure(
          'UNAVAILABLE', `Accepted result preserved, but canonical HEAD readback failed (${headReadback.code}: ${headReadback.message}).`,
        ) })
        return
      }
      if (!historyReadback.ok) {
        dispatch({ type: 'APPLY_READBACK_FAILED', failure: readbackFailure(
          'UNAVAILABLE', `Accepted result preserved, but revision-history refresh failed (${historyReadback.code}: ${historyReadback.message}).`,
        ) })
        return
      }
      dispatch({ type: 'APPLY_READBACK_VERIFIED', head: headReadback.value, revisions: historyReadback.value })
    } catch (error) {
      if (!ownsRequest('apply', generation)) return
      dispatch({ type: 'APPLY_READBACK_FAILED', failure: readbackFailure(
        'UNAVAILABLE', `Accepted result preserved, but authoritative readback was unavailable: ${error instanceof Error ? error.message : 'unknown readback failure'}.`,
      ) })
    }
  }

  const releaseApplyOwnership = () => {
    applyInFlight.current = false
    if (mounted.current) setApplyOwnershipRelease(current => current + 1)
  }

  const apply = async () => {
    if (applyInFlight.current || !project.tenantId || !state.server.head || !state.draft.command || !state.draft.preview || !stableCommandId || !confirmed) return
    applyInFlight.current = true
    const generation = ++requestGeneration.current.apply
    const selectionGeneration = ++requestGeneration.current.selection
    const priorHead = state.server.head
    const submittedDraft = state.draft.command
    const confirmedPreview = state.draft.preview
    const submittedCommandId = stableCommandId
    setQueryMessage(null)
    dispatch({ type: 'APPLY_STARTED', commandId: submittedCommandId, selectionGeneration })
    let result
    try {
      result = await commandGateway.applyAddMediaClip(
        project.tenantId, priorHead.projectId, submittedDraft, confirmedPreview, submittedCommandId,
      )
    } catch (error) {
      if (ownsRequest('apply', generation)) {
        dispatch({ type: 'OPERATION_FAILED', failure: readbackFailure(
          'UNAVAILABLE', error instanceof Error ? error.message : 'Operation apply was unavailable.',
        ) })
        setConfirmed(false)
        releaseApplyOwnership()
      }
      return
    }
    if (!ownsRequest('apply', generation)) return
    if (!result.ok) {
      dispatch({ type: 'OPERATION_FAILED', failure: result })
      setConfirmed(false)
      releaseApplyOwnership()
      return
    }
    dispatch({ type: 'APPLY_ACCEPTED', commandId: submittedCommandId, accepted: result.value })
    setConfirmed(false)
    try {
      await verifyAuthoritativeApplyReadback(generation, priorHead.projectId)
    } finally {
      if (ownsRequest('apply', generation)) releaseApplyOwnership()
    }
  }

  const retryAuthoritativeReadback = async () => {
    if (
      applyInFlight.current
      || state.phase.kind !== 'AppliedRevision'
      || typeof state.phase.readback !== 'object'
      || !state.server.head
    ) return
    applyInFlight.current = true
    setReadbackRetrying(true)
    const generation = requestGeneration.current.apply
    try {
      await verifyAuthoritativeApplyReadback(generation, state.server.head.projectId)
    } finally {
      if (ownsRequest('apply', generation)) releaseApplyOwnership()
      if (mounted.current) setReadbackRetrying(false)
    }
  }

  const previewReady = state.draft.preview && !state.draft.previewRequired && state.draft.preview.failures.length === 0
  const revisionSelectionLocked = state.phase.kind === 'PendingApply'
    || (state.phase.kind === 'AppliedRevision' && state.phase.readback !== 'VERIFIED')
  return (
    <>
      <PageHeading eyebrow="Creative · NLE" title="Timeline editor" description="One canonical Timeline core with explicit server HEAD, read-only revision queries, and preview-before-apply operations." />
      {loading ? <Skeleton label="Loading canonical Timeline HEAD" /> : null}
      <EditorPhaseNotice phase={state.phase} readbackRetrying={readbackRetrying} onRetryReadback={() => void retryAuthoritativeReadback()} />
      <div className="ff-nle-layout">
        <Panel title="Canonical revision authority" actions={<Button onClick={() => void loadHead()} disabled={loading || revisionSelectionLocked}>Reload HEAD</Button>}>
          {state.server.head ? <div className="ff-head-identity"><Status label="HEAD · main" tone="success" /><PropertyRow label="Revision"><code>{state.server.head.revisionId}</code></PropertyRow><PropertyRow label="Content hash"><code>{state.server.head.contentHash}</code></PropertyRow></div> : <EmptyState title="HEAD unavailable" description="The editor does not infer HEAD from history or local state." />}
          <h3>Revision history</h3>
          {state.server.revisions.length ? <ol className="ff-revision-list">{state.server.revisions.map(revision => <li key={revision.id}><button type="button" aria-pressed={state.server.selected?.revision.id === revision.id} disabled={revisionSelectionLocked} onClick={() => void selectRevision(revision.id)}><span>r{revision.revisionNumber}</span><code>{revision.id}</code><small>{revision.message ?? 'No message'}</small></button></li>)}</ol> : <p>No accepted history projection.</p>}
          {queryMessage ? <p role="status">{queryMessage}</p> : null}
          {state.server.selected ? <div className="ff-revision-detail"><h3>Selected revision detail</h3><PropertyRow label="Revision"><code>{state.server.selected.revision.id}</code></PropertyRow><PropertyRow label="Source"><span>{state.server.selected.revision.source}</span></PropertyRow><PropertyRow label="Server change count"><span>{state.server.selected.changeCount}</span></PropertyRow></div> : <p>Select a revision explicitly to load detail.</p>}
        </Panel>
        <Panel title="Timeline workspace" className="ff-nle-workspace-panel">
          <TimelinePresentation
            playhead={state.presentation.playheadStep} playing={state.presentation.playing}
            selectedTrackId={state.presentation.selectedTrackId} ghost={fields.timelineStart && fields.timelineEnd ? fields : null}
            onPlayhead={step => dispatch({ type: 'PLAYHEAD_CHANGED', step })}
            onPlaying={playing => dispatch({ type: 'PLAYBACK_CHANGED', playing })}
            onSelectTrack={value => dispatch({ type: 'TRACK_SELECTED', trackId: value })}
          />
        </Panel>
        <Panel title="Add Media Clip · advanced / provisional" className="ff-operation-panel">
          <div className="ff-state ff-state--unavailable" role="note"><span className="ff-state__code">ASSET GATEWAY · UNAVAILABLE</span><p>{assetBoundary} Enter only canonical logical IDs below. Physical locations and provider coordinates are not accepted.</p><p>{capabilityBoundary}</p></div>
          {!project.tenantId ? <div className="ff-operation-notice ff-operation-notice--danger" role="alert"><strong>TENANT CONTEXT UNAVAILABLE</strong><p>The authenticated Workspace projection did not supply a tenant. Preview and apply remain disabled.</p></div> : null}
          <form onSubmit={event => { event.preventDefault(); void preview() }}>
            <fieldset disabled={!state.server.head || !project.tenantId || state.phase.kind === 'PendingPreview' || state.phase.kind === 'PendingApply'}>
              <legend>Canonical source and placement pins</legend>
              <div className="ff-operation-fields">{fieldLabels.map(([key, label]) => <label key={key}><span>{label}</span><Input value={fields[key]} onChange={changeField(key)} autoComplete="off" /></label>)}<label><span>Direction</span><select value={fields.direction} onChange={changeField('direction')}><option value="">Select direction</option><option value="FORWARD">Forward</option><option value="REVERSE">Reverse</option></select></label></div>
              {formError ? <p className="ff-form-error" role="alert">{formError}</p> : null}
              <div className="ff-operation-actions"><Button type="submit" variant="primary">Preview operation</Button><Button type="button" onClick={() => { setFields(emptyFields); setStableCommandId(null); setConfirmed(false); dispatch({ type: 'ROLLBACK_DRAFT' }) }}>Discard draft</Button></div>
            </fieldset>
          </form>
          {state.draft.preview && state.draft.command ? <section className="ff-operation-preview" aria-label="Server operation preview">
            <h3>Server preview</h3>
            <section aria-label="Frontend-known canonical operation metadata">
              <h4>Frontend-known canonical operation metadata</h4>
              <p>Definition and presentation version are not echoed response fields; the preview discriminator is locally known and response-validated.</p>
              <PropertyRow label="Operation definition"><code>{ADD_MEDIA_CLIP_DEFINITION}</code></PropertyRow>
              <PropertyRow label="Presentation version"><code>{ADD_MEDIA_CLIP_PRESENTATION_VERSION}</code></PropertyRow>
              <PropertyRow label="Preview discriminator"><code>{ADD_MEDIA_CLIP_PREVIEW_OPERATION}</code></PropertyRow>
            </section>
            <FrozenDraft draft={state.draft.command} />
            <PropertyRow label="Plan digest"><code>{state.draft.preview.planDigest}</code></PropertyRow>
            <PropertyRow label="Candidate hash"><code>{state.draft.preview.candidateContentHash}</code></PropertyRow>
            <PreviewList title="Expected changes" items={state.draft.preview.expectedChanges} />
            <PreviewList title="Validation" items={state.draft.preview.validation} />
            <PreviewList title="Capability requirements" items={state.draft.preview.capabilityRequirements} />
            <PreviewList title="Warnings" items={state.draft.preview.warnings} />
            <PreviewList title="Failures" items={state.draft.preview.failures} />
            <p>The server will replan this exact draft at apply using the opaque plan digest. A changed plan fails closed and requires a new preview.</p>
            <label className="ff-confirm"><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} disabled={!previewReady} /> I confirm this exact frozen draft, complete server preview, and opaque plan digest.</label>
            <Button variant="primary" disabled={!previewReady || !confirmed || !stableCommandId || state.phase.kind === 'PendingApply'} onClick={() => void apply()}>Confirm and apply</Button>
          </section> : null}
          {(state.phase.kind === 'StaleBase' || state.phase.kind === 'Conflict') ? <Button onClick={() => void loadHead()}>Reload HEAD for re-preview</Button> : null}
        </Panel>
        <Panel title="Server semantic comparison" className="ff-nle-diff-panel"><SemanticDiff comparison={state.server.comparison} actionFilter={state.presentation.comparisonActionFilter} onActionFilterChange={action => dispatch({ type: 'COMPARISON_FILTER_CHANGED', action })} /></Panel>
      </div>
    </>
  )
}
