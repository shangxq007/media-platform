import { useMutation, useQueryClient } from '@tanstack/react-query'
import { queryKeys } from '../../../api/query-keys'
import { createProductsClient } from '../../../api/app/products.client'
import { AppQueryScope } from '../../shared/query-options'

interface UploadRawMediaInput {
  scope: AppQueryScope
  file: File
  displayName?: string
  contentType?: string
}

interface UploadRawMediaResult {
  productId: string
  productType: string
  productStatus: string
  createdAt?: string
}

interface UploadMutationError {
  kind: 'validation' | 'network' | 'permission' | 'quota' | 'server' | 'unknown'
  message: string
  retryable: boolean
}

const productsClient = createProductsClient({ baseUrl: '' })

export function useUploadRawMediaMutation() {
  const queryClient = useQueryClient()

  return useMutation<UploadRawMediaResult, UploadMutationError, UploadRawMediaInput>({
    mutationKey: ['app', 'upload', 'rawMedia'],
    mutationFn: async (input) => {
      // Placeholder - would use actual upload API client
      // In real implementation: return await uploadClient.uploadRawMedia(input.scope, input.file, ...)
      
      // Simulate upload for now
      await new Promise(resolve => setTimeout(resolve, 1000))
      
      return {
        productId: `prod-${Date.now()}`,
        productType: 'RAW_MEDIA',
        productStatus: 'ACTIVE',
        createdAt: new Date().toISOString(),
      }
    },
    onSuccess: (data, variables) => {
      // Invalidate Product list queries
      queryClient.invalidateQueries({
        queryKey: queryKeys.products.all(variables.scope.tenantId, variables.scope.projectId),
      })
    },
    onError: (error) => {
      console.error('Upload failed:', error.message)
    },
  })
}

export type { UploadRawMediaInput, UploadRawMediaResult, UploadMutationError }
