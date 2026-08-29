package com.example.platform.entitlement.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.commercial.CommercialAdmissionPort;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Adapted retirement proof: commercial tiers no longer route technical export execution. */
class ClientExportRoutingPolicyTest {
    @Test
    void tierRoutingShadowIsRetiredInFavorOfNeutralAdmission() {
        Path root = repositoryRoot();
        assertFalse(Files.exists(root.resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/domain/ClientExportRoutingPolicy.java")));
        assertTrue(CommercialAdmissionPort.class.isInterface());
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
