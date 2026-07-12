import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import api from '../api/index'

const PROJECT_ID = 'prj_6802ca7a12c24aafa31cf77fa63890be'

// User-friendly status mapping
const STATUS_MAP: Record<string, { label: string; description: string; color: string }> = {
  'QUEUED': { label: 'Waiting', description: 'Your render is waiting to start.', color: 'text-blue-400' },
  'EXECUTING': { label: 'Rendering', description: 'Your video is being rendered.', color: 'text-yellow-400' },
  'COMPLETED': { label: 'Completed', description: 'Your render is ready.', color: 'text-green-400' },
  'FAILED': { label: 'Failed', description: 'The render could not be completed.', color: 'text-red-400' },
}

function mapStatus(status: string, retryable?: boolean): { label: string; description: string; color: string } {
  if (status === 'FAILED' && retryable) {
    return { label: 'Failed, can retry', description: 'The render failed, but you can try again.', color: 'text-orange-400' }
  }
  return STATUS_MAP[status] || { label: status, description: '', color: 'text-gray-400' }
}

function useMetrics() {
  return useQuery({
    queryKey: ['user-metrics', PROJECT_ID],
    queryFn: () => api.get(`/render/projects/${PROJECT_ID}/jobs/metrics?lookback=PT7D`).then(r => r.data),
  })
}

function StatusBadge({ status, retryable }: { status: string; retryable?: boolean }) {
  const { label, color } = mapStatus(status, retryable)
  return <span className={`${color} font-medium`}>{label}</span>
}

export default function UserRenderHistoryPage() {
  const { data: metrics, isLoading } = useMetrics()
  const [selectedFilter, setSelectedFilter] = useState('ALL')

  const stateCounts = metrics?.stateCounts || {}

  const filters = ['ALL', 'Waiting', 'Rendering', 'Completed', 'Failed']

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Header */}
      <div className="border-b border-gray-800 px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold">My Renders</h1>
            <p className="text-sm text-gray-400">View your render progress and completed videos</p>
          </div>
          <span className="rounded bg-green-900 px-2 py-1 text-xs text-green-200">User App</span>
        </div>
      </div>

      <div className="p-6">
        {/* Summary Cards */}
        <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
            <div className="text-sm text-gray-400">Waiting</div>
            <div className="text-2xl font-bold text-blue-400">{stateCounts.queued || 0}</div>
          </div>
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
            <div className="text-sm text-gray-400">Rendering</div>
            <div className="text-2xl font-bold text-yellow-400">{stateCounts.executing || 0}</div>
          </div>
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
            <div className="text-sm text-gray-400">Completed</div>
            <div className="text-2xl font-bold text-green-400">{stateCounts.completed || 0}</div>
          </div>
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
            <div className="text-sm text-gray-400">Failed</div>
            <div className="text-2xl font-bold text-red-400">{stateCounts.failed || 0}</div>
          </div>
        </div>

        {/* Filters */}
        <div className="mb-4 flex gap-2">
          {filters.map(f => (
            <button
              key={f}
              onClick={() => setSelectedFilter(f)}
              className={`rounded px-3 py-1 text-sm ${
                selectedFilter === f
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
              }`}
            >
              {f}
            </button>
          ))}
        </div>

        {/* Content */}
        {isLoading ? (
          <div className="text-center text-gray-400">Loading your renders...</div>
        ) : (
          <div className="rounded-lg border border-gray-800 bg-gray-900 p-6 text-center">
            <div className="mb-2 text-lg font-semibold">No renders yet</div>
            <p className="text-sm text-gray-400">
              Render a timeline revision to see results here.
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
