import { z } from 'zod'
import { apiRequest, ApiClientConfig, ApiResult } from '../core/api-client'
import { artifactsPath, artifactAccessPath } from '../core/endpoint-builder'
import { ArtifactListResponse, ArtifactAccessResponse } from '../../contracts/app/artifact'

export function createArtifactsClient(config: ApiClientConfig) {
  return {
    async list(tenantId: string, projectId: string, jobId: string): Promise<ApiResult<ArtifactListResponse>> {
      return apiRequest(config, artifactsPath(tenantId, projectId, jobId), ArtifactListResponse)
    },

    async getAccess(tenantId: string, projectId: string, jobId: string, artifactId: string): Promise<ApiResult<ArtifactAccessResponse>> {
      return apiRequest(config, artifactAccessPath(tenantId, projectId, jobId, artifactId), ArtifactAccessResponse)
    },
  }
}
