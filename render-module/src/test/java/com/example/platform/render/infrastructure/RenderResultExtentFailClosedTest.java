package com.example.platform.render.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PRE-#21 W4 — authoritative frame output / RenderExtent fail-closed (C10/C11).
 *
 * Authoritative render results distinguish REQUESTED_RENDER_EXTENT from
 * ACHIEVED_RENDER_EXTENT. Insufficient extent must never produce authoritative
 * success (AUTHORITATIVE_FRAME_OUTPUT_SILENT_PARTIAL_COUNT = 0).
 */
class RenderResultExtentFailClosedTest {

    private static final String EXTENT = "00:00:00.000000,00:00:10.000000,25/1";

    @Test
    void successWithMatchingExtentIsAuthoritative() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "ffmpeg", "chain-1", "1.0", "ok", EXTENT, EXTENT, null);
        assertTrue(r.success());
        assertTrue(r.authoritativeSuccess());
    }

    @Test
    void insufficientAchievedExtentIsTypedFailure() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "ffmpeg", "chain-1", "1.0", "ok",
                EXTENT, "00:00:00.000000,00:00:05.000000,25/1", null);
        assertFalse(r.success(), "insufficient extent must not be authoritative success");
        assertFalse(r.authoritativeSuccess());
        assertTrue(r.hitReason().contains("extent not achieved"));
    }

    @Test
    void missingAchievedExtentWithRequestedExtentIsNotAuthoritative() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "ffmpeg", "chain-1", "1.0", "ok", EXTENT, null, null);
        assertFalse(r.success());
        assertFalse(r.authoritativeSuccess());
    }

    @Test
    void noRequestedExtentRemainsSuccessWithoutExtentClaim() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "ffmpeg", "chain-1", "1.0", "ok", null, null, null);
        assertTrue(r.success());
        assertTrue(r.authoritativeSuccess(), "no extent requested → no extent claim");
    }

    @Test
    void failedResultIsNeverAuthoritative() {
        var r = RenderOrchestrator.RenderResult.failed("job-1", "provider error");
        assertFalse(r.success());
        assertFalse(r.authoritativeSuccess());
    }
}
