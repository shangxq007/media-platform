import { z } from 'zod'
import { apiRequest, ApiClientConfig, ApiResult } from '../core/api-client'
import { devSafePreflightReportsPath, devSafePreflightReportDetailPath, devRetentionDryRunPath } from '../core/endpoint-builder'
import { SafePreflightReportListResponse, SafePreflightReportDetailResponse, RetentionDryRunResponse } from '../../contracts/dev/safe-preflight-report'

export function createSafePreflightReportsClient(config: ApiClientConfig) {
  return {
    async list(tenantId: string, projectId: string): Promise<ApiResult<SafePreflightReportListResponse>> {
      return apiRequest(config, devSafePreflightReportsPath(tenantId, projectId), SafePreflightReportListResponse)
    },

    async get(tenantId: string, projectId: string, recordId: string): Promise<ApiResult<SafePreflightReportDetailResponse>> {
      return apiRequest(config, devSafePreflightReportDetailPath(tenantId, projectId, recordId), SafePreflightReportDetailResponse)
    },

    async dryRun(tenantId: string, projectId: string): Promise<ApiResult<RetentionDryRunResponse>> {
      return apiRequest(config, devRetentionDryRunPath(tenantId, projectId), RetentionDryRunResponse)
    },
  }
}
