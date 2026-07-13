import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '../../api/query-keys'
import { cacheBoundaries } from '../../api/cache-boundaries'
import { ARTIFACT_ACCESS_STALE_TIME, ARTIFACT_ACCESS_CACHE_TIME } from '../shared/artifact-access-policy'
import { createArtifactsClient } from '../../api/app/artifacts.client'
import { AppQueryScope, enabledWhenScoped } from '../shared/query-options'

const artifactsClient = createArtifactsClient({ baseUrl: '' })

export function useArtifacts(scope: Partial<AppQueryScope>, jobId: string) {
  return useQuery({
    queryKey: queryKeys.artifacts.all(scope.tenantId ?? '', scope.projectId ?? '', jobId),
    queryFn: () => artifactsClient.list(scope.tenantId!, scope.projectId!, jobId),
    enabled: enabledWhenScoped(scope) && Boolean(jobId),
    staleTime: cacheBoundaries.artifacts.staleTime,
    gcTime: cacheBoundaries.artifacts.cacheTime,
  })
}

export function useArtifactAccess(scope: Partial<AppQueryScope>, jobId: string, artifactId: string) {
  return useQuery({
    queryKey: queryKeys.artifacts.access(scope.tenantId ?? '', scope.projectId ?? '', jobId, artifactId),
    queryFn: () => artifactsClient.getAccess(scope.tenantId!, scope.projectId!, jobId, artifactId),
    enabled: enabledWhenScoped(scope) && Boolean(jobId) && Boolean(artifactId),
    staleTime: ARTIFACT_ACCESS_STALE_TIME,
    gcTime: ARTIFACT_ACCESS_CACHE_TIME,
  })
}
