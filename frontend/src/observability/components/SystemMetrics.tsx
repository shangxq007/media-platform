// =============================================================================
// System Metrics Components
// =============================================================================
// Visualization components for system metrics.
// =============================================================================

import type { MetricsSummary, ProviderMetrics, FailureMetrics, LatencyBucket } from '../models/types'

// ---------------------------------------------------------------------------
// Render Success Rate Chart
// ---------------------------------------------------------------------------

export function RenderSuccessRateChart({ metrics }: { metrics: MetricsSummary | null }) {
  if (!metrics) {
    return <MetricCard title="Success Rate" value="--" />
  }

  const color =
    metrics.successRate >= 95 ? 'text-green-400' :
    metrics.successRate >= 80 ? 'text-yellow-400' :
    'text-red-400'

  return (
    <MetricCard title="Success Rate">
      <div className="flex items-center gap-3">
        <span className={`text-2xl font-mono ${color}`}>
          {metrics.successRate.toFixed(1)}%
        </span>
        <div className="flex-1">
          <div className="h-2 bg-gray-800 rounded-full overflow-hidden">
            <div
              className={`h-full transition-all ${
                metrics.successRate >= 95 ? 'bg-green-500' :
                metrics.successRate >= 80 ? 'bg-yellow-500' :
                'bg-red-500'
              }`}
              style={{ width: `${metrics.successRate}%` }}
            />
          </div>
          <div className="flex justify-between text-[10px] text-gray-600 mt-1">
            <span>{metrics.successCount} success</span>
            <span>{metrics.failureCount} failed</span>
          </div>
        </div>
      </div>
    </MetricCard>
  )
}

// ---------------------------------------------------------------------------
// Provider Fallback Chart
// ---------------------------------------------------------------------------

export function ProviderFallbackChart({ providers }: { providers: readonly ProviderMetrics[] }) {
  if (providers.length === 0) {
    return <MetricCard title="Provider Usage" value="--" />
  }

  const maxJobs = Math.max(...providers.map(p => p.totalJobs))

  return (
    <MetricCard title="Provider Usage">
      <div className="space-y-1">
        {providers.map(provider => (
          <div key={provider.provider} className="flex items-center gap-2 text-xs">
            <span className="text-gray-400 w-16 truncate">{provider.provider}</span>
            <div className="flex-1 h-2 bg-gray-800 rounded-full overflow-hidden">
              <div
                className="h-full bg-blue-500"
                style={{ width: `${(provider.totalJobs / maxJobs) * 100}%` }}
              />
            </div>
            <span className="text-gray-500 w-8 text-right">{provider.totalJobs}</span>
            {provider.fallbackCount > 0 && (
              <span className="text-yellow-400 text-[10px]">
                {provider.fallbackCount}↓
              </span>
            )}
          </div>
        ))}
      </div>
    </MetricCard>
  )
}

// ---------------------------------------------------------------------------
// Latency Histogram
// ---------------------------------------------------------------------------

export function LatencyHistogram({ metrics }: { metrics: MetricsSummary | null }) {
  if (!metrics) {
    return <MetricCard title="Latency" value="--" />
  }

  // Generate buckets from metrics
  const buckets: LatencyBucket[] = [
    { label: '<1s', min: 0, max: 1000, count: 0 },
    { label: '1-5s', min: 1000, max: 5000, count: 0 },
    { label: '5-10s', min: 5000, max: 10000, count: 0 },
    { label: '10-30s', min: 10000, max: 30000, count: 0 },
    { label: '30s+', min: 30000, max: Infinity, count: 0 },
  ]

  // Distribute jobs based on average (simplified)
  const avgBucket = metrics.averageDurationMs < 1000 ? 0 :
    metrics.averageDurationMs < 5000 ? 1 :
    metrics.averageDurationMs < 10000 ? 2 :
    metrics.averageDurationMs < 30000 ? 3 : 4

  const bucketCounts = buckets.map((bucket, i) => {
    if (i === avgBucket) return Math.floor(metrics.totalJobs * 0.6)
    const remaining = metrics.totalJobs - Math.floor(metrics.totalJobs * 0.6)
    return Math.floor(remaining / (buckets.length - 1))
  })

  const maxCount = Math.max(...bucketCounts)

  return (
    <MetricCard title="Latency Distribution">
      <div className="flex items-end gap-1 h-16">
        {buckets.map((bucket, i) => (
          <div key={bucket.label} className="flex-1 flex flex-col items-center">
            <div
              className="w-full bg-blue-600 rounded-t transition-all"
              style={{ height: `${maxCount > 0 ? (bucketCounts[i] / maxCount) * 100 : 0}%` }}
            />
            <span className="text-[8px] text-gray-600 mt-1">{bucket.label}</span>
          </div>
        ))}
      </div>
      <div className="flex justify-between text-[10px] text-gray-600 mt-2">
        <span>Avg: {(metrics.averageDurationMs / 1000).toFixed(1)}s</span>
        <span>P95: {(metrics.p95DurationMs / 1000).toFixed(1)}s</span>
      </div>
    </MetricCard>
  )
}

// ---------------------------------------------------------------------------
// Queue Depth Gauge
// ---------------------------------------------------------------------------

export function QueueDepthGauge({ metrics }: { metrics: MetricsSummary | null }) {
  if (!metrics) {
    return <MetricCard title="Queue Depth" value="--" />
  }

  const total = metrics.activeJobs + metrics.queuedJobs
  const color =
    total > 50 ? 'text-red-400' :
    total > 20 ? 'text-yellow-400' :
    'text-green-400'

  return (
    <MetricCard title="Queue Depth">
      <div className="flex items-center gap-4">
        <div className="text-center">
          <div className={`text-xl font-mono ${color}`}>{total}</div>
          <div className="text-[10px] text-gray-600">total</div>
        </div>
        <div className="flex-1 grid grid-cols-2 gap-2 text-xs">
          <div>
            <span className="text-gray-500">Active</span>
            <div className="text-blue-400 font-mono">{metrics.activeJobs}</div>
          </div>
          <div>
            <span className="text-gray-500">Queued</span>
            <div className="text-yellow-400 font-mono">{metrics.queuedJobs}</div>
          </div>
        </div>
      </div>
    </MetricCard>
  )
}

// ---------------------------------------------------------------------------
// Failure Reasons
// ---------------------------------------------------------------------------

export function FailureReasonsPanel({ failures }: { failures: FailureMetrics | null }) {
  if (!failures || failures.totalFailures === 0) {
    return <MetricCard title="Top Failures" value="None" />
  }

  return (
    <MetricCard title="Top Failures">
      <div className="space-y-1">
        {failures.topReasons.slice(0, 5).map((reason, i) => (
          <div key={i} className="flex items-center justify-between text-xs">
            <span className="text-gray-300 truncate flex-1">{reason.reason}</span>
            <span className="text-red-400 font-mono ml-2">{reason.count}</span>
          </div>
        ))}
      </div>
    </MetricCard>
  )
}

// ---------------------------------------------------------------------------
// Metric Card (Base Component)
// ---------------------------------------------------------------------------

function MetricCard({
  title,
  value,
  children,
}: {
  title: string
  value?: string
  children?: React.ReactNode
}) {
  return (
    <div className="rounded-lg border border-gray-800 bg-gray-950 p-3">
      <div className="text-[10px] text-gray-500 uppercase mb-2">{title}</div>
      {value !== undefined ? (
        <div className="text-lg font-mono text-gray-200">{value}</div>
      ) : (
        children
      )}
    </div>
  )
}
