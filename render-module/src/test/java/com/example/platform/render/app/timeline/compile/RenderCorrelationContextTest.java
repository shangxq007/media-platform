package com.example.platform.render.app.timeline.compile;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RenderCorrelationContext}.
 * Proves: immutability, correlation ID != fingerprint, safe snapshots, no unsafe fields.
 */
class RenderCorrelationContextTest {

    @Test
    @DisplayName("Context has renderCorrelationId")
    void hasCorrelationId() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY");
        assertNotNull(ctx.renderCorrelationId());
        assertFalse(ctx.renderCorrelationId().isEmpty());
    }

    @Test
    @DisplayName("Correlation ID is not equal to fingerprint")
    void correlationIdNotFingerprint() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY")
                .withFingerprint("rfp-test");
        assertNotEquals(ctx.renderCorrelationId(), ctx.renderRequestFingerprint());
    }

    @Test
    @DisplayName("Correlation ID does not affect fingerprint determinism")
    void correlationIdDoesNotAffectFingerprint() {
        RenderRequestFingerprint fp1 = RenderRequestFingerprint.generate("proj-1", "rev-1", "default_1080p", "LEGACY");
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY");
        RenderRequestFingerprint fp2 = RenderRequestFingerprint.generate("proj-1", "rev-1", "default_1080p", "LEGACY");
        assertEquals(fp1.value(), fp2.value());
    }

    @Test
    @DisplayName("Same request gets different correlation IDs across attempts")
    void differentCorrelationIdsAcrossAttempts() {
        RenderCorrelationContext ctx1 = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY");
        RenderCorrelationContext ctx2 = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY");
        assertNotEquals(ctx1.renderCorrelationId(), ctx2.renderCorrelationId());
    }

    @Test
    @DisplayName("Context includes projectId and timelineRevisionId")
    void includesProjectAndRevision() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY");
        assertEquals("proj-1", ctx.projectId());
        assertEquals("rev-1", ctx.timelineRevisionId());
    }

    @Test
    @DisplayName("Context includes executionMode")
    void includesExecutionMode() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "PLAN_BASED");
        assertEquals("PLAN_BASED", ctx.executionMode());
    }

    @Test
    @DisplayName("withFingerprint returns new immutable copy")
    void withFingerprintImmutable() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY");
        RenderCorrelationContext withFp = ctx.withFingerprint("rfp-test");
        assertNull(ctx.renderRequestFingerprint());
        assertEquals("rfp-test", withFp.renderRequestFingerprint());
        assertEquals(ctx.renderCorrelationId(), withFp.renderCorrelationId());
    }

    @Test
    @DisplayName("withGraphIds attaches graph IDs")
    void withGraphIds() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY")
                .withGraphIds("ag-1", "cg-1");
        assertEquals("ag-1", ctx.artifactGraphId());
        assertEquals("cg-1", ctx.capabilityGraphId());
    }

    @Test
    @DisplayName("withPlanIds attaches plan IDs")
    void withPlanIds() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY")
                .withPlanIds("pbp-1", "rep-1");
        assertEquals("pbp-1", ctx.providerBindingPlanId());
        assertEquals("rep-1", ctx.renderExecutionPlanId());
    }

    @Test
    @DisplayName("withLocalExecutionRunId attaches run ID")
    void withLocalExecutionRunId() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY")
                .withLocalExecutionRunId("run-1");
        assertEquals("run-1", ctx.localExecutionRunId());
    }

    @Test
    @DisplayName("withOutputProductId attaches output product ID")
    void withOutputProductId() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY")
                .withOutputProductId("prod-1");
        assertEquals("prod-1", ctx.outputProductId());
    }

    @Test
    @DisplayName("withInputProductIds attaches input product IDs")
    void withInputProductIds() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY")
                .withInputProductIds(List.of("input-1", "input-2"));
        assertEquals(List.of("input-1", "input-2"), ctx.inputProductIds());
    }

    @Test
    @DisplayName("snapshot returns same context (immutable)")
    void snapshotReturnsSame() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY");
        assertSame(ctx, ctx.snapshot());
    }

    @Test
    @DisplayName("hasFingerprint returns correct state")
    void hasFingerprintState() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY");
        assertFalse(ctx.hasFingerprint());
        assertTrue(ctx.withFingerprint("rfp-test").hasFingerprint());
    }

    @Test
    @DisplayName("Context does not contain unsafe fields")
    void noUnsafeFields() {
        RenderCorrelationContext ctx = RenderCorrelationContext.create("proj-1", "rev-1", "LEGACY");
        String s = ctx.toString();
        assertFalse(s.contains("ffmpeg -i"));
        assertFalse(s.contains("bucket"));
        assertFalse(s.contains("objectKey"));
        assertFalse(s.contains("rootPath"));
        assertFalse(s.contains("materializedPath"));
        assertFalse(s.contains("signedUrl"));
        assertFalse(s.contains("processEnvironment"));
    }
}
