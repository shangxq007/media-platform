import { useQuery } from '@tanstack/react-query'
import { AssetAPI } from '../api/assets'
import { toAssetDomain } from '../domain/mappers'
import type { AssetDomain } from '../domain/models'
import type { AssetSummary } from '../api/contracts/mappers'
import { mapAssetToSummary } from '../api/contracts/mappers'

export { mapAssetToSummary as assetToSummary }
export type { AssetSummary, AssetDomain }

export function useAssets(projectId: string) {
  return useQuery({
    queryKey: ['assets', projectId],
    queryFn: () => AssetAPI.listByProject(projectId),
    enabled: !!projectId,
    staleTime: 30_000,
    select: (data) => data.map(toAssetDomain),
  })
}

export function useAsset(projectId: string, assetId: string | null) {
  return useQuery({
    queryKey: ['asset', projectId, assetId],
    queryFn: () => AssetAPI.get(projectId, assetId!),
    enabled: !!projectId && !!assetId,
    select: (data) => data ? toAssetDomain(data) : null,
  })
}

export function useAssetPreviewUrl(projectId: string, assetId: string | null) {
  return useQuery({
    queryKey: ['asset-preview-url', projectId, assetId],
    queryFn: () => AssetAPI.getPreviewUrl(projectId, assetId!),
    enabled: !!projectId && !!assetId,
    staleTime: 60_000,
  })
}
