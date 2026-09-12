import type { PresentationSelectionRef, SelectionScope } from '../../interaction/model'

export type CanvasReferenceKind = 'PROJECT' | 'TIMELINE_REVISION' | 'MEDIA_ASSET'

export interface CanvasSemanticReference {
  readonly referenceId: string
  readonly kind: CanvasReferenceKind
  readonly entityId: string
  readonly label: string
}

export interface CanvasNode {
  readonly presentationId: string
  readonly semanticReferenceId: string | null
  readonly title: string
  readonly x: number
  readonly y: number
}

export interface CanvasVisualEdge {
  readonly edgeId: string
  readonly fromPresentationId: string
  readonly toPresentationId: string
  readonly meaning: 'VISUAL_ONLY'
}

export interface WorkspaceCanvasState {
  readonly references: readonly CanvasSemanticReference[]
  readonly nodes: readonly CanvasNode[]
  readonly edges: readonly CanvasVisualEdge[]
  readonly zoom: number
  readonly viewportX: number
  readonly viewportY: number
}

export function moveViewport(state: WorkspaceCanvasState, deltaX: number, deltaY: number): WorkspaceCanvasState {
  return { ...state, viewportX: state.viewportX + deltaX, viewportY: state.viewportY + deltaY }
}

export function changeZoom(state: WorkspaceCanvasState, delta: number): WorkspaceCanvasState {
  if (!Number.isFinite(delta)) return state
  return { ...state, zoom: Math.min(2, Math.max(0.5, Number((state.zoom + delta).toFixed(2)))) }
}

export function createCanvasState(projectId: string): WorkspaceCanvasState {
  return {
    references: [{ referenceId: 'project-ref', kind: 'PROJECT', entityId: projectId, label: projectId }],
    nodes: [
      { presentationId: 'project-node', semanticReferenceId: 'project-ref', title: 'Project reference', x: 120, y: 90 },
      { presentationId: 'note-node', semanticReferenceId: null, title: 'Local composition note', x: 430, y: 230 },
    ],
    edges: [{ edgeId: 'visual-guide', fromPresentationId: 'project-node', toPresentationId: 'note-node', meaning: 'VISUAL_ONLY' }],
    zoom: 1, viewportX: 0, viewportY: 0,
  }
}

// Bounds are local layout units, never canonical media geometry.
export function placeCanvasNode(state: WorkspaceCanvasState, id: string, x: number, y: number): WorkspaceCanvasState {
  if (!Number.isFinite(x) || !Number.isFinite(y)) return state
  const bound = (value: number) => Math.min(2000, Math.max(-2000, Math.round(value)))
  return { ...state, nodes: state.nodes.map(node => node.presentationId === id ? { ...node, x: bound(x), y: bound(y) } : node) }
}

export function renameCanvasNode(state: WorkspaceCanvasState, id: string, title: string): WorkspaceCanvasState {
  return { ...state, nodes: state.nodes.map(node => node.presentationId === id ? { ...node, title: title.slice(0, 120) } : node) }
}

export function resetViewport(state: WorkspaceCanvasState): WorkspaceCanvasState {
  return { ...state, zoom: 1, viewportX: 0, viewportY: 0 }
}

// Fit is a local camera operation; it never moves or changes the referenced objects.
export function fitCanvasViewport(state: WorkspaceCanvasState, width: number, height: number): WorkspaceCanvasState {
  if (width <= 32 || height <= 32 || !Number.isFinite(width + height) || !state.nodes.length) return state
  const left = Math.min(...state.nodes.map(node => node.x))
  const top = Math.min(...state.nodes.map(node => node.y))
  const right = Math.max(...state.nodes.map(node => node.x + 230))
  const bottom = Math.max(...state.nodes.map(node => node.y + 160))
  const zoom = Math.min(1, (width - 32) / (right - left), (height - 32) / (bottom - top))
  return { ...state, zoom, viewportX: (width - (right - left) * zoom) / 2 - left * zoom, viewportY: (height - (bottom - top) * zoom) / 2 - top * zoom }
}

export interface CanvasPoint { readonly x: number; readonly y: number }

export function screenToCanvas(client: CanvasPoint, origin: CanvasPoint, pan: CanvasPoint, zoom: number): CanvasPoint {
  return { x: (client.x - origin.x - pan.x) / zoom, y: (client.y - origin.y - pan.y) / zoom }
}

// Intersect legal translation intervals before moving any original. Never clamp individual results.
export function boundedGroupDelta(originals: readonly CanvasNode[], requested: CanvasPoint): CanvasPoint {
  let minX = -Infinity, maxX = Infinity, minY = -Infinity, maxY = Infinity
  for (const node of originals) {
    minX = Math.max(minX, -2000 - node.x); maxX = Math.min(maxX, 2000 - node.x)
    minY = Math.max(minY, -2000 - node.y); maxY = Math.min(maxY, 2000 - node.y)
  }
  return { x: Math.max(minX, Math.min(maxX, requested.x)), y: Math.max(minY, Math.min(maxY, requested.y)) }
}

export function moveCanvasGroup(state: WorkspaceCanvasState, originals: readonly CanvasNode[], requested: CanvasPoint): WorkspaceCanvasState {
  if (!originals.length || !Number.isFinite(requested.x) || !Number.isFinite(requested.y)) return state
  const byId = new Map(originals.map(node => [node.presentationId, node]))
  if (byId.size !== originals.length || originals.some(original => {
    const matches = state.nodes.filter(node => node.presentationId === original.presentationId)
    return matches.length !== 1 || matches[0].x !== original.x || matches[0].y !== original.y
      || !Number.isFinite(original.x) || !Number.isFinite(original.y) || Math.abs(original.x) > 2000 || Math.abs(original.y) > 2000
  })) return state
  const delta = boundedGroupDelta(originals, requested)
  if (delta.x === 0 && delta.y === 0) return state
  return { ...state, nodes: state.nodes.map(node => byId.has(node.presentationId) ? { ...node, x: node.x + delta.x, y: node.y + delta.y } : node) }
}

// Fixed presentation bounds shared by rendered cards and model-only hit testing.
export const CANVAS_NODE_SIZE = { width: 230, height: 160 } as const
export const CANVAS_GESTURE_THRESHOLD = 4 // client pixels; independent of zoom

export function isMarqueeArea(start: CanvasPoint, end: CanvasPoint): boolean {
  return start.x !== end.x && start.y !== end.y && Math.hypot(end.x - start.x, end.y - start.y) >= CANVAS_GESTURE_THRESHOLD
}

export function marqueeHits(nodes: readonly CanvasNode[], start: CanvasPoint, end: CanvasPoint): readonly CanvasNode[] {
  const left = Math.min(start.x, end.x), right = Math.max(start.x, end.x)
  const top = Math.min(start.y, end.y), bottom = Math.max(start.y, end.y)
  if (!(right > left && bottom > top)) return []
  return nodes.filter(node => node.x < right && node.x + CANVAS_NODE_SIZE.width > left
    && node.y < bottom && node.y + CANVAS_NODE_SIZE.height > top)
}

export function canvasMarqueeRefs(nodes: readonly CanvasNode[], start: CanvasPoint, end: CanvasPoint, scope: SelectionScope, existing: readonly PresentationSelectionRef[] = []): readonly PresentationSelectionRef[] {
  const eligible = (ref: PresentationSelectionRef) => ref.kind === 'NODE' && ref.surfaceId === 'canvas' && scope.surfaceId === 'canvas'
    && ref.workspaceId === scope.workspaceId && ref.projectId === scope.projectId && nodes.filter(node => node.presentationId === ref.localId).length === 1
  const survivors = existing.filter(eligible)
  const hits: PresentationSelectionRef[] = marqueeHits(nodes, start, end).map<PresentationSelectionRef>(node => ({ ...scope, kind: 'NODE', localId: node.presentationId })).filter(eligible)
  // Map preserves insertion order: survivors first, then fresh hits in Canvas MODEL array order.
  return [...new Map([...survivors, ...hits].map(ref => [ref.localId, ref])).values()]
}

// History contains only local presentation keys and editable values, never a Canvas/Selection snapshot.
export type CanvasLocalValues = Pick<CanvasNode, 'presentationId' | 'title' | 'x' | 'y'>
export interface CanvasEditEntry { readonly before: readonly CanvasLocalValues[]; readonly after: readonly CanvasLocalValues[] }
export interface CanvasHistory { readonly undo: readonly CanvasEditEntry[]; readonly redo: readonly CanvasEditEntry[] }
export const emptyCanvasHistory = (): CanvasHistory => ({ undo: [], redo: [] })
export function recordCanvasEdit(history: CanvasHistory, before: WorkspaceCanvasState, after: WorkspaceCanvasState): CanvasHistory {
  const values = (state: WorkspaceCanvasState) => state.nodes.map(({ presentationId, title, x, y }) => ({ presentationId, title, x, y }))
  const previous = values(before), next = values(after)
  if (JSON.stringify(previous) === JSON.stringify(next)) return history
  return { undo: [...history.undo, { before: previous, after: next }].slice(-50), redo: [] }
}
export function restoreCanvasEdit(state: WorkspaceCanvasState, values: readonly CanvasLocalValues[]): WorkspaceCanvasState {
  return { ...state, nodes: state.nodes.map(node => {
    const value = values.find(value => value.presentationId === node.presentationId)
    return value ? { ...node, title: value.title, x: value.x, y: value.y } : node
  }) }
}
