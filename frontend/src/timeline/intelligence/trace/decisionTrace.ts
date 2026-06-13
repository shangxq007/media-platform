// =============================================================================
// Decision Trace Core
// =============================================================================
// Records and manages decision traces for intelligence operations.
// Provides explainability and reproducibility for all intelligence decisions.
// =============================================================================

// ---------------------------------------------------------------------------
// Trace Node Types
// ---------------------------------------------------------------------------
export type TraceNodeType = 'ANALYZE' | 'SUGGEST' | 'RESOLVE' | 'LAYOUT' | 'RULE'

export interface DecisionTraceNode {
  readonly id: string
  readonly type: TraceNodeType
  readonly ruleId: string
  readonly inputStateHash: string
  readonly outputStateHash: string
  readonly confidence: number
  readonly timestamp: number
  readonly parentIds: readonly string[]
  readonly childIds: readonly string[]
  readonly explanation: string
  readonly metadata: Readonly<Record<string, unknown>>
}

export interface DecisionTrace {
  readonly id: string
  readonly rootId: string
  readonly nodes: Readonly<Record<string, DecisionTraceNode>>
  readonly startedAt: number
  readonly completedAt: number | null
  readonly summary: string
}

// ---------------------------------------------------------------------------
// State Hashing
// ---------------------------------------------------------------------------
export function hashTimelineState(state: unknown): string {
  // Simple hash for trace purposes - not cryptographic
  const str = JSON.stringify(state)
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    const char = str.charCodeAt(i)
    hash = ((hash << 5) - hash) + char
    hash = hash & hash // Convert to 32-bit integer
  }
  return `state-${Math.abs(hash).toString(36)}`
}

// ---------------------------------------------------------------------------
// Trace ID Generator
// ---------------------------------------------------------------------------
let traceCounter = 0
let nodeCounter = 0

export function generateTraceId(): string {
  return `trace-${Date.now()}-${++traceCounter}`
}

export function generateNodeId(): string {
  return `node-${Date.now()}-${++nodeCounter}`
}

// ---------------------------------------------------------------------------
// Decision Trace Builder
// ---------------------------------------------------------------------------
export class DecisionTraceBuilder {
  private trace: DecisionTrace
  private nodes: Record<string, DecisionTraceNode> = {}

  constructor(summary: string) {
    this.trace = {
      id: generateTraceId(),
      rootId: '',
      nodes: {},
      startedAt: Date.now(),
      completedAt: null,
      summary,
    }
  }

  addNode(
    type: TraceNodeType,
    ruleId: string,
    inputState: unknown,
    outputState: unknown,
    explanation: string,
    confidence: number = 1.0,
    parentIds: string[] = [],
    metadata: Record<string, unknown> = {}
  ): DecisionTraceNode {
    const node: DecisionTraceNode = {
      id: generateNodeId(),
      type,
      ruleId,
      inputStateHash: hashTimelineState(inputState),
      outputStateHash: hashTimelineState(outputState),
      confidence,
      timestamp: Date.now(),
      parentIds,
      childIds: [],
      explanation,
      metadata,
    }

    // Update parent nodes to include this as child
    for (const parentId of parentIds) {
      const parent = this.nodes[parentId]
      if (parent) {
        this.nodes[parentId] = {
          ...parent,
          childIds: [...parent.childIds, node.id],
        }
      }
    }

    this.nodes[node.id] = node

    // Set root if first node
    if (!this.trace.rootId) {
      this.trace = { ...this.trace, rootId: node.id }
    }

    return node
  }

  build(): DecisionTrace {
    this.trace = {
      ...this.trace,
      nodes: { ...this.nodes },
      completedAt: Date.now(),
    }
    return this.trace
  }
}

// ---------------------------------------------------------------------------
// Trace Store (in-memory)
// ---------------------------------------------------------------------------
const traceStore: Map<string, DecisionTrace> = new Map()
const MAX_TRACES = 50

export function storeTrace(trace: DecisionTrace): void {
  traceStore.set(trace.id, trace)

  // Trim old traces
  if (traceStore.size > MAX_TRACES) {
    const oldest = Array.from(traceStore.keys())[0]
    traceStore.delete(oldest)
  }
}

export function getTrace(traceId: string): DecisionTrace | undefined {
  return traceStore.get(traceId)
}

export function getAllTraces(): DecisionTrace[] {
  return Array.from(traceStore.values())
}

export function getRecentTraces(count: number): DecisionTrace[] {
  return Array.from(traceStore.values())
    .sort((a, b) => b.startedAt - a.startedAt)
    .slice(0, count)
}

export function clearTraces(): void {
  traceStore.clear()
}

// ---------------------------------------------------------------------------
// Trace Query Helpers
// ---------------------------------------------------------------------------
export function getTraceNodesByType(trace: DecisionTrace, type: TraceNodeType): DecisionTraceNode[] {
  return Object.values(trace.nodes).filter(n => n.type === type)
}

export function getTraceNodesByRule(trace: DecisionTrace, ruleId: string): DecisionTraceNode[] {
  return Object.values(trace.nodes).filter(n => n.ruleId === ruleId)
}

export function getNodeChain(nodeId: string, trace: DecisionTrace): DecisionTraceNode[] {
  const chain: DecisionTraceNode[] = []
  let currentId: string | undefined = nodeId

  while (currentId) {
    const node: DecisionTraceNode | undefined = trace.nodes[currentId]
    if (!node) break
    chain.push(node)
    currentId = node.parentIds[0] // Follow first parent
  }

  return chain.reverse()
}

export function formatTraceDuration(trace: DecisionTrace): string {
  if (!trace.completedAt) return 'in progress'
  const ms = trace.completedAt - trace.startedAt
  if (ms < 1000) return `${ms}ms`
  return `${(ms / 1000).toFixed(2)}s`
}
