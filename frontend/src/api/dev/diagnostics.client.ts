import { z } from 'zod'
import { apiRequest, type ApiClientConfig } from '../core/api-client'
import {
  devIngestPreflightPolicyPath,
  devStorageDeliveryProfilesPath,
} from '../core/endpoint-builder'

const IngestPreflightPolicyConfigDiagnosticsSchema = z.object({
  enabled: z.boolean(),
  mode: z.string(),
  profile: z.string(),
  failOpen: z.boolean(),
  maxFindings: z.number().int(),
  logResult: z.boolean(),
  includeWarningFindings: z.boolean(),
  includeMediaTechnicalFindings: z.boolean(),
  includeRejectCandidates: z.boolean(),
  validationStatus: z.string(),
  validationErrorCount: z.number().int(),
  validationWarningCount: z.number().int(),
})

const IngestPreflightPolicyDecisionSemanticsDiagnosticsSchema = z.object({
  accept: z.string(),
  acceptWithWarnings: z.string(),
  rejectCandidate: z.string(),
  reject: z.string(),
  errorFailOpen: z.string(),
})

export const IngestPreflightPolicyDiagnosticsResponseSchema = z.object({
  diagnosticsMode: z.string(),
  reportOnlyEvaluatorImplemented: z.boolean(),
  hookIntegrationImplemented: z.boolean(),
  configBindingImplemented: z.boolean(),
  reportOnlyMode: z.boolean(),
  failOpenRequired: z.boolean(),
  enforceModeEnabled: z.boolean(),
  uploadRejectionImplemented: z.boolean(),
  runtimePolicyGateImplemented: z.boolean(),
  policyEvaluationPersistenceImplemented: z.boolean(),
  preflightReportPersistenceImplemented: z.boolean(),
  publicUploadResponseChanged: z.boolean(),
  rawMetadataExposureAllowed: z.boolean(),
  ocrEnabled: z.boolean(),
  fullTextExtractionEnabled: z.boolean(),
  config: IngestPreflightPolicyConfigDiagnosticsSchema,
  decisionSemantics: IngestPreflightPolicyDecisionSemanticsDiagnosticsSchema,
  generatedAt: z.string().datetime(),
})

export const StorageDeliveryProfileIdSchema = z.object({ value: z.string().min(1) })
const StorageDeliveryProfileCapabilityDiagnosticsSchema = z.object({
  supportsSignedUrl: z.boolean(),
  supportsInternalStream: z.boolean(),
  supportsExternalBucket: z.boolean(),
  supportsExportBundle: z.boolean(),
  supportsRead: z.boolean(),
  supportsWrite: z.boolean(),
  supportsDelete: z.boolean(),
})
const StorageDeliveryProfileSecurityDiagnosticsSchema = z.object({
  signedUrlGeneratedOnDemand: z.boolean(),
  signedUrlPersisted: z.boolean(),
  exposesBucket: z.boolean(),
  exposesObjectKey: z.boolean(),
  exposesStorageReferenceId: z.boolean(),
  exposesLocalPath: z.boolean(),
  requiresAuthorization: z.boolean(),
  userFacingAccessAllowed: z.boolean(),
})
const StorageDeliveryProfileValidationDiagnosticsSchema = z.object({
  valid: z.boolean(),
  errorCount: z.number().int(),
  warningCount: z.number().int(),
  errorCodes: z.array(z.string()),
  warningCodes: z.array(z.string()),
})

export const StorageDeliveryProfileDiagnosticsResponseSchema = z.object({
  diagnosticsMode: z.string(),
  runtimeSwitchingImplemented: z.boolean(),
  artifactAccessUsesRegistry: z.boolean(),
  providerSelectionUsesRegistry: z.boolean(),
  remoteCallsPerformed: z.boolean(),
  defaultProfileId: StorageDeliveryProfileIdSchema,
  profileCount: z.number().int(),
  profileIds: z.array(StorageDeliveryProfileIdSchema),
  enabledProfileIds: z.array(StorageDeliveryProfileIdSchema),
  runtimeSelectableProfileIds: z.array(StorageDeliveryProfileIdSchema),
  profiles: z.array(z.object({
    profileId: StorageDeliveryProfileIdSchema,
    status: z.enum(['DESIGN_ONLY', 'LAB_ONLY', 'EXPERIMENTAL', 'PREVIEW_VERIFIED', 'VERIFIED', 'DEPRECATED', 'DISABLED']),
    accessMode: z.enum(['SIGNED_URL', 'INTERNAL_STREAM', 'LOCAL_PATH', 'DIRECT_COPY', 'EXTERNAL_BUCKET', 'EXPORT_BUNDLE', 'NO_PUBLIC_ACCESS']),
    backendType: z.enum(['R2', 'AWS_S3', 'RUSTFS', 'MINIO', 'LOCAL_FS', 'EXTERNAL_S3', 'INTERNAL', 'UNKNOWN']),
    providerType: z.enum(['S3_COMPATIBLE', 'OPENDAL', 'LOCAL_FILESYSTEM', 'CUSTOMER_S3', 'EXPORT_BUNDLE', 'INTERNAL_CACHE', 'UNKNOWN']),
    enabled: z.boolean(),
    runtimeSelectable: z.boolean(),
    userFacingAllowed: z.boolean(),
    capabilities: StorageDeliveryProfileCapabilityDiagnosticsSchema,
    securityPolicy: StorageDeliveryProfileSecurityDiagnosticsSchema,
    validationStatus: z.string(),
  })),
  validation: StorageDeliveryProfileValidationDiagnosticsSchema,
  generatedAt: z.string().datetime(),
})

export type IngestPreflightPolicyDiagnosticsResponse = z.infer<typeof IngestPreflightPolicyDiagnosticsResponseSchema>
export type StorageDeliveryProfileDiagnosticsResponse = z.infer<typeof StorageDeliveryProfileDiagnosticsResponseSchema>

export function createDevDiagnosticsClient(config: ApiClientConfig) {
  return {
    getIngestPreflightPolicy: () => apiRequest(
      config,
      devIngestPreflightPolicyPath(),
      IngestPreflightPolicyDiagnosticsResponseSchema,
    ),
    getStorageDeliveryProfiles: () => apiRequest(
      config,
      devStorageDeliveryProfilesPath(),
      StorageDeliveryProfileDiagnosticsResponseSchema,
    ),
  }
}

export const devDiagnosticsClient = createDevDiagnosticsClient({ baseUrl: '' })
