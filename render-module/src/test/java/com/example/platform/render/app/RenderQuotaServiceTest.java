package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.commercial.QuotaConsumptionRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Adapted retirement and stable-command proof for the former Render quota shadow. */
class RenderQuotaServiceTest {
    @Test
    void renderQuotaShadowIsAbsentAndConsumptionRequiresStableIdempotency() {
        Path root = repositoryRoot();
        assertFalse(Files.exists(root.resolve(
                "render-module/src/main/java/com/example/platform/render/app/RenderQuotaService.java")));
        PrincipalRef principal = PrincipalRef.tenantScoped(
                "tenant-1", PrincipalType.ORGANIZATION, "tenant-1");
        Instant start = Instant.parse("2026-08-01T00:00:00Z");
        Instant end = Instant.parse("2026-09-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> new QuotaConsumptionRequest(
                principal, "render.job.create", 1, start, end,
                " ", "trace-1", "render completion", start));
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            current = current.getParent();
        }
        if (current == null) throw new IllegalStateException("repository root not found");
        return current;
    }
}
