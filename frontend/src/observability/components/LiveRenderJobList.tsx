// =============================================================================
// LiveRenderJobList Component
// =============================================================================
// Real-time render job list with status filtering and failure highlighting.
// =============================================================================

import type { RenderJobSummaryObs, RenderJobStatus } from '../models/types'

interface LiveRenderJobListProps {
  jobs: readonly RenderJobSummaryObs[]
  selectedJobId: string | null
  onSelectJob: (jobId: string) => void
  statusFilter: string
  onStatusFilterChange: (filter: string) => void
}

const STATUS_FILTERS = ['ALL', 'RUNNING', 'QUEUED', 'COMPLETED', 'FAILED', 'CANCELLED']

const STATUS_COLORS: Record<RenderJobStatus, string> = {
  QUEUED: 'bg-yellow-900/50 text-yellow-400',
  PROCESSING: 'bg-blue-900/50 text-blue-400',
  COMPLETED: 'bg-green-900/50 text-green-400',
  FAILED: 'bg-red-900/50 text-red-400',
  CANCELLED: 'bg-gray-800 text-gray-500',
}

export function LiveRenderJobList({
  jobs,
  selectedJobId,
  onSelectJob,
  statusFilter,
  onStatusFilterChange,
}: LiveRenderJobListProps) {
  return (
    <div className="flex flex-col h-full">
      {/* Filter Bar */}
      <div className="flex flex-wrap gap-1 p-2 border-b border-gray-800">
        {STATUS_FILTERS.map(filter => (
          <button
            key={filter}
            type="button"
            onClick={() => onStatusFilterChange(filter)}
            className={`rounded px-2 py-0.5 text-[10px] transition-colors ${
              statusFilter === filter
                ? 'bg-blue-600 text-white'
                : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
            }`}
          >
            {filter}
          </button>
        ))}
      </div>

      {/* Failure Rate Indicator */}
      <div className="px-2 py-1 border-b border-gray-800">
        <FailureRateIndicator jobs={jobs} />
      </div>

      {/* Job List */}
      <div className="flex-1 overflow-y-auto">
        {jobs.length === 0 ? (
          <div className="p-4 text-center text-xs text-gray-600">
            No render jobs found
          </div>
        ) : (
          jobs.map(job => (
            <JobListItem
              key={job.id}
              job={job}
              isSelected={job.id === selectedJobId}
              onSelect={() => onSelectJob(job.id)}
            />
          ))
        )}
      </div>
    </div>
  )
}

// ---------------------------------------------------------------------------
// Job List Item
// ---------------------------------------------------------------------------

function JobListItem({
  job,
  isSelected,
  onSelect,
}: {
  job: RenderJobSummaryObs
  isSelected: boolean
  onSelect: () => void
}) {
  const statusColor = STATUS_COLORS[job.status] ?? 'bg-gray-800 text-gray-500'

  return (
    <button
      type="button"
      onClick={onSelect}
      className={`w-full text-left px-3 py-2 border-b border-gray-800/50 transition-colors ${
        isSelected ? 'bg-gray-800' : 'hover:bg-gray-900'
      }`}
    >
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs font-mono text-gray-300 truncate">
          {job.id.slice(0, 12)}...
        </span>
        <span className={`rounded px-1.5 py-0.5 text-[10px] ${statusColor}`}>
          {job.status}
        </span>
      </div>
      <div className="flex items-center gap-2 text-[10px] text-gray-500">
        {job.duration != null && (
          <span>{(job.duration / 1000).toFixed(1)}s</span>
        )}
        {job.provider && (
          <span className="text-gray-600">{job.provider}</span>
        )}
        <span className="ml-auto">
          {new Date(job.createdAt).toLocaleTimeString()}
        </span>
      </div>
      {job.failureReason && (
        <div className="mt-1 text-[10px] text-red-400 truncate">
          {job.failureReason}
        </div>
      )}
    </button>
  )
}

// ---------------------------------------------------------------------------
// Failure Rate Indicator
// ---------------------------------------------------------------------------

function FailureRateIndicator({ jobs }: { jobs: readonly RenderJobSummaryObs[] }) {
  const total = jobs.length
  const failures = jobs.filter(j => j.status === 'FAILED').length
  const rate = total > 0 ? (failures / total) * 100 : 0

  const color =
    rate > 20 ? 'text-red-400' :
    rate > 5 ? 'text-yellow-400' :
    'text-green-400'

  return (
    <div className="flex items-center justify-between text-[10px]">
      <span className="text-gray-500">Failure Rate</span>
      <span className={`font-mono ${color}`}>
        {rate.toFixed(1)}% ({failures}/{total})
      </span>
    </div>
  )
}
