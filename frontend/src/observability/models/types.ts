// =============================================================================
// Observability Domain Models
// =============================================================================
// UI consumes only these types. Never raw API responses.
// =============================================================================

// ---------------------------------------------------------------------------
// Render Job Status
// ---------------------------------------------------------------------------
export type RenderJobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

// ---------------------------------------------------------------------------
// Render Job Summary (List View)
// ---------------------------------------------------------------------------
export interface RenderJobSummaryObs {
  readonly id: string
  readonly projectId: string
  readonly status: RenderJobStatus
  readonly createdAt: string
  readonly duration: number | null
  readonly failureReason: string | null
  readonly provider: string | null
  readonly traceId: string | null
}

// ---------------------------------------------------------------------------
// Execution Step
// ---------------------------------------------------------------------------
export interface ExecutionStep {
  readonly id: string
  readonly type: 'ANALYZE' | 'PROVIDER_SELECT' | 'RENDER' | 'EFFECT' | 'SUBTITLE' | 'OUTPUT'
  readonly name: string
  readonly status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  readonly startedAt: string | null
  readonly completedAt: string | null
  readonly durationMs: number | null
  readonly input: unknown
  readonly output: unknown
  readonly error: string | null
}

// ---------------------------------------------------------------------------
// Provider Decision
// ---------------------------------------------------------------------------
export interface ProviderDecision {
  readonly id: string
  readonly timestamp: string
  readonly selectedProvider: string
  readonly candidates: readonly string[]
  readonly reason: string
  readonly fallbackTriggered: boolean
  readonly fallbackFrom: string | null
  readonly fallbackReason: string | null
}

// ---------------------------------------------------------------------------
// Effect Execution
// ---------------------------------------------------------------------------
export interface EffectExecution {
  readonly id: string
  readonly effectKey: string
  readonly effectName: string
  readonly status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
  readonly startedAt: string | null
  readonly completedAt: string | null
  readonly durationMs: number | null
  readonly parameters: Record<string, unknown>
  readonly error: string | null
}

// ---------------------------------------------------------------------------
// Render Job Detail (Inspector View)
// ---------------------------------------------------------------------------
export interface RenderJobDetailObs {
  readonly id: string
  readonly projectId: string
  readonly status: RenderJobStatus
  readonly createdAt: string
  readonly completedAt: string | null
  readonly duration: number | null
  readonly failureReason: string | null
  readonly traceId: string | null
  readonly timelineSnapshot: unknown
  readonly providerSelection: ProviderDecision | null
  readonly executionSteps: readonly ExecutionStep[]
  readonly effectExecutions: readonly EffectExecution[]
  readonly errorTrace: string | null
}

// ---------------------------------------------------------------------------
// Trace Node
// ---------------------------------------------------------------------------
export interface TraceNode {
  readonly id: string
  readonly type: 'ANALYZE' | 'RENDER' | 'EFFECT' | 'PROVIDER' | 'TIMELINE'
  readonly name: string
  readonly timestamp: string
  readonly durationMs: number | null
  readonly inputState: unknown
  readonly outputState: unknown
  readonly parentId: string | null
  readonly childIds: readonly string[]
  readonly status: 'SUCCESS' | 'FAILURE' | 'SKIPPED'
  readonly metadata: Record<string, unknown>
}

// ---------------------------------------------------------------------------
// Trace Graph
// ---------------------------------------------------------------------------
export interface TraceGraph {
  readonly traceId: string
  readonly jobId: string
  readonly nodes: readonly TraceNode[]
  readonly rootId: string
  readonly startedAt: string
  readonly completedAt: string | null
  readonly totalDurationMs: number | null
}

// ---------------------------------------------------------------------------
// System Metrics Summary
// ---------------------------------------------------------------------------
export interface MetricsSummary {
  readonly totalJobs: number
  readonly successCount: number
  readonly failureCount: number
  readonly successRate: number
  readonly averageDurationMs: number
  readonly p95DurationMs: number
  readonly p99DurationMs: number
  readonly activeJobs: number
  readonly queuedJobs: number
}

// ---------------------------------------------------------------------------
// Provider Metrics
// ---------------------------------------------------------------------------
export interface ProviderMetrics {
  readonly provider: string
  readonly totalJobs: number
  readonly successCount: number
  readonly failureCount: number
  readonly averageDurationMs: number
  readonly fallbackCount: number
}

// ---------------------------------------------------------------------------
// Failure Metrics
// ---------------------------------------------------------------------------
export interface FailureMetrics {
  readonly period: string
  readonly totalFailures: number
  readonly byProvider: Record<string, number>
  readonly byStage: Record<string, number>
  readonly topReasons: readonly { reason: string; count: number }[]
}

// ---------------------------------------------------------------------------
// Latency Bucket (for histogram)
// ---------------------------------------------------------------------------
export interface LatencyBucket {
  readonly label: string
  readonly min: number
  readonly max: number
  readonly count: number
}
