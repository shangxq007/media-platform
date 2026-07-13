import { z } from 'zod'
import { apiRequest, ApiClientConfig, ApiResult } from '../core/api-client'
import { productsPath, productDetailPath } from '../core/endpoint-builder'
import { ProductSummary, ProductListResponse } from '../../contracts/app/product'

export function createProductsClient(config: ApiClientConfig) {
  return {
    async list(tenantId: string, projectId: string): Promise<ApiResult<ProductListResponse>> {
      return apiRequest(config, productsPath(tenantId, projectId), ProductListResponse)
    },

    async get(tenantId: string, projectId: string, productId: string): Promise<ApiResult<ProductSummary>> {
      return apiRequest(config, productDetailPath(tenantId, projectId, productId), ProductSummary)
    },
  }
}
