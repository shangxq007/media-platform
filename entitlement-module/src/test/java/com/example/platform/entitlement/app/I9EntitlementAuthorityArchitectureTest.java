package com.example.platform.entitlement.app;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class I9EntitlementAuthorityArchitectureTest {

    @Test
    void tierMetadataServiceCannotDefineCapabilityProviderPresetOrRuntimeSemantics() {
        String source = read(repositoryRoot().resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/app/EntitlementPolicyService.java"));
        for (String forbidden : List.of(
                "implements EntitlementPort", "FeatureFlag", "ExportCapabilityPolicy",
                "ProviderAccessPolicy", "validateExport(", "isFeatureEnabled(",
                "getExportCapabilities(", "getProviderAccess(", "selectProvider(",
                "findProviderCandidates(", "gpuAllowed", "allowedProviders")) {
            assertFalse(source.contains(forbidden), () -> "tier metadata retains capability authority: " + forbidden);
        }
    }

    @Test
    void entitlementDecisionCannotGrantFromTierOrPlanBranches() {
        String source = read(repositoryRoot().resolve(
                "entitlement-module/src/main/java/com/example/platform/entitlement/app/EntitlementDecisionService.java"));
        for (String forbidden : List.of(
                "checkTierPolicy", "tierAllowed", "Access granted by tier policy",
                "providerKey()", "requestedPreset()", "gpuAllowed", "maxResolutionHeight")) {
            assertFalse(source.contains(forbidden), () -> "tier branch can grant capability: " + forbidden);
        }
    }

    @Test
    void activeEntitlementAuthorityHasNoPlanNamedCapabilityForkTypes() {
        Path main = repositoryRoot().resolve("entitlement-module/src/main/java");
        for (String retired : List.of(
                "com/example/platform/entitlement/domain/ExportCapabilityPolicy.java",
                "com/example/platform/entitlement/domain/ProviderAccessPolicy.java",
                "com/example/platform/entitlement/domain/FeatureFlag.java",
                "com/example/platform/entitlement/app/EntitlementPort.java")) {
            assertFalse(Files.exists(main.resolve(retired)), () -> "capability fork still active: " + retired);
        }
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("settings.gradle.kts"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("settings.gradle.kts not found");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }
}
