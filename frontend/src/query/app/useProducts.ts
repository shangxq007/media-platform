import { useQuery } from '@tanstack/react-query'
import { queryKeys } from '../../api/query-keys'
import { cacheBoundaries } from '../../api/cache-boundaries'
import { createProductsClient } from '../../api/app/products.client'
import { AppQueryScope, enabledWhenScoped } from '../shared/query-options'

const productsClient = createProductsClient({ baseUrl: '' })

export function useProducts(scope: Partial<AppQueryScope>) {
  return useQuery({
    queryKey: queryKeys.products.all(scope.tenantId ?? '', scope.projectId ?? ''),
    queryFn: () => productsClient.list(scope.tenantId!, scope.projectId!),
    enabled: enabledWhenScoped(scope),
    staleTime: cacheBoundaries.products.staleTime,
    gcTime: cacheBoundaries.products.cacheTime,
  })
}

export function useProductDetail(scope: Partial<AppQueryScope>, productId: string) {
  return useQuery({
    queryKey: queryKeys.products.detail(scope.tenantId ?? '', scope.projectId ?? '', productId),
    queryFn: () => productsClient.get(scope.tenantId!, scope.projectId!, productId),
    enabled: enabledWhenScoped(scope) && Boolean(productId),
    staleTime: cacheBoundaries.products.staleTime,
    gcTime: cacheBoundaries.products.cacheTime,
  })
}
