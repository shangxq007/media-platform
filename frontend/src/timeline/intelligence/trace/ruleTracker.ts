// =============================================================================
// Rule Execution Tracker
// =============================================================================
// Tracks execution of intelligence rules with timing and confidence metrics.
// Stores intermediate states for debugging and replay.
// =============================================================================

import type { DecisionTraceNode, TraceNodeType } from './decisionTrace'
import { generateNodeId, hashTimelineState } from './decisionTrace'

// ---------------------------------------------------------------------------
// Rule Execution Record
// ---------------------------------------------------------------------------
export interface RuleExecution {
  readonly id: string
  readonly ruleId: string
  readonly ruleType: TraceNodeType
  readonly inputHash: string
  readonly outputHash: string
  readonly startedAt: number
  readonly completedAt: number
  readonly durationMs: number
  readonly success: boolean
  readonly confidence: number
  readonly intermediateStates: readonly IntermediateState[]
  readonly error?: string
}

export interface IntermediateState {
  readonly label: string
  readonly stateHash: string
  readonly timestamp: number
}

// ---------------------------------------------------------------------------
// Rule Tracker
// ---------------------------------------------------------------------------
export class RuleTracker {
  private executions: RuleExecution[] = []
  private currentExecution: Partial<RuleExecution> | null = null
  private intermediateStates: IntermediateState[] = []
  private maxExecutions: number

  constructor(maxExecutions: number = 200) {
    this.maxExecutions = maxExecutions
  }

  startExecution(ruleId: string, ruleType: TraceNodeType, input: unknown): void {
    this.currentExecution = {
      id: generateNodeId(),
      ruleId,
      ruleType,
      inputHash: hashTimelineState(input),
      startedAt: Date.now(),
      intermediateStates: [],
    }
    this.intermediateStates = []
  }

  recordIntermediate(label: string, state: unknown): void {
    this.intermediateStates.push({
      label,
      stateHash: hashTimelineState(state),
      timestamp: Date.now(),
    })
  }

  completeExecution(output: unknown, confidence: number, success: boolean = true, error?: string): RuleExecution {
    if (!this.currentExecution) {
      throw new Error('No execution in progress')
    }

    const now = Date.now()
    const execution: RuleExecution = {
      id: this.currentExecution.id!,
      ruleId: this.currentExecution.ruleId!,
      ruleType: this.currentExecution.ruleType!,
      inputHash: this.currentExecution.inputHash!,
      outputHash: hashTimelineState(output),
      startedAt: this.currentExecution.startedAt!,
      completedAt: now,
      durationMs: now - this.currentExecution.startedAt!,
      success,
      confidence,
      intermediateStates: [...this.intermediateStates],
      error,
    }

    this.executions.push(execution)

    // Trim old executions
    if (this.executions.length > this.maxExecutions) {
      this.executions = this.executions.slice(-this.maxExecutions)
    }

    this.currentExecution = null
    this.intermediateStates = []

    return execution
  }

  getExecutions(): readonly RuleExecution[] {
    return [...this.executions]
  }

  getExecutionsByRule(ruleId: string): readonly RuleExecution[] {
    return this.executions.filter(e => e.ruleId === ruleId)
  }

  getExecutionsByType(ruleType: TraceNodeType): readonly RuleExecution[] {
    return this.executions.filter(e => e.ruleType === ruleType)
  }

  getAverageConfidence(ruleId: string): number {
    const ruleExecutions = this.getExecutionsByRule(ruleId)
    if (ruleExecutions.length === 0) return 0
    const sum = ruleExecutions.reduce((s, e) => s + e.confidence, 0)
    return sum / ruleExecutions.length
  }

  getAverageDuration(ruleId: string): number {
    const ruleExecutions = this.getExecutionsByRule(ruleId)
    if (ruleExecutions.length === 0) return 0
    const sum = ruleExecutions.reduce((s, e) => s + e.durationMs, 0)
    return sum / ruleExecutions.length
  }

  getSuccessRate(ruleId: string): number {
    const ruleExecutions = this.getExecutionsByRule(ruleId)
    if (ruleExecutions.length === 0) return 1
    const successes = ruleExecutions.filter(e => e.success).length
    return successes / ruleExecutions.length
  }

  clear(): void {
    this.executions = []
    this.currentExecution = null
    this.intermediateStates = []
  }
}

// ---------------------------------------------------------------------------
// Global Rule Tracker Instance
// ---------------------------------------------------------------------------
export const globalRuleTracker = new RuleTracker()

// ---------------------------------------------------------------------------
// Helper: Track a rule execution
// ---------------------------------------------------------------------------
export function trackRuleExecution<T>(
  ruleId: string,
  ruleType: TraceNodeType,
  input: unknown,
  ruleFn: () => T,
  confidenceExtractor: (result: T) => number = () => 1.0
): T {
  globalRuleTracker.startExecution(ruleId, ruleType, input)

  try {
    const result = ruleFn()
    const confidence = confidenceExtractor(result)
    globalRuleTracker.completeExecution(result, confidence, true)
    return result
  } catch (error) {
    const message = error instanceof Error ? error.message : 'Unknown error'
    globalRuleTracker.completeExecution(null, 0, false, message)
    throw error
  }
}

// ---------------------------------------------------------------------------
// Helper: Measure rule confidence from historical execution
// ---------------------------------------------------------------------------
export function measureRuleConfidence(ruleId: string): number {
  return globalRuleTracker.getAverageConfidence(ruleId)
}

// ---------------------------------------------------------------------------
// Helper: Get rule statistics
// ---------------------------------------------------------------------------
export function getRuleStats(ruleId: string): {
  executions: number
  avgConfidence: number
  avgDuration: number
  successRate: number
} {
  const tracker = globalRuleTracker
  return {
    executions: tracker.getExecutionsByRule(ruleId).length,
    avgConfidence: tracker.getAverageConfidence(ruleId),
    avgDuration: tracker.getAverageDuration(ruleId),
    successRate: tracker.getSuccessRate(ruleId),
  }
}
