import { useCallback, useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type MouseEvent, type PointerEvent } from 'react'
import { flushSync } from 'react-dom'
import { Badge, Button } from '../../components/design-system'
import { useProjectContext } from '../../foundation/projectContext'
import { PageHeading, ProjectFrame } from '../../surfaces/FoundationPages'
import { SelectionProvider, useInteractionStore, useSelection, useSurfaceAdapter } from '../../interaction/SelectionContext'
import type { InteractionStore, SelectionState, PresentationSelectionRef } from '../../interaction/model'
import { SelectionActionBar } from '../../interaction/InteractionShell'
import { emptyCanvasHistory, recordCanvasEdit, restoreCanvasEdit, canvasMarqueeRefs, isMarqueeArea, CANVAS_NODE_SIZE, CANVAS_GESTURE_THRESHOLD, screenToCanvas, moveCanvasGroup, type CanvasPoint, type CanvasNode, type WorkspaceCanvasState, changeZoom, fitCanvasViewport, createCanvasState, moveViewport, placeCanvasNode, renameCanvasNode, resetViewport } from './model'
import { useTranslation } from '../../localization'

interface CanvasGesture {
  kind: 'drag' | 'marquee' | 'pan'
  nodeId?: string
  union: boolean
  pointerId: number
  capture: HTMLDivElement
  owner: InteractionStore
  context: SelectionState
  refs: readonly PresentationSelectionRef[]
  originals: readonly CanvasNode[]
  canvas: WorkspaceCanvasState
  startScreen: CanvasPoint
  startLocal: CanvasPoint
  origin: CanvasPoint
  outerBounds: DOMRect
  moved: boolean
}

function drawingOrigin(element: HTMLDivElement, bounds: DOMRect): CanvasPoint {
  // Absolute viewport and marquee coordinates begin inside the stage border.
  return { x: bounds.left + element.clientLeft, y: bounds.top + element.clientTop }
}

function gestureBoundsCurrent(active: CanvasGesture, element: HTMLDivElement | null) {
  if (!element) return false
  const bounds = element.getBoundingClientRect(), origin = drawingOrigin(element, bounds)
  return bounds.left === active.outerBounds.left && bounds.top === active.outerBounds.top
    && bounds.width === active.outerBounds.width && bounds.height === active.outerBounds.height
    && origin.x === active.origin.x && origin.y === active.origin.y
}

export function CanvasContent() {
  const project = useProjectContext()
  return <SelectionProvider scope={{ surfaceId: 'canvas', workspaceId: project.workspaceId, projectId: project.projectId }}><CanvasSession key={JSON.stringify([project.workspaceId, project.projectId])} /></SelectionProvider>
}

function CanvasSession() {
  const project = useProjectContext()
  const { t } = useTranslation()
  const [canvas, updateCanvas] = useState(() => createCanvasState(project.projectId))
  const canvasRef = useRef(canvas)
  const history = useRef(emptyCanvasHistory())
  const historyOwner = useRef<object | null>(null)
  const [, refreshHistory] = useState(0)
  const gesture = useRef<CanvasGesture | null>(null)
  const [previewNodes, setPreviewNodes] = useState<readonly CanvasNode[] | null>(null)
  const [previewPoint, setPreviewPoint] = useState<CanvasPoint | null>(null)
  const [mode, setMode] = useState<'select' | 'pan'>('select')
  const suppressClick = useRef(false)
  const completedNodeClick = useRef<(Pick<CanvasGesture, 'owner' | 'context' | 'pointerId'> & { nodeId: string }) | null>(null)
  const cancelGesture = useCallback(() => {
    completedNodeClick.current = null
    const active = gesture.current
    gesture.current = null
    setPreviewNodes(null)
    setPreviewPoint(null)
    if (!active) return
    suppressClick.current = true
    // Release can race native cancellation or element removal. Preview is already discarded.
    try { active.capture.releasePointerCapture(active.pointerId) } catch { /* capture already lost */ }
  }, [])
  const store = useInteractionStore(), selection = useSelection()
  const setCanvas = (update: (current: WorkspaceCanvasState) => WorkspaceCanvasState): boolean => {
    const owner = store.getSnapshot(), current = canvasRef.current
    if (historyOwner.current !== owner.lifetime) return false
    const next = update(current)
    if (next !== current) cancelGesture()
    // Capture release may synchronously replace either ownership or the local projection.
    if (historyOwner.current !== owner.lifetime || store.getSnapshot().revision !== owner.revision || canvasRef.current !== current) return false
    history.current = recordCanvasEdit(history.current, current, next)
    canvasRef.current = next
    updateCanvas(next)
    return true
  }
  const [multiSelect, setMultiSelect] = useState(false)
  const selectNode = (localId: string, toggle: boolean) => {
    const current = store.getSnapshot()
    store.select({ mode: toggle ? 'toggle' : 'replace', refs: [{ workspaceId: current.workspaceId, projectId: current.projectId, surfaceId: current.surfaceId, kind: 'NODE', localId }], lifetime: current.lifetime, revision: current.revision })
  }
  const stage = useRef<HTMLDivElement>(null)
  const viewport = useRef<HTMLDivElement>(null)
  useLayoutEffect(() => {
    history.current = emptyCanvasHistory()
    historyOwner.current = store.getSnapshot().lifetime
    refreshHistory(value => value + 1)
    const unsubscribe = store.subscribe(() => {
      const active = gesture.current, current = store.getSnapshot()
      if (historyOwner.current && historyOwner.current !== current.lifetime) {
        historyOwner.current = null
        history.current = emptyCanvasHistory()
        cancelGesture()
        refreshHistory(value => value + 1)
      }
      if (active && (current.revision !== active.context.revision || current.lifetime !== active.context.lifetime)) cancelGesture()
    })
    return () => { historyOwner.current = null; history.current = emptyCanvasHistory(); unsubscribe(); cancelGesture() }
  }, [store, cancelGesture])

  const gestureContextCurrent = (active: CanvasGesture) => {
    // Reconcile the live adapter before every continuation, even before React has rendered.
    store.reconcile()
    const current = store.getSnapshot()
    return !(active.owner !== store || active.context.lifetime !== current.lifetime
      || active.context.revision !== current.revision || current.surfaceId !== 'canvas'
      || current.workspaceId !== project.workspaceId || current.projectId !== project.projectId
      || canvasRef.current !== active.canvas || !gestureBoundsCurrent(active, stage.current)
      || active.refs.some(ref => !current.selectedObjects.some(object => object.kind === 'NODE' && object.id === ref.localId
        && active.originals.some(node => node.presentationId === object.id && node.x === object.x && node.y === object.y))))
  }
  const liveGesture = (pointerId: number) => {
    const active = gesture.current
    if (!active || active.pointerId !== pointerId) return null
    if (!gestureContextCurrent(active) || gesture.current !== active) { cancelGesture(); return null }
    return active
  }
  const isEmptyTarget = (target: EventTarget) => target === stage.current || target === viewport.current
  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (gesture.current) return
    suppressClick.current = false
    completedNodeClick.current = null
    if (event.button !== 0 || event.isPrimary === false || event.pointerType === 'touch' || event.altKey || event.shiftKey) return
    const target = event.target as HTMLElement
    if (target.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"])')) return
    const nodeId = target.closest<HTMLElement>('[data-canvas-node]')?.dataset.canvasNode
    if (nodeId && (event.ctrlKey || event.metaKey || multiSelect)) return
    if (!nodeId && !isEmptyTarget(target)) return
    const kind = nodeId ? 'drag' : mode === 'select' ? 'marquee' : 'pan'
    store.reconcile()
    let context = store.getSnapshot()
    if (context.surfaceId !== 'canvas' || context.workspaceId !== project.workspaceId || context.projectId !== project.projectId) return
    if (nodeId && !context.selectedRefs.some(ref => ref.kind === 'NODE' && ref.localId === nodeId)) {
      // Commit the shared action bar/Inspector layout before measuring the gesture.
      flushSync(() => selectNode(nodeId, false))
      context = store.getSnapshot()
    }
    const refs = context.selectedRefs.filter(ref => ref.kind === 'NODE' && ref.surfaceId === 'canvas' && ref.workspaceId === project.workspaceId && ref.projectId === project.projectId)
    const current = canvasRef.current
    const originals = refs.flatMap(ref => current.nodes.filter(node => node.presentationId === ref.localId))
    if (kind === 'drag' && (!refs.length || originals.length !== refs.length || !refs.some(ref => ref.localId === nodeId))) return
    const bounds = event.currentTarget.getBoundingClientRect()
    const origin = drawingOrigin(event.currentTarget, bounds), startScreen = { x: event.clientX, y: event.clientY }
    const startLocal = screenToCanvas(startScreen, origin, { x: current.viewportX, y: current.viewportY }, current.zoom)
    const active: CanvasGesture = { kind, nodeId, union: event.ctrlKey || event.metaKey, pointerId: event.pointerId, capture: event.currentTarget, owner: store, context, refs, originals, canvas: current, startScreen, startLocal, origin, outerBounds: bounds, moved: false }
    gesture.current = active
    try { event.currentTarget.setPointerCapture(event.pointerId) } catch { cancelGesture(); return }
    // Focus remains visible; captured camera changes (including focus reveal) cancel the gesture.
    event.preventDefault()
    if (nodeId) target.closest<HTMLElement>('[data-canvas-node]')?.focus({ preventScroll: true }); else event.currentTarget.focus({ preventScroll: true })
  }
  const gestureDelta = (active: CanvasGesture, event: PointerEvent<HTMLDivElement>) => {
    const local = screenToCanvas({ x: event.clientX, y: event.clientY }, active.origin, { x: active.canvas.viewportX, y: active.canvas.viewportY }, active.canvas.zoom)
    if (Math.hypot(event.clientX - active.startScreen.x, event.clientY - active.startScreen.y) >= CANVAS_GESTURE_THRESHOLD) active.moved = true
    return { x: local.x - active.startLocal.x, y: local.y - active.startLocal.y }
  }
  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    const active = liveGesture(event.pointerId)
    if (!active) return
    const delta = gestureDelta(active, event)
    if (active.moved) {
      if (active.kind === 'drag') setPreviewNodes(moveCanvasGroup(active.canvas, active.originals, delta).nodes)
      else setPreviewPoint({ x: event.clientX, y: event.clientY })
    }
  }
  const onPointerUp = (event: PointerEvent<HTMLDivElement>) => {
    const active = liveGesture(event.pointerId)
    if (!active) return
    const delta = gestureDelta(active, event)
    cancelGesture()
    // Release may invoke synchronous listeners. Do not commit into a changed context.
    if (!gestureContextCurrent(active) || gesture.current) return
    suppressClick.current = active.moved
    if (active.kind === 'drag') {
      if (!active.moved) {
        if (active.nodeId) completedNodeClick.current = { owner: active.owner, context: active.context, pointerId: active.pointerId, nodeId: active.nodeId }
        return
      }
      setCanvas(current => moveCanvasGroup(current, active.originals, delta))
      store.reconcile()
    } else if (active.kind === 'pan' && active.moved) {
      setCanvas(current => moveViewport(current, event.clientX - active.startScreen.x, event.clientY - active.startScreen.y))
    } else {
      const end = { x: event.clientX, y: event.clientY }
      const refs = active.kind === 'marquee' && isMarqueeArea(active.startScreen, end)
        ? canvasMarqueeRefs(canvasRef.current.nodes, active.startLocal, { x: active.startLocal.x + delta.x, y: active.startLocal.y + delta.y }, active.context, active.union ? active.refs : [])
        : []
      // The accepted store resolves every scoped ref against its live adapter before publishing once.
      store.select({ mode: 'replace', refs, primary: active.union && refs.some(ref => ref.localId === active.context.primaryRef?.localId) ? active.context.primaryRef! : undefined, lifetime: active.context.lifetime, revision: active.context.revision })
      suppressClick.current = true
    }
  }
  const onClickCapture = (event: MouseEvent<HTMLDivElement>) => {
    const completed = completedNodeClick.current, suppressed = suppressClick.current
    // One completion owns at most its next click; keyboard activation never consumes pointer intent.
    completedNodeClick.current = null
    suppressClick.current = false
    if (event.detail === 0) return
    const samePointer = !('pointerId' in event.nativeEvent) || event.nativeEvent.pointerId === completed?.pointerId
    const sameTarget = isEmptyTarget(event.target) || (event.target as HTMLElement).closest<HTMLElement>('[data-canvas-node]')?.dataset.canvasNode === completed?.nodeId
    if (completed && samePointer && sameTarget) {
      event.preventDefault(); event.stopPropagation()
      const current = store.getSnapshot()
      // Capture can retarget the click to the stage. Preserve ordinary node activation,
      // including collapsing a group on a plain click, without reviving stale context.
      if (completed.owner === store && completed.context.lifetime === current.lifetime && completed.context.revision === current.revision) selectNode(completed.nodeId, false)
    } else if (suppressed) {
      event.preventDefault(); event.stopPropagation()
    }
  }
  const displayNodes = previewNodes ?? canvas.nodes
  const active = gesture.current
  const previewPan = active?.kind === 'pan' && previewPoint ? { x: previewPoint.x - active.startScreen.x, y: previewPoint.y - active.startScreen.y } : { x: 0, y: 0 }
  const marquee = active?.kind === 'marquee' && previewPoint && isMarqueeArea(active.startScreen, previewPoint) ? {
    left: Math.min(active.startScreen.x, previewPoint.x) - active.origin.x,
    top: Math.min(active.startScreen.y, previewPoint.y) - active.origin.y,
    width: Math.abs(previewPoint.x - active.startScreen.x), height: Math.abs(previewPoint.y - active.startScreen.y),
  } : null
  const selected = canvas.nodes.find(node => node.presentationId === selection.primarySelectedObject?.id)
  const pan = (x: number, y: number) => setCanvas(current => moveViewport(current, x, y))
  const zoom = (delta: number) => setCanvas(current => changeZoom(current, delta))
  const clear = () => { store.dispatch({ category: 'LOCAL_EPHEMERAL', type: 'select', ids: [] }); stage.current?.focus() }
  const fit = () => { const bounds = stage.current?.getBoundingClientRect(); if (bounds) setCanvas(current => fitCanvasViewport(current, bounds.width, bounds.height)) }
  const reveal = () => {
    const bounds = stage.current?.getBoundingClientRect()
    if (!bounds) return
    setCanvas(current => {
      const node = current.nodes.find(item => item.presentationId === selection.primarySelectedObject?.id)
      if (!node) return current
      const zoom = Math.min(1, (bounds.width - 32) / 230, (bounds.height - 32) / 160)
      return { ...current, zoom, viewportX: (bounds.width - 230 * zoom) / 2 - node.x * zoom, viewportY: (bounds.height - 160 * zoom) / 2 - node.y * zoom }
    })
  }
  useSurfaceAdapter({
    objects: () => canvasRef.current.nodes.map(node => {
      const reference = canvasRef.current.references.find(ref => ref.referenceId === node.semanticReferenceId)
      return { id: node.presentationId, kind: 'NODE', title: node.title, x: node.x, y: node.y, reference: reference?.entityId, logicalRef: reference ? { kind: reference.kind, referenceId: reference.referenceId, entityId: reference.entityId } : undefined }
    }),
    get supports() { return ['rename', 'move', 'reveal', ...(history.current.undo.length ? ['undo-local' as const] : []), ...(history.current.redo.length ? ['redo-local' as const] : [])] as const },
    handle: action => {
      if (historyOwner.current !== store.getSnapshot().lifetime) return false
      if (action.type === 'undo-local' || action.type === 'redo-local') {
        const direction = action.type === 'undo-local' ? 'undo' : 'redo'
        const entry = history.current[direction].slice(-1)[0]
        if (!entry) return false
        const owner = store.getSnapshot()
        cancelGesture()
        // Releasing native capture can synchronously retire ownership or change the projection.
        if (historyOwner.current !== owner.lifetime || store.getSnapshot().lifetime !== owner.lifetime || store.getSnapshot().revision !== owner.revision || history.current[direction].slice(-1)[0] !== entry) return false
        history.current = direction === 'undo'
          ? { undo: history.current.undo.slice(0, -1), redo: [...history.current.redo, entry] }
          : { undo: [...history.current.undo, entry], redo: history.current.redo.slice(0, -1) }
        const next = restoreCanvasEdit(canvasRef.current, direction === 'undo' ? entry.before : entry.after)
        canvasRef.current = next
        updateCanvas(next)
      } else if (action.type === 'rename') return setCanvas(current => renameCanvasNode(current, action.targetId, action.title))
      else if (action.type === 'move') return setCanvas(current => placeCanvasNode(current, action.targetId, action.x, action.y))
      else if (action.type === 'reveal') reveal()
      else return false
      return true
    },
  })
  useEffect(() => { fit() }, [])
  useEffect(() => {
    if (typeof ResizeObserver === 'undefined' || !stage.current) return
    const observer = new ResizeObserver(() => {
      const active = gesture.current
      // Selection's queued resize already matches the committed capture geometry.
      // A later external resize still cancels, including width/height-only changes.
      if (active) {
        if (gestureBoundsCurrent(active, stage.current)) return
        cancelGesture()
      }
      if (selected) reveal(); else fit()
    })
    observer.observe(stage.current)
    return () => observer.disconnect()
  }, [selection.primarySelectedObject?.id])
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    completedNodeClick.current = null
    if (!gesture.current) suppressClick.current = false
    const target = event.target as HTMLElement
    if (target.closest('input, textarea, select, [contenteditable]:not([contenteditable="false"])')) return
    const nodeId = target.closest<HTMLButtonElement>('[data-canvas-node]')?.dataset.canvasNode
    if (event.nativeEvent.isComposing || event.keyCode === 229) {
      // Returning alone still permits native button activation during composition.
      if (nodeId && (event.key === 'Enter' || event.key === ' ')) event.preventDefault()
      return
    }
    if (target !== event.currentTarget && !nodeId) return
    if (nodeId && (event.ctrlKey || event.metaKey) && !event.altKey && !event.shiftKey && (event.key === 'Enter' || event.key === ' ')) {
      event.preventDefault()
      if (!event.repeat) selectNode(nodeId, true)
      return
    }
    if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return
    const delta: Record<string, readonly [number, number]> = { ArrowLeft: [-24, 0], ArrowRight: [24, 0], ArrowUp: [0, -24], ArrowDown: [0, 24] }
    if (delta[event.key]) {
      event.preventDefault()
      const [x, y] = delta[event.key]
      if (nodeId) {
        const node = canvas.nodes.find(item => item.presentationId === nodeId)!
        if (!selection.selectedRefs.some(ref => ref.kind === 'NODE' && ref.localId === nodeId)) selectNode(nodeId, false)
        store.dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'move', targetId: nodeId, x: node.x + x, y: node.y + y })
      }
      else pan(-x, -y)
    }
    if (event.key.toLowerCase() === 'r' && selected) { event.preventDefault(); store.dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'reveal', targetId: selected.presentationId }) }
    if (event.key === '=' || event.key === '+') { event.preventDefault(); zoom(0.1) }
    if (event.key === '-') { event.preventDefault(); zoom(-0.1) }
    if (event.key === 'Escape') { event.preventDefault(); if (gesture.current) cancelGesture(); else clear() }
  }

  return <>
    <PageHeading eyebrow={t('canvas.eyebrow')} title={t('canvas.title')} description={t('canvas.description')} />
    <SelectionActionBar />
    <Button aria-pressed={multiSelect} onClick={() => { cancelGesture(); setMultiSelect(value => !value) }}>{multiSelect ? t('canvas.done') : t('canvas.multiSelect')}</Button>
    <div className="ff-canvas-toolbar" role="group" aria-label={t('canvas.historyControls')}>
      <Button data-canvas-history="undo" aria-disabled={!selection.supported.includes('undo-local')} aria-describedby="canvas-history-status" onClick={() => store.dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'undo-local' }, 'TOOLBAR')}>{t('canvas.undoLocal')}</Button>
      <Button data-canvas-history="redo" aria-disabled={!selection.supported.includes('redo-local')} aria-describedby="canvas-history-status" onClick={() => store.dispatch({ category: 'WORKSPACE_PRESENTATION', type: 'redo-local' }, 'TOOLBAR')}>{t('canvas.redoLocal')}</Button>
      <span id="canvas-history-status" role="status">{t('canvas.historyStatus', { undo: history.current.undo.length, redo: history.current.redo.length })}</span>
    </div>
    <p className="ff-canvas-guidance">{t('canvas.historyHelp')}</p>
    <div className="ff-canvas-toolbar" aria-label={t('canvas.viewportControls')}>
      <Button aria-pressed={mode === 'select'} onClick={() => { cancelGesture(); setMode('select') }}>{t('canvas.selectMode')}</Button>
      <Button aria-pressed={mode === 'pan'} onClick={() => { cancelGesture(); setMode('pan') }}>{t('canvas.panMode')}</Button>
      <Button onClick={fit}>{t('canvas.fit')}</Button>
      <details><summary>{t('canvas.moreViewport')}</summary><div className="ff-viewport-more">
        <Button onClick={() => pan(24, 0)}>{t('canvas.panLeft')}</Button><Button onClick={() => pan(-24, 0)}>{t('canvas.panRight')}</Button>
        <Button onClick={() => pan(0, 24)}>{t('canvas.panUp')}</Button><Button onClick={() => pan(0, -24)}>{t('canvas.panDown')}</Button>
        <Button disabled={canvas.zoom <= 0.5} onClick={() => zoom(-0.1)}>{t('canvas.zoomOut')}</Button><Button disabled={canvas.zoom >= 2} onClick={() => zoom(0.1)}>{t('canvas.zoomIn')}</Button>
        <Button onClick={() => setCanvas(resetViewport)}>{t('canvas.resetViewport')}</Button>
      </div></details><output aria-live="polite">{Math.round(canvas.zoom * 100)}%</output>
    </div>
    <details className="ff-compact-details"><summary>{t('canvas.controls')}</summary><p className="ff-canvas-guidance" id="canvas-keyboard-help"><Badge>{t('agent.localOnly')}</Badge> {t('canvas.keyboardHelp')}</p><Button disabled title={t('canvas.relationshipUnavailable')}>{t('canvas.relationship')}</Button></details>
    <p className="ff-canvas-guidance" id="canvas-pointer-help">{t('canvas.pointerHelp')}</p>
    <div className="ff-canvas-layout" data-selected={Boolean(selected)}>
      <div ref={stage} className="ff-workspace-canvas" role="region" aria-label={t('canvas.workspace')} aria-describedby="canvas-keyboard-help canvas-pointer-help" tabIndex={0} onKeyDown={onKeyDown} onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerCancel={event => { if (gesture.current?.pointerId === event.pointerId) cancelGesture() }} onLostPointerCapture={event => { if (gesture.current?.pointerId === event.pointerId) cancelGesture() }} onClickCapture={onClickCapture} onClick={event => { if (isEmptyTarget(event.target)) clear() }} style={{ backgroundSize: `${24 * canvas.zoom}px ${24 * canvas.zoom}px` }}>
        {/* Keep the rendered transform equal to the camera, even when pointerdown follows a camera command. */}
        <div ref={viewport} className="ff-canvas-viewport" style={{ transition: 'none', transform: `translate(${canvas.viewportX + previewPan.x}px, ${canvas.viewportY + previewPan.y}px) scale(${canvas.zoom})` }}>
          {canvas.edges.map(edge => {
            const from = displayNodes.find(node => node.presentationId === edge.fromPresentationId)!
            const to = displayNodes.find(node => node.presentationId === edge.toPresentationId)!
            const dx = to.x - from.x, dy = to.y - from.y
            return <div key={edge.edgeId} className="ff-visual-edge" aria-hidden="true" style={{ left: from.x + 115, top: from.y + 60, width: Math.hypot(dx, dy), transform: `rotate(${Math.atan2(dy, dx)}rad)` }}><span>{t('canvas.visualOnly')}</span></div>
          })}
          {displayNodes.map(node => {
            const reference = canvas.references.find(item => item.referenceId === node.semanticReferenceId)
            return <button key={node.presentationId} data-canvas-node={node.presentationId} type="button" className="ff-canvas-node" style={{ left: node.x, top: node.y, width: CANVAS_NODE_SIZE.width, height: CANVAS_NODE_SIZE.height, overflow: 'hidden' }} aria-label={node.title || t('canvas.untitledNode')} aria-pressed={selection.selectedRefs.some(ref => ref.kind === 'NODE' && ref.localId === node.presentationId)} onFocus={event => {
              const bounds = stage.current?.getBoundingClientRect()
              const rect = event.currentTarget.getBoundingClientRect()
              if (bounds && (rect.left < bounds.left || rect.right > bounds.right || rect.top < bounds.top || rect.bottom > bounds.bottom)) setCanvas(current => ({ ...current, viewportX: 24 - node.x * current.zoom, viewportY: 24 - node.y * current.zoom }))
            }} onClick={event => selectNode(node.presentationId, multiSelect || event.ctrlKey || event.metaKey)}><Badge tone={reference ? 'info' : 'neutral'}>{reference?.kind ?? 'LOCAL'}</Badge><strong>{node.title || t('canvas.untitledNode')}</strong><span>{reference ? project.projectName ?? reference.label : t('canvas.noReference')}</span>{reference ? <code>{reference.entityId}</code> : null}</button>
          })}
        </div>
        {marquee ? <div data-canvas-marquee aria-hidden="true" style={{ ...marquee, position: 'absolute', pointerEvents: 'none', zIndex: 3, border: '1px solid var(--accent-400)', background: 'color-mix(in srgb, var(--accent-400) 15%, transparent)' }} /> : null}
      </div>
    </div>
    <div className="ff-canvas-status" role="status">{previewNodes ? <span>{t('canvas.dragPreview')} </span> : marquee ? <span>{t('canvas.marqueePreview')} </span> : previewPoint && active?.kind === 'pan' ? <span>{t('canvas.panPreview')} </span> : null}{t('canvas.selectionStatus', { count: selection.selectedRefs.length, title: selected?.title || t('common.none') })} · {t('canvas.viewportStatus', { x: canvas.viewportX, y: canvas.viewportY })}</div>
  </>
}

export function CanvasPage() {
  return <ProjectFrame surfaceId="canvas"><CanvasContent /></ProjectFrame>
}
