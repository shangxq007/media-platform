package com.example.platform.quota.app;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class QuotaServiceMutationAuthorityTest {

    @Test
    void deprecatedModuleExposesNoUsageMutationApiOrStateMaps() throws Exception {
        assertFalse(Arrays.stream(QuotaService.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch("recordUsage"::equals));

        String source = Files.readString(repositoryRoot().resolve(
                "quota-billing-module/src/main/java/com/example/platform/quota/app/QuotaService.java"));
        assertFalse(source.contains("usageRecords"));
        assertFalse(source.contains("idempotencyIndex"));
        assertFalse(source.contains("withUsage"));
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("settings.gradle.kts"))) {
            return parent;
        }
        throw new IllegalStateException("Could not locate repository root from " + current);
    }
}
