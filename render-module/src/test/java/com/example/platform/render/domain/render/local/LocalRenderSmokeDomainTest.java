package com.example.platform.render.domain.render.local;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for local render smoke domain types.
 * Does not require FFmpeg.
 */
class LocalRenderSmokeDomainTest {

    @Test
    void smokeIdRejectsNull() {
        assertThrows(NullPointerException.class, () -> new LocalRenderSmokeId(null));
    }

    @Test
    void smokeIdRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new LocalRenderSmokeId(""));
        assertThrows(IllegalArgumentException.class, () -> new LocalRenderSmokeId("  "));
    }

    @Test
    void smokeIdAcceptsValue() {
        assertEquals("test-123", new LocalRenderSmokeId("test-123").value());
    }

    @Test
    void smokeIdGeneratesUnique() {
        LocalRenderSmokeId a = LocalRenderSmokeId.generate();
        LocalRenderSmokeId b = LocalRenderSmokeId.generate();
        assertNotEquals(a.value(), b.value());
        assertTrue(a.value().startsWith("smoke-"));
    }

    @Test
    void smokeNameRejectsNull() {
        assertThrows(NullPointerException.class, () -> new LocalRenderSmokeName(null));
    }

    @Test
    void smokeNameRejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new LocalRenderSmokeName(""));
    }

    @Test
    void statusEnumContainsRequired() {
        assertNotNull(LocalRenderSmokeStatus.PASS);
        assertNotNull(LocalRenderSmokeStatus.PASS_WITH_WARNINGS);
        assertNotNull(LocalRenderSmokeStatus.FAIL);
        assertNotNull(LocalRenderSmokeStatus.BLOCKED);
        assertNotNull(LocalRenderSmokeStatus.SKIPPED);
        assertNotNull(LocalRenderSmokeStatus.NOT_AVAILABLE);
    }

    @Test
    void issueSeverityEnumContainsRequired() {
        assertNotNull(LocalRenderSmokeIssueSeverity.INFO);
        assertNotNull(LocalRenderSmokeIssueSeverity.WARNING);
        assertNotNull(LocalRenderSmokeIssueSeverity.ERROR);
        assertNotNull(LocalRenderSmokeIssueSeverity.BLOCKING);
    }

    @Test
    void issueCodeEnumContainsSafetyBoundaries() {
        assertNotNull(LocalRenderSmokeIssueCode.FFMPEG_NOT_AVAILABLE);
        assertNotNull(LocalRenderSmokeIssueCode.FFPROBE_NOT_AVAILABLE);
        assertNotNull(LocalRenderSmokeIssueCode.COMMAND_ALLOWLIST_VIOLATION);
        assertNotNull(LocalRenderSmokeIssueCode.SHELL_INVOCATION_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.USER_COMMAND_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.RAW_FILTERGRAPH_INPUT_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.OPEN_CUE_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.PRODUCT_RUNTIME_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.STORAGE_RUNTIME_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.ARTIFACT_DAG_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.REMOTION_EXECUTION_FORBIDDEN);
        assertNotNull(LocalRenderSmokeIssueCode.PUBLIC_API_FORBIDDEN);
    }

    @Test
    void issueFactoryMethods() {
        var info = LocalRenderSmokeIssue.info(LocalRenderSmokeIssueCode.FFMPEG_NOT_AVAILABLE, "test");
        assertEquals(LocalRenderSmokeIssueSeverity.INFO, info.severity());

        var warn = LocalRenderSmokeIssue.warning(LocalRenderSmokeIssueCode.INVALID_OUTPUT_CODEC, "test");
        assertEquals(LocalRenderSmokeIssueSeverity.WARNING, warn.severity());

        var err = LocalRenderSmokeIssue.error(LocalRenderSmokeIssueCode.FFMPEG_EXIT_NONZERO, "test");
        assertEquals(LocalRenderSmokeIssueSeverity.ERROR, err.severity());

        var block = LocalRenderSmokeIssue.blocking(LocalRenderSmokeIssueCode.SHELL_INVOCATION_FORBIDDEN, "test");
        assertEquals(LocalRenderSmokeIssueSeverity.BLOCKING, block.severity());
    }

    @Test
    void issueMetadataDefaultsToEmpty() {
        var issue = LocalRenderSmokeIssue.info(LocalRenderSmokeIssueCode.FFMPEG_NOT_AVAILABLE, "test");
        assertNotNull(issue.metadata());
        assertTrue(issue.metadata().isEmpty());
    }

    @Test
    void requestRejectsInvalidDimensions() {
        var id = LocalRenderSmokeId.generate();
        var name = new LocalRenderSmokeName("test");
        assertThrows(IllegalArgumentException.class,
                () -> new LocalRenderSmokeRequest(id, name, 0, 180, 2.0, 30, Path.of("/tmp")));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalRenderSmokeRequest(id, name, 320, 0, 2.0, 30, Path.of("/tmp")));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalRenderSmokeRequest(id, name, 320, 180, 0, 30, Path.of("/tmp")));
        assertThrows(IllegalArgumentException.class,
                () -> new LocalRenderSmokeRequest(id, name, 320, 180, 2.0, 0, Path.of("/tmp")));
    }

    @Test
    void requestTestsrcFactory() {
        var req = LocalRenderSmokeRequest.testsrcH264Mp4(Path.of("/tmp/test"));
        assertEquals(320, req.width());
        assertEquals(180, req.height());
        assertEquals(2.0, req.durationSec());
        assertEquals(30, req.fps());
        assertEquals("local-smoke-001-testsrc-h264-mp4", req.smokeName().value());
    }

    @Test
    void policyDefaultDisabled() {
        var policy = LocalRenderSmokePolicy.defaultDisabled();
        assertFalse(policy.allowExecution());
        assertFalse(policy.strictMode());
        assertEquals(20, policy.timeoutSeconds());
        assertTrue(policy.allowedBinaries().contains("ffmpeg"));
        assertTrue(policy.allowedBinaries().contains("ffprobe"));
    }

    @Test
    void policyDefaultEnabled() {
        var policy = LocalRenderSmokePolicy.defaultEnabled();
        assertTrue(policy.allowExecution());
    }

    @Test
    void policyBinaryAllowlist() {
        var policy = LocalRenderSmokePolicy.defaultDisabled();
        assertTrue(policy.isBinaryAllowed("ffmpeg"));
        assertTrue(policy.isBinaryAllowed("ffprobe"));
        assertFalse(policy.isBinaryAllowed("bash"));
        assertFalse(policy.isBinaryAllowed("sh"));
        assertFalse(policy.isBinaryAllowed("python"));
    }

    @Test
    void policyShellInvocationDetection() {
        var policy = LocalRenderSmokePolicy.defaultDisabled();
        assertTrue(policy.containsShellInvocation(List.of("sh", "-c", "echo hello")));
        assertTrue(policy.containsShellInvocation(List.of("bash", "-c", "ls")));
        assertFalse(policy.containsShellInvocation(List.of("ffmpeg", "-y", "-i", "input")));
        assertFalse(policy.containsShellInvocation(List.of()));
        assertFalse(policy.containsShellInvocation(null));
    }

    @Test
    void policyRejectsNegativeTimeout() {
        assertThrows(IllegalArgumentException.class,
                () -> new LocalRenderSmokePolicy(true, -1, Path.of("/tmp"), true, Set.of("ffmpeg"), false));
    }
}
