/**
 * Cache Boundary Conventions
 * 
 * Defines stale time, cache time, and invalidation rules per resource type.
 */

export const cacheBoundaries = {
  // Products - moderate cache
  products: {
    staleTime: 5 * 60 * 1000, // 5 minutes
    cacheTime: 10 * 60 * 1000, // 10 minutes
  },

  // Upload - no cache (mutation)
  upload: {
    staleTime: 0,
    cacheTime: 0,
  },

  // Render Jobs - short cache, needs fresh status
  renderJobs: {
    staleTime: 30 * 1000, // 30 seconds
    cacheTime: 2 * 60 * 1000, // 2 minutes
  },

  // Timeline Revision Render - short cache
  timelineRevisionRender: {
    staleTime: 30 * 1000, // 30 seconds
    cacheTime: 2 * 60 * 1000, // 2 minutes
  },

  // Artifacts - moderate cache
  artifacts: {
    staleTime: 5 * 60 * 1000, // 5 minutes
    cacheTime: 10 * 60 * 1000, // 10 minutes
  },

  // Artifact Access - short cache (signed URL expires)
  artifactAccess: {
    staleTime: 60 * 1000, // 1 minute
    cacheTime: 5 * 60 * 1000, // 5 minutes
  },

  // DEV ONLY - moderate cache
  dev: {
    storageDeliveryProfiles: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      cacheTime: 10 * 60 * 1000, // 10 minutes
    },
    ingestPreflightPolicy: {
      staleTime: 5 * 60 * 1000, // 5 minutes
      cacheTime: 10 * 60 * 1000, // 10 minutes
    },
    safePreflightReports: {
      staleTime: 30 * 1000, // 30 seconds
      cacheTime: 2 * 60 * 1000, // 2 minutes
    },
    retentionDryRun: {
      staleTime: 0, // Always fresh
      cacheTime: 0,
    },
  },
} as const

// Polling intervals for real-time resources
export const pollingIntervals = {
  renderJobStatus: 5 * 1000, // 5 seconds
  timelineRevisionRender: 3 * 1000, // 3 seconds
} as const
