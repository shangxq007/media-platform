/**
 * TanStack Query Key Conventions
 * 
 * All query keys follow the pattern: [domain, ...scope, ...params]
 * Tenant/Project scoped resources include tenantId and projectId.
 * DEV_ONLY keys are prefixed with 'dev' and isolated from app keys.
 */

// Shared scope keys
export const queryKeys = {
  // Products
  products: {
    all: (tenantId: string, projectId: string) => 
      ['products', tenantId, projectId] as const,
    detail: (tenantId: string, projectId: string, productId: string) => 
      ['products', tenantId, projectId, productId] as const,
  },

  // Upload
  upload: {
    rawMedia: (tenantId: string, projectId: string) => 
      ['upload', 'rawMedia', tenantId, projectId] as const,
  },

  // Render Jobs
  renderJobs: {
    all: (tenantId: string, projectId: string) => 
      ['renderJobs', tenantId, projectId] as const,
    detail: (tenantId: string, projectId: string, jobId: string) => 
      ['renderJobs', tenantId, projectId, jobId] as const,
  },

  // Timeline Revision Render
  timelineRevisionRender: {
    byRevision: (tenantId: string, projectId: string, revisionId: string) => 
      ['timelineRevisionRender', tenantId, projectId, revisionId] as const,
  },

  // Artifacts
  artifacts: {
    all: (tenantId: string, projectId: string, jobId: string) => 
      ['artifacts', tenantId, projectId, jobId] as const,
    detail: (tenantId: string, projectId: string, jobId: string, artifactId: string) => 
      ['artifacts', tenantId, projectId, jobId, artifactId] as const,
    access: (tenantId: string, projectId: string, jobId: string, artifactId: string) => 
      ['artifactAccess', tenantId, projectId, jobId, artifactId] as const,
  },

  // DEV ONLY - Isolated from app keys
  dev: {
    storageDeliveryProfiles: {
      all: () => ['dev', 'storageDeliveryProfiles'] as const,
      detail: (profileId: string) => ['dev', 'storageDeliveryProfiles', profileId] as const,
      validation: () => ['dev', 'storageDeliveryProfiles', 'validation'] as const,
    },
    ingestPreflightPolicy: {
      all: () => ['dev', 'ingestPreflightPolicy'] as const,
      config: () => ['dev', 'ingestPreflightPolicy', 'config'] as const,
      decisionSemantics: () => ['dev', 'ingestPreflightPolicy', 'decisionSemantics'] as const,
    },
    safePreflightReports: {
      all: (tenantId: string, projectId: string) => 
        ['dev', 'safePreflightReports', tenantId, projectId] as const,
      detail: (tenantId: string, projectId: string, recordId: string) => 
        ['dev', 'safePreflightReports', tenantId, projectId, recordId] as const,
    },
    retentionDryRun: {
      byScope: (tenantId: string, projectId: string) => 
        ['dev', 'retentionDryRun', tenantId, projectId] as const,
    },
  },
} as const

// Type helpers
export type QueryKey = ReturnType<typeof queryKeys.products.all>
export type DevQueryKey = ReturnType<typeof queryKeys.dev.storageDeliveryProfiles.all>
