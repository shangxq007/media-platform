import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '../../api/query-keys'
import { cacheBoundaries } from '../../api/cache-boundaries'
import { AppQueryScope, enabledWhenScoped } from '../shared/query-options'
import { getRenderPollingInterval } from '../shared/polling-policy'

export function useRenderJobStatus(scope: Partial<AppQueryScope>, jobId: string, currentStatus?: string) {
  const pollingInterval = currentStatus ? getRenderPollingInterval(currentStatus) : false

  return useQuery({
    queryKey: queryKeys.renderJobs.detail(scope.tenantId ?? '', scope.projectId ?? '', jobId),
    queryFn: async () => {
      // Placeholder - would use actual API client
      return { job: { id: jobId, status: 'QUEUED' } }
    },
    enabled: enabledWhenScoped(scope) && Boolean(jobId),
    staleTime: cacheBoundaries.renderJobs.staleTime,
    gcTime: cacheBoundaries.renderJobs.cacheTime,
    refetchInterval: pollingInterval,
  })
}
