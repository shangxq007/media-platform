// =============================================================================
// ObservabilityDashboard Page
// =============================================================================
// Production-grade observability dashboard for render platform.
// 3-column layout: Jobs Stream | Job Inspector | System Metrics
// =============================================================================

import { useState, useCallback } from 'react'
import { useRenderJobs, useRenderJobDetail, useMetricsSummary, useProviderMetrics, useFailureMetrics } from '../observability/hooks/useObservability'
import { LiveRenderJobList } from '../observability/components/LiveRenderJobList'
import { RenderJobDetailPanel } from '../observability/components/RenderJobDetailPanel'
import {
  RenderSuccessRateChart,
  ProviderFallbackChart,
  LatencyHistogram,
  QueueDepthGauge,
  FailureReasonsPanel,
} from '../observability/components/SystemMetrics'

export function ObservabilityDashboard() {
  const [selectedJobId, setSelectedJobId] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [autoRefresh, setAutoRefresh] = useState(true)

  // Data fetching
  const { data: jobs, isLoading: jobsLoading } = useRenderJobs(statusFilter)
  const { data: jobDetail } = useRenderJobDetail(selectedJobId)
  const { data: metrics } = useMetricsSummary()
  const { data: providers } = useProviderMetrics()
  const { data: failures } = useFailureMetrics()

  const handleSelectJob = useCallback((jobId: string) => {
    setSelectedJobId(jobId)
  }, [])

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      {/* Header */}
      <div className="flex items-center justify-between px-4 py-3 border-b border-gray-800 bg-gray-900">
        <div>
          <h1 className="text-lg font-bold">Observability Dashboard</h1>
          <p className="text-xs text-gray-400">Render pipeline monitoring and debugging</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={() => setAutoRefresh(!autoRefresh)}
            className={`rounded px-3 py-1 text-xs transition-colors ${
              autoRefresh
                ? 'bg-green-900/50 text-green-400'
                : 'bg-gray-800 text-gray-400'
            }`}
          >
            {autoRefresh ? '● Live' : '○ Paused'}
          </button>
          <a
            href="/"
            className="rounded bg-gray-800 px-3 py-1 text-xs text-gray-300 hover:bg-gray-700 transition-colors"
          >
            Back to Editor
          </a>
        </div>
      </div>

      {/* 3-Column Layout */}
      <div className="flex h-[calc(100vh-52px)]">
        {/* Left Column: Jobs Stream */}
        <div className="w-80 flex-shrink-0 border-r border-gray-800 flex flex-col">
          <div className="px-3 py-2 border-b border-gray-800 bg-gray-900">
            <h2 className="text-xs font-medium text-gray-400">Render Jobs Stream</h2>
            <div className="text-[10px] text-gray-600">
              {jobs?.length ?? 0} jobs · {autoRefresh ? 'auto-refreshing' : 'paused'}
            </div>
          </div>
          <div className="flex-1 overflow-hidden">
            {jobsLoading ? (
              <div className="p-4 text-center text-xs text-gray-600">Loading...</div>
            ) : (
              <LiveRenderJobList
                jobs={jobs ?? []}
                selectedJobId={selectedJobId}
                onSelectJob={handleSelectJob}
                statusFilter={statusFilter}
                onStatusFilterChange={setStatusFilter}
              />
            )}
          </div>
        </div>

        {/* Center Column: Job Inspector */}
        <div className="flex-1 flex flex-col overflow-hidden">
          <div className="px-3 py-2 border-b border-gray-800 bg-gray-900">
            <h2 className="text-xs font-medium text-gray-400">Job Inspector</h2>
            {selectedJobId && (
              <div className="text-[10px] text-gray-600 font-mono">{selectedJobId}</div>
            )}
          </div>
          <div className="flex-1 overflow-hidden">
            <RenderJobDetailPanel job={jobDetail ?? null} />
          </div>
        </div>

        {/* Right Column: System Metrics */}
        <div className="w-80 flex-shrink-0 border-l border-gray-800 flex flex-col">
          <div className="px-3 py-2 border-b border-gray-800 bg-gray-900">
            <h2 className="text-xs font-medium text-gray-400">System Metrics</h2>
            <div className="text-[10px] text-gray-600">
              {metrics?.totalJobs ?? 0} total jobs
            </div>
          </div>
          <div className="flex-1 overflow-y-auto p-3 space-y-3">
            <RenderSuccessRateChart metrics={metrics ?? null} />
            <QueueDepthGauge metrics={metrics ?? null} />
            <LatencyHistogram metrics={metrics ?? null} />
            <ProviderFallbackChart providers={providers ?? []} />
            <FailureReasonsPanel failures={failures ?? null} />
          </div>
        </div>
      </div>
    </div>
  )
}
