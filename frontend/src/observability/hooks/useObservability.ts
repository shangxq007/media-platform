// =============================================================================
// Observability Hooks
// =============================================================================
// React Query hooks for observability data fetching.
// =============================================================================

import { useQuery } from '@tanstack/react-query'
import {
  fetchRenderJobs,
  fetchRenderJobDetail,
  fetchTraceByJobId,
  fetchMetricsSummary,
  fetchProviderMetrics,
  fetchFailureMetrics,
} from '../api/observabilityApi'

// ---------------------------------------------------------------------------
// Render Jobs
// ---------------------------------------------------------------------------

export function useRenderJobs(statusFilter: string = 'ALL') {
  return useQuery({
    queryKey: ['observability', 'render-jobs', statusFilter],
    queryFn: () => fetchRenderJobs(statusFilter),
    refetchInterval: 2000, // Poll every 2s
  })
}

export function useRenderJobDetail(jobId: string | null) {
  return useQuery({
    queryKey: ['observability', 'render-job', jobId],
    queryFn: () => fetchRenderJobDetail(jobId!),
    enabled: !!jobId,
    refetchInterval: 2000,
  })
}

// ---------------------------------------------------------------------------
// Tracing
// ---------------------------------------------------------------------------

export function useTraceGraph(jobId: string | null) {
  return useQuery({
    queryKey: ['observability', 'trace', jobId],
    queryFn: () => fetchTraceByJobId(jobId!),
    enabled: !!jobId,
  })
}

// ---------------------------------------------------------------------------
// Metrics
// ---------------------------------------------------------------------------

export function useMetricsSummary() {
  return useQuery({
    queryKey: ['observability', 'metrics', 'summary'],
    queryFn: fetchMetricsSummary,
    refetchInterval: 5000, // Poll every 5s
  })
}

export function useProviderMetrics() {
  return useQuery({
    queryKey: ['observability', 'metrics', 'providers'],
    queryFn: fetchProviderMetrics,
    refetchInterval: 10000,
  })
}

export function useFailureMetrics() {
  return useQuery({
    queryKey: ['observability', 'metrics', 'failures'],
    queryFn: fetchFailureMetrics,
    refetchInterval: 10000,
  })
}
