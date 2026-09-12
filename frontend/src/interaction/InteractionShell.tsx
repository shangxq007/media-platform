import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Button, Input, PropertyRow } from '../components/design-system'
import type { PaletteAction } from '../components/design-system'
import { InteractionDialog } from './InteractionDialog'
import { useInteractionStore, useSelection } from './SelectionContext'
import { applyProposal, createProposal, type InvocationSource, type ProductAction, type SyntheticProposal } from './model'
import { isWorkflowSketchTitleValid } from '../product/workflow-sketch/model'
import { WorkflowTitleField, WorkflowPlacementFields } from '../product/workflow-sketch/WorkflowSketch'
import { useTranslation } from '../localization'

export function SelectionActionBar() {
  const store = useInteractionStore(), selection = useSelection()
  const { t } = useTranslation()
  const selected = selection.primarySelectedObject
  if (!selected) return null
  const selectedCount = selection.selectedObjects.length
  const dispatch = (action: ProductAction) => {
    const current = store.getSnapshot()
    if (current.lifetime !== selection.lifetime || current.revision !== selection.revision || current.primarySelectedObject?.id !== selected.id) return
    store.dispatch(action, 'TOOLBAR')
  }
  const title = selected.title || t('common.untitled')
  return <div className="ff-selection-bar" role="toolbar" aria-label={t('agent.selectionActions')} data-testid="selection-actions">
    <span className="ff-selection-title">{selectedCount > 1 ? t('agent.selectedPrimary', { count: selectedCount, kind: t(`agent.kind${selected.kind}`), title }) : t('agent.singleSelection', { kind: t(`agent.kind${selected.kind}`), title })}</span>
    <Button aria-expanded={selection.inspectorOpen} aria-controls="selection-inspector" onClick={() => dispatch({ category: 'LOCAL_EPHEMERAL', type: 'inspect', open: !selection.inspectorOpen })}>{selection.inspectorOpen ? t('agent.hideInspector') : t('agent.showInspector')}</Button>
    {selection.supported.includes('reveal') ? <Button onClick={() => dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'reveal', targetId: selected.id })}>{t('agent.revealPrimary', { kind: t(`agent.kindLower${selected.kind}`) })}</Button> : null}
    <Button onClick={() => dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] })}>{t('agent.clearSelection')}</Button>
    <details><summary>{t('agent.more')}</summary><div className="ff-selection-more"><p>{t('agent.localOnly')}</p><Button onClick={() => dispatch({ category: 'LOCAL_EPHEMERAL', type: 'inspect', open: true })}>{t('agent.editProperties')}</Button><UnavailableAction /></div></details>
    <Button onClick={() => dispatch({ category: 'LOCAL_EPHEMERAL', type: 'agent', open: true })}>{t('agent.ask')}</Button>
  </div>
}
export function UnavailableAction() {
  const { t } = useTranslation()
  return <div className="ff-unavailable-action"><Button disabled>{t('agent.applyCanonical')}</Button><p>{t('agent.canonicalUnavailable')}</p><details><summary>{t('agent.technicalDetails')}</summary><code>No generic application gateway contract · CANONICAL_SEMANTIC rejected</code></details></div>
}
function InspectorProperties() {
  const selection = useSelection(), store = useInteractionStore()
  const { t } = useTranslation()
  const selected = selection.primarySelectedObject
  if (!selected) return null
  return <>
    <h3>{selected.kind === 'NODE' ? t('agent.localNodeInspector') : t('agent.presentationSelection')}</h3>
    <p>{selected.synthetic ? t('agent.syntheticFixture') : t('agent.localPresentationLifetime')}</p>
    {selected.kind !== 'LANE' ? <PropertyRow label={t('agent.selectedObject')}>{selection.selectedObjects.length > 1 ? t('agent.selectedPrimary', { count: selection.selectedObjects.length, kind: t(`agent.kind${selected.kind}`), title: selected.title || t('common.untitled') }) : selected.title || t('common.untitled')}</PropertyRow> : null}
    {selection.selectedObjects.length > 1 ? <p>{t('agent.primaryOnly')}</p> : null}
    {selection.selectedObjects.length > 1 ? <PropertyRow label={t('agent.selectedTargets')}>{selection.selectedObjects.map(object => object.title).join(', ')}</PropertyRow> : null}
    {selected.kind === 'LANE' ? <PropertyRow label={t('agent.selectedTrack')}>{selected.title}</PropertyRow> : null}
    {selection.supported.includes('rename') && selected.kind !== 'LANE' ? selection.surfaceId === 'workflow' ? <WorkflowTitleField /> : <label>{t('agent.localTitle')}<Input maxLength={120} value={selected.title} onChange={event => store.dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'rename', targetId: selected.id, title: event.target.value }, 'INSPECTOR')} /></label> : null}
    {selected.kind === 'NODE' ? <><PropertyRow label={t('agent.reference')}><code>{selected.reference ?? t('canvas.noReference')}</code></PropertyRow>{selection.surfaceId === 'workflow' ? <WorkflowPlacementFields /> : <>{(['x', 'y'] as const).map(axis => <label key={axis}>{t('agent.localAxis', { axis: axis.toUpperCase() })}<Input type="number" min={-2000} max={2000} value={selected[axis] ?? 0} onChange={event => {
      if (event.target.value === '' || !Number.isFinite(event.target.valueAsNumber)) return
      store.dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'move', targetId: selected.id, x: axis === 'x' ? event.target.valueAsNumber : selected.x ?? 0, y: axis === 'y' ? event.target.valueAsNumber : selected.y ?? 0 }, 'INSPECTOR')
    }} /></label>)}<p>{t('agent.placementRange')}</p></>}</> : null}
    {selection.time ? <><PropertyRow label={t('agent.simulationStep')}>{selection.time.playhead} / 20</PropertyRow><p>{t('agent.simulatedTimeHelp')}</p></> : null}
    {selection.supported.includes('reveal') ? <Button onClick={() => store.dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'reveal', targetId: selected.id }, 'INSPECTOR')}>{selected.kind === 'NODE' ? t('agent.showPrimaryNode') : t('agent.revealPrimary', { kind: t(`agent.kindLower${selected.kind}`) })}</Button> : null}
    {selected.kind === 'LANE' ? <Button onClick={() => store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] }, 'INSPECTOR')}>{t('agent.clearTrackSelection')}</Button> : null}
  </>
}
export function SelectionInspector() {
  const store = useInteractionStore(), selection = useSelection()
  const { t } = useTranslation()
  const id = selection.primarySelectedObject?.id
  const [mobileOpen, setMobileOpen] = useState<{ store: typeof store; lifetime: object; id: string } | null>(null)
  const currentMobile = mobileOpen && mobileOpen.store === store && mobileOpen.lifetime === selection.lifetime && mobileOpen.id === id && selection.inspectorOpen ? mobileOpen : null
  // Discard the occurrence, including while this component renders no inspector.
  if (mobileOpen && !currentMobile) setMobileOpen(null)
  const committedMobile = useRef(currentMobile), hadMobile = useRef(false)
  useLayoutEffect(() => {
    committedMobile.current = currentMobile
    return () => { committedMobile.current = null }
  }, [currentMobile])
  useLayoutEffect(() => {
    if (!currentMobile) return
    return store.subscribe(() => {
      const current = store.getSnapshot()
      // Observe intermediate clear/hide states even when React batches a reselect.
      if (current.lifetime !== currentMobile.lifetime || current.primarySelectedObject?.id !== currentMobile.id || !current.inspectorOpen) {
        committedMobile.current = null
        setMobileOpen(null)
      }
    })
  }, [store, currentMobile])
  useEffect(() => {
    if (hadMobile.current && !currentMobile && document.activeElement === document.body) document.getElementById('main-content')?.focus()
    hadMobile.current = Boolean(currentMobile)
  }, [currentMobile])
  const ownsSelection = () => {
    const current = store.getSnapshot()
    return current.lifetime === selection.lifetime && current.revision === selection.revision && current.primarySelectedObject?.id === id && current.inspectorOpen
  }
  const closeMobile = (hide = false) => {
    if (!ownsSelection() || !currentMobile || committedMobile.current !== currentMobile) return
    committedMobile.current = null
    setMobileOpen(null)
    if (hide) store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'inspect', open: false }, 'INSPECTOR')
  }
  if (!selection.primarySelectedObject || !selection.inspectorOpen) return null
  if (selection.surfaceId === 'workflow') return <WorkflowSelectionInspector />
  return <aside id="selection-inspector" className="ff-selection-inspector" aria-label={t('agent.selectionInspector')}>
    <div className="ff-desktop-inspector"><InspectorProperties /></div>
    <Button className="ff-mobile-inspector-launcher" onClick={() => { if (ownsSelection() && id) setMobileOpen({ store, lifetime: selection.lifetime, id }) }}>{t('agent.openSelectionProperties')}</Button>
    {currentMobile ? <InteractionDialog title={t('agent.selectionProperties')} onClose={() => closeMobile()}><InspectorProperties /><Button onClick={() => closeMobile(true)}>{t('agent.hideInspector')}</Button></InteractionDialog> : null}
  </aside>
}
function WorkflowSelectionInspector() {
  const store = useInteractionStore(), selection = useSelection()
  const { t } = useTranslation()
  const id = selection.primarySelectedObject?.id
  const fresh = () => ({ store, lifetime: selection.lifetime, id, open: false })
  const [owned, setOwned] = useState(fresh)
  const changed = owned.store !== store || owned.lifetime !== selection.lifetime || owned.id !== id
  const current = changed ? fresh() : owned
  if (changed) setOwned(current)
  const setOpen = (open: boolean) => {
    const snapshot = store.getSnapshot()
    if (snapshot.lifetime !== selection.lifetime || snapshot.primarySelectedObject?.id !== id) return
    setOwned({ ...current, open })
  }
  return <aside id="selection-inspector" className="ff-selection-inspector" aria-label={t('agent.selectionInspector')}>
    <Button onClick={() => setOpen(true)}>{t('workflow.check')}</Button>
    {current.open ? <InteractionDialog title={t('agent.selectionProperties')} closeLabel={t('workflow.closeProperties')} onClose={() => setOpen(false)}><InspectorProperties /></InteractionDialog>
      : <div className="ff-desktop-inspector"><InspectorProperties /></div>}
  </aside>
}
export function AgentLauncher() {
  const store = useInteractionStore()
  const { t } = useTranslation()
  return <Button className="ff-agent-launcher" onClick={() => store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'agent', open: true })}>{t('agent.ask')}</Button>
}
export function AgentShell() {
  const selection = useSelection()
  if (!selection.agentOpen) return null
  return <AgentConversation />
}
function AgentConversation() {
  const store = useInteractionStore(), selection = useSelection()
  const { t } = useTranslation()
  const [intent, setIntent] = useState(selection.revisionPair ? 'What changed between these revisions?' : selection.primarySelectedObject ? 'inspect' : '')
  const [proposal, setProposal] = useState<SyntheticProposal | null>(null)
  const [result, setResult] = useState('')
  const [expanded, setExpanded] = useState(false)
  const input = useRef<HTMLTextAreaElement>(null)
  const stale = proposal && (proposal.revision !== selection.revision || proposal.lifetime !== selection.lifetime)
  const localActionLabel = proposal?.action?.category === 'LOCAL_EPHEMERAL' ? t('agent.openInspector')
    : proposal?.action?.category === 'WORKSPACE_PRESENTATION' ? proposal.action.type === 'rename' ? t('agent.applyLocal') : t('agent.revealLocal')
      : null
  const selectionDescription = selection.selectedObjects.length > 1 ? `${t('agent.selectedPrimary', { count: selection.selectedObjects.length, kind: t(`agent.kind${selection.primarySelectedObject!.kind}`), title: selection.primarySelectedObject!.title || t('common.untitled') })} · ${selection.selectedObjects.map(object => object.title || t('common.untitled')).join(', ')}` : selection.primarySelectedObject ? t('agent.singleSelection', { kind: t(`agent.kind${selection.primarySelectedObject.kind}`), title: selection.primarySelectedObject.title || t('common.untitled') }) : t('agent.none')
  return <InteractionDialog title={t('agent.title')} closeLabel={t('agent.close')} layout="agent" className={`ff-agent-conversation${expanded ? ' ff-agent-conversation--expanded' : ''}`} onClose={() => store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'agent', open: false })}>
    <div className="ff-agent-scroll-region" role="region" aria-label={t('agent.body')}>
    <div className="ff-agent-context" aria-label={t('agent.context')} data-testid="agent-context"><span>{t('agent.project', { project: selection.projectId ?? t('agent.noProject') })}</span><span>{t('agent.surface', { surface: selection.surfaceId })}</span><span>{t('agent.selection', { selection: selectionDescription })}</span>{selection.time ? <span>{t('agent.time', { time: t('agent.simulatedTime', { count: selection.time.playhead, range: selection.time.range ? t('agent.simulatedRange', { range: selection.time.range.join('–') }) : '' }) })}</span> : null}{selection.revisionPair ? <span>{t('agent.revisionPair', { pair: `${selection.revisionPair.from} → ${selection.revisionPair.to}` })}</span> : null}</div>
    <p className="ff-synthetic-note">{t('agent.demonstration')}</p>
    <Button className="ff-agent-expand" aria-pressed={expanded} onClick={() => setExpanded(value => !value)}>{expanded ? t('agent.useCompact') : t('agent.expand')}</Button>
    <details className="ff-agent-diagnostics"><summary>{t('agent.details')}</summary><div className="ff-evidence-flags"><code>AGENT_PROPOSAL_UI_IS_SYNTHETIC=YES</code><code>BACKEND_OPERATIONPLAN_INTEGRATION_EVIDENCE=NO</code><code>CANONICAL_MUTATION_EVIDENCE=NO</code><code>REAL_AUTHORIZATION_EVIDENCE=NO</code></div></details>
    {proposal ? <section aria-label={t('agent.proposal')} data-testid="agent-proposal"><p className="ff-synthetic-note">{t('agent.syntheticDemo')}</p><h3>{t('agent.understoodRequest')}</h3><p>{proposal.intent || t('agent.noRequest')}</p><h3>{t('agent.targets')}</h3><p>{proposal.target}</p><h3>{t('agent.proposedChange')}</h3><p>{proposal.description}</p>{proposal.action ? <p className="ff-local-action-copy">{proposal.action.category === 'READ_ONLY_QUERY' ? t('agent.readOnlyAvailable') : t('agent.localActionAvailable')}</p> : <p className="ff-unavailable-copy">{t('agent.proposalUnavailable')}</p>}
      {stale ? <p role="alert">{t('agent.contextChanged')}</p> : null}
      <div className="ff-agent-proposal-actions"><Button onClick={() => { setProposal(null); setResult(''); input.current?.focus() }}>{t('agent.modify')}</Button>{proposal.action?.category === 'READ_ONLY_QUERY' ? <Button disabled={Boolean(stale)} onClick={() => { const next = applyProposal(store, proposal); setResult(t(next.ok ? 'agent.localResult' : 'agent.requestUnavailable')) }}>{t('agent.runReadOnly')}</Button> : null}{localActionLabel ? <Button disabled={Boolean(stale)} onClick={() => { const next = applyProposal(store, proposal); setResult(t(next.ok ? 'agent.localResult' : 'agent.requestUnavailable')); if (next.ok && proposal.action?.type === 'inspect') store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'agent', open: false }, 'AGENT') }}>{localActionLabel}</Button> : null}<Button disabled aria-describedby="proposal-apply-unavailable">{t('agent.apply')}</Button></div><p id="proposal-apply-unavailable" className="ff-visually-hidden">{t('agent.applyUnavailableA11y')}</p>
      <details className="ff-agent-diagnostics"><summary>{t('agent.proposalDiagnostics')}</summary><p><code>SYNTHETIC_EFFECTIVE_ACTION_PROJECTION</code></p><p>This projection is demonstration only, never an execution credential.</p><p>Local presentation and read-only query paths remain distinct; this semantic Apply has no backend OperationPlan, authorization, preview, or canonical mutation integration.</p></details>
    </section> : null}
    {result ? <p role="status">{result}</p> : null}
    <UnavailableAction />
    </div>
    <form className="ff-agent-composer" aria-label={t('agent.composer')} onSubmit={event => { event.preventDefault()
      const snapshot = store.getSnapshot()
      const next = createProposal(snapshot, intent)
      const rename = /^rename to\s+([\s\S]*)$/i.exec(intent.trimStart())
      const invalidTitle = snapshot.surfaceId === 'workflow' && rename && !isWorkflowSketchTitleValid(rename[1])
      setProposal(invalidTitle ? { ...next, action: null, description: t('workflow.titleError') } : next)
      setResult('') }}>
      <label>{t('agent.userIntent')}<textarea ref={input} data-dialog-entry value={intent} onChange={event => { setIntent(event.target.value); setProposal(null); setResult('') }} placeholder={t('agent.intentPlaceholder')} /></label>
      <Button type="submit" disabled={!intent.trim()}>{t('agent.preview')}</Button>
    </form>
  </InteractionDialog>
}
export function useSelectionCommands(): PaletteAction[] {
  const selection = useSelection(), store = useInteractionStore()
  const { t } = useTranslation()
  const selected = selection.primarySelectedObject
  const command = (id: string, label: string, action: ProductAction): PaletteAction => ({ id, label, onSelect: () => {
    // A retained projection must not apply its captured target to a newer context.
    const current = store.getSnapshot()
    if (current.lifetime !== selection.lifetime || current.revision !== selection.revision) return
    store.dispatch(action, 'COMMAND' satisfies InvocationSource)
  } })
  const pluralTarget = selection.selectedObjects.every(object => object.kind === 'CLIP') ? 'agent.selectedClips' : 'agent.selectedObjects'
  const labelTarget = selection.selectedObjects.length > 1 ? t(pluralTarget, { count: selection.selectedObjects.length }) : `${selected ? t(`agent.kindLower${selected.kind}`) : ''}: ${selected?.title}`
  const actions: PaletteAction[] = selected ? [command('selection.inspect', t('agent.commandInspect', { target: labelTarget }), { category: 'LOCAL_EPHEMERAL', type: 'inspect', open: true }), ...(selection.supported.includes('reveal') ? [command('selection.reveal', t('agent.commandReveal', { target: `${t(`agent.kindLower${selected.kind}`)}: ${selected.title}` }), { category: 'WORKSPACE_PRESENTATION', type: 'reveal', targetId: selected.id })] : []), command('selection.clear', t('agent.clearSelection'), { category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] })] : []
  if (selection.selectedObjects.length > 1 && selection.selectedObjects.every(object => object.kind === 'CLIP' && object.synthetic)) actions.push(...[{ id: 'selection.adjust-audio', key: 'agent.adjustAudio' }, { id: 'selection.duplicate', key: 'agent.duplicate' }].map(({ id, key }) => ({ id, label: t(key), disabledReason: t('agent.fixtureUnavailable'), onSelect: () => {} })))
  if (selection.surfaceId === 'canvas') {
    if (selection.supported.includes('undo-local')) actions.push(command('canvas.undo-local', t('canvas.undoLocal'), { category: 'WORKSPACE_PRESENTATION', type: 'undo-local' }))
    if (selection.supported.includes('redo-local')) actions.push(command('canvas.redo-local', t('canvas.redoLocal'), { category: 'WORKSPACE_PRESENTATION', type: 'redo-local' }))
  }
  if (selection.revisionPair) actions.unshift(command('review.compare', t('agent.commandCompare'), { category: 'READ_ONLY_QUERY', type: 'compare-revisions', pair: selection.revisionPair }))
  actions.push(command('agent.open', t('agent.commandAsk', { target: selection.selectedObjects.length > 1 ? t(pluralTarget, { count: selection.selectedObjects.length }) : selected ? selected.title : `${selection.projectId ?? t('shell.workspace')} · ${selection.surfaceId}` }), { category: 'LOCAL_EPHEMERAL', type: 'agent', open: true }))
  return actions
}
