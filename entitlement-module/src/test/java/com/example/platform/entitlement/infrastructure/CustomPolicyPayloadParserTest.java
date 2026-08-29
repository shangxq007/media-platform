package com.example.platform.entitlement.infrastructure;

import static org.junit.jupiter.api.Assertions.assertFalse;

import com.example.platform.entitlement.app.EntitlementPolicyService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Adapted retirement proof for plan-named capability override payloads. */
class CustomPolicyPayloadParserTest {
    @Test
    void customCapabilityPolicyParserAndGrantApiAreAbsent() {
        Path root = repositoryRoot();
        assertFalse(Files.exists(root.resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/infrastructure/CustomPolicyPayloadParser.java")));
        var methodNames = Arrays.stream(EntitlementPolicyService.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).toList();
        assertFalse(methodNames.contains("getPolicy"));
        assertFalse(methodNames.contains("validateExport"));
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
