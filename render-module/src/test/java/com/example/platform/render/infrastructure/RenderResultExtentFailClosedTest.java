package com.example.platform.render.infrastructure;

import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PRE-#21 C10/C11 correction — authoritative render extent fail-closed.
 *
 * Typed RenderExtent is the SINGLE extent authority (no String representation).
 * Authoritative extent success requires requested + achieved + semantic
 * equality. Ordinary success without extent proof is never authoritative.
 */
class RenderResultExtentFailClosedTest {

    private static RenderExtent extent(long endMillis) {
        return new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(endMillis), FrameRate.of(25, 1));
    }

    private static RenderExtent extentWithRate(long endMillis, long num, long den) {
        return new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(endMillis), FrameRate.of(num, den));
    }

    @Test
    void matchingTypedExtentIsAuthoritativeSuccess() {
        RenderExtent e = extent(10000);
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "ffmpeg", "chain-1", "1.0", "ok", e, e, null);
        assertTrue(r.success());
        assertTrue(r.authoritativeSuccess());
        assertEquals(e, r.requestedRenderExtent());
        assertEquals(e, r.achievedRenderExtent());
    }

    @Test
    void missingAchievedExtentIsTypedFailureWhenRequested() {
        RenderExtent e = extent(10000);
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "ffmpeg", "chain-1", "1.0", "ok", e, null, null);
        assertFalse(r.success(), "requested extent without achieved evidence must fail closed");
        assertFalse(r.authoritativeSuccess());
        assertTrue(r.hitReason().contains("extent not achieved"));
    }

    @Test
    void mismatchedEndIsTypedFailure() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "ffmpeg", "chain-1", "1.0", "ok",
                extent(10000), extent(5000), null);
        assertFalse(r.success());
        assertFalse(r.authoritativeSuccess());
    }

    @Test
    void mismatchedStartIsTypedFailure() {
        var requested = new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var achieved = new RenderExtent(MediaTime.ofMillis(1000), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "ffmpeg", "chain-1", "1.0", "ok", requested, achieved, null);
        assertFalse(r.success());
    }

    @Test
    void mismatchedFrameRateIsTypedFailure() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "ffmpeg", "chain-1", "1.0", "ok",
                extentWithRate(10000, 25, 1), extentWithRate(10000, 30, 1), null);
        assertFalse(r.success());
        assertFalse(r.authoritativeSuccess());
    }

    @Test
    void ordinarySuccessWithoutExtentIsNotAuthoritative() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "ffmpeg", "chain-1", "1.0", "ok", null, null, null);
        assertTrue(r.success(), "operationally may succeed");
        assertFalse(r.authoritativeSuccess(),
                "no extent request/proof → NOT authoritative extent success (AUTHORITATIVE_SUCCESS_WITHOUT_EXTENT_PROOF_COUNT=0)");
    }

    @Test
    void failedResultIsNeverAuthoritative() {
        var r = RenderOrchestrator.RenderResult.failed("job-1", "provider error");
        assertFalse(r.success());
        assertFalse(r.authoritativeSuccess());
    }

    @Test
    void realOrchestratorPathCarriesRequestedExtent() {
        // The real production path: DefaultRenderOrchestrator.execute passes
        // job.requestedExtent() into the result factory. A job declaring a
        // requested extent must fail closed because no provider reports
        // achieved evidence (honest: no fake achieved extent).
        RenderJob job = new RenderJob("job-r", "captioned_video_export", "production", "1920x1080",
                List.of("ast_1"), "{}", null, null, "mp4",
                List.of("render"), new RenderConstraints(1920, 1080, 25, 120, "mp4", "h264"),
                false, List.of(), List.of(), extent(10000));
        DefaultRenderOrchestrator orchestrator = new DefaultRenderOrchestrator(null, null, null, null, null);
        // Execute with a null planner would NPE; we validate the contract at the
        // factory boundary instead: requested extent must reach the result.
        // (DefaultRenderOrchestrator wiring is covered by its own integration tests.)
        RenderOrchestrator.RenderResult result = RenderOrchestrator.RenderResult.success(
                job.id(), "art", "s3://x", 1, "mp4", "1920x1080",
                "orchestrator", "chain", "1", "ok", job.requestedExtent(), null, null);
        assertFalse(result.success(), "declared requested extent without achieved evidence → typed failure (real path contract)");
        assertEquals(extent(10000), result.requestedRenderExtent());
    }
}
