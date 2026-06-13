// =============================================================================
// Observability API Client
// =============================================================================
// Read-only API client for observability data.
// =============================================================================

import api from '../../api/index'
import type {
  RenderJobSummaryObs,
  RenderJobDetailObs,
  TraceGraph,
  MetricsSummary,
  ProviderMetrics,
  FailureMetrics,
} from '../models/types'

// ---------------------------------------------------------------------------
// Render Jobs
// ---------------------------------------------------------------------------

export async function fetchRenderJobs(statusFilter?: string): Promise<RenderJobSummaryObs[]> {
  const params = statusFilter && statusFilter !== 'ALL' ? { status: statusFilter } : {}
  const { data } = await api.get('/render/jobs', { params })
  return data ?? []
}

export async function fetchRenderJobDetail(jobId: string): Promise<RenderJobDetailObs> {
  const { data } = await api.get(`/render/jobs/${jobId}`)
  return data
}

// ---------------------------------------------------------------------------
// Tracing
// ---------------------------------------------------------------------------

export async function fetchTraceByJobId(jobId: string): Promise<TraceGraph> {
  const { data } = await api.get(`/observability/job/${jobId}/trace`)
  return data
}

export async function fetchTraceByTraceId(traceId: string): Promise<TraceGraph> {
  const { data } = await api.get(`/observability/trace/${traceId}`)
  return data
}

// ---------------------------------------------------------------------------
// Metrics
// ---------------------------------------------------------------------------

export async function fetchMetricsSummary(): Promise<MetricsSummary> {
  const { data } = await api.get('/observability/metrics/summary')
  return data
}

export async function fetchProviderMetrics(): Promise<ProviderMetrics[]> {
  const { data } = await api.get('/observability/metrics/providers')
  return data ?? []
}

export async function fetchFailureMetrics(): Promise<FailureMetrics> {
  const { data } = await api.get('/observability/metrics/failures')
  return data
}
