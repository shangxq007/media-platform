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
  readonly selectedPresentationId: string | null
}

export function moveViewport(state: WorkspaceCanvasState, deltaX: number, deltaY: number): WorkspaceCanvasState {
  return { ...state, viewportX: state.viewportX + deltaX, viewportY: state.viewportY + deltaY }
}

export function changeZoom(state: WorkspaceCanvasState, delta: number): WorkspaceCanvasState {
  return { ...state, zoom: Math.min(2, Math.max(0.5, Number((state.zoom + delta).toFixed(2)))) }
}

export function selectCanvasNode(state: WorkspaceCanvasState, presentationId: string | null): WorkspaceCanvasState {
  return { ...state, selectedPresentationId: presentationId }
}
