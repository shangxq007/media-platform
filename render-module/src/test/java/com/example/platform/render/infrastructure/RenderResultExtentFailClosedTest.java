package com.example.platform.render.infrastructure;

import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PRE-#21 final — typed render failure algebra + authoritative extent.
 *
 * Typed RenderExtent is the SINGLE extent authority (no String representation).
 * Typed RenderResultFailureReason is the failure identity; hitReason is
 * explanation only. Authoritative extent success requires requested +
 * achieved + semantic equality; ordinary success without extent proof is
 * never authoritative.
 */
class RenderResultExtentFailClosedTest {

    private static RenderExtent extent(long endMillis) {
        return new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(endMillis), FrameRate.of(25, 1));
    }

    private static RenderExtent extentWithRate(long endMillis, long num, long den) {
        return new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(endMillis), FrameRate.of(num, den));
    }

    // TEST-3: matching extent → authoritative success
    @Test
    void matchingTypedExtentIsAuthoritativeSuccess() {
        RenderExtent e = extent(10000);
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "provider-a", "chain-1", "1.0", "ok", e, e, null);
        assertTrue(r.success());
        assertNull(r.failureReason(), "success → typed failure reason absent");
        assertTrue(r.authoritativeSuccess());
        assertEquals(e, r.requestedRenderExtent());
        assertEquals(e, r.achievedRenderExtent());
    }

    // TEST-1: missing achieved extent → typed RENDER_EXTENT_UNPROVEN
    @Test
    void missingAchievedExtentIsTypedFailureWhenRequested() {
        RenderExtent e = extent(10000);
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "provider-a", "chain-1", "1.0", "ok", e, null, null);
        assertFalse(r.success(), "requested extent without achieved evidence must fail closed");
        assertEquals(RenderResultFailureReason.RENDER_EXTENT_UNPROVEN, r.failureReason(),
                "typed semantic failure reason required (not free-text)");
        assertFalse(r.authoritativeSuccess());
        assertNotNull(r.hitReason());
        assertTrue(r.hitReason().contains("extent not proven"));
    }

    // TEST-2: mismatched achieved extent → typed RENDER_EXTENT_NOT_ACHIEVED
    @Test
    void mismatchedEndIsTypedFailure() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "provider-a", "chain-1", "1.0", "ok",
                extent(10000), extent(5000), null);
        assertFalse(r.success());
        assertEquals(RenderResultFailureReason.RENDER_EXTENT_NOT_ACHIEVED, r.failureReason());
        assertFalse(r.authoritativeSuccess());
    }

    @Test
    void mismatchedStartIsTypedFailure() {
        var requested = new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var achieved = new RenderExtent(MediaTime.ofMillis(1000), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "provider-a", "chain-1", "1.0", "ok", requested, achieved, null);
        assertFalse(r.success());
        assertEquals(RenderResultFailureReason.RENDER_EXTENT_NOT_ACHIEVED, r.failureReason());
    }

    @Test
    void mismatchedFrameRateIsTypedFailure() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "provider-a", "chain-1", "1.0", "ok",
                extentWithRate(10000, 25, 1), extentWithRate(10000, 30, 1), null);
        assertFalse(r.success());
        assertEquals(RenderResultFailureReason.RENDER_EXTENT_NOT_ACHIEVED, r.failureReason());
        assertFalse(r.authoritativeSuccess());
    }

    // TEST-4: ordinary non-extent success → operational success, not authoritative
    @Test
    void ordinarySuccessWithoutExtentIsNotAuthoritative() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "provider-a", "chain-1", "1.0", "ok", null, null, null);
        assertTrue(r.success(), "operationally may succeed");
        assertNull(r.failureReason());
        assertFalse(r.authoritativeSuccess(),
                "no extent request/proof → NOT authoritative extent success");
    }

    @Test
    void failedResultCarriesTypedReason() {
        var r = RenderOrchestrator.RenderResult.failed("job-1",
                RenderResultFailureReason.ORCHESTRATION_ERROR, "provider error");
        assertFalse(r.success());
        assertEquals(RenderResultFailureReason.ORCHESTRATION_ERROR, r.failureReason());
        assertFalse(r.authoritativeSuccess());
    }

    // TEST-5: semantic reason available without parsing String detail
    @Test
    void semanticReasonIsAvailableWithoutParsingDetail() {
        var r = RenderOrchestrator.RenderResult.success(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                "provider-a", "chain-1", "1.0", "ok", extent(10000), null, null);
        assertEquals(RenderResultFailureReason.RENDER_EXTENT_UNPROVEN, r.failureReason(),
                "semantic branching uses typed reason, never String content");
    }

    // Direct-constructor bypass: raw success with requested but NO achieved
    // proof must NOT be authoritative (compact ctor allows it; semantics reject it)
    @Test
    void directlyConstructedSuccessWithoutAchievedProofIsNotAuthoritative() {
        RenderExtent e = extent(10000);
        var r = new RenderOrchestrator.RenderResult(
                "job-1", "art-1", "s3://out.mp4", 1000, "video/mp4", "1920x1080",
                true, null, "ok", "provider-a", "chain-1", "1.0",
                e, null, null);
        assertTrue(r.success());
        assertFalse(r.authoritativeSuccess(),
                "authoritative extent success requires achieved proof");
    }

    // TEST-6: real orchestrator path contract
    @Test
    void realOrchestratorPathCarriesRequestedExtentAndTypedFailure() {
        RenderJob job = new RenderJob("job-r", "captioned_video_export", "production", "1920x1080",
                List.of("ast_1"), "{}", null, null, "mp4",
                List.of("render"), new RenderConstraints(1920, 1080, 25, 120, "mp4", "h264"),
                false, List.of(), List.of(), extent(10000));
        // DefaultRenderOrchestrator passes job.requestedExtent(); no provider
        // reports achieved evidence → typed fail-closed result.
        RenderOrchestrator.RenderResult result = RenderOrchestrator.RenderResult.success(
                job.id(), "art", "s3://x", 1, "mp4", "1920x1080",
                "orchestrator", "chain", "1", "ok", job.requestedExtent(), null, null);
        assertFalse(result.success(), "declared requested extent without achieved evidence → typed failure");
        assertEquals(RenderResultFailureReason.RENDER_EXTENT_UNPROVEN, result.failureReason());
        assertFalse(result.authoritativeSuccess());
        assertEquals(extent(10000), result.requestedRenderExtent());
    }

    // Success invariant: success=true → failureReason absent (compact ctor)
    @Test
    void successResultCannotCarryFailureReason() {
        assertThrows(IllegalArgumentException.class, () -> new RenderOrchestrator.RenderResult(
                "job-1", null, null, 0, null, null,
                true, RenderResultFailureReason.STEP_FAILED, "x", null, null, null,
                null, null, null));
    }

    // Failure invariant: success=false → failureReason present (compact ctor)
    @Test
    void failedResultMustCarryTypedReason() {
        assertThrows(IllegalArgumentException.class, () -> new RenderOrchestrator.RenderResult(
                "job-1", null, null, 0, null, null,
                false, null, "x", null, null, null,
                null, null, null));
    }
}
