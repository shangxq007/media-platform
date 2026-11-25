import { DEFAULT_COMPOSITION_ASPECT, type CompositionAspectRatio } from '../../contracts/app/composition-aspect'
import { useEffect, useReducer, useRef, useState, type ChangeEvent } from 'react'
import { operationGateway as defaultOperationGateway } from '../../api/app/operation.gateway'
import { listEditingBranches, readEditingBranch, timelineQueryGateway as defaultTimelineQueryGateway } from '../../api/app/timeline-query.gateway'
import { Button, EmptyState, Input, Panel, PropertyRow, Skeleton, Status } from '../../components/design-system'
import { useProjectContext } from '../../foundation/projectContext'
import { PageHeading, ProjectFrame } from '../../surfaces/FoundationPages'
import { initialTimelineEditorState, timelineEditorReducer } from './editor-state'
import type {
  AddMediaClipCommandDraft,
  CanonicalHeadReference,
  EditorCommandAccess,
  GatewayFailure,
  GatewayResult,
  OperationGateway,
  RevisionDetail,
  RevisionListEntry,
  TimelineQueryGateway,
} from './gateways'
import { SemanticDiff } from './SemanticDiff'
import { SourceBrowser } from './SourceBrowser'
import { ClipClipboard } from './ClipClipboard'
import { ClipEditor } from './ClipEditor'
import { FormalRenderAPI } from '../../api/formal-render'
import { projectSnapshotJson, timelineEnd, type ProjectedTrack } from './timeline-projection'
import { add, formatRational, parseRational, toSeconds } from './trim'
import { NleRenderPanel } from './NleRenderPanel'
import { TimelineMediaPreview } from './TimelineMediaPreview'
import { RestoreRevisionControl } from './RestoreRevisionControl'
import { useTrimHistory } from './useTrimHistory'
import { CreateEditingBranch } from './CreateEditingBranch'
import { useTranslation } from '../../localization'
import {
  ADD_MEDIA_CLIP_DEFINITION,
  ADD_MEDIA_CLIP_PRESENTATION_VERSION,
  ADD_MEDIA_CLIP_PREVIEW_OPERATION,
  applyCommandId,
  artifactId,
  clipId,
  exactMediaTime,
  mediaAssetId,
  mediaStreamId,
  projectId,
  sourceContentDigest,
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
    contentDigest: sourceContentDigest(fields.contentDigest),
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

export function NlePage() {
  return <ProjectFrame surfaceId="nle"><EditingBranchWorkspace /></ProjectFrame>
}

function EditingBranchWorkspace() {
  const project = useProjectContext()
  return <ProjectEditingBranches key={JSON.stringify([project.tenantId, project.projectId])} />
}

function ProjectEditingBranches() {
  const [branch, setBranch] = useState(() => new URLSearchParams(window.location.search).get('branch') ?? 'main')
  function switchBranch(next: string) {
    if (next === branch) return
    const url = new URL(window.location.href)
    if (next === 'main') url.searchParams.delete('branch')
    else url.searchParams.set('branch', next)
    window.history.replaceState(window.history.state, '', url)
    setBranch(next)
  }
  return <NleWorkspace key={branch} editingBranch={branch} onSwitchBranch={switchBranch} />
}

export function NleWorkspace({
  queryGateway = defaultTimelineQueryGateway,
  commandGateway = defaultOperationGateway,
  editingBranch = "main", onSwitchBranch,
}: {
  queryGateway?: TimelineQueryGateway
  commandGateway?: OperationGateway
  editingBranch?: string
  onSwitchBranch?: (refId: string) => void
}) {
  const { t } = useTranslation()
  const project = useProjectContext()
  const [branches, setBranches] = useState<string[]>([])
  const [branchListError, setBranchListError] = useState(false)
  const [switchTarget, setSwitchTarget] = useState<string | null>(null)
  const [createdBranch, setCreatedBranch] = useState<string | null>(null)
  const [clipDraft, setClipDraft] = useState(false)
  const [clipWriteLocked, setClipWriteLocked] = useState(false)
  const [restoreDraft, setRestoreDraft] = useState(false)
  const defaultBranch = editingBranch === 'main'

  useEffect(() => {
    if (!onSwitchBranch) return
    const abort = new AbortController()
    void listEditingBranches(projectId(project.projectId), abort.signal).then(
      value => { if (!abort.signal.aborted) setBranches(value) },
      () => { if (!abort.signal.aborted) setBranchListError(true) },
    )
    return () => abort.abort()
  }, [project.projectId, project.tenantId])

  async function readAuthority(requestedProject: CanonicalHeadReference['projectId']) {
    if (!onSwitchBranch) {
      return Promise.all([queryGateway.getHead(requestedProject), queryGateway.listRevisions(requestedProject)] as const)
    }
    const view = await readEditingBranch(requestedProject, editingBranch)
    if (!view.ok) return [view, view] as const
    return [{ ok: true, value: view.value.head }, { ok: true, value: view.value.history }] as const
  }

  const [state, dispatch] = useReducer(timelineEditorReducer, initialTimelineEditorState)
  const [fields, setFields] = useState<ManualFields>(emptyFields)
  const [loading, setLoading] = useState(true)
  type EditorCommand = 'trim' | 'create' | 'restore' | 'trim-history' | 'paste'
  const issuedCommand = useRef<EditorCommand | null>(null)
  const [commandOwner, setCommandOwner] = useState<EditorCommand | null>(null)
  const [selectionPending, setSelectionPending] = useState(false)
  const selectionPendingRef = useRef(false)

  function markSelectionPending(pending: boolean) {
    selectionPendingRef.current = pending
    setSelectionPending(pending)
  }

  function commandAccess(command: EditorCommand): EditorCommandAccess {
    const canStart = () => mounted.current && !selectionPendingRef.current && !applyInFlight.current
      && (issuedCommand.current === null || issuedCommand.current === command)
    return {
      blocked: selectionPending || (commandOwner !== null && commandOwner !== command),
      canStart,
      claim: () => {
        if (!canStart()) return false
        issuedCommand.current = command
        setCommandOwner(command)
        return true
      },
      release: () => {
        if (issuedCommand.current !== command) return
        issuedCommand.current = null
        if (mounted.current) setCommandOwner(null)
      },
    }
  }

  const [restoreMessage, setRestoreMessage] = useState('')
  const [queryMessage, setQueryMessage] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [confirmed, setConfirmed] = useState(false)
  const [readbackRetrying, setReadbackRetrying] = useState(false)
  const [applyOwnershipRelease, setApplyOwnershipRelease] = useState(0)
  const [aspectRatio, setAspectRatio] = useState<CompositionAspectRatio>(DEFAULT_COMPOSITION_ASPECT)
  const [projectionError, setProjectionError] = useState<string | null>(null)
  const [projectionRevision, setProjectionRevision] = useState<string | null>(null)
  const [tracks, setTracks] = useState<readonly ProjectedTrack[]>([])
  const [previewTime, setPreviewTime] = useState(0)
  const [initializing, setInitializing] = useState(false)
  const [initializeError, setInitializeError] = useState('')
  const initializeCommand = useRef<{ projectId: string; id: string } | null>(null)
  const [appendStart, setAppendStart] = useState('0/1')
  const [focusClipId, setFocusClipId] = useState<string | null>(null)
  const [stableCommandId, setStableCommandId] = useState<ReturnType<typeof applyCommandId> | null>(null)
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

  const loadHead = async (reason: 'NAVIGATION' | 'ORDINARY' | 'RESTORE_READBACK' | 'CLIP_READBACK' | 'TRIM_HISTORY_READBACK' | 'PASTE_READBACK' = 'ORDINARY') => {
    const ownReadback = (issuedCommand.current === 'restore' && reason === 'RESTORE_READBACK')
      || (issuedCommand.current === 'trim' && reason === 'CLIP_READBACK')
      || (issuedCommand.current === 'trim-history' && reason === 'TRIM_HISTORY_READBACK')
      || (issuedCommand.current === 'paste' && reason === 'PASTE_READBACK')
    const authoritativeTransitionOwned = (issuedCommand.current !== null && !ownReadback) || applyInFlight.current
      || state.phase.kind === 'PendingApply'
      || (state.phase.kind === 'AppliedRevision' && state.phase.readback !== 'VERIFIED')
    if (authoritativeTransitionOwned) {
      if (reason === 'NAVIGATION') deferredNavigationHeadLoad.current = true
      return
    }
    const generation = ++requestGeneration.current.head
    requestGeneration.current.selection += 1
    markSelectionPending(false)
    requestGeneration.current.preview += 1
    requestGeneration.current.apply += 1
    setLoading(true)
    setQueryMessage(null)
    try {
      const requestedProject = projectId(project.projectId)
      const [head, history] = await readAuthority(requestedProject)
      if (!ownsRequest('head', generation)) return
      if (!head.ok) { dispatch({ type: 'UNAVAILABLE', reason: `${head.code}: ${head.message}` }); return }
      trimHistory.observeHead(head.value)
      if (!history.ok) { dispatch({ type: 'UNAVAILABLE', reason: `${history.code}: ${history.message}` }); return }
      dispatch({ type: 'HEAD_LOADED', head: head.value, revisions: history.value })
      setConfirmed(false)
      setStableCommandId(null)
      return head.value
    } catch (error) {
      if (!ownsRequest('head', generation)) return
      dispatch({ type: 'UNAVAILABLE', reason: error instanceof Error ? error.message : 'Project identity is invalid.' })
    } finally {
      if (ownsRequest('head', generation)) setLoading(false)
    }
  }

  useEffect(() => { void loadHead('NAVIGATION') }, [project.projectId, queryGateway])

  useEffect(() => {
    const authoritativeTransitionOwned = issuedCommand.current !== null || applyInFlight.current
      || state.phase.kind === 'PendingApply'
      || (state.phase.kind === 'AppliedRevision' && state.phase.readback !== 'VERIFIED')
    if (!deferredNavigationHeadLoad.current || authoritativeTransitionOwned) return
    deferredNavigationHeadLoad.current = false
    void loadHead()
  }, [state.phase, project.projectId, queryGateway, applyOwnershipRelease, commandOwner])

  useEffect(() => {
    let active = true
    setProjectionRevision(null)
    if (!state.server.head) { setTracks([]); setAppendStart('0/1'); setProjectionError(null); return }
    void (async () => {
      try {
        const snapshot = await FormalRenderAPI.snapshot(project.projectId, state.server.head!.revisionId)
        if (!active) return
        const projection = projectSnapshotJson(snapshot.canonicalTimelineJson)
        setAspectRatio(projection.compositionAspectRatio)
        setProjectionError(null)
        setTracks(projection.tracks)
        setProjectionRevision(snapshot.revisionId)
        setAppendStart(formatRational(timelineEnd(projection)))
      } catch (error) {
        if (active) {
          setTracks([])
          setAppendStart('0/1')
          setProjectionError(error instanceof Error ? error.message : 'Timeline projection unavailable.')
        }
      }
    })()
    return () => { active = false }
  }, [state.server.head?.revisionId, project.projectId])

  const selectRevision = async (selectedId: string) => {
    const selectionLocked = clipWriteLocked || issuedCommand.current !== null || applyInFlight.current
      || state.phase.kind === 'PendingApply'
      || (state.phase.kind === 'AppliedRevision' && state.phase.readback !== 'VERIFIED')
    if (!state.server.head || selectionLocked) return
    const generation = ++requestGeneration.current.selection
    const request = { purpose: 'USER_SELECTION' as const, generation }
    const requestedHead = state.server.head
    const requestedRevisions = state.server.revisions
    markSelectionPending(true)
    dispatch({ type: 'REVISION_SELECTION_STARTED', request })
    setQueryMessage('Loading explicit revision…')
    const detail = await loadExplicitRevisionSelection(queryGateway, requestedHead, requestedRevisions, selectedId)
    if (!ownsRequest('selection', generation)) return
    markSelectionPending(false)
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
    if (issuedCommand.current || !state.server.head || !project.tenantId) return
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
      const [headReadback, historyReadback] = await readAuthority(requestedProject)
      if (!ownsRequest('apply', generation)) return
      if (!headReadback.ok) {
        dispatch({ type: 'APPLY_READBACK_FAILED', failure: readbackFailure(
          'UNAVAILABLE', `Accepted result preserved, but canonical HEAD readback failed (${headReadback.code}: ${headReadback.message}).`,
        ) })
        return
      }
      trimHistory.observeHead(headReadback.value)
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
    if (issuedCommand.current || applyInFlight.current || !project.tenantId || !state.server.head
        || !state.draft.command || !state.draft.preview || !stableCommandId || !confirmed) return
    applyInFlight.current = true
    const generation = ++requestGeneration.current.apply
    const selectionGeneration = ++requestGeneration.current.selection
    markSelectionPending(false)
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
    setFocusClipId(submittedDraft.clipId)
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

  async function initialize() {
    if (issuedCommand.current || !project.tenantId || initializing) return
    setInitializing(true)
    setInitializeError('')
    if (initializeCommand.current?.projectId !== project.projectId) {
      initializeCommand.current = { projectId: project.projectId, id: crypto.randomUUID() }
    }
    try {
      await FormalRenderAPI.initializeTimeline(project.tenantId, project.projectId, initializeCommand.current.id)
      await loadHead()
    } catch (error) {
      setInitializeError(error instanceof Error ? error.message : 'Timeline initialization failed.')
    } finally {
      setInitializing(false)
    }
  }

  const previewReady = state.draft.preview && !state.draft.previewRequired && state.draft.preview.failures.length === 0
  const revisionSelectionLocked = commandOwner !== null || clipWriteLocked || state.phase.kind === 'PendingApply'
    || (state.phase.kind === 'AppliedRevision' && state.phase.readback !== 'VERIFIED')
  const switchLocked = revisionSelectionLocked || initializing
  function requestSwitch(next: string) {
    if (!onSwitchBranch || next === editingBranch || switchLocked || issuedCommand.current !== null) return
    const manualDraft = !['AppliedRevision', 'CanonicalHead'].includes(state.phase.kind) && state.draft.command !== null
      || (state.phase.kind !== 'AppliedRevision' && fields !== emptyFields)
    if (clipDraft || restoreDraft || manualDraft) setSwitchTarget(next)
    else onSwitchBranch(next)
  }
  useEffect(() => {
    if (!createdBranch || switchLocked) return
    requestSwitch(createdBranch)
    setCreatedBranch(null)
  }, [createdBranch, switchLocked])

  const editingHead = state.server.head?.projectId === project.projectId ? state.server.head : null
  const selectedRevision = editingHead ? state.server.selected?.revision.id : undefined
  const historyRevision = selectedRevision && selectedRevision !== editingHead?.revisionId ? selectedRevision : undefined
  const viewedRevision = historyRevision ?? editingHead?.revisionId
  const trimHistoryAccess = commandAccess('trim-history')
  const trimHistory = useTrimHistory(JSON.stringify([project.tenantId, project.projectId, editingBranch]), editingHead,
    project.tenantId, trimHistoryAccess, loading || !!historyRevision || clipWriteLocked || trimHistoryAccess.blocked,
    () => loadHead('TRIM_HISTORY_READBACK'))
  return (
    <>
      <PageHeading eyebrow="Creative · NLE" title="Timeline editor" description="One canonical Timeline core with explicit server HEAD, read-only revision queries, and preview-before-apply operations." />
      {loading ? <Skeleton label="Loading canonical Timeline HEAD" /> : null}
      <EditorPhaseNotice phase={state.phase} readbackRetrying={readbackRetrying} onRetryReadback={() => void retryAuthoritativeReadback()} />
      <div className="ff-nle-layout">
        <Panel title="Canonical revision authority" actions={<Button onClick={() => void loadHead()} disabled={loading || revisionSelectionLocked}>Reload HEAD</Button>}>
          {state.server.head ? <div className="ff-head-identity"><Status label={`${historyRevision ? "Editing head" : "Viewing editing head"} · ${editingBranch}`} tone="success" /><PropertyRow label="Revision"><code>{state.server.head.revisionId}</code></PropertyRow><PropertyRow label="Content hash"><code>{state.server.head.contentHash}</code></PropertyRow></div> : <>
            <EmptyState title="HEAD unavailable" description={defaultBranch ? "Create a Timeline to start editing this project." : "This editing branch could not be loaded."} />
            {defaultBranch && <Button disabled={commandOwner !== null || !project.tenantId || initializing || loading} onClick={() => void initialize()}>Initialize Timeline</Button>}
            {initializeError && <p role="alert">{initializeError}</p>}
          </>}
          {onSwitchBranch && <>
            <label>Editing branch <select aria-label="Editing branch" value={editingBranch} disabled={switchLocked}
              onChange={event => requestSwitch(event.target.value)}>
              {Array.from(new Set(['main', ...branches, editingBranch])).map(ref => <option key={ref}>{ref}</option>)}
            </select></label>
            {branchListError && <p role="alert">Editing branches could not be listed.</p>}
            {switchTarget && <section aria-label="Unsaved branch edits" role="alert">
              <p>Discard unsaved edits and switch to {switchTarget}?</p>
              <Button disabled={switchLocked} onClick={() => { if (!issuedCommand.current) onSwitchBranch(switchTarget) }}>Discard edits and switch</Button>
              <Button onClick={() => setSwitchTarget(null)}>Stay on this branch</Button>
            </section>}
          </>}
          <h3>Revision history</h3>
          {historyRevision && <PropertyRow label="Viewing revision"><code data-viewed-revision={viewedRevision}>{viewedRevision}</code></PropertyRow>}
          {historyRevision && <>
            <Status label="Read-only historical revision" tone="info" />
            <Button disabled={loading || revisionSelectionLocked} onClick={() => { setRestoreMessage(''); void loadHead() }}>Return to editing head</Button>
          </>}
          {onSwitchBranch && historyRevision && !selectionPending && project.tenantId && <CreateEditingBranch
            key={historyRevision} projectId={project.projectId} source={historyRevision}
            disabled={loading} access={commandAccess('create')}
            onCreated={ref => {
              setBranches(previous => previous.includes(ref) ? previous : [...previous, ref])
              setCreatedBranch(ref)
            }} />}
          {historyRevision && !defaultBranch && <p>Restore is available on main.</p>}
          {defaultBranch && historyRevision && editingHead && project.tenantId && !selectionPending && (!loading || commandOwner === 'restore') && <RestoreRevisionControl
            key={JSON.stringify([project.projectId, editingHead.revisionId, editingHead.contentHash, historyRevision])}
            head={editingHead} source={historyRevision} tenantId={project.tenantId} access={commandAccess('restore')} onDraft={setRestoreDraft}
            onRefresh={async message => { setRestoreMessage(message); await loadHead('RESTORE_READBACK') }} />}
          {restoreMessage && <p role="status">{restoreMessage}</p>}
          {state.server.revisions.length ? <ol className="ff-revision-list">{state.server.revisions.map(revision => <li key={revision.id}><button type="button" aria-pressed={state.server.selected?.revision.id === revision.id} disabled={revisionSelectionLocked} onClick={() => { setRestoreMessage(''); void selectRevision(revision.id) }}><span>r{revision.revisionNumber}</span><code>{revision.id}</code><small>{revision.message ?? 'No message'}</small></button></li>)}</ol> : <p>No accepted history projection.</p>}
          {queryMessage ? <p role="status">{queryMessage}</p> : null}
          {state.server.selected ? <div className="ff-revision-detail"><h3>Selected revision detail</h3><PropertyRow label="Revision"><code>{state.server.selected.revision.id}</code></PropertyRow><PropertyRow label="Source"><span>{state.server.selected.revision.source}</span></PropertyRow><PropertyRow label="Server change count"><span>{state.server.selected.changeCount}</span></PropertyRow></div> : <p>Select a revision explicitly to load detail.</p>}
        </Panel>
        <Panel title="Timeline media preview" className="ff-operation-panel">
          <TimelineMediaPreview key={JSON.stringify([project.projectId, viewedRevision])} revisionId={viewedRevision} tenantId={project.tenantId} projectId={project.projectId} onTime={setPreviewTime} />
        </Panel>
        <Panel title={t('timeline.workspace')} className="ff-nle-workspace-panel">
          {defaultBranch && !historyRevision && <section aria-label="Confirmed trim history">
            {(['Undo', 'Redo'] as const).map(direction => <Button key={direction}
              disabled={loading || revisionSelectionLocked || selectionPending || trimHistory.busy || !trimHistory.can(direction)}
              onClick={() => void trimHistory.execute(direction)}>{direction} trim</Button>)}
            {trimHistory.retry && <Button disabled={trimHistory.busy}
              onClick={() => void trimHistory.execute(trimHistory.retry!.direction, trimHistory.retry!)}>Retry {trimHistory.retry.direction.toLowerCase()} trim</Button>}
            {trimHistory.message && <p role={trimHistory.failed ? 'alert' : 'status'}>{trimHistory.message}</p>}
          </section>}
          {defaultBranch && <ClipClipboard key={JSON.stringify([project.tenantId, project.projectId, editingBranch])}
            head={editingHead} tenant={project.tenantId} tracks={projectionRevision === editingHead?.revisionId ? tracks : []}
            playhead={previewTime} visible={!historyRevision}
            disabled={loading || clipWriteLocked || projectionRevision !== editingHead?.revisionId}
            access={commandAccess('paste')} readback={() => loadHead('PASTE_READBACK')} />}
          <ClipEditor key={JSON.stringify([project.projectId, historyRevision])}
            head={historyRevision ? null : editingHead} historicalRevisionId={historyRevision}
            editingBranch={editingBranch} trimAccess={commandAccess('trim')} onDraft={setClipDraft} onWriteLock={setClipWriteLocked} focusClipId={focusClipId} playhead={previewTime}
            selectedTrackId={state.presentation.selectedTrackId}
            onSelectTrack={trackId => dispatch({ type: 'TRACK_SELECTED', trackId })}
            onRevisionChanged={async () => { await loadHead('CLIP_READBACK') }}
            onTrimAccepted={defaultBranch ? trimHistory.recordTrim : undefined}
            onTrimReadback={() => loadHead('CLIP_READBACK')} />
        </Panel>
        {!defaultBranch && !historyRevision && <p>Source insertion and render controls are available on main.</p>}
        {defaultBranch && !historyRevision && <><Panel title="Add media clip" className="ff-operation-panel">
          <SourceBrowser tenantId={project.tenantId} project={project.projectId} onSelect={source => {
            const selectedTrack = tracks.find(track => track.trackId === state.presentation.selectedTrackId)
            const selectedStream = source.streams.find(item => item.kind === selectedTrack?.type)
            const stream = selectedStream ?? source.streams.find(item => item.kind === 'VIDEO') ?? source.streams[0]
            const duration = parseRational(stream.duration) ?? { numerator: 0n, denominator: 1n }
            const start = parseRational(appendStart) ?? { numerator: 0n, denominator: 1n }
            const preferred = selectedStream ? selectedTrack : tracks.find(track => track.type === stream.kind)
            setFields({ trackId: preferred?.trackId ?? '', clipId: `clip-${crypto.randomUUID()}`,
              mediaAssetId: source.mediaAssetId, mediaStreamId: stream.streamId, artifactId: source.artifactId,
              contentDigest: source.contentDigest, sourceStart: '0', sourceEnd: formatRational(duration),
              timelineStart: formatRational(start), timelineEnd: formatRational(add(start, duration)),
              rateNumerator: '1', rateDenominator: '1', direction: 'FORWARD' })
            setStableCommandId(null); setConfirmed(false); dispatch({ type: 'ROLLBACK_DRAFT' })
          }} />
          {!project.tenantId ? <div className="ff-operation-notice ff-operation-notice--danger" role="alert"><strong>TENANT CONTEXT UNAVAILABLE</strong><p>The authenticated Workspace projection did not supply a tenant. Preview and apply remain disabled.</p></div> : null}
          <form onSubmit={event => { event.preventDefault(); void preview() }}>
            <fieldset disabled={commandOwner !== null || !state.server.head || !project.tenantId
              || state.phase.kind === 'PendingPreview' || state.phase.kind === 'PendingApply'}>
              <legend>Canonical source and placement pins</legend>
              <div className="ff-operation-fields">{fieldLabels.map(([key, label]) => <label key={key}><span>{label}</span><Input value={fields[key]} onChange={changeField(key)} autoComplete="off"
                list={key === 'trackId' ? 'nle-track-options' : undefined} /></label>)}<label><span>Direction</span><select value={fields.direction} onChange={changeField('direction')}><option value="">Select direction</option><option value="FORWARD">Forward</option><option value="REVERSE">Reverse</option></select></label></div>
              <datalist id="nle-track-options">{tracks.map(track =>
                <option key={track.trackId} value={track.trackId}>{track.name} · {track.type}</option>)}</datalist>
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
            <label className="ff-confirm"><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)} disabled={commandOwner !== null || !previewReady} /> I confirm this exact frozen draft, complete server preview, and opaque plan digest.</label>
            <Button variant="primary" disabled={commandOwner !== null || !previewReady || !confirmed || !stableCommandId || state.phase.kind === 'PendingApply'} onClick={() => void apply()}>Confirm and apply</Button>
          </section> : null}
          {(state.phase.kind === 'StaleBase' || state.phase.kind === 'Conflict') ? <Button disabled={revisionSelectionLocked} onClick={() => void loadHead()}>Reload HEAD for re-preview</Button> : null}
        </Panel>
        <Panel title="Render and delivery" className="ff-operation-panel">
          {projectionError && <p role="alert">{projectionError}</p>}
          <NleRenderPanel head={projectionError ? null : state.server.head} aspectRatio={aspectRatio} timelineEndMillis={Math.ceil(
            1000 * toSeconds(parseRational(appendStart) ?? { numerator: 0n, denominator: 1n })
          )} />
        </Panel>
        </>}
        <Panel title="Server semantic comparison" className="ff-nle-diff-panel"><SemanticDiff comparison={state.server.comparison} actionFilter={state.presentation.comparisonActionFilter} onActionFilterChange={action => dispatch({ type: 'COMPARISON_FILTER_CHANGED', action })} /></Panel>
      </div>
    </>
  )
}
