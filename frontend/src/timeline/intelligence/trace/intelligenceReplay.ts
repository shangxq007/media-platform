// =============================================================================
// Intelligence Replay System
// =============================================================================
// Enables deterministic replay of intelligence operations.
// Used for debugging, testing, and reproducing decisions.
// =============================================================================

import type { TimelineCanvasState } from '../../model/timeline'
import type { DecisionTrace, DecisionTraceNode } from './decisionTrace'
import {
  DecisionTraceBuilder,
  storeTrace,
  getTrace,
  hashTimelineState,
} from './decisionTrace'
import { analyzeTimeline, type TimelineAnalysisReport } from '../timelineAnalyzer'
import { generateSuggestions, type Suggestion } from '../suggestionEngine'
import { detectConflicts, resolveTimelineConflicts, type ResolutionResult } from '../conflictResolver'
import { autoStackClips, compactTimeline, type LayoutResult } from '../autoLayout'

// ---------------------------------------------------------------------------
// Replay Result
// ---------------------------------------------------------------------------
export interface ReplayResult {
  readonly traceId: string
  readonly success: boolean
  readonly steps: readonly ReplayStep[]
  readonly finalState: TimelineCanvasState
  readonly errors: readonly string[]
}

export interface ReplayStep {
  readonly order: number
  readonly type: string
  readonly description: string
  readonly inputHash: string
  readonly outputHash: string
  readonly success: boolean
}

// ---------------------------------------------------------------------------
// Replay Analysis
// ---------------------------------------------------------------------------
export function replayAnalysis(timeline: TimelineCanvasState): {
  report: TimelineAnalysisReport
  trace: DecisionTrace
} {
  const builder = new DecisionTraceBuilder('Timeline Analysis Replay')

  // Track analysis steps
  const report = analyzeTimeline(timeline)

  builder.addNode(
    'ANALYZE',
    'detectOverlaps',
    timeline,
    report.issues.filter(i => i.type === 'OVERLAP'),
    `Detected ${report.issues.filter(i => i.type === 'OVERLAP').length} overlaps`,
    1.0,
    [],
    { issueCount: report.issues.filter(i => i.type === 'OVERLAP').length }
  )

  builder.addNode(
    'ANALYZE',
    'detectGaps',
    timeline,
    report.warnings.filter(w => w.type === 'GAP'),
    `Detected ${report.warnings.filter(w => w.type === 'GAP').length} gaps`,
    1.0,
    [],
    { warningCount: report.warnings.filter(w => w.type === 'GAP').length }
  )

  builder.addNode(
    'ANALYZE',
    'calculateHealth',
    timeline,
    report.overallHealth,
    `Health score: ${report.overallHealth}%`,
    1.0,
    [],
    { health: report.overallHealth }
  )

  const trace = builder.build()
  storeTrace(trace)

  return { report, trace }
}

// ---------------------------------------------------------------------------
// Replay Suggestions
// ---------------------------------------------------------------------------
export function replaySuggestions(timeline: TimelineCanvasState): {
  suggestions: Suggestion[]
  trace: DecisionTrace
} {
  const builder = new DecisionTraceBuilder('Suggestion Generation Replay')

  const suggestions = generateSuggestions(timeline)

  for (const suggestion of suggestions) {
    builder.addNode(
      'SUGGEST',
      `suggest_${suggestion.type.toLowerCase()}`,
      timeline,
      suggestion,
      suggestion.description,
      suggestion.confidence === 'high' ? 0.9 : suggestion.confidence === 'medium' ? 0.7 : 0.5,
      [],
      {
        suggestionId: suggestion.id,
        title: suggestion.title,
        type: suggestion.type,
        confidence: suggestion.confidence,
      }
    )
  }

  const trace = builder.build()
  storeTrace(trace)

  return { suggestions, trace }
}

// ---------------------------------------------------------------------------
// Replay Conflict Resolution
// ---------------------------------------------------------------------------
export function replayConflictResolution(
  timeline: TimelineCanvasState,
  strategy: 'SHIFT_RIGHT' | 'TRIM_CLIP' | 'MERGE_TRACK' = 'SHIFT_RIGHT'
): {
  result: ResolutionResult
  trace: DecisionTrace
} {
  const builder = new DecisionTraceBuilder('Conflict Resolution Replay')

  const conflicts = detectConflicts(timeline)

  builder.addNode(
    'ANALYZE',
    'detectConflicts',
    timeline,
    conflicts,
    `Detected ${conflicts.length} conflicts`,
    1.0,
    [],
    { conflictCount: conflicts.length }
  )

  const result = resolveTimelineConflicts(timeline, strategy)

  for (const conflict of conflicts) {
    builder.addNode(
      'RESOLVE',
      `resolve_${strategy.toLowerCase()}`,
      conflict,
      result.description,
      `Resolved conflict between "${conflict.clipA.name}" and "${conflict.clipB.name}"`,
      0.9,
      [],
      {
        conflictId: conflict.id,
        strategy,
        overlapDuration: conflict.overlapDuration,
      }
    )
  }

  const trace = builder.build()
  storeTrace(trace)

  return { result, trace }
}

// ---------------------------------------------------------------------------
// Replay Auto Layout
// ---------------------------------------------------------------------------
export function replayAutoLayout(
  timeline: TimelineCanvasState,
  operation: 'stack' | 'compact' = 'stack'
): {
  result: LayoutResult
  trace: DecisionTrace
} {
  const builder = new DecisionTraceBuilder(`Auto Layout Replay (${operation})`)

  const result = operation === 'stack'
    ? autoStackClips(timeline)
    : compactTimeline(timeline)

  builder.addNode(
    'LAYOUT',
    `auto_${operation}`,
    timeline,
    result,
    result.description,
    0.9,
    [],
    {
      operation,
      affectedClips: result.affectedClips,
    }
  )

  const trace = builder.build()
  storeTrace(trace)

  return { result, trace }
}

// ---------------------------------------------------------------------------
// Full Deterministic Replay
// ---------------------------------------------------------------------------
export function fullReplay(timeline: TimelineCanvasState): ReplayResult {
  const builder = new DecisionTraceBuilder('Full Intelligence Replay')
  const steps: ReplayStep[] = []
  const errors: string[] = []
  let stepOrder = 0

  // Step 1: Analysis
  try {
    const analysisInput = timeline
    const report = analyzeTimeline(timeline)
    stepOrder++
    steps.push({
      order: stepOrder,
      type: 'ANALYZE',
      description: `Analysis: ${report.issues.length} issues, ${report.warnings.length} warnings`,
      inputHash: hashTimelineState(analysisInput),
      outputHash: hashTimelineState(report),
      success: true,
    })

    builder.addNode(
      'ANALYZE',
      'full_analysis',
      analysisInput,
      report,
      `Found ${report.issues.length} issues and ${report.warnings.length} warnings`,
      1.0
    )
  } catch (e) {
    errors.push(`Analysis failed: ${e}`)
  }

  // Step 2: Suggestions
  try {
    const suggestionsInput = timeline
    const suggestions = generateSuggestions(timeline)
    stepOrder++
    steps.push({
      order: stepOrder,
      type: 'SUGGEST',
      description: `Generated ${suggestions.length} suggestions`,
      inputHash: hashTimelineState(suggestionsInput),
      outputHash: hashTimelineState(suggestions),
      success: true,
    })

    builder.addNode(
      'SUGGEST',
      'full_suggestions',
      suggestionsInput,
      suggestions,
      `Generated ${suggestions.length} suggestions`,
      0.8
    )
  } catch (e) {
    errors.push(`Suggestions failed: ${e}`)
  }

  // Step 3: Conflict Detection
  try {
    const conflictsInput = timeline
    const conflicts = detectConflicts(timeline)
    stepOrder++
    steps.push({
      order: stepOrder,
      type: 'RESOLVE',
      description: `Detected ${conflicts.length} conflicts`,
      inputHash: hashTimelineState(conflictsInput),
      outputHash: hashTimelineState(conflicts),
      success: true,
    })

    builder.addNode(
      'RESOLVE',
      'detect_conflicts',
      conflictsInput,
      conflicts,
      `Detected ${conflicts.length} conflicts`,
      1.0
    )
  } catch (e) {
    errors.push(`Conflict detection failed: ${e}`)
  }

  const trace = builder.build()
  storeTrace(trace)

  return {
    traceId: trace.id,
    success: errors.length === 0,
    steps,
    finalState: timeline,
    errors,
  }
}
