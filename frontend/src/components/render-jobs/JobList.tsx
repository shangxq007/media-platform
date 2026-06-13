import { useState } from 'react'
import type { RenderJobSummary } from '../../api/render-jobs'

const STATUS_OPTIONS = ['ALL', 'QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'] as const
type StatusFilter = typeof STATUS_OPTIONS[number]

interface Props {
  jobs: RenderJobSummary[]
  selectedJobId: string | null
  onSelect: (jobId: string) => void
}

export function JobList({ jobs, selectedJobId, onSelect }: Props) {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')

  const filtered = statusFilter === 'ALL'
    ? jobs
    : jobs.filter(j => j.status === statusFilter)

  return (
    <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
      <div className="flex items-center justify-between mb-4">
        <h2 className="text-lg font-semibold">Render Jobs</h2>
        <span className="text-xs text-gray-500">{filtered.length} / {jobs.length}</span>
      </div>

      {/* Status filter */}
      <div className="flex flex-wrap gap-1 mb-3">
        {STATUS_OPTIONS.map(status => (
          <button
            key={status}
            onClick={() => setStatusFilter(status)}
            className={`px-2 py-1 text-xs rounded ${
              statusFilter === status
                ? 'bg-blue-600 text-white'
                : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
            }`}
          >
            {status}
          </button>
        ))}
      </div>

      {/* Job list */}
      {filtered.length === 0 ? (
        <div className="text-sm text-gray-500 py-4 text-center">
          {jobs.length === 0 ? 'No render jobs yet' : `No ${statusFilter} jobs`}
        </div>
      ) : (
        <div className="space-y-1 max-h-96 overflow-y-auto">
          {filtered.map(job => (
            <button
              key={job.id}
              onClick={() => onSelect(job.id)}
              className={`w-full text-left rounded px-3 py-2 text-sm transition-colors ${
                selectedJobId === job.id
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-800 text-gray-300 hover:bg-gray-700'
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs truncate max-w-32">{job.id}</span>
                <StatusBadge status={job.status} />
              </div>
              <div className="flex items-center justify-between mt-1">
                <span className="text-xs text-gray-500">{job.profile}</span>
                <span className="text-xs text-gray-500 truncate max-w-24">{job.projectId}</span>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  let color: string
  switch (status) {
    case 'COMPLETED':
      color = 'bg-green-900 text-green-300'
      break
    case 'FAILED':
      color = 'bg-red-900 text-red-300'
      break
    case 'QUEUED':
      color = 'bg-yellow-900 text-yellow-300'
      break
    case 'PROCESSING':
      color = 'bg-blue-900 text-blue-300'
      break
    case 'CANCELLED':
      color = 'bg-gray-700 text-gray-400'
      break
    default:
      color = 'bg-gray-700 text-gray-400'
  }

  return (
    <span className={`text-xs px-1.5 py-0.5 rounded ${color}`}>
      {status}
    </span>
  )
}
