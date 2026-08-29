package com.example.platform.render.infrastructure.environment;

import com.example.platform.render.domain.environment.*;
import com.example.platform.render.domain.execution.ExecutionJob;
import com.example.platform.render.domain.execution.ExecutionStatus;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * OpenCue execution environment — submits jobs via OpenCue.
 *
 * <p>Uses injectable OpenCueSubmissionClient seam.
 * Validates that a backend identity is bound before submission.
 * No static mutable state. No silent fallback.
 *
 * <p>Disabled by default (opencue.enabled=false).
 * Submit rejected when disabled. Production submit requires
 * explicit configuration (opencue.production-submit-enabled=true).
 *
 * <p>Architecture boundary enforcement:
 * <ul>
 *   <li>Never accesses repositories</li>
 *   <li>Never modifies ProductRuntime</li>
 *   <li>Never performs planning</li>
 *   <li>Never calculates pricing, billing, quota, or metering</li>
 *   <li>Environment reports state — platform owns lifecycle</li>
 * </ul>
 *
 * @see OpenCueJobSpecValidator for job spec validation
 */
@Component
@ConditionalOnProperty(name = "opencue.enabled", havingValue = "true", matchIfMissing = false)
public class OpenCueExecutionEnvironment implements ExecutionEnvironment {

    private static final Logger log = LoggerFactory.getLogger(OpenCueExecutionEnvironment.class);
    private static final Map<String, ExecutionStatus> OPENCUE_STATUS_MAP = Map.of(
            "pending", ExecutionStatus.SUBMITTED,
            "queued", ExecutionStatus.QUEUED,
            "running", ExecutionStatus.RUNNING,
            "succeeded", ExecutionStatus.COMPLETED,
            "dead", ExecutionStatus.FAILED,
            "killed", ExecutionStatus.CANCELLED,
            "dependent", ExecutionStatus.QUEUED
    );

    private final OpenCueProperties props;
    private final OpenCueJobSpecValidator jobSpecValidator;
    private final OpenCueSubmissionClient submissionClient;

    public OpenCueExecutionEnvironment(OpenCueProperties props,
                                         OpenCueJobSpecValidator jobSpecValidator,
                                         OpenCueSubmissionClient submissionClient) {
        this.props = props;
        this.jobSpecValidator = jobSpecValidator;
        this.submissionClient = submissionClient;
    }

    @Override
    public String environmentId() {
        return "opencue";
    }

    @Override
    public String environmentType() {
        return "opencue";
    }

    @Override
    public boolean supports(List<String> capabilities) {
        return props.isEnabled() && (capabilities.contains("MEDIA_PIPELINE")
                || capabilities.contains("TRANSCODE"));
    }

    @Override
    public String submit(ExecutionJob job) {
        if (!props.isEnabled()) {
            throw new IllegalStateException("OpenCue is disabled (opencue.enabled=false). "
                    + "Cannot submit job " + job.jobId());
        }

        if (!props.isProductionSubmitEnabled() && !props.isStubModeEnabled()) {
            throw new IllegalStateException("OpenCue production submit is not enabled "
                    + "(opencue.production-submit-enabled=false). "
                    + "Cannot submit job " + job.jobId());
        }

        if (!OpenCueSubmissionRequest.isBoundBackendIdentity(job.backendId())) {
            throw new IllegalStateException(
                    "OpenCue requires a bound backend identity: " + job.backendId());
        }

        // Delegate to submission client via submission request
        OpenCueSubmissionRequest request = OpenCueSubmissionRequest.fromExecutionJob(job, null);
        OpenCueSubmissionResult result = submissionClient.submit(request);

        if (result.isAccepted()) {
            log.info("OpenCue submission accepted: renderJobId={} backend={} externalId={} dur={}ms",
                    job.jobId(), job.backendId(), result.externalJobId(), result.submissionDurationMs());
            return result.externalJobId();
        }

        if (result.isRejected()) {
            throw new IllegalStateException("OpenCue rejected submission: " + result.error()
                    + " — " + result.errorMessage());
        }

        // Failure
        throw new IllegalStateException("OpenCue submission failed: " + result.error()
                + " — " + result.errorMessage());
    }

    /**
     * Submit via submission request directly — returns full result.
     * Use when caller needs the acknowledgement details.
     */
    public OpenCueSubmissionResult submitToOpenCue(OpenCueSubmissionRequest request) {
        if (!props.isEnabled()) {
            return OpenCueSubmissionResult.failure(
                    OpenCueSubmissionError.MISSING_CONFIGURATION,
                    "OpenCue is disabled (opencue.enabled=false)",
                    0);
        }
        return submissionClient.submit(request);
    }

    @Override
    public boolean cancel(String executionId) {
        if (!props.isEnabled()) {
            log.warn("OpenCue cancel rejected: disabled. executionId={}", executionId);
            return false;
        }
        log.info("OpenCue cancel: executionId={} (Phase 1 stub)", executionId);
        return true;
    }

    @Override
    public String status(String executionId) {
        if (!props.isEnabled()) {
            log.warn("OpenCue status rejected: disabled. executionId={}", executionId);
            return "dead";
        }
        log.debug("OpenCue status: executionId={} (Phase 1 stub, returns QUEUED)", executionId);
        return "queued";
    }

    public ExecutionStatus mapOpenCueStatusToPlatform(String opencueState) {
        if (opencueState == null || opencueState.isBlank()) {
            log.warn("OpenCue reported null/blank state, mapping to FAILED");
            return ExecutionStatus.FAILED;
        }
        String normalized = opencueState.toLowerCase().trim();
        ExecutionStatus mapped = OPENCUE_STATUS_MAP.get(normalized);
        if (mapped == null) {
            log.warn("OpenCue reported unknown state '{}', mapping to FAILED", opencueState);
            return ExecutionStatus.FAILED;
        }
        return mapped;
    }
}
