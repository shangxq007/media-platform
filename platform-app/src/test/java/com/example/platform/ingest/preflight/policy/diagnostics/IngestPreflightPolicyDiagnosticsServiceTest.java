package com.example.platform.ingest.preflight.policy.diagnostics;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.ingest.preflight.policy.config.IngestPreflightPolicyConfigValidator;
import com.example.platform.ingest.preflight.policy.config.IngestPreflightPolicyProperties;
import org.junit.jupiter.api.Test;

class IngestPreflightPolicyDiagnosticsServiceTest {

    private final IngestPreflightPolicyProperties properties = new IngestPreflightPolicyProperties();
    private final IngestPreflightPolicyConfigValidator validator = new IngestPreflightPolicyConfigValidator();
    private final IngestPreflightPolicyDiagnosticsService service = new IngestPreflightPolicyDiagnosticsService(properties, validator);

    @Test
    void testSafetySummary() {
        var response = service.getDiagnostics();

        assertEquals("READ_ONLY", response.diagnosticsMode());
        assertTrue(response.reportOnlyEvaluatorImplemented());
        assertTrue(response.hookIntegrationImplemented());
        assertTrue(response.configBindingImplemented());
        assertTrue(response.reportOnlyMode());
        assertTrue(response.failOpenRequired());
        assertFalse(response.enforceModeEnabled());
        assertFalse(response.uploadRejectionImplemented());
        assertFalse(response.runtimePolicyGateImplemented());
        assertFalse(response.policyEvaluationPersistenceImplemented());
        assertFalse(response.preflightReportPersistenceImplemented());
        assertFalse(response.publicUploadResponseChanged());
        assertFalse(response.rawMetadataExposureAllowed());
        assertFalse(response.ocrEnabled());
        assertFalse(response.fullTextExtractionEnabled());
    }

    @Test
    void testConfigDiagnostics() {
        var config = service.getConfigDiagnostics();

        assertFalse(config.enabled());
        assertEquals("report_only", config.mode());
        assertEquals("preview_safe", config.profile());
        assertTrue(config.failOpen());
        assertEquals(50, config.maxFindings());
        assertTrue(config.logResult());
        assertEquals("VALID", config.validationStatus());
    }

    @Test
    void testDecisionSemantics() {
        var semantics = service.getDecisionSemantics();

        assertEquals("non-blocking", semantics.accept());
        assertEquals("non-blocking", semantics.acceptWithWarnings());
        assertEquals("diagnostic-only, non-blocking", semantics.rejectCandidate());
        assertEquals("not-emitted-by-report-only-evaluator", semantics.reject());
        assertEquals("non-blocking, upload continues", semantics.errorFailOpen());
    }

    @Test
    void testNoSensitiveFields() {
        var response = service.getDiagnostics();
        String json = response.toString();

        assertFalse(json.contains("rawJson"));
        assertFalse(json.contains("rawFfprobeJson"));
        assertFalse(json.contains("rawTikaMetadata"));
        assertFalse(json.contains("localPath"));
        assertFalse(json.contains("bucket"));
        assertFalse(json.contains("objectKey"));
        assertFalse(json.contains("storageReferenceId"));
        assertFalse(json.contains("signedUrl"));
        assertFalse(json.contains("accessKey"));
        assertFalse(json.contains("secretKey"));
    }

    @Test
    void testNoPersistence() {
        var response = service.getDiagnostics();
        assertFalse(response.policyEvaluationPersistenceImplemented());
        assertFalse(response.preflightReportPersistenceImplemented());
    }
}
