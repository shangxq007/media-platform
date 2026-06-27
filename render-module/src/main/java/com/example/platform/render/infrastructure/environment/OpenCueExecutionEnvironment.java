package com.example.platform.render.infrastructure.environment;

import com.example.platform.render.domain.environment.*;
import com.example.platform.render.domain.execution.ExecutionJob;
import com.example.platform.render.domain.execution.ExecutionStatus;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenCue execution environment — submits jobs via OpenCue.
 *
 * <p>Phase 1: submit/cancel/status are stub implementations.
 * No real REST/gRPC client. No frame scheduling. No workers.
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

    public OpenCueExecutionEnvironment(OpenCueProperties props, OpenCueJobSpecValidator jobSpecValidator) {
        this.props = props;
        this.jobSpecValidator = jobSpecValidator;
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

        if (props.isStubModeEnabled()) {
            String execId = "oc-" + System.currentTimeMillis();
            log.info("OpenCue stub submit: job={} backend={} executionId={} (Phase 1 stub)",
                    job.jobId(), job.backendId(), execId);
            return execId;
        }

        log.info("OpenCue submit: job={} backend={} server={}:{}",
                job.jobId(), job.backendId(), props.getServer(), props.getGrpcPort());
        String execId = "oc-" + System.currentTimeMillis();
        log.info("OpenCue executionId={} (Phase 1 — real submit deferred)", execId);
        return execId;
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
