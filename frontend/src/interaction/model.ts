import type { SurfaceId } from '../foundation/surfaceRegistry'

export interface SelectionScope { readonly surfaceId: SurfaceId; readonly workspaceId?: string; readonly projectId?: string }
export interface PresentationSelectionRef extends SelectionScope { readonly kind: 'NODE' | 'CLIP' | 'LANE' | 'PUBLICATION'; readonly localId: string }
// Application projection only; neither presentation identity nor canonical operation authority.
export interface ApplicationLogicalRef { readonly kind: 'PROJECT' | 'TIMELINE_REVISION' | 'MEDIA_ASSET'; readonly referenceId: string; readonly entityId: string }
export interface SelectionRequest { mode: 'replace' | 'toggle' | 'remove' | 'clear'; refs: readonly PresentationSelectionRef[]; primary?: PresentationSelectionRef; lifetime: object; revision: number }
export interface SelectedObject { id: string; kind: 'NODE' | 'CLIP' | 'LANE' | 'PUBLICATION'; title: string; x?: number; y?: number; reference?: string; logicalRef?: ApplicationLogicalRef; synthetic?: boolean }
export interface SimulationTime { unit: 'SIMULATED_STEP_0_20'; playhead: number; range?: readonly [number, number] }
export interface RevisionPair { from: string; to: string }
export type InvocationSource = 'DIRECT' | 'TOOLBAR' | 'INSPECTOR' | 'COMMAND' | 'AGENT'
export type PresentationAction =
  | { category: 'WORKSPACE_PRESENTATION'; type: 'undo-local' }
  | { category: 'WORKSPACE_PRESENTATION'; type: 'redo-local' }
  | { category: 'WORKSPACE_PRESENTATION'; type: 'rename'; targetId: string; title: string }
  | { category: 'WORKSPACE_PRESENTATION'; type: 'move'; targetId: string; x: number; y: number }
  | { category: 'WORKSPACE_PRESENTATION'; type: 'reveal'; targetId: string }
export type QueryAction = { category: 'READ_ONLY_QUERY'; type: 'compare-revisions'; pair: RevisionPair }
export type ProductAction = PresentationAction | QueryAction
  | { category: 'LOCAL_EPHEMERAL'; type: 'select'; ids: readonly string[]; primaryId?: string }
  | { category: 'LOCAL_EPHEMERAL'; type: 'inspect'; open: boolean }
  | { category: 'LOCAL_EPHEMERAL'; type: 'agent'; open: boolean }
  | { category: 'CANONICAL_SEMANTIC'; type: 'semantic'; commandId: string }
export type InteractionIntent = ProductAction
export interface ActionResult { ok: boolean; reason: string }
export interface SurfaceAdapter {
  objects: () => readonly SelectedObject[]
  onSelection?: (objects: readonly SelectedObject[]) => void
  supports: readonly (PresentationAction['type'] | QueryAction['type'])[]
  handle: (action: PresentationAction | QueryAction) => boolean
}
export interface SelectionState extends SelectionScope {
  readonly lifetime: object
  readonly revision: number
  readonly selectedRefs: readonly PresentationSelectionRef[]
  readonly primaryRef: PresentationSelectionRef | null
  readonly selectedObjects: readonly SelectedObject[]
  readonly primarySelectedObject: SelectedObject | null
  readonly inspectorOpen: boolean
  readonly agentOpen: boolean
  readonly time?: SimulationTime
  readonly revisionPair?: RevisionPair
  readonly supported: SurfaceAdapter['supports']
}
export const canonicalRejection = 'This change cannot be applied here yet. A connected server preview and confirmation flow is required; use the existing timeline advanced controls for supported operations.'
const reject = (reason: string): ActionResult => ({ ok: false, reason })
const success = (): ActionResult => ({ ok: true, reason: 'Local presentation or read-only request only; no canonical mutation.' })
const same = (left: unknown, right: unknown) => JSON.stringify(left) === JSON.stringify(right)
const refKey = (ref: PresentationSelectionRef) => JSON.stringify([ref.workspaceId, ref.projectId, ref.surfaceId, ref.kind, ref.localId])
export function createInteractionStore(scope: SelectionScope, initialRevision: number = 0) {
  const ownedScope = Object.freeze({ ...scope })
  const refFor = (object: SelectedObject): PresentationSelectionRef => Object.freeze({ ...ownedScope, kind: object.kind, localId: object.id })
  const freezeSnapshot = (value: SelectionState): SelectionState => Object.freeze({
    ...value, lifetime: Object.freeze(value.lifetime), selectedRefs: Object.freeze(value.selectedRefs), selectedObjects: Object.freeze(value.selectedObjects), supported: Object.freeze(value.supported),
    time: value.time ? Object.freeze({ ...value.time, range: value.time.range ? Object.freeze([...value.time.range]) as readonly [number, number] : undefined }) : undefined,
    revisionPair: value.revisionPair ? Object.freeze({ ...value.revisionPair }) : undefined,
  })
  let state: SelectionState = freezeSnapshot({ ...ownedScope, lifetime: {}, revision: initialRevision, selectedRefs: [], primaryRef: null, selectedObjects: [], primarySelectedObject: null, inspectorOpen: true, agentOpen: false, supported: [] })
  let adapter: SurfaceAdapter | null = null
  let retired = false
  let documentRetired = false
  const listeners = new Set<() => void>()
  const update = (next: Partial<SelectionState>, invalidate = false) => {
    if (Object.entries(next).every(([key, value]) => same(state[key as keyof SelectionState], value)) && !invalidate) return
    state = freezeSnapshot({ ...state, ...next, revision: state.revision + (invalidate ? 1 : 0) })
    listeners.forEach(fn => fn())
  }
  // Selection alone ends logical ownership. Notify synchronously so active gestures release capture.
  const retireSelectionOwner = (reason: 'document' | 'owner' | 'adapter') => {
    if (reason === 'document') documentRetired = true
    if (retired) return
    retired = true
    adapter = null
    update({ lifetime: {}, selectedRefs: [], primaryRef: null, selectedObjects: [], primarySelectedObject: null, supported: [], revisionPair: undefined, time: undefined, agentOpen: false }, true)
  }
  // Copy only the bounded projection. Adapter objects and edge-shaped runtime values are not authority.
  const inventory = () => (adapter?.objects() ?? []).filter(object => ['NODE', 'CLIP', 'LANE', 'PUBLICATION'].includes(object.kind)).map(object => Object.freeze({
    id: object.id, kind: object.kind, title: object.title, x: object.x, y: object.y,
    reference: object.reference, synthetic: object.synthetic,
    logicalRef: object.logicalRef ? Object.freeze({ kind: object.logicalRef.kind, referenceId: object.logicalRef.referenceId, entityId: object.logicalRef.entityId }) : undefined,
  }))
  const resolve = (ref: PresentationSelectionRef, objects: readonly SelectedObject[]) => {
    const matches = objects.filter(object => refKey(refFor(object)) === refKey(ref))
    return matches.length === 1 ? matches[0] : undefined
  }
  const publishSelection = (objects: readonly SelectedObject[], primary?: PresentationSelectionRef | null, supported = adapter?.supports ?? []) => {
    const selectedObjects = Object.freeze([...objects])
    const selectedRefs = Object.freeze(objects.map(refFor))
    const primaryIndex = selectedRefs.findIndex(ref => primary && refKey(ref) === refKey(primary))
    const primarySelectedObject = selectedObjects[primaryIndex < 0 ? 0 : primaryIndex] ?? null
    const primaryRef = selectedRefs[primaryIndex < 0 ? 0 : primaryIndex] ?? null
    if (same(selectedObjects, state.selectedObjects) && same(primaryRef, state.primaryRef) && same(supported, state.supported)) return
    const membershipChanged = !same(selectedRefs, state.selectedRefs)
    update({ selectedObjects, selectedRefs, primarySelectedObject, primaryRef, supported: Object.freeze([...supported]) }, true)
    if (membershipChanged) adapter?.onSelection?.(selectedObjects)
  }
  const reconcile = () => {
    const objects = inventory()
    publishSelection(state.selectedRefs.flatMap(ref => { const object = resolve(ref, objects); return object ? [object] : [] }), state.primaryRef)
  }
  const select = (request: SelectionRequest): ActionResult => {
    if (retired) return reject('Selection ownership has retired.')
    reconcile()
    if (request.lifetime !== state.lifetime || request.revision !== state.revision) return reject('Context changed. Select again on the current surface.')
    const objects = inventory()
    const refs = [...new Map(request.refs.map(ref => [refKey(ref), ref])).values()]
    if (refs.some(ref => !resolve(ref, objects))) return reject('The requested selection is no longer on this surface.')
    let next = state.selectedRefs.slice()
    if (request.mode === 'clear') next = []
    else if (request.mode === 'replace') next = refs
    else if (request.mode === 'remove') next = next.filter(ref => !refs.some(item => refKey(item) === refKey(ref)))
    else if (request.mode === 'toggle') {
      const additions = refs.filter(ref => !next.some(item => refKey(item) === refKey(ref)))
      next = [...next.filter(ref => !refs.some(item => refKey(item) === refKey(ref))), ...additions]
    } else return reject('Unsupported local intent.')
    if (request.primary && !next.some(ref => refKey(ref) === refKey(request.primary!))) return reject('Primary must be a selected presentation.')
    const lastAddition = request.mode === 'toggle' ? refs.filter(ref => !state.selectedRefs.some(item => refKey(item) === refKey(ref))).slice(-1)[0] : undefined
    const primary = request.primary ?? lastAddition ?? (request.mode === 'replace' ? next[0] : state.primaryRef)
    publishSelection(next.map(ref => resolve(ref, objects)!), primary)
    return success()
  }
  const store = {
    getSnapshot: () => state,
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener) } },
    reconcile,
    select,
    retireSelectionOwner,
    register: (next: SurfaceAdapter) => {
      if (documentRetired) return () => {}
      if (adapter) throw new Error('Only one active surface adapter may own the selection context.')
      retired = false
      adapter = next; reconcile()
      return () => { if (adapter === next) retireSelectionOwner('adapter') }
    },
    setTime: (time: SimulationTime) => {
      if (retired) return
      const clamp = (value: number) => Math.min(20, Math.max(0, Number.isFinite(value) ? Math.round(value) : 0))
      const bounded = { ...time, playhead: clamp(time.playhead), range: time.range ? [...time.range].map(clamp).sort((a, b) => a - b) as [number, number] : undefined }
      if (!same(bounded, state.time)) update({ time: bounded }, true)
    },
    setRevisionPair: (revisionPair?: RevisionPair) => {
      if (retired) return
      if (!same(revisionPair, state.revisionPair)) update({ revisionPair }, true)
    },
    dispatch: (action: ProductAction, _source: InvocationSource = 'DIRECT'): ActionResult => {
      if (['CANONICAL_SEMANTIC'].includes(action.category) || action.type === 'semantic') return reject(canonicalRejection)
      if (retired) return reject('Selection ownership has retired.')
      reconcile()
      if (action.category === 'LOCAL_EPHEMERAL') {
        if (action.type === 'agent') { update({ agentOpen: action.open }); return success() }
        if (action.type === 'inspect') { update({ inspectorOpen: action.open }); return success() }
        if (action.type !== 'select') return reject('Unsupported local intent.')
        // Compatibility for synchronous shell/NLE intents: IDs resolve only within this active adapter.
        const objects = inventory()
        const ids = [...new Set(action.ids)]
        if (ids.some(id => objects.filter(object => object.id === id).length !== 1)) return reject('The requested selection is no longer on this surface.')
        const refs = ids.map(id => refFor(objects.find(object => object.id === id)!))
        const primary = action.primaryId === undefined ? undefined : refs.find(ref => ref.localId === action.primaryId)
        if (action.primaryId !== undefined && !primary) return reject('Primary must be a selected presentation.')
        return select({ mode: 'replace', refs, primary, lifetime: state.lifetime, revision: state.revision })
      }
      if (action.category === 'READ_ONLY_QUERY') {
        if (action.type !== 'compare-revisions' || !state.revisionPair || !same(action.pair, state.revisionPair) || action.pair.from === action.pair.to) return reject('Choose a current pair of distinct revisions first.')
      } else if (action.category === 'WORKSPACE_PRESENTATION') {
        if (action.type === 'undo-local' || action.type === 'redo-local') {
          if (state.surfaceId !== 'canvas') return reject('Local history is only available on Canvas.')
        } else if (!['rename', 'move', 'reveal'].includes(action.type) || inventory().filter(object => object.id === action.targetId).length !== 1 || !state.selectedObjects.some(object => object.id === action.targetId)) return reject('Select this object again before changing its local presentation.')
      } else return reject('Unsupported intent category.')
      if (!adapter?.supports.includes(action.type) || !adapter.handle(action)) return reject('This action is not integrated on the current surface.')
      reconcile()
      return success()
    },
  }
  return store
}
export type InteractionStore = ReturnType<typeof createInteractionStore>
export interface SyntheticProposal { refs: readonly PresentationSelectionRef[]; logicalProjection: readonly { presentation: PresentationSelectionRef; logical: ApplicationLogicalRef }[]; lifetime: object; revision: number; intent: string; target: string; description: string; action: ProductAction | null }
export function createProposal(state: SelectionState, intent: string): SyntheticProposal {
  const selected = state.primarySelectedObject
  const selectedTargets = state.selectedObjects
  const text = intent.trim()
  let action: ProductAction | null = null
  let description = 'This request is not integrated. Try “inspect”, “reveal”, “rename to …”, or ask what changed between selected revisions.'
  const rename = /^rename to\s+(.+)$/i.exec(text)
  if (rename && selected && state.supported.includes('rename')) { action = { category: 'WORKSPACE_PRESENTATION', type: 'rename', targetId: selected.id, title: rename[1].slice(0, 120) }; description = `${selectedTargets.length > 1 ? `Rename only the primary ${selected.kind.toLowerCase()} “${selected.title}”` : 'Rename local presentation'} to “${rename[1].slice(0, 120)}”. ${selectedTargets.length > 1 ? 'Other selected objects remain unchanged. ' : ''}Not saved.` }
  else if (/^inspect(?: selection)?$/i.test(text) && selected) { action = { category: 'LOCAL_EPHEMERAL', type: 'inspect', open: true }; description = `${selectedTargets.length > 1 ? `Open Inspector for only the primary ${selected.kind.toLowerCase()} “${selected.title}”. Other selected objects remain selected.` : 'Open Inspector for the selected presentation object.'}` }
  else if (/^reveal(?: selection)?$/i.test(text) && selected && state.supported.includes('reveal')) { action = { category: 'WORKSPACE_PRESENTATION', type: 'reveal', targetId: selected.id }; description = `${selectedTargets.length > 1 ? `Reveal only the primary ${selected.kind.toLowerCase()} “${selected.title}” in local presentation coordinates. Other selected objects remain selected.` : 'Reveal the selected object in local presentation coordinates.'}` }
  else if (/what changed between (?:these|the) revisions\??/i.test(text) && state.revisionPair) { action = { category: 'READ_ONLY_QUERY', type: 'compare-revisions', pair: state.revisionPair }; description = 'Request the existing revision comparison. No diff facts are inferred by Agent; conversational analysis is not integrated.' }
  else if (/把这两段的音量都降低一点/.test(text) && selectedTargets.length === 2 && selectedTargets.every(object => object.kind === 'CLIP' && object.synthetic)) {
    description = 'Lower the volume of 2 selected clips.'
  }
  const target = state.revisionPair ? `${state.revisionPair.from} → ${state.revisionPair.to}` : selectedTargets.length > 1
    ? `${selectedTargets.length} selected: ${selectedTargets.map(object => `${object.kind}: ${object.title}`).join(' · ')}`
    : selected ? `${selected.kind}: ${selected.title}` : `${state.surfaceId} · ${state.projectId ?? 'workspace'}`
  return { refs: state.selectedRefs, logicalProjection: state.selectedObjects.flatMap((object, index) => object.logicalRef ? [{ presentation: state.selectedRefs[index], logical: object.logicalRef }] : []), lifetime: state.lifetime, revision: state.revision, intent: text, target, description, action }
}
export function applyProposal(store: InteractionStore, proposal: SyntheticProposal): ActionResult {
  store.reconcile()
  const current = store.getSnapshot()
  if (proposal.lifetime !== current.lifetime || proposal.revision !== current.revision) return reject('Context changed. Preview again for the current selection and time.')
  if (!proposal.action) return reject('No supported local action or read-only query was interpreted.')
  return store.dispatch(proposal.action, 'AGENT')
}
