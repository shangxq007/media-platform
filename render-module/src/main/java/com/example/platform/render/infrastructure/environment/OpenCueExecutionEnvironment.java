package com.example.platform.render.infrastructure.environment;

import com.example.platform.render.domain.environment.*;
import com.example.platform.render.domain.execution.ExecutionJob;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenCue execution environment — submits jobs via OpenCue REST/gRPC.
 * Phase 1: submit/cancel/status only. No workers. No frame scheduling.
 */
@Component
public class OpenCueExecutionEnvironment implements ExecutionEnvironment {

    private static final Logger log = LoggerFactory.getLogger(OpenCueExecutionEnvironment.class);
    private final OpenCueProperties props;

    public OpenCueExecutionEnvironment(OpenCueProperties props) { this.props = props; }

    @Override public String environmentId() { return "opencue"; }
    @Override public String environmentType() { return "opencue"; }

    @Override
    public boolean supports(List<String> capabilities) {
        return props.enabled() && (capabilities.contains("MEDIA_PIPELINE")
                || capabilities.contains("TRANSCODE"));
    }

    @Override
    public String submit(ExecutionJob job) {
        String execId = "oc-" + System.currentTimeMillis();
        log.info("OpenCueExecutionEnvironment: submitting job={} backend={} env={}",
                job.jobId(), job.backendId(), props.server());
        log.info("OpenCueExecutionEnvironment: executionId={} (Phase 1 — stub)", execId);
        return execId;
    }

    @Override
    public boolean cancel(String executionId) {
        log.info("OpenCueExecutionEnvironment: cancelling execution={}", executionId);
        return true;
    }

    @Override
    public String status(String executionId) {
        return "QUEUED";
    }
}
