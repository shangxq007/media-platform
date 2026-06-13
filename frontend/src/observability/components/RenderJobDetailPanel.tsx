// =============================================================================
// RenderJobDetailPanel Component
// =============================================================================
// Detailed view of a render job with execution steps, provider decisions,
// and error traces.
// =============================================================================

import { useState } from 'react'
import type { RenderJobDetailObs, ExecutionStep, EffectExecution } from '../models/types'

interface RenderJobDetailPanelProps {
  job: RenderJobDetailObs | null
}

const STEP_STATUS_COLORS: Record<string, string> = {
  PENDING: 'text-gray-500',
  RUNNING: 'text-blue-400',
  COMPLETED: 'text-green-400',
  FAILED: 'text-red-400',
}

export function RenderJobDetailPanel({ job }: RenderJobDetailPanelProps) {
  const [expandedSections, setExpandedSections] = useState<Set<string>>(new Set(['overview']))

  if (!job) {
    return (
      <div className="flex items-center justify-center h-full text-xs text-gray-600">
        Select a job to inspect
      </div>
    )
  }

  const toggleSection = (section: string) => {
    setExpandedSections(prev => {
      const next = new Set(prev)
      if (next.has(section)) {
        next.delete(section)
      } else {
        next.add(section)
      }
      return next
    })
  }

  return (
    <div className="overflow-y-auto h-full">
      {/* Header */}
      <div className="px-3 py-2 border-b border-gray-800 bg-gray-900">
        <div className="flex items-center justify-between">
          <span className="text-xs font-mono text-gray-200">{job.id}</span>
          <StatusBadge status={job.status} />
        </div>
        {job.failureReason && (
          <div className="mt-1 text-xs text-red-400">{job.failureReason}</div>
        )}
      </div>

      {/* Overview Section */}
      <CollapsibleSection
        title="Overview"
        isOpen={expandedSections.has('overview')}
        onToggle={() => toggleSection('overview')}
      >
        <div className="grid grid-cols-2 gap-2 text-xs">
          <div>
            <span className="text-gray-500">Project</span>
            <div className="text-gray-300">{job.projectId}</div>
          </div>
          <div>
            <span className="text-gray-500">Duration</span>
            <div className="text-gray-300">
              {job.duration != null ? `${(job.duration / 1000).toFixed(2)}s` : 'N/A'}
            </div>
          </div>
          <div>
            <span className="text-gray-500">Trace ID</span>
            <div className="text-gray-300 font-mono">{job.traceId ?? 'N/A'}</div>
          </div>
          <div>
            <span className="text-gray-500">Created</span>
            <div className="text-gray-300">{new Date(job.createdAt).toLocaleString()}</div>
          </div>
        </div>
      </CollapsibleSection>

      {/* Provider Decision */}
      {job.providerSelection && (
        <CollapsibleSection
          title="Provider Decision"
          isOpen={expandedSections.has('provider')}
          onToggle={() => toggleSection('provider')}
        >
          <ProviderDecisionView decision={job.providerSelection} />
        </CollapsibleSection>
      )}

      {/* Execution Steps */}
      <CollapsibleSection
        title={`Execution Steps (${job.executionSteps.length})`}
        isOpen={expandedSections.has('steps')}
        onToggle={() => toggleSection('steps')}
      >
        <ExecutionStepsView steps={job.executionSteps} />
      </CollapsibleSection>

      {/* Effect Executions */}
      {job.effectExecutions.length > 0 && (
        <CollapsibleSection
          title={`Effects (${job.effectExecutions.length})`}
          isOpen={expandedSections.has('effects')}
          onToggle={() => toggleSection('effects')}
        >
          <EffectExecutionsView effects={job.effectExecutions} />
        </CollapsibleSection>
      )}

      {/* Timeline Snapshot */}
      {job.timelineSnapshot != null && (
        <CollapsibleSection
          title="Timeline Snapshot"
          isOpen={expandedSections.has('timeline')}
          onToggle={() => toggleSection('timeline')}
        >
          <JsonViewer data={job.timelineSnapshot} />
        </CollapsibleSection>
      )}

      {/* Error Trace */}
      {job.errorTrace && (
        <CollapsibleSection
          title="Error Trace"
          isOpen={expandedSections.has('error')}
          onToggle={() => toggleSection('error')}
        >
          <pre className="text-xs text-red-300 bg-red-950/30 p-2 rounded overflow-x-auto">
            {job.errorTrace}
          </pre>
        </CollapsibleSection>
      )}
    </div>
  )
}

// ---------------------------------------------------------------------------
// Sub-Components
// ---------------------------------------------------------------------------

function StatusBadge({ status }: { status: string }) {
  const colors: Record<string, string> = {
    QUEUED: 'bg-yellow-900/50 text-yellow-400',
    PROCESSING: 'bg-blue-900/50 text-blue-400',
    COMPLETED: 'bg-green-900/50 text-green-400',
    FAILED: 'bg-red-900/50 text-red-400',
    CANCELLED: 'bg-gray-800 text-gray-500',
  }

  return (
    <span className={`rounded px-2 py-0.5 text-[10px] ${colors[status] ?? 'bg-gray-800 text-gray-500'}`}>
      {status}
    </span>
  )
}

function CollapsibleSection({
  title,
  isOpen,
  onToggle,
  children,
}: {
  title: string
  isOpen: boolean
  onToggle: () => void
  children: React.ReactNode
}) {
  return (
    <div className="border-b border-gray-800">
      <button
        type="button"
        onClick={onToggle}
        className="w-full flex items-center justify-between px-3 py-2 text-xs font-medium text-gray-400 hover:text-gray-200 transition-colors"
      >
        <span>{title}</span>
        <span className="text-gray-600">{isOpen ? '−' : '+'}</span>
      </button>
      {isOpen && <div className="px-3 pb-3">{children}</div>}
    </div>
  )
}

function ProviderDecisionView({ decision }: { decision: { selectedProvider: string; candidates: readonly string[]; reason: string; fallbackTriggered: boolean; fallbackFrom: string | null; fallbackReason: string | null } }) {
  return (
    <div className="space-y-2 text-xs">
      <div className="flex items-center gap-2">
        <span className="text-gray-500">Selected:</span>
        <span className="text-green-400 font-mono">{decision.selectedProvider}</span>
      </div>
      <div>
        <span className="text-gray-500">Candidates:</span>
        <div className="flex flex-wrap gap-1 mt-1">
          {decision.candidates.map(c => (
            <span key={c} className="rounded bg-gray-800 px-1.5 py-0.5 text-[10px] text-gray-400">
              {c}
            </span>
          ))}
        </div>
      </div>
      <div>
        <span className="text-gray-500">Reason:</span>
        <div className="text-gray-300">{decision.reason}</div>
      </div>
      {decision.fallbackTriggered && (
        <div className="rounded bg-yellow-950/30 p-2">
          <div className="text-yellow-400">Fallback Triggered</div>
          <div className="text-gray-400">From: {decision.fallbackFrom}</div>
          <div className="text-gray-400">Reason: {decision.fallbackReason}</div>
        </div>
      )}
    </div>
  )
}

function ExecutionStepsView({ steps }: { steps: readonly ExecutionStep[] }) {
  return (
    <div className="space-y-1">
      {steps.map((step, i) => (
        <ExecutionStepItem key={step.id} step={step} index={i} />
      ))}
    </div>
  )
}

function ExecutionStepItem({ step, index }: { step: ExecutionStep; index: number }) {
  const statusColor = STEP_STATUS_COLORS[step.status] ?? 'text-gray-500'

  return (
    <div className="flex items-start gap-2 text-xs">
      <span className="text-gray-600 w-4 text-right">{index + 1}</span>
      <div className={`w-2 h-2 rounded-full mt-1 ${statusColor.replace('text-', 'bg-')}`} />
      <div className="flex-1">
        <div className="flex items-center gap-2">
          <span className="text-gray-300">{step.name}</span>
          <span className={`text-[10px] ${statusColor}`}>{step.status}</span>
          {step.durationMs != null && (
            <span className="text-gray-600 text-[10px]">{step.durationMs}ms</span>
          )}
        </div>
        {step.error && (
          <div className="text-red-400 text-[10px] mt-0.5">{step.error}</div>
        )}
      </div>
    </div>
  )
}

function EffectExecutionsView({ effects }: { effects: readonly EffectExecution[] }) {
  return (
    <div className="space-y-1">
      {effects.map(effect => (
        <EffectItem key={effect.id} effect={effect} />
      ))}
    </div>
  )
}

function EffectItem({ effect }: { effect: EffectExecution }) {
  const statusColor = STEP_STATUS_COLORS[effect.status] ?? 'text-gray-500'

  return (
    <div className="rounded bg-gray-900 p-2 text-xs">
      <div className="flex items-center justify-between">
        <span className="text-gray-200">{effect.effectName}</span>
        <span className={`text-[10px] ${statusColor}`}>{effect.status}</span>
      </div>
      <div className="text-[10px] text-gray-500 font-mono">{effect.effectKey}</div>
      {effect.durationMs != null && (
        <div className="text-[10px] text-gray-600">{effect.durationMs}ms</div>
      )}
      {effect.error && (
        <div className="text-[10px] text-red-400 mt-1">{effect.error}</div>
      )}
    </div>
  )
}

function JsonViewer({ data }: { data: unknown }) {
  const [expanded, setExpanded] = useState(false)
  const json = JSON.stringify(data, null, 2)
  const preview = json.length > 200 ? json.slice(0, 200) + '...' : json

  return (
    <div className="text-xs">
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className="text-blue-400 hover:text-blue-300 text-[10px] mb-1"
      >
        {expanded ? 'Collapse' : 'Expand'} JSON
      </button>
      <pre className="bg-gray-900 p-2 rounded overflow-x-auto text-[10px] text-gray-400">
        {expanded ? json : preview}
      </pre>
    </div>
  )
}
