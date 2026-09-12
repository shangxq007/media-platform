import { useId, useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react'
import { flushSync } from 'react-dom'
import { Badge, Button, EmptyState, Input, Panel } from '../../components/design-system'
import { InteractionDialog } from '../../interaction/InteractionDialog'
import { useInteractionStore, useSelection, useSurfaceAdapter } from '../../interaction/SelectionContext'
import type { PresentationAction, QueryAction, SelectedObject } from '../../interaction/model'
import { useTranslation } from '../../localization'
import {
  WORKFLOW_NODE_CATEGORIES,
  WORKFLOW_SKETCH_BOUNDS,
  WORKFLOW_SKETCH_LIMIT,
  addWorkflowSketchNode,
  createWorkflowSketch,
  moveWorkflowSketchNode,
  isWorkflowSketchTitleValid,
  removeWorkflowSketchNode,
  renameWorkflowSketchNode,
  resetWorkflowSketch,
  type WorkflowNodeCategory,
  type WorkflowSketchState,
} from './model'

const MOVE_STEP = 24
type Removal = { id: string; title: string; revision: number } | null
const SUPPORTED_ACTIONS = ['rename', 'move', 'reveal'] as const

export function WorkflowSketch() {
  const { t } = useTranslation()
  const store = useInteractionStore()
  const selection = useSelection()
  const [owned, setOwned] = useState(() => ({ store, lifetime: selection.lifetime, sketch: createWorkflowSketch(), confirmingReset: null as object | null, removal: null as Removal }))
  const ownerChanged = owned.store !== store || owned.lifetime !== selection.lifetime
  const current = ownerChanged ? { store, lifetime: selection.lifetime, sketch: createWorkflowSketch(), confirmingReset: null as object | null, removal: null as Removal } : owned
  if (ownerChanged) setOwned(current)
  const { sketch, confirmingReset } = current
  const removal = current.removal?.revision === selection.revision ? current.removal : null
  if (current.removal && !removal) setOwned({ ...current, removal: null })
  const committedDialogs = useRef<{ removal: Removal; reset: object | null }>({ removal: null, reset: null })
  useLayoutEffect(() => {
    committedDialogs.current = { removal, reset: confirmingReset }
    return () => { committedDialogs.current = { removal: null, reset: null } }
  }, [removal, confirmingReset])
  const alive = useRef(false)
  useLayoutEffect(() => { alive.current = true; return () => { alive.current = false } }, [])
  const owns = () => alive.current && store.getSnapshot().lifetime === selection.lifetime && store.getSnapshot().supported.includes('rename')
  const paletteRef = useRef<HTMLDivElement>(null)
  const boardRef = useRef<HTMLDivElement>(null)
  const nodeRefs = useRef(new Map<string, HTMLButtonElement>())

  const updateSketch = (change: (current: WorkflowSketchState) => WorkflowSketchState) => {
    if (!owns()) return
    setOwned(current => current.store === store && current.lifetime === selection.lifetime
      ? { ...current, sketch: change(current.sketch) }
      : current)
  }
  const setResetConfirmation = (confirming: boolean) => {
    if (!owns() || (!confirming && committedDialogs.current.reset !== confirmingReset)) return
    setOwned(currentOwned => currentOwned.store === store && currentOwned.lifetime === selection.lifetime
      ? { ...currentOwned, confirmingReset: confirming ? {} : null }
      : currentOwned)
  }
  const objects = (): readonly SelectedObject[] => sketch.nodes.map(node => ({
    id: node.id,
    kind: 'NODE',
    title: node.title,
    x: node.x,
    y: node.y,
    reference: node.category,
  }))
  const handle = (action: PresentationAction | QueryAction) => {
    if (!owns()) return false
    if (action.category !== 'WORKSPACE_PRESENTATION') return false
    if (action.type === 'undo-local' || action.type === 'redo-local') return false
    const exists = sketch.nodes.some(node => node.id === action.targetId)
    if (!exists) return false
    if (action.type === 'rename' && !isWorkflowSketchTitleValid(action.title)) return false
    if (action.type === 'rename') updateSketch(current => renameWorkflowSketchNode(current, action.targetId, action.title))
    else if (action.type === 'move') updateSketch(current => moveWorkflowSketchNode(current, action.targetId, action.x, action.y))
    else if (action.type === 'reveal') nodeRefs.current.get(action.targetId)?.scrollIntoView({ block: 'nearest', inline: 'nearest' })
    else return false
    return true
  }
  useSurfaceAdapter({ objects, supports: SUPPORTED_ACTIONS, handle })

  const selectedNode = selection.primarySelectedObject?.kind === 'NODE'
    ? sketch.nodes.find(node => node.id === selection.primarySelectedObject?.id)
    : undefined
  const selectNode = (id: string) => owns() && store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [id], primaryId: id }, 'DIRECT')
  const addNode = (category: WorkflowNodeCategory, initiator: HTMLButtonElement) => {
    if (!owns()) return
    const next = addWorkflowSketchNode(sketch, category)
    if (next === sketch) return
    if (next.nodes.length === WORKFLOW_SKETCH_LIMIT && document.activeElement === initiator) boardRef.current?.focus()
    updateSketch(value => addWorkflowSketchNode(value, category))
  }
  const moveSelected = (event: KeyboardEvent<HTMLButtonElement>, id: string, x: number, y: number) => {
    if (!owns() || selection.primarySelectedObject?.id !== id || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return
    const delta = event.key === 'ArrowLeft' ? [-MOVE_STEP, 0]
      : event.key === 'ArrowRight' ? [MOVE_STEP, 0]
        : event.key === 'ArrowUp' ? [0, -MOVE_STEP]
          : event.key === 'ArrowDown' ? [0, MOVE_STEP]
            : null
    if (!delta) return
    event.preventDefault()
    store.dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'move', targetId: id, x: x + delta[0], y: y + delta[1] }, 'DIRECT')
  }
  const onNodeKeyDown = (event: KeyboardEvent<HTMLButtonElement>, id: string, x: number, y: number) => {
    const activates = event.key === 'Enter' || event.key === ' '
    if (event.nativeEvent.isComposing || event.keyCode === 229) {
      if (activates) event.preventDefault()
      return
    }
    if (activates) {
      event.preventDefault()
      selectNode(id)
      return
    }
    moveSelected(event, id, x, y)
  }
  const setRemoval = (next: Removal) => {
    if (!owns() || (!next && committedDialogs.current.removal !== removal)) return
    setOwned(value => value.store === store && value.lifetime === selection.lifetime ? { ...value, removal: next } : value)
  }
  const removeSelected = () => {
    if (!removal || committedDialogs.current.removal !== removal || !owns() || store.getSnapshot().revision !== removal.revision) return
    const remaining = sketch.nodes.find(node => node.id !== removal.id)
    flushSync(() => {
      store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] }, 'DIRECT')
      updateSketch(value => removeWorkflowSketchNode(value, removal.id))
      setRemoval(null)
    })
    const fallback = remaining ? nodeRefs.current.get(remaining.id) : paletteRef.current?.querySelector<HTMLButtonElement>('button:not(:disabled)')
    fallback?.focus()
    fallback?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
  }
  const discardSketch = (initiator: HTMLButtonElement) => {
    if (!owns() || !confirmingReset || committedDialogs.current.reset !== confirmingReset) return
    const transferFocus = document.activeElement === initiator
    store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] }, 'DIRECT')
    flushSync(() => {
      updateSketch(resetWorkflowSketch)
      setResetConfirmation(false)
    })
    if (transferFocus) boardRef.current?.focus()
  }
  useLayoutEffect(() => {
    if (selectedNode) nodeRefs.current.get(selectedNode.id)?.scrollIntoView?.({ block: 'nearest', inline: 'nearest' })
  }, [selectedNode])
  const atLimit = sketch.nodes.length >= WORKFLOW_SKETCH_LIMIT

  return <section className="ff-workflow-sketch" aria-label={t('workflow.region')}>
    <Panel title={t('workflow.title')}>
      <p className="ff-workflow-boundary">{t('workflow.boundary')}</p>
      <div ref={paletteRef} className="ff-workflow-palette" aria-label={t('workflow.palette')}>
        {WORKFLOW_NODE_CATEGORIES.map(category => <div key={category} className="ff-workflow-category"><Button disabled={atLimit} aria-describedby={`workflow-category-${category}`} onClick={event => addNode(category, event.currentTarget)}>{t('workflow.addCard', { category })}</Button><p id={`workflow-category-${category}`}>{t(`workflow.category${category}`)}</p></div>)}
      </div>
      <div className="ff-workflow-status-row">
        <p role="status">{t('workflow.count', { count: sketch.nodes.length, limit: WORKFLOW_SKETCH_LIMIT })}</p>
        {sketch.nodes.length ? <div className="ff-workflow-local-actions">
          {selectedNode ? <Button onClick={() => setRemoval({ id: selectedNode.id, title: selectedNode.title, revision: selection.revision })}>{t('workflow.removeSelected')}</Button> : null}
          <Button onClick={() => setResetConfirmation(true)}>{t('workflow.reset')}</Button>
        </div> : null}
      </div>
      {atLimit ? <p role="alert" className="ff-workflow-limit">{t('workflow.limitReached', { limit: WORKFLOW_SKETCH_LIMIT })}</p> : null}
      <p id="workflow-sketch-bounds" className="ff-workflow-bounds">{t('workflow.bounds', {
        minX: WORKFLOW_SKETCH_BOUNDS.minX,
        maxX: WORKFLOW_SKETCH_BOUNDS.maxX,
        minY: WORKFLOW_SKETCH_BOUNDS.minY,
        maxY: WORKFLOW_SKETCH_BOUNDS.maxY,
      })}</p>
      <p>{t('workflow.selectHelp')}</p>
      <div ref={boardRef} className="ff-workflow-board" role="region" aria-label={t('workflow.board')} aria-describedby="workflow-sketch-bounds" tabIndex={0}>
        {sketch.nodes.length ? <div className="ff-workflow-board-plane">
          {sketch.nodes.map(node => {
            const selected = selection.selectedObjects.some(object => object.id === node.id)
            return <button
              key={node.id}
              ref={element => {
                if (element) nodeRefs.current.set(node.id, element)
                else nodeRefs.current.delete(node.id)
              }}
              type="button"
              className="ff-workflow-node"
              style={{ '--workflow-node-x': `${node.x}px`, '--workflow-node-y': `${node.y}px` } as CSSProperties}
              aria-label={node.title || t('common.untitled')}
              aria-pressed={selected}
              data-testid="workflow-sketch-node"
              onClick={() => selectNode(node.id)}
              onKeyDown={event => onNodeKeyDown(event, node.id, node.x, node.y)}
            >
              <Badge>{node.category}</Badge>
              <strong>{node.title || t('common.untitled')}</strong>
              <span>{t('workflow.position', { x: node.x, y: node.y })}</span>
            </button>
          })}
        </div> : <EmptyState title={t('workflow.emptyTitle')} description={t('workflow.emptyBody')} />}
      </div>
      {removal ? <InteractionDialog title={t('workflow.removeTitle')} closeLabel={t('workflow.closeRemove')} onClose={() => setRemoval(null)}>
        <p>{t('workflow.removeBody', { title: removal.title })}</p>
        <div className="ff-workflow-dialog-actions">
          <Button data-dialog-entry onClick={() => setRemoval(null)}>{t('workflow.keepCard')}</Button>
          <Button variant="danger" onClick={removeSelected}>{t('workflow.removeCard')}</Button>
        </div>
      </InteractionDialog> : null}
      {confirmingReset ? <InteractionDialog title={t('workflow.discardTitle')} closeLabel={t('workflow.closeDiscard')} onClose={() => setResetConfirmation(false)}>
        <p>{t('workflow.discardBody')}</p>
        <div className="ff-workflow-dialog-actions">
          <Button data-dialog-entry onClick={() => setResetConfirmation(false)}>{t('workflow.keep')}</Button>
          <Button variant="danger" onClick={event => discardSketch(event.currentTarget)}>{t('workflow.discard')}</Button>
        </div>
      </InteractionDialog> : null}
    </Panel>
  </section>
}

// Rendered only inside the shared Selection inspector; draft text is not selection authority.
export function WorkflowTitleField() {
  const store = useInteractionStore(), selection = useSelection()
  const selected = selection.primarySelectedObject!
  const { t } = useTranslation()
  const errorId = useId()
  const fresh = () => ({ store, lifetime: selection.lifetime, id: selected.id, base: selected.title, value: selected.title })
  const [draft, setDraft] = useState(fresh)
  const changed = draft.store !== store || draft.lifetime !== selection.lifetime || draft.id !== selected.id || draft.base !== selected.title
  const current = changed ? fresh() : draft
  if (changed) setDraft(current)
  const invalid = !isWorkflowSketchTitleValid(current.value)
  return <>
    <label>{t('agent.localTitle')}<Input value={current.value} aria-invalid={invalid} aria-describedby={invalid ? errorId : undefined} onChange={event => {
      const snapshot = store.getSnapshot()
      if (snapshot.lifetime !== selection.lifetime || snapshot.revision !== selection.revision) return
      const value = event.target.value
      setDraft({ ...current, value, base: isWorkflowSketchTitleValid(value) ? value : selected.title })
      if (isWorkflowSketchTitleValid(value)) store.dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'rename', targetId: selected.id, title: value }, 'INSPECTOR')
    }} /></label>
    {invalid ? <p id={errorId} role="alert">{t('workflow.titleError')}</p> : null}
  </>
}

export function WorkflowPlacementFields() {
  const store = useInteractionStore(), selection = useSelection()
  const selected = selection.primarySelectedObject!
  const { t } = useTranslation()
  const x = selected.x ?? 0, y = selected.y ?? 0
  const move = (nextX: number, nextY: number) => {
    const snapshot = store.getSnapshot()
    if (snapshot.lifetime !== selection.lifetime || snapshot.revision !== selection.revision) return
    store.dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'move', targetId: selected.id, x: nextX, y: nextY }, 'INSPECTOR')
  }
  const edge = x === WORKFLOW_SKETCH_BOUNDS.minX || x === WORKFLOW_SKETCH_BOUNDS.maxX || y === WORKFLOW_SKETCH_BOUNDS.minY || y === WORKFLOW_SKETCH_BOUNDS.maxY
  return <>
    {(['x', 'y'] as const).map(axis => <label key={axis}>{t('agent.localAxis', { axis: axis.toUpperCase() })}<Input type="number" min={axis === 'x' ? WORKFLOW_SKETCH_BOUNDS.minX : WORKFLOW_SKETCH_BOUNDS.minY} max={axis === 'x' ? WORKFLOW_SKETCH_BOUNDS.maxX : WORKFLOW_SKETCH_BOUNDS.maxY} value={axis === 'x' ? x : y} onChange={event => {
      if (event.target.value === '' || !Number.isFinite(event.target.valueAsNumber)) return
      move(axis === 'x' ? event.target.valueAsNumber : x, axis === 'y' ? event.target.valueAsNumber : y)
    }} /></label>)}
    <div className="ff-workflow-local-actions" role="group" aria-label={t('workflow.placement')}>
      <Button onClick={() => move(x - MOVE_STEP, y)}>{t('workflow.left')}</Button>
      <Button onClick={() => move(x + MOVE_STEP, y)}>{t('workflow.right')}</Button>
      <Button onClick={() => move(x, y - MOVE_STEP)}>{t('workflow.up')}</Button>
      <Button onClick={() => move(x, y + MOVE_STEP)}>{t('workflow.down')}</Button>
    </div>
    <p>{t('workflow.moveHelp')}</p>
    {edge ? <p role="status">{t('workflow.edge')}</p> : null}
  </>
}
