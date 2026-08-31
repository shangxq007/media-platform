package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class FinalHostileRepairSourceGuardTest {

    @Test
    void unsafeAuthorityFamiliesAreDenyFirstAndAbsentFromAuthenticatedFamilies() {
        String policy = read(
                "platform-app/src/main/java/com/example/platform/security/PhaseZeroContainmentPolicy.java");
        String disabled = between(policy,
                "private static final List<String> DISABLED_FAMILIES",
                "/** Known application families");
        String authenticated = between(policy,
                "private static final List<String> AUTHENTICATED_FAMILIES",
                "private PhaseZeroContainmentPolicy()");

        for (String pattern : List.of(
                "/api/audit/compliance/**",
                "/api/navigation/preview",
                "/api/tenants/*/projects/*/upload/**",
                "/api/semantic/**",
                "/api/storage/*",
                "/api/feature-flags/**")) {
            assertTrue(disabled.contains("\"" + pattern + "\""),
                    () -> pattern + " must be explicitly deny-first contained");
        }
        for (String unsafeAuthenticatedPattern : List.of(
                "/api/audit/compliance/**",
                "/api/navigation/**",
                "/api/tenants/*/projects/*/upload/**",
                "/api/semantic/**",
                "/api/storage/*",
                "/api/feature-flags/**")) {
            assertFalse(authenticated.contains("\"" + unsafeAuthenticatedPattern + "\""),
                    () -> unsafeAuthenticatedPattern + " must not retain authenticated dispatch");
        }
    }

    @Test
    void finalHostileExternalStubsContainNoPlaceholderSuccessSourcePaths() {
        String producer = read(
                "render-module/src/main/java/com/example/platform/render/infrastructure/asset/provider/RemotionProducer.java");
        assertFalse(producer.contains("ProducerResult.success("));

        for (String path : List.of(
                "render-module/src/main/java/com/example/platform/render/infrastructure/renderplan/RemotionTool.java",
                "render-module/src/main/java/com/example/platform/render/infrastructure/renderplan/MLTTool.java")) {
            String tool = read(path);
            assertFalse(tool.contains("ToolResult.success("), path);
            assertFalse(tool.contains("Files.create"), path);
            assertFalse(tool.contains("/tmp/renderplan-output"), path);
            assertFalse(tool.contains("return true;"), path);
        }
    }

    private static String between(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start, "source markers must exist in order");
        return source.substring(start, end);
    }

    private static String read(String relativePath) {
        try {
            return Files.readString(repoRoot().resolve(relativePath));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static Path repoRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("settings.gradle.kts not found");
        }
        return candidate;
    }
}
