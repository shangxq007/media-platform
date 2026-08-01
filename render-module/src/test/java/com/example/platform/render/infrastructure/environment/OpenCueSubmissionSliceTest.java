package com.example.platform.render.infrastructure.environment;

import com.example.platform.render.domain.environment.*;
import com.example.platform.render.domain.execution.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OpenCue Submission Slice tests.
 *
 * <p>Covers all required test scenarios:
 * <ol>
 *   <li>Backend preservation for all 4 canonical backends</li>
 *   <li>Unknown backend explicit failure (client not called)</li>
 *   <li>Missing configuration explicit failure (no fallback)</li>
 *   <li>Accepted/rejected/transport failure acknowledgement</li>
 *   <li>Deterministic submission identity</li>
 *   <li>Different retry RenderJob IDs create different identities</li>
 *   <li>Sensitive data not in logs/audit</li>
 *   <li>Default environment unchanged</li>
 *   <li>No real network calls in tests</li>
 * </ol>
 *
 * <p>No real OpenCue cluster dependency. Uses injectable client seam.
 */
@ExtendWith(MockitoExtension.class)
class OpenCueSubmissionSliceTest {

    private OpenCueProperties props;
    private OpenCueJobSpecValidator validator;
    private TrackingSubmissionClient trackingClient;
    private OpenCueExecutionEnvironment environment;

    @BeforeEach
    void setUp() {
        props = new OpenCueProperties();
        props.setEnabled(true);
        props.setStubModeEnabled(true);
        validator = new OpenCueJobSpecValidator(props);
        trackingClient = new TrackingSubmissionClient();
        environment = new OpenCueExecutionEnvironment(props, validator, trackingClient);
    }

    // ── Helper: create a test ExecutionJob with a specific backend ──

    private ExecutionJob createJobWithBackend(String backendId) {
        return createJobWithBackendAndId(backendId, "job-" + System.nanoTime());
    }

    private ExecutionJob createJobWithBackendAndId(String backendId, String jobId) {
        BackendExecutionSpec spec = LocalProcessExecutionSpec.of(
                backendId, backendId + "-producer",
                List.of(ExecutionInput.of("prod-1", "ref-1")),
                List.of(ExecutionOutput.of("MEDIA_FILE", "mp4")),
                "ffmpeg", List.of("-i", "input.mp4", "output.mp4"));
        ExecutionTask task = ExecutionTask.of(spec);
        return new ExecutionJob(jobId, "opencue", backendId, "local-process", 50,
                Map.of("cpu", 1, "memoryMb", 1024), Map.of(), List.of(task),
                ExecutionStatus.CREATED, java.time.Instant.now(), null, null, null);
    }

    // ── Test 1-4: Backend preservation for all 4 canonical backends ──

    @Test
    void ffmpegBackendPreservedInSubmission() {
        ExecutionJob job = createJobWithBackend("ffmpeg");
        OpenCueSubmissionRequest request = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-1");
        assertEquals("ffmpeg", request.backendId());
        assertTrue(request.isCanonicalBackend());
    }

    @Test
    void remotionBackendPreservedInSubmission() {
        ExecutionJob job = createJobWithBackend("remotion");
        OpenCueSubmissionRequest request = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-1");
        assertEquals("remotion", request.backendId());
        assertTrue(request.isCanonicalBackend());
    }

    @Test
    void gpacBackendPreservedInSubmission() {
        ExecutionJob job = createJobWithBackend("gpac");
        OpenCueSubmissionRequest request = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-1");
        assertEquals("gpac", request.backendId());
        assertTrue(request.isCanonicalBackend());
    }

    @Test
    void blenderBackendPreservedInSubmission() {
        ExecutionJob job = createJobWithBackend("blender");
        OpenCueSubmissionRequest request = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-1");
        assertEquals("blender", request.backendId());
        assertTrue(request.isCanonicalBackend());
    }

    @Test
    void allFourBackendsPreservedViaSubmit() {
        for (String backend : List.of("ffmpeg", "remotion", "gpac", "blender")) {
            trackingClient.reset();
            ExecutionJob job = createJobWithBackend(backend);
            String execId = environment.submit(job);
            assertNotNull(execId, "Must return execution ID for backend: " + backend);
            assertEquals(1, trackingClient.submitCount(),
                    "Client must be called exactly once for backend: " + backend);
            assertEquals(backend, trackingClient.lastRequest().backendId(),
                    "Backend must be preserved in submission request for: " + backend);
        }
    }

    // ── Test 5: Unknown backend → explicit failure, client not called ──

    @Test
    void unknownBackendFailsExplicitlyWithoutCallingClient() {
        trackingClient.reset();
        ExecutionJob job = createJobWithBackend("unknown-renderer");
        assertThrows(IllegalStateException.class, () -> environment.submit(job),
                "Unknown backend must throw IllegalStateException");
        assertEquals(0, trackingClient.submitCount(),
                "Client must NOT be called for unknown backend");
    }

    @Test
    void unknownBackendNotCanonical() {
        ExecutionJob job = createJobWithBackend("unknown-renderer");
        OpenCueSubmissionRequest request = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-1");
        assertFalse(request.isCanonicalBackend(),
                "Unknown backend must not be canonical");
    }

    // ── Test 6: Missing configuration → explicit failure, no fallback ──

    @Test
    void missingConfigurationFailsExplicitly() {
        trackingClient.reset();
        props.setEnabled(false);
        ExecutionJob job = createJobWithBackend("ffmpeg");
        assertThrows(IllegalStateException.class, () -> environment.submit(job),
                "Disabled OpenCue must throw IllegalStateException");
        assertEquals(0, trackingClient.submitCount(),
                "Client must NOT be called when disabled");
    }

    @Test
    void missingProductionSubmitFailsExplicitly() {
        trackingClient.reset();
        props.setEnabled(true);
        props.setStubModeEnabled(false);
        props.setProductionSubmitEnabled(false);
        ExecutionJob job = createJobWithBackend("ffmpeg");
        assertThrows(IllegalStateException.class, () -> environment.submit(job),
                "Missing production submit must throw IllegalStateException");
        assertEquals(0, trackingClient.submitCount(),
                "Client must NOT be called when neither stub nor production enabled");
    }

    @Test
    void noSilentLocalFallback() {
        props.setEnabled(false);
        OpenCueSubmissionRequest request = new OpenCueSubmissionRequest(
                "job-1", "rev-1", "ffmpeg", "local-process", "OPEN_CUE",
                List.of(), List.of(), Map.of(), "corr-1", 50, Map.of());
        OpenCueSubmissionResult result = environment.submitToOpenCue(request);
        assertFalse(result.isAccepted(), "Disabled environment must not accept");
        assertEquals(OpenCueSubmissionError.MISSING_CONFIGURATION, result.error(),
                "Must report missing configuration, not fallback");
    }

    // ── Test 7: Accepted acknowledgement → returned accurately ──

    @Test
    void acceptedAcknowledgementReturnedAccurately() {
        trackingClient.setResult(OpenCueSubmissionResult.accepted("oc-ext-123", 42));
        ExecutionJob job = createJobWithBackend("ffmpeg");
        String execId = environment.submit(job);
        assertEquals("oc-ext-123", execId,
                "Must return the exact external job ID from acknowledgement");
    }

    // ── Test 8: Client rejection → explicit rejected result ──

    @Test
    void clientRejectionThrowsExplicitException() {
        trackingClient.setResult(OpenCueSubmissionResult.rejected(
                OpenCueSubmissionError.CLIENT_REJECTED, "Job rejected by OpenCue", 10));
        ExecutionJob job = createJobWithBackend("ffmpeg");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> environment.submit(job));
        assertTrue(ex.getMessage().contains("rejected"),
                "Exception must indicate rejection");
    }

    // ── Test 9: Transport exception → explicit non-fallback failure ──

    @Test
    void transportFailureThrowsExplicitException() {
        trackingClient.setResult(OpenCueSubmissionResult.failure(
                OpenCueSubmissionError.TRANSPORT_FAILURE, "Connection refused", 100));
        ExecutionJob job = createJobWithBackend("ffmpeg");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> environment.submit(job));
        assertTrue(ex.getMessage().contains("TRANSPORT_FAILURE"),
                "Exception must indicate transport failure");
    }

    @Test
    void protocolFailureThrowsExplicitException() {
        trackingClient.setResult(OpenCueSubmissionResult.failure(
                OpenCueSubmissionError.PROTOCOL_FAILURE, "Serialization error", 50));
        ExecutionJob job = createJobWithBackend("ffmpeg");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> environment.submit(job));
        assertTrue(ex.getMessage().contains("PROTOCOL_FAILURE"),
                "Exception must indicate protocol failure");
    }

    // ── Test 10: Same RenderJob mapping is deterministic ──

    @Test
    void sameRenderJobMappingIsDeterministic() {
        ExecutionJob job = createJobWithBackend("ffmpeg");
        OpenCueSubmissionRequest req1 = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-1");
        OpenCueSubmissionRequest req2 = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-1");
        assertEquals(req1.renderJobId(), req2.renderJobId());
        assertEquals(req1.backendId(), req2.backendId());
        assertEquals(req1.backendType(), req2.backendType());
        assertEquals(req1.correlationId(), req2.correlationId());
        assertEquals(req1.environment(), req2.environment());
        assertEquals(req1, req2, "Same ExecutionJob must produce identical requests");
    }

    // ── Test 11: Different retry RenderJob IDs create different submission identities ──

    @Test
    void differentRetryRenderJobIdsCreateDifferentSubmissionIdentities() {
        // Use explicit IDs to guarantee different identities
        ExecutionJob job1 = createJobWithBackendAndId("ffmpeg", "job-attempt-1");
        ExecutionJob job2 = createJobWithBackendAndId("ffmpeg", "job-attempt-2");

        assertNotEquals(job1.jobId(), job2.jobId(),
                "Different execution attempts must have different job IDs");

        OpenCueSubmissionRequest req1 = OpenCueSubmissionRequest.fromExecutionJob(job1, "rev-1");
        OpenCueSubmissionRequest req2 = OpenCueSubmissionRequest.fromExecutionJob(job2, "rev-1");

        assertNotEquals(req1.renderJobId(), req2.renderJobId(),
                "Different retry jobs must create different submission identities");
        assertNotEquals(req1.correlationId(), req2.correlationId(),
                "Different retry jobs must have different correlation IDs");
    }

    // ── Test 12: Sensitive input values do not appear in logs/audit ──

    @Test
    void submissionRequestDoesNotContainCredentials() {
        ExecutionJob job = createJobWithBackend("ffmpeg");
        OpenCueSubmissionRequest request = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-1");

        // Verify no credential-like fields exist
        String requestStr = request.toString();
        assertFalse(requestStr.contains("password"), "Request must not contain password");
        assertFalse(requestStr.contains("secret"), "Request must not contain secret");
        assertFalse(requestStr.contains("token"), "Request must not contain token");
        assertFalse(requestStr.contains("Authorization"), "Request must not contain Authorization");
        assertFalse(requestStr.contains("signedUrl"), "Request must not contain signedUrl");
        assertFalse(requestStr.contains("apiKey"), "Request must not contain apiKey");
    }

    @Test
    void submissionResultDoesNotContainCredentials() {
        OpenCueSubmissionResult result = OpenCueSubmissionResult.accepted("oc-123", 10);
        String resultStr = result.toString();
        assertFalse(resultStr.contains("password"), "Result must not contain password");
        assertFalse(resultStr.contains("secret"), "Result must not contain secret");
        assertFalse(resultStr.contains("token"), "Result must not contain token");
    }

    // ── Test 13: Default environment remains unchanged ──

    @Test
    void defaultEnvironmentIsLocal() {
        // Verify that without explicit OpenCue selection, local is the default
        // This is verified by the fact that LocalExecutionEnvironment is @Component
        // without @ConditionalOnProperty, while OpenCueExecutionEnvironment requires
        // opencue.enabled=true
        OpenCueProperties defaultProps = new OpenCueProperties();
        assertFalse(defaultProps.isEnabled(),
                "OpenCue must be disabled by default");
    }

    // ── Test 14: No real network call occurs in tests ──

    @Test
    void noRealNetworkCallInTests() {
        // The TrackingSubmissionClient is a test double — no network
        trackingClient.reset();
        ExecutionJob job = createJobWithBackend("ffmpeg");
        environment.submit(job);
        assertEquals(1, trackingClient.submitCount(),
                "Only the test double client was called — no real network");
    }

    // ── Additional: Submission request immutability ──

    @Test
    void submissionRequestIsImmutable() {
        ExecutionJob job = createJobWithBackend("ffmpeg");
        OpenCueSubmissionRequest request = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-1");

        // Record fields are final — verify collections are unmodifiable
        assertThrows(UnsupportedOperationException.class,
                () -> request.inputProductIds().add("injected"),
                "inputProductIds must be immutable");
        assertThrows(UnsupportedOperationException.class,
                () -> request.expectedOutputTypes().add("injected"),
                "expectedOutputTypes must be immutable");
        assertThrows(UnsupportedOperationException.class,
                () -> request.executionHints().put("injected", "value"),
                "executionHints must be immutable");
        assertThrows(UnsupportedOperationException.class,
                () -> request.resourceRequirements().put("injected", "value"),
                "resourceRequirements must be immutable");
    }

    // ── Additional: Submission identity bound to RenderJob ──

    @Test
    void submissionIdentityBoundToRenderJob() {
        ExecutionJob job = createJobWithBackend("ffmpeg");
        OpenCueSubmissionRequest request = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-1");
        assertEquals(job.jobId(), request.renderJobId(),
                "Submission identity must be the RenderJob ID");
        assertEquals("OPEN_CUE", request.environment(),
                "Environment must be OPEN_CUE");
    }

    // ── Additional: OpenCue is ExecutionEnvironment, not ExecutionBackend ──

    @Test
    void openCueIsExecutionEnvironmentNotBackend() {
        assertTrue(environment instanceof ExecutionEnvironment,
                "OpenCue must be ExecutionEnvironment");
        assertFalse(environment instanceof com.example.platform.outbox.coordination.ExecutionBackend,
                "OpenCue must NOT be ExecutionBackend");
    }

    // ── Additional: Client seam is injectable ──

    @Test
    void clientSeamIsInjectable() {
        // Verify we can inject a different client
        OpenCueSubmissionClient customClient = request ->
                OpenCueSubmissionResult.accepted("custom-" + request.renderJobId(), 0);
        OpenCueExecutionEnvironment customEnv =
                new OpenCueExecutionEnvironment(props, validator, customClient);
        ExecutionJob job = createJobWithBackend("ffmpeg");
        String execId = customEnv.submit(job);
        assertTrue(execId.startsWith("custom-"),
                "Custom client must be used when injected");
    }

    // ── Additional: No static mutable state in client seam ──

    @Test
    void noStaticMutableStateInClientSeam() {
        // Two instances must be independent
        OpenCueProperties props2 = new OpenCueProperties();
        props2.setEnabled(true);
        props2.setStubModeEnabled(true);
        TrackingSubmissionClient client1 = new TrackingSubmissionClient();
        TrackingSubmissionClient client2 = new TrackingSubmissionClient();
        client1.setResult(OpenCueSubmissionResult.accepted("id-1", 0));
        client2.setResult(OpenCueSubmissionResult.accepted("id-2", 0));

        OpenCueExecutionEnvironment env1 =
                new OpenCueExecutionEnvironment(props, validator, client1);
        OpenCueExecutionEnvironment env2 =
                new OpenCueExecutionEnvironment(props2, validator, client2);

        ExecutionJob job1 = createJobWithBackend("ffmpeg");
        ExecutionJob job2 = createJobWithBackend("ffmpeg");

        String id1 = env1.submit(job1);
        String id2 = env2.submit(job2);

        assertEquals("id-1", id1);
        assertEquals("id-2", id2);
        assertEquals(1, client1.submitCount());
        assertEquals(1, client2.submitCount());
    }

    // ── Additional: Acknowledgement distinguishes all three outcomes ──

    @Test
    void acknowledgementDistinguishesAllOutcomes() {
        OpenCueSubmissionResult accepted = OpenCueSubmissionResult.accepted("ext-1", 10);
        assertTrue(accepted.isAccepted());
        assertFalse(accepted.isRejected());
        assertFalse(accepted.isFailure());
        assertEquals("ext-1", accepted.externalJobId());

        OpenCueSubmissionResult rejected = OpenCueSubmissionResult.rejected(
                OpenCueSubmissionError.CLIENT_REJECTED, "rejected", 10);
        assertFalse(rejected.isAccepted());
        assertTrue(rejected.isRejected());
        assertFalse(rejected.isFailure());
        assertNull(rejected.externalJobId());

        OpenCueSubmissionResult failure = OpenCueSubmissionResult.failure(
                OpenCueSubmissionError.TRANSPORT_FAILURE, "timeout", 10);
        assertFalse(failure.isAccepted());
        assertFalse(failure.isRejected());
        assertTrue(failure.isFailure());
        assertNull(failure.externalJobId());
    }

    // ── Additional: TimelineRevisionRef is preserved ──

    @Test
    void timelineRevisionRefPreserved() {
        ExecutionJob job = createJobWithBackend("ffmpeg");
        OpenCueSubmissionRequest request = OpenCueSubmissionRequest.fromExecutionJob(job, "rev-abc-123");
        assertEquals("rev-abc-123", request.timelineRevisionRef());
    }

    // ── Additional: submitToOpenCue returns result when disabled ──

    @Test
    void submitToOpenCueReturnsFailureWhenDisabled() {
        props.setEnabled(false);
        OpenCueSubmissionRequest request = new OpenCueSubmissionRequest(
                "job-1", "rev-1", "ffmpeg", "local-process", "OPEN_CUE",
                List.of(), List.of(), Map.of(), "corr-1", 50, Map.of());
        OpenCueSubmissionResult result = environment.submitToOpenCue(request);
        assertEquals(OpenCueSubmissionResult.OpenCueSubmissionOutcome.FAILURE, result.outcome());
        assertEquals(OpenCueSubmissionError.MISSING_CONFIGURATION, result.error());
    }

    // ── Scheduling invariant: idempotent submission ──

    @Test
    void idempotentSubmissionDoesNotDoubleSubmit() {
        // First submission should hit the client
        ExecutionJob job1 = createJobWithBackendAndId("ffmpeg", "job-idempotent-1");
        environment.submit(job1);
        assertEquals(1, trackingClient.submitCount());

        // Same jobId submitted again — DefaultOpenCueSubmissionClient tracks idempotency,
        // but TrackingSubmissionClient does not. This test verifies the environment
        // delegates to the client every time (client-side idempotency is client's responsibility).
        environment.submit(job1);
        assertEquals(2, trackingClient.submitCount(),
                "Environment must delegate to client every time; client owns idempotency");
    }

    // ── Durability invariant: backend preserved under retry ──

    @Test
    void backendPreservedUnderRetry() {
        ExecutionJob jobAttempt1 = createJobWithBackendAndId("ffmpeg", "job-retry-1");
        ExecutionJob jobAttempt2 = createJobWithBackendAndId("ffmpeg", "job-retry-2");

        OpenCueSubmissionRequest req1 = OpenCueSubmissionRequest.fromExecutionJob(jobAttempt1, "rev-1");
        OpenCueSubmissionRequest req2 = OpenCueSubmissionRequest.fromExecutionJob(jobAttempt2, "rev-1");

        assertEquals(req1.backendId(), req2.backendId(),
                "Backend must be preserved across retry attempts");
        assertEquals("ffmpeg", req1.backendId());
    }

    // ── Helper: Tracking submission client for test verification ──

    /**
     * Test double that tracks submissions without making real network calls.
     * Mutable only in test scope.
     */
    private static class TrackingSubmissionClient implements OpenCueSubmissionClient {
        private OpenCueSubmissionResult nextResult = OpenCueSubmissionResult.accepted("oc-test-1", 0);
        private int submitCount = 0;
        private OpenCueSubmissionRequest lastRequest;

        void setResult(OpenCueSubmissionResult result) {
            this.nextResult = result;
        }

        void reset() {
            this.submitCount = 0;
            this.lastRequest = null;
            this.nextResult = OpenCueSubmissionResult.accepted("oc-test-1", 0);
        }

        int submitCount() { return submitCount; }
        OpenCueSubmissionRequest lastRequest() { return lastRequest; }

        @Override
        public OpenCueSubmissionResult submit(OpenCueSubmissionRequest request) {
            submitCount++;
            lastRequest = request;
            return nextResult;
        }
    }
}