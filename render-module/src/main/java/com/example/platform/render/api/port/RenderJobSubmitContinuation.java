package com.example.platform.render.api.port;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;

/**
 * Continues render execution after a {@code render_job} row is created (local sync vs Temporal workflow).
 */
public interface RenderJobSubmitContinuation {

    /**
     * @return job id when execution is scheduled or completed
     */
    String continueAfterSubmit(String tenantId, String jobId, SubmitRenderJobRequest request);
}
