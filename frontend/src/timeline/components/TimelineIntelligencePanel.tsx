// =============================================================================
// TimelineIntelligencePanel Component
// =============================================================================
// Displays timeline analysis results, issues, and suggestions.
// Allows one-click application of suggestions and auto-fixes.
// Includes decision tracing, explainability, and replay features.
// =============================================================================

import { useState, useCallback } from 'react'
import { useTimelineStore } from '../store/timelineStore'
import {
  analyzeTimeline,
  generateSuggestions,
  resolveTimelineConflicts,
  autoStackClips,
  compactTimeline,
  type TimelineAnalysisReport,
  type Suggestion,
  type OverlapStrategy,
} from '../intelligence'
import {
  replayAnalysis,
  replaySuggestions,
  replayConflictResolution,
  replayAutoLayout,
  fullReplay,
  explainSuggestion,
  formatExplanationAsText,
  getAllTraces,
  getRecentTraces,
  clearTraces,
  globalRuleTracker,
  type DecisionTrace,
  type Explanation,
} from '../intelligence/trace'

export function TimelineIntelligencePanel() {
  const { timeline, executeCommand } = useTimelineStore()
  const [report, setReport] = useState<TimelineAnalysisReport | null>(null)
  const [suggestions, setSuggestions] = useState<Suggestion[]>([])
  const [isAnalyzing, setIsAnalyzing] = useState(false)
  const [selectedStrategy, setSelectedStrategy] = useState<OverlapStrategy>('SHIFT_RIGHT')

  // Trace state
  const [showTrace, setShowTrace] = useState(false)
  const [traces, setTraces] = useState<DecisionTrace[]>([])
  const [selectedTrace, setSelectedTrace] = useState<DecisionTrace | null>(null)
  const [explanation, setExplanation] = useState<Explanation | null>(null)
  const [showExplanation, setShowExplanation] = useState(false)

  const handleAnalyze = useCallback(() => {
    setIsAnalyzing(true)

    // Use replay to get trace
    const { report: analysisReport, trace } = replayAnalysis(timeline)
    const { suggestions: generatedSuggestions } = replaySuggestions(timeline)

    setReport(analysisReport)
    setSuggestions(generatedSuggestions)
    setTraces(getRecentTraces(10))
    setIsAnalyzing(false)
  }, [timeline])

  const handleApplySuggestion = useCallback(
    (suggestion: Suggestion) => {
      if (suggestion.command) {
        executeCommand(suggestion.command)
        handleAnalyze()
      }
    },
    [executeCommand, handleAnalyze]
  )

  const handleExplainSuggestion = useCallback(
    (suggestion: Suggestion) => {
      const recentTraces = getRecentTraces(5)
      const suggestTrace = recentTraces.find(t => t.summary.includes('Suggestion'))

      if (suggestTrace) {
        const exp = explainSuggestion(suggestion.id, suggestTrace)
        setExplanation(exp)
        setShowExplanation(true)
      }
    },
    []
  )

  const handleResolveAllConflicts = useCallback(() => {
    const { result, trace } = replayConflictResolution(timeline, selectedStrategy)

    for (const command of result.commands) {
      executeCommand(command)
    }

    setTraces(getRecentTraces(10))
    setTimeout(handleAnalyze, 100)
  }, [timeline, selectedStrategy, executeCommand, handleAnalyze])

  const handleAutoStack = useCallback(() => {
    const { result } = replayAutoLayout(timeline, 'stack')

    for (const command of result.commands) {
      executeCommand(command)
    }

    setTraces(getRecentTraces(10))
    setTimeout(handleAnalyze, 100)
  }, [timeline, executeCommand, handleAnalyze])

  const handleCompact = useCallback(() => {
    const { result } = replayAutoLayout(timeline, 'compact')

    for (const command of result.commands) {
      executeCommand(command)
    }

    setTraces(getRecentTraces(10))
    setTimeout(handleAnalyze, 100)
  }, [timeline, executeCommand, handleAnalyze])

  const handleFullReplay = useCallback(() => {
    const replayResult = fullReplay(timeline)
    setTraces(getRecentTraces(10))
    handleAnalyze()
  }, [timeline, handleAnalyze])

  const handleClearTraces = useCallback(() => {
    clearTraces()
    globalRuleTracker.clear()
    setTraces([])
    setSelectedTrace(null)
  }, [])

  const applicableSuggestions = suggestions.filter(s => s.autoApplicable && s.command)

  return (
    <div className="rounded-lg border border-gray-800 bg-gray-950 overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between px-3 py-2 border-b border-gray-800 bg-gray-900">
        <span className="text-xs font-medium text-gray-400">Timeline Intelligence</span>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => setShowTrace(!showTrace)}
            className={`rounded px-2 py-1 text-xs transition-colors ${
              showTrace ? 'bg-purple-600 text-white' : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
            }`}
            title="Toggle Trace View"
          >
            Trace
          </button>
          <button
            type="button"
            onClick={handleAnalyze}
            disabled={isAnalyzing}
            className="rounded bg-blue-600 px-2 py-1 text-xs font-medium text-white hover:bg-blue-500 disabled:opacity-50 transition-colors"
          >
            {isAnalyzing ? 'Analyzing...' : 'Analyze'}
          </button>
        </div>
      </div>

      {/* Content */}
      <div className="max-h-[500px] overflow-y-auto">
        {/* Quick Actions */}
        <div className="px-3 py-2 border-b border-gray-800">
          <div className="text-[10px] text-gray-500 uppercase mb-2">Quick Actions</div>
          <div className="flex flex-wrap gap-1">
            <ActionButton
              label="Auto-Stack"
              onClick={handleAutoStack}
              disabled={!report || report.issues.filter(i => i.type === 'OVERLAP').length === 0}
            />
            <ActionButton
              label="Compact"
              onClick={handleCompact}
              disabled={!report}
            />
            <ActionButton
              label="Full Replay"
              onClick={handleFullReplay}
              disabled={false}
            />
            <div className="flex items-center gap-1">
              <select
                value={selectedStrategy}
                onChange={(e) => setSelectedStrategy(e.target.value as OverlapStrategy)}
                className="rounded bg-gray-800 px-2 py-1 text-xs text-gray-300 border border-gray-700"
              >
                <option value="SHIFT_RIGHT">Shift Right</option>
                <option value="TRIM_CLIP">Trim Clip</option>
              </select>
              <ActionButton
                label="Resolve All"
                onClick={handleResolveAllConflicts}
                disabled={!report || report.issues.filter(i => i.type === 'OVERLAP').length === 0}
              />
            </div>
          </div>
        </div>

        {/* Explanation Panel */}
        {showExplanation && explanation && (
          <div className="px-3 py-2 border-b border-gray-800 bg-gray-900/50">
            <div className="flex items-center justify-between mb-2">
              <div className="text-[10px] text-purple-400 uppercase">Explanation</div>
              <button
                type="button"
                onClick={() => setShowExplanation(false)}
                className="text-gray-500 hover:text-gray-300 text-xs"
              >
                Close
              </button>
            </div>
            <div className="text-xs text-gray-300 whitespace-pre-wrap">
              {formatExplanationAsText(explanation)}
            </div>
          </div>
        )}

        {/* Trace Panel */}
        {showTrace && (
          <div className="px-3 py-2 border-b border-gray-800">
            <div className="flex items-center justify-between mb-2">
              <div className="text-[10px] text-purple-400 uppercase">
                Decision Traces ({traces.length})
              </div>
              <button
                type="button"
                onClick={handleClearTraces}
                className="text-[10px] text-gray-500 hover:text-gray-300"
              >
                Clear
              </button>
            </div>
            <div className="space-y-1 max-h-40 overflow-y-auto">
              {traces.map(trace => (
                <TraceItem
                  key={trace.id}
                  trace={trace}
                  isSelected={selectedTrace?.id === trace.id}
                  onSelect={() => setSelectedTrace(trace)}
                />
              ))}
              {traces.length === 0 && (
                <div className="text-xs text-gray-600 text-center py-2">
                  No traces recorded
                </div>
              )}
            </div>
          </div>
        )}

        {/* Health Score */}
        {report && (
          <div className="px-3 py-2 border-b border-gray-800">
            <div className="text-[10px] text-gray-500 uppercase mb-1">Health Score</div>
            <HealthBar score={report.overallHealth} />
          </div>
        )}

        {/* Issues */}
        {report && report.issues.length > 0 && (
          <div className="px-3 py-2 border-b border-gray-800">
            <div className="text-[10px] text-gray-500 uppercase mb-2">
              Issues ({report.issues.length})
            </div>
            <div className="space-y-1">
              {report.issues.map(issue => (
                <IssueItem key={issue.id} issue={issue} />
              ))}
            </div>
          </div>
        )}

        {/* Warnings */}
        {report && report.warnings.length > 0 && (
          <div className="px-3 py-2 border-b border-gray-800">
            <div className="text-[10px] text-gray-500 uppercase mb-2">
              Warnings ({report.warnings.length})
            </div>
            <div className="space-y-1">
              {report.warnings.map(warning => (
                <IssueItem key={warning.id} issue={warning} />
              ))}
            </div>
          </div>
        )}

        {/* Suggestions */}
        {applicableSuggestions.length > 0 && (
          <div className="px-3 py-2 border-b border-gray-800">
            <div className="text-[10px] text-gray-500 uppercase mb-2">
              Suggestions ({applicableSuggestions.length})
            </div>
            <div className="space-y-1">
              {applicableSuggestions.map(suggestion => (
                <SuggestionItem
                  key={suggestion.id}
                  suggestion={suggestion}
                  onApply={() => handleApplySuggestion(suggestion)}
                  onExplain={() => handleExplainSuggestion(suggestion)}
                />
              ))}
            </div>
          </div>
        )}

        {/* Track Density */}
        {report && report.trackDensity.length > 0 && (
          <div className="px-3 py-2">
            <div className="text-[10px] text-gray-500 uppercase mb-2">Track Density</div>
            <div className="space-y-1">
              {report.trackDensity.map(density => (
                <TrackDensityItem key={density.trackId} density={density} />
              ))}
            </div>
          </div>
        )}

        {/* Empty State */}
        {!report && (
          <div className="px-3 py-8 text-center text-xs text-gray-600">
            Click "Analyze" to inspect your timeline
          </div>
        )}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Sub-Components
// ---------------------------------------------------------------------------

function ActionButton({
  label,
  onClick,
  disabled,
}: {
  label: string
  onClick: () => void
  disabled: boolean
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className="rounded bg-gray-800 px-2 py-1 text-xs text-gray-300 hover:bg-gray-700 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
    >
      {label}
    </button>
  )
}

function HealthBar({ score }: { score: number }) {
  const color =
    score >= 80 ? 'bg-green-500' :
    score >= 50 ? 'bg-yellow-500' :
    'bg-red-500'

  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-2 bg-gray-800 rounded-full overflow-hidden">
        <div
          className={`h-full ${color} transition-all`}
          style={{ width: `${score}%` }}
        />
      </div>
      <span className="text-xs text-gray-400">{score}%</span>
    </div>
  )
}

function IssueItem({ issue }: { issue: { severity: string; message: string; type: string } }) {
  const icon =
    issue.severity === 'error' ? '!' :
    issue.severity === 'warning' ? '⚠' :
    'i'

  const iconColor =
    issue.severity === 'error' ? 'text-red-400' :
    issue.severity === 'warning' ? 'text-yellow-400' :
    'text-blue-400'

  return (
    <div className="flex items-start gap-2 text-xs">
      <span className={`${iconColor} flex-shrink-0`}>{icon}</span>
      <span className="text-gray-300">{issue.message}</span>
    </div>
  )
}

function SuggestionItem({
  suggestion,
  onApply,
  onExplain,
}: {
  suggestion: Suggestion
  onApply: () => void
  onExplain: () => void
}) {
  const confidenceColor =
    suggestion.confidence === 'high' ? 'text-green-400' :
    suggestion.confidence === 'medium' ? 'text-yellow-400' :
    'text-gray-400'

  return (
    <div className="flex items-start gap-2 text-xs">
      <div className="flex-1">
        <div className="flex items-center gap-2">
          <span className="text-gray-200">{suggestion.title}</span>
          <span className={`${confidenceColor} text-[10px]`}>
            {suggestion.confidence}
          </span>
        </div>
        <div className="text-gray-500">{suggestion.description}</div>
      </div>
      <div className="flex items-center gap-1 flex-shrink-0">
        <button
          type="button"
          onClick={onExplain}
          className="rounded bg-purple-600 px-2 py-0.5 text-[10px] text-white hover:bg-purple-500 transition-colors"
          title="Explain this suggestion"
        >
          ?
        </button>
        {suggestion.autoApplicable && (
          <button
            type="button"
            onClick={onApply}
            className="rounded bg-blue-600 px-2 py-0.5 text-[10px] text-white hover:bg-blue-500 transition-colors"
          >
            Apply
          </button>
        )}
      </div>
    </div>
  )
}

function TraceItem({
  trace,
  isSelected,
  onSelect,
}: {
  trace: DecisionTrace
  isSelected: boolean
  onSelect: () => void
}) {
  const nodeCount = Object.keys(trace.nodes).length
  const duration = trace.completedAt ? trace.completedAt - trace.startedAt : 0

  return (
    <button
      type="button"
      onClick={onSelect}
      className={`w-full text-left rounded px-2 py-1 text-xs transition-colors ${
        isSelected ? 'bg-purple-900/50 border border-purple-700' : 'bg-gray-900 hover:bg-gray-800'
      }`}
    >
      <div className="flex items-center justify-between">
        <span className="text-gray-300 truncate">{trace.summary}</span>
        <span className="text-gray-600 text-[10px] ml-2">{nodeCount} nodes</span>
      </div>
      <div className="text-gray-600 text-[10px]">
        {duration}ms · {new Date(trace.startedAt).toLocaleTimeString()}
      </div>
    </button>
  )
}

function TrackDensityItem({
  density,
}: {
  density: { trackName: string; clipCount: number; coverage: number }
}) {
  return (
    <div className="flex items-center gap-2 text-xs">
      <span className="text-gray-300 w-24 truncate">{density.trackName}</span>
      <div className="flex-1 h-1.5 bg-gray-800 rounded-full overflow-hidden">
        <div
          className="h-full bg-blue-500"
          style={{ width: `${Math.min(density.coverage * 100, 100)}%` }}
        />
      </div>
      <span className="text-gray-500 w-16 text-right">
        {density.clipCount} clips
      </span>
    </div>
  )
}
