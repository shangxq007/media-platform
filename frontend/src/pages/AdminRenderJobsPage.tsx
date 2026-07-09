import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from '@tanstack/react-router'
import api from '../api/index'

const TENANT_ID = 'ten_307b8956545642a9a45097f2f480a7b4'
const PROJECT_ID = 'prj_6802ca7a12c24aafa31cf77fa63890be'

interface MetricsData {
  projectId: string
  lookback: string
  generatedAt: string
  stateCounts: Record<string, number>
  health: Record<string, number>
  warnings: string[]
}

function useMetrics() {
  return useQuery({
    queryKey: ['admin-metrics', PROJECT_ID],
    queryFn: () => api.get(`/render/projects/${PROJECT_ID}/jobs/metrics?lookback=PT24H`).then(r => r.data as MetricsData),
  })
}

function MetricCard({ label, value, color = 'text-white' }: { label: string; value: number; color?: string }) {
  return (
    <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
      <div className="text-sm text-gray-400">{label}</div>
      <div className={`text-2xl font-bold ${color}`}>{value}</div>
    </div>
  )
}

function WarningBadge({ warning }: { warning: string }) {
  const colors: Record<string, string> = {
    'STALE_EXECUTING_JOBS_PRESENT': 'bg-red-900 text-red-200',
    'RETRY_ELIGIBLE_FAILED_JOBS_PRESENT': 'bg-yellow-900 text-yellow-200',
    'RETRY_EXHAUSTED_JOBS_PRESENT': 'bg-orange-900 text-orange-200',
    'OLD_QUEUED_JOB': 'bg-purple-900 text-purple-200',
  }
  return (
    <span className={`inline-block rounded px-2 py-1 text-xs ${colors[warning] || 'bg-gray-800 text-gray-300'}`}>
      {warning.replace(/_/g, ' ')}
    </span>
  )
}

export default function AdminRenderJobsPage() {
  const { data: metrics, isLoading, error } = useMetrics()
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL')

  const stateCounts = metrics?.stateCounts || {}
  const health = metrics?.health || {}
  const warnings = metrics?.warnings || []

  const statusFilters = ['ALL', 'QUEUED', 'EXECUTING', 'COMPLETED', 'FAILED']

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Header */}
      <div className="border-b border-gray-800 px-6 py-4">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold">Render Jobs</h1>
            <p className="text-sm text-gray-400">Manage and inspect render job execution state</p>
          </div>
          <span className="rounded bg-blue-900 px-2 py-1 text-xs text-blue-200">Admin Console</span>
        </div>
      </div>

      <div className="p-6">
        {/* Warnings */}
        {warnings.length > 0 && (
          <div className="mb-6 flex flex-wrap gap-2">
            {warnings.map((w, i) => <WarningBadge key={i} warning={w} />)}
          </div>
        )}

        {/* Metrics Cards */}
        <div className="mb-6 grid grid-cols-2 gap-4 md:grid-cols-4">
          {isLoading ? (
            <div className="col-span-4 text-center text-gray-400">Loading metrics...</div>
          ) : error ? (
            <div className="col-span-4 text-center text-red-400">Failed to load metrics</div>
          ) : (
            <>
              <MetricCard label="Queued" value={stateCounts.queued || 0} />
              <MetricCard label="Executing" value={stateCounts.executing || 0} />
              <MetricCard label="Completed" value={stateCounts.completed || 0} color="text-green-400" />
              <MetricCard label="Failed" value={stateCounts.failed || 0} color="text-red-400" />
              <MetricCard label="Stale Executing" value={health.staleExecuting || 0} color="text-red-400" />
              <MetricCard label="Retry Eligible" value={health.retryEligibleFailed || 0} color="text-yellow-400" />
              <MetricCard label="Retry Exhausted" value={health.retryExhausted || 0} color="text-orange-400" />
              <MetricCard label="Total" value={Object.values(stateCounts).reduce((a, b) => a + b, 0)} />
            </>
          )}
        </div>

        {/* Status Filters */}
        <div className="mb-4 flex gap-2">
          {statusFilters.map(status => (
            <button
              key={status}
              onClick={() => setSelectedStatus(status)}
              className={`rounded px-3 py-1 text-sm ${
                selectedStatus === status
                  ? 'bg-blue-600 text-white'
                  : 'bg-gray-800 text-gray-400 hover:bg-gray-700'
              }`}
            >
              {status}
            </button>
          ))}
        </div>

        {/* Info */}
        <div className="rounded-lg border border-gray-800 bg-gray-900 p-4">
          <h2 className="mb-2 font-semibold">RenderJob List</h2>
          <p className="text-sm text-gray-400">
            RenderJob list API is not yet available. This view shows metrics summary only.
          </p>
          <p className="mt-2 text-sm text-gray-400">
            For detailed per-job lifecycle events, visit{' '}
            <Link to="/dev/timeline-git" className="text-blue-400 hover:underline">
              /dev/timeline-git
            </Link>
          </p>
        </div>
      </div>
    </div>
  )
}
