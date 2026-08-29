package com.example.platform.render.infrastructure.environment;

import com.example.platform.render.domain.environment.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default OpenCue submission client.
 *
 * <p>Phase 1: Stub implementation. No real gRPC/REST connection.
 * Disabled by default (opencue.enabled=false).
 *
 * <p>Thread-safe. No static mutable state.
 * Tracks submission attempts for idempotency.
 */
@Component
@ConditionalOnProperty(name = "opencue.enabled", havingValue = "true", matchIfMissing = false)
public class DefaultOpenCueSubmissionClient implements OpenCueSubmissionClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultOpenCueSubmissionClient.class);

    private final OpenCueProperties props;
    private final Map<String, String> submissionIdempotency = new ConcurrentHashMap<>();

    public DefaultOpenCueSubmissionClient(OpenCueProperties props) {
        this.props = props;
    }

    @Override
    public OpenCueSubmissionResult submit(OpenCueSubmissionRequest request) {
        long start = System.currentTimeMillis();

        if (!request.hasBoundBackendIdentity()) {
            long dur = System.currentTimeMillis() - start;
            log.warn("OpenCue submission rejected: unbound backend identity={}", request.backendId());
            return OpenCueSubmissionResult.rejected(
                    OpenCueSubmissionError.UNSUPPORTED_BACKEND,
                    "Unbound backend identity: " + request.backendId(),
                    dur);
        }

        // Check idempotency — same renderJobId should not be resubmitted
        String existing = submissionIdempotency.get(request.renderJobId());
        if (existing != null) {
            long dur = System.currentTimeMillis() - start;
            log.info("OpenCue submission idempotent: renderJobId={} externalId={}",
                    request.renderJobId(), existing);
            return OpenCueSubmissionResult.accepted(existing, dur);
        }

        // Stub mode: simulate acceptance
        if (props.isStubModeEnabled()) {
            String externalId = "oc-stub-" + System.currentTimeMillis();
            submissionIdempotency.put(request.renderJobId(), externalId);
            long dur = System.currentTimeMillis() - start;
            log.info("OpenCue stub submission: renderJobId={} backend={} externalId={} dur={}ms",
                    request.renderJobId(), request.backendId(), externalId, dur);
            return OpenCueSubmissionResult.accepted(externalId, dur);
        }

        // Production submit (Phase 1: not implemented — explicit failure)
        if (!props.isProductionSubmitEnabled()) {
            long dur = System.currentTimeMillis() - start;
            log.warn("OpenCue production submit not enabled: renderJobId={}", request.renderJobId());
            return OpenCueSubmissionResult.failure(
                    OpenCueSubmissionError.MISSING_CONFIGURATION,
                    "OpenCue production submit is not enabled",
                    dur);
        }

        // Real submission would go here (gRPC/REST)
        // Phase 1: explicit failure — not yet implemented
        long dur = System.currentTimeMillis() - start;
        return OpenCueSubmissionResult.failure(
                OpenCueSubmissionError.PROTOCOL_FAILURE,
                "Real OpenCue submission not yet implemented (Phase 1)",
                dur);
    }
}
