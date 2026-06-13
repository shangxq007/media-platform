// =============================================================================
// Explainability Engine
// =============================================================================
// Generates human-readable explanations for intelligence decisions.
// Provides cause-effect chains for debugging and transparency.
// =============================================================================

import type { DecisionTrace, DecisionTraceNode } from './decisionTrace'
import { getTrace, getNodeChain, getTraceNodesByType, formatTraceDuration } from './decisionTrace'
import type { RuleExecution } from './ruleTracker'
import { globalRuleTracker } from './ruleTracker'

// ---------------------------------------------------------------------------
// Explanation Types
// ---------------------------------------------------------------------------
export interface Explanation {
  readonly id: string
  readonly title: string
  readonly summary: string
  readonly steps: readonly ExplanationStep[]
  readonly confidence: number
  readonly duration: number
}

export interface ExplanationStep {
  readonly order: number
  readonly description: string
  readonly ruleId: string
  readonly confidence: number
  readonly timestamp: number
}

// ---------------------------------------------------------------------------
// Explain a Suggestion
// ---------------------------------------------------------------------------
export function explainSuggestion(suggestionId: string, trace: DecisionTrace | null): Explanation {
  if (!trace) {
    return {
      id: suggestionId,
      title: 'Suggestion',
      summary: 'No trace available for this suggestion',
      steps: [],
      confidence: 0,
      duration: 0,
    }
  }

  const suggestionNodes = getTraceNodesByType(trace, 'SUGGEST')
  const relatedNode = suggestionNodes.find(n =>
    n.metadata.suggestionId === suggestionId
  )

  if (!relatedNode) {
    return {
      id: suggestionId,
      title: 'Suggestion',
      summary: 'No trace node found for this suggestion',
      steps: [],
      confidence: 0,
      duration: 0,
    }
  }

  const chain = getNodeChain(relatedNode.id, trace)
  const steps: ExplanationStep[] = chain.map((node, i) => ({
    order: i + 1,
    description: node.explanation,
    ruleId: node.ruleId,
    confidence: node.confidence,
    timestamp: node.timestamp,
  }))

  return {
    id: suggestionId,
    title: `Suggestion: ${relatedNode.metadata.title ?? suggestionId}`,
    summary: relatedNode.explanation,
    steps,
    confidence: relatedNode.confidence,
    duration: trace.completedAt ? trace.completedAt - trace.startedAt : 0,
  }
}

// ---------------------------------------------------------------------------
// Explain an Auto Layout Operation
// ---------------------------------------------------------------------------
export function explainAutoLayout(traceId: string): Explanation {
  const trace = getTrace(traceId)

  if (!trace) {
    return {
      id: traceId,
      title: 'Auto Layout',
      summary: 'No trace available',
      steps: [],
      confidence: 0,
      duration: 0,
    }
  }

  const layoutNodes = getTraceNodesByType(trace, 'LAYOUT')
  const steps: ExplanationStep[] = layoutNodes.map((node, i) => ({
    order: i + 1,
    description: node.explanation,
    ruleId: node.ruleId,
    confidence: node.confidence,
    timestamp: node.timestamp,
  }))

  const avgConfidence = layoutNodes.length > 0
    ? layoutNodes.reduce((s, n) => s + n.confidence, 0) / layoutNodes.length
    : 0

  return {
    id: traceId,
    title: 'Auto Layout',
    summary: `Applied ${layoutNodes.length} layout operations`,
    steps,
    confidence: avgConfidence,
    duration: trace.completedAt ? trace.completedAt - trace.startedAt : 0,
  }
}

// ---------------------------------------------------------------------------
// Explain a Conflict Resolution
// ---------------------------------------------------------------------------
export function explainConflictResolution(traceId: string): Explanation {
  const trace = getTrace(traceId)

  if (!trace) {
    return {
      id: traceId,
      title: 'Conflict Resolution',
      summary: 'No trace available',
      steps: [],
      confidence: 0,
      duration: 0,
    }
  }

  const resolveNodes = getTraceNodesByType(trace, 'RESOLVE')
  const steps: ExplanationStep[] = []

  for (const node of resolveNodes) {
    const chain = getNodeChain(node.id, trace)
    for (const chainNode of chain) {
      if (!steps.find(s => s.ruleId === chainNode.ruleId && s.timestamp === chainNode.timestamp)) {
        steps.push({
          order: steps.length + 1,
          description: chainNode.explanation,
          ruleId: chainNode.ruleId,
          confidence: chainNode.confidence,
          timestamp: chainNode.timestamp,
        })
      }
    }
  }

  const avgConfidence = resolveNodes.length > 0
    ? resolveNodes.reduce((s, n) => s + n.confidence, 0) / resolveNodes.length
    : 0

  return {
    id: traceId,
    title: 'Conflict Resolution',
    summary: `Resolved ${resolveNodes.length} conflicts`,
    steps,
    confidence: avgConfidence,
    duration: trace.completedAt ? trace.completedAt - trace.startedAt : 0,
  }
}

// ---------------------------------------------------------------------------
// Explain a Rule Execution
// ---------------------------------------------------------------------------
export function explainRuleExecution(execution: RuleExecution): Explanation {
  const steps: ExplanationStep[] = execution.intermediateStates.map((state, i) => ({
    order: i + 1,
    description: state.label,
    ruleId: execution.ruleId,
    confidence: execution.confidence,
    timestamp: state.timestamp,
  }))

  return {
    id: execution.id,
    title: `Rule: ${execution.ruleId}`,
    summary: `Executed in ${execution.durationMs}ms with ${(execution.confidence * 100).toFixed(0)}% confidence`,
    steps,
    confidence: execution.confidence,
    duration: execution.durationMs,
  }
}

// ---------------------------------------------------------------------------
// Format Explanation as Text
// ---------------------------------------------------------------------------
export function formatExplanationAsText(explanation: Explanation): string {
  const lines: string[] = []

  lines.push(`=== ${explanation.title} ===`)
  lines.push(`Summary: ${explanation.summary}`)
  lines.push(`Confidence: ${(explanation.confidence * 100).toFixed(0)}%`)
  lines.push(`Duration: ${explanation.duration}ms`)
  lines.push('')

  if (explanation.steps.length > 0) {
    lines.push('Steps:')
    for (const step of explanation.steps) {
      lines.push(`  ${step.order}. ${step.description} (${(step.confidence * 100).toFixed(0)}% confidence)`)
    }
  }

  return lines.join('\n')
}

// ---------------------------------------------------------------------------
// Format Explanation as Markdown
// ---------------------------------------------------------------------------
export function formatExplanationAsMarkdown(explanation: Explanation): string {
  const lines: string[] = []

  lines.push(`## ${explanation.title}`)
  lines.push('')
  lines.push(`**Summary:** ${explanation.summary}`)
  lines.push(`**Confidence:** ${(explanation.confidence * 100).toFixed(0)}%`)
  lines.push(`**Duration:** ${explanation.duration}ms`)
  lines.push('')

  if (explanation.steps.length > 0) {
    lines.push('### Decision Steps')
    lines.push('')
    for (const step of explanation.steps) {
      lines.push(`${step.order}. ${step.description}`)
      lines.push(`   - Confidence: ${(step.confidence * 100).toFixed(0)}%`)
      lines.push(`   - Rule: \`${step.ruleId}\``)
      lines.push('')
    }
  }

  return lines.join('\n')
}
