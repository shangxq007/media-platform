export const WORKFLOW_NODE_CATEGORIES = ['OPERATION', 'RENDER', 'REVIEW', 'WAIT', 'CONDITION', 'AGENT', 'INTEGRATION'] as const
export type WorkflowNodeCategory = typeof WORKFLOW_NODE_CATEGORIES[number]
export const WORKFLOW_SKETCH_LIMIT = 12
export const WORKFLOW_SKETCH_TITLE_LIMIT = 120
export const WORKFLOW_SKETCH_BOUNDS = { minX: 16, maxX: 896, minY: 16, maxY: 560 } as const

export interface WorkflowSketchNode {
  readonly id: string
  readonly category: WorkflowNodeCategory
  readonly title: string
  readonly x: number
  readonly y: number
}

export interface WorkflowSketchState {
  readonly nodes: readonly WorkflowSketchNode[]
  readonly nextId: number
}

export function createWorkflowSketch(): WorkflowSketchState {
  return { nodes: [], nextId: 1 }
}

export function addWorkflowSketchNode(state: WorkflowSketchState, category: WorkflowNodeCategory): WorkflowSketchState {
  if (state.nodes.length >= WORKFLOW_SKETCH_LIMIT || !WORKFLOW_NODE_CATEGORIES.includes(category)) return state
  // Reuse a vacant palette slot, never an unchanged card's exact placement.
  const slots = Array.from({ length: WORKFLOW_SKETCH_LIMIT }, (_, index) => ({ x: 32 + (index % 4) * 220, y: 32 + Math.floor(index / 4) * 136 }))
  const position = slots.find(slot => !state.nodes.some(node => node.x === slot.x && node.y === slot.y))!

  const node: WorkflowSketchNode = {
    id: `workflow-node-${state.nextId}`,
    category,
    title: category,
    ...position,
  }
  return { nodes: [...state.nodes, node], nextId: state.nextId + 1 }
}

export function moveWorkflowSketchNode(state: WorkflowSketchState, _id: string, _x: number, _y: number): WorkflowSketchState {
  if (!Number.isFinite(_x) || !Number.isFinite(_y) || !state.nodes.some(node => node.id === _id)) return state
  const bound = (value: number, min: number, max: number) => Math.min(max, Math.max(min, Math.round(value)))
  const x = bound(_x, WORKFLOW_SKETCH_BOUNDS.minX, WORKFLOW_SKETCH_BOUNDS.maxX)
  const y = bound(_y, WORKFLOW_SKETCH_BOUNDS.minY, WORKFLOW_SKETCH_BOUNDS.maxY)
  if (state.nodes.find(node => node.id === _id)?.x === x && state.nodes.find(node => node.id === _id)?.y === y) return state
  return { ...state, nodes: state.nodes.map(node => node.id === _id ? { ...node, x, y } : node) }
}

export function isWorkflowSketchTitleValid(title: string): boolean {
  return Boolean(title.trim()) && title.length <= WORKFLOW_SKETCH_TITLE_LIMIT
}

export function renameWorkflowSketchNode(state: WorkflowSketchState, _id: string, _title: string): WorkflowSketchState {
  if (!isWorkflowSketchTitleValid(_title)) return state
  const title = _title
  if (!state.nodes.some(node => node.id === _id) || state.nodes.find(node => node.id === _id)?.title === title) return state
  return { ...state, nodes: state.nodes.map(node => node.id === _id ? { ...node, title } : node) }
}

export function removeWorkflowSketchNode(state: WorkflowSketchState, _id: string): WorkflowSketchState {
  if (!state.nodes.some(node => node.id === _id)) return state
  return { ...state, nodes: state.nodes.filter(node => node.id !== _id) }
}

export function resetWorkflowSketch(state: WorkflowSketchState): WorkflowSketchState {
  return state.nodes.length ? { nodes: [], nextId: state.nextId } : state
}
