/**
 * DEV Diagnostics Route Map
 * 
 * Maps DEV_ONLY diagnostic routes to contracts, API clients, and query keys.
 * All routes are isolated from /app and must not be exposed to normal users.
 */

export interface DevDiagnosticsRouteMeta {
  id: string
  path: string
  title: string
  description: string
  devOnly: true
  contractGroup: string
  apiClientGroup: string
  queryKeyGroup: string
  cachePolicy: string
  mutation: false
  appVisible: false
  status: 'ready-with-limits' | 'deferred' | 'blocked'
}

export const DEV_DIAGNOSTICS_NAMESPACE = '/dev/diagnostics'

export const devDiagnosticsRoutes: DevDiagnosticsRouteMeta[] = [
  {
    id: 'storage-delivery',
    path: `${DEV_DIAGNOSTICS_NAMESPACE}/storage-delivery`,
    title: 'Storage Delivery Profiles',
    description: 'Storage delivery profile registry diagnostics',
    devOnly: true,
    contractGroup: 'dev.storageDeliveryProfiles',
    apiClientGroup: 'dev.storageDeliveryProfiles',
    queryKeyGroup: 'dev.storageDeliveryProfiles',
    cachePolicy: 'short staleTime, manual refresh preferred',
    mutation: false,
    appVisible: false,
    status: 'ready-with-limits',
  },
  {
    id: 'ingest-preflight-policy',
    path: `${DEV_DIAGNOSTICS_NAMESPACE}/ingest-preflight-policy`,
    title: 'Ingest Preflight Policy',
    description: 'Report-only preflight policy evaluator diagnostics',
    devOnly: true,
    contractGroup: 'dev.ingestPreflightPolicy',
    apiClientGroup: 'dev.ingestPreflightPolicy',
    queryKeyGroup: 'dev.ingestPreflightPolicy',
    cachePolicy: 'short/medium staleTime, manual refresh acceptable',
    mutation: false,
    appVisible: false,
    status: 'ready-with-limits',
  },
  {
    id: 'safe-preflight-reports',
    path: `${DEV_DIAGNOSTICS_NAMESPACE}/safe-preflight-reports`,
    title: 'Safe Preflight Reports',
    description: 'DEV_ONLY safe preflight report list (PAUSED persistence)',
    devOnly: true,
    contractGroup: 'dev.safePreflightReports',
    apiClientGroup: 'dev.safePreflightReports',
    queryKeyGroup: 'dev.safePreflightReports',
    cachePolicy: 'short staleTime, no auto-poll',
    mutation: false,
    appVisible: false,
    status: 'ready-with-limits',
  },
  {
    id: 'safe-preflight-report-detail',
    path: `${DEV_DIAGNOSTICS_NAMESPACE}/safe-preflight-reports/$recordId`,
    title: 'Safe Preflight Report Detail',
    description: 'DEV_ONLY safe preflight report detail',
    devOnly: true,
    contractGroup: 'dev.safePreflightReports',
    apiClientGroup: 'dev.safePreflightReports',
    queryKeyGroup: 'dev.safePreflightReports',
    cachePolicy: 'short staleTime',
    mutation: false,
    appVisible: false,
    status: 'ready-with-limits',
  },
  {
    id: 'retention-dry-run',
    path: `${DEV_DIAGNOSTICS_NAMESPACE}/retention-dry-run`,
    title: 'Retention Dry-run',
    description: 'DEV_ONLY retention cleanup dry-run diagnostics (no-mutation)',
    devOnly: true,
    contractGroup: 'dev.retentionDryRun',
    apiClientGroup: 'dev.retentionDryRun',
    queryKeyGroup: 'dev.retentionDryRun',
    cachePolicy: 'manual refresh preferred, no auto-poll',
    mutation: false,
    appVisible: false,
    status: 'ready-with-limits',
  },
]

export function getDevDiagnosticsRoute(id: string): DevDiagnosticsRouteMeta | undefined {
  return devDiagnosticsRoutes.find(route => route.id === id)
}

export function isDevDiagnosticsRoute(path: string): boolean {
  return path.startsWith(DEV_DIAGNOSTICS_NAMESPACE)
}
