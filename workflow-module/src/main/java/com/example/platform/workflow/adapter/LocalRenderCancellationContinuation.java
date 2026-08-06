package com.example.platform.workflow.adapter;

import com.example.platform.render.api.port.RenderJobCancellationContinuation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Local (no-op) implementation of {@link RenderJobCancellationContinuation}
 * (frozen contract TEPHV1 CONTRACT_V1, W1-GAP-006).
 *
 * <p>Active in the default {@code render.execution.mode=local} path. Local
 * synchronous execution has no durable workflow to cancel — render_job
 * cancellation is already handled by RenderJobService state transitions.
 * Kept as a no-op to preserve the default behavior exactly.</p>
 */
@Component
@ConditionalOnProperty(prefix = "render.execution", name = "mode", havingValue = "local",
        matchIfMissing = true)
public class LocalRenderCancellationContinuation implements RenderJobCancellationContinuation {

    private static final Logger log = LoggerFactory.getLogger(LocalRenderCancellationContinuation.class);

    @Override
    public void cancelAfterJobCancelled(String tenantId, String jobId) {
        log.debug("Local render cancellation continuation (no-op): job={} tenant={}", jobId, tenantId);
    }
}
