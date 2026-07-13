// App endpoint builders
export function tenantProjectPath(tenantId: string, projectId: string): string {
  return `/tenants/${tenantId}/projects/${projectId}`
}

export function productsPath(tenantId: string, projectId: string): string {
  return `${tenantProjectPath(tenantId, projectId)}/products`
}

export function productDetailPath(tenantId: string, projectId: string, productId: string): string {
  return `${productsPath(tenantId, projectId)}/${productId}`
}

export function uploadRawMediaPath(tenantId: string, projectId: string): string {
  return `${tenantProjectPath(tenantId, projectId)}/upload/raw-media`
}

export function renderJobsPath(tenantId: string, projectId: string): string {
  return `${tenantProjectPath(tenantId, projectId)}/render-jobs`
}

export function renderJobDetailPath(tenantId: string, projectId: string, jobId: string): string {
  return `${renderJobsPath(tenantId, projectId)}/${jobId}`
}

export function timelineRevisionRenderPath(tenantId: string, projectId: string, revisionId: string): string {
  return `${tenantProjectPath(tenantId, projectId)}/timeline-revisions/${revisionId}/render`
}

export function artifactsPath(tenantId: string, projectId: string, jobId: string): string {
  return `${renderJobDetailPath(tenantId, projectId, jobId)}/artifacts`
}

export function artifactAccessPath(tenantId: string, projectId: string, jobId: string, artifactId: string): string {
  return `${artifactsPath(tenantId, projectId, jobId)}/${artifactId}/access`
}

// Admin endpoint builders
export function adminRenderJobsPath(): string {
  return '/admin/render-jobs'
}

export function adminStorageHealthPath(): string {
  return '/admin/storage/health'
}

// Dev endpoint builders (DEV_ONLY - must not be imported by app client)
export function devStorageDeliveryProfilesPath(): string {
  return '/dev/storage-delivery-profiles'
}

export function devStorageDeliveryProfileDetailPath(profileId: string): string {
  return `/dev/storage-delivery-profiles/${profileId}`
}

export function devStorageDeliveryProfileValidationPath(): string {
  return '/dev/storage-delivery-profiles/validation'
}

export function devIngestPreflightPolicyPath(): string {
  return '/dev/ingest/preflight-policy'
}

export function devIngestPreflightPolicyConfigPath(): string {
  return '/dev/ingest/preflight-policy/config'
}

export function devIngestPreflightPolicyDecisionSemanticsPath(): string {
  return '/dev/ingest/preflight-policy/decision-semantics'
}

export function devSafePreflightReportsPath(tenantId: string, projectId: string): string {
  return `/dev/tenants/${tenantId}/projects/${projectId}/ingest/preflight/safe-reports`
}

export function devSafePreflightReportDetailPath(tenantId: string, projectId: string, recordId: string): string {
  return `${devSafePreflightReportsPath(tenantId, projectId)}/${recordId}`
}

export function devRetentionDryRunPath(tenantId: string, projectId: string): string {
  return `/dev/tenants/${tenantId}/projects/${projectId}/ingest/preflight/safe-reports/retention/dry-run`
}
