import { describe, expect, it } from 'vitest'
import {
  IngestPreflightPolicyDiagnosticsResponseSchema,
  StorageDeliveryProfileDiagnosticsResponseSchema,
  StorageDeliveryProfileIdSchema,
} from './diagnostics.client'

describe('developer diagnostics transport contracts', () => {
  it('parses the implemented ingest preflight diagnostics response', () => {
    const parsed = IngestPreflightPolicyDiagnosticsResponseSchema.parse({
      diagnosticsMode: 'READ_ONLY',
      reportOnlyEvaluatorImplemented: true,
      hookIntegrationImplemented: true,
      configBindingImplemented: true,
      reportOnlyMode: true,
      failOpenRequired: true,
      enforceModeEnabled: false,
      uploadRejectionImplemented: false,
      runtimePolicyGateImplemented: false,
      policyEvaluationPersistenceImplemented: false,
      preflightReportPersistenceImplemented: false,
      publicUploadResponseChanged: false,
      rawMetadataExposureAllowed: false,
      ocrEnabled: false,
      fullTextExtractionEnabled: false,
      config: {
        enabled: true,
        mode: 'REPORT_ONLY',
        profile: 'default',
        failOpen: true,
        maxFindings: 25,
        logResult: true,
        includeWarningFindings: true,
        includeMediaTechnicalFindings: true,
        includeRejectCandidates: true,
        validationStatus: 'VALID',
        validationErrorCount: 0,
        validationWarningCount: 0,
      },
      decisionSemantics: {
        accept: 'accept',
        acceptWithWarnings: 'accept with warnings',
        rejectCandidate: 'report candidate',
        reject: 'not enabled',
        errorFailOpen: 'accept',
      },
      generatedAt: '2026-08-30T00:00:00Z',
    })
    expect(parsed.config.mode).toBe('REPORT_ONLY')
  })

  it('parses storage profile identifiers as the backend record shape', () => {
    const profileId = { value: 'preview-r2-signed-url' }
    const parsed = StorageDeliveryProfileDiagnosticsResponseSchema.parse({
      diagnosticsMode: 'READ_ONLY',
      runtimeSwitchingImplemented: false,
      artifactAccessUsesRegistry: false,
      providerSelectionUsesRegistry: false,
      remoteCallsPerformed: false,
      defaultProfileId: profileId,
      profileCount: 1,
      profileIds: [profileId],
      enabledProfileIds: [profileId],
      runtimeSelectableProfileIds: [],
      profiles: [{
        profileId,
        status: 'PREVIEW_VERIFIED',
        accessMode: 'SIGNED_URL',
        backendType: 'R2',
        providerType: 'S3_COMPATIBLE',
        enabled: true,
        runtimeSelectable: false,
        userFacingAllowed: true,
        capabilities: {
          supportsSignedUrl: true,
          supportsInternalStream: false,
          supportsExternalBucket: false,
          supportsExportBundle: false,
          supportsRead: true,
          supportsWrite: true,
          supportsDelete: true,
        },
        securityPolicy: {
          signedUrlGeneratedOnDemand: true,
          signedUrlPersisted: false,
          exposesBucket: false,
          exposesObjectKey: false,
          exposesStorageReferenceId: false,
          exposesLocalPath: false,
          requiresAuthorization: true,
          userFacingAccessAllowed: true,
        },
        validationStatus: 'VALID',
      }],
      validation: { valid: true, errorCount: 0, warningCount: 0, errorCodes: [], warningCodes: [] },
      generatedAt: '2026-08-30T00:00:00Z',
    })
    expect(parsed.defaultProfileId.value).toBe('preview-r2-signed-url')
  })

  it('rejects the former handwritten string profile-id assumption', () => {
    expect(StorageDeliveryProfileIdSchema.safeParse('preview-r2-signed-url').success).toBe(false)
  })
})
