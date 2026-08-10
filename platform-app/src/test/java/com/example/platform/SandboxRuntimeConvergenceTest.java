package com.example.platform;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * PMPR-S1 convergence guards (PMPR-S1-AR-01..10 core subset).
 * RED on published parent ef261639, GREEN after sandbox-runtime -> extension::runtime convergence.
 */
class SandboxRuntimeConvergenceTest {

    private Path repoRoot() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.exists(p.resolve("settings.gradle.kts"))) {
            p = p.getParent();
        }
        if (p == null) {
            throw new IllegalStateException("settings.gradle.kts not found");
        }
        return p;
    }

    private String read(String rel) {
        try {
            return Files.readString(repoRoot().resolve(rel));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private boolean exists(String rel) {
        return Files.exists(repoRoot().resolve(rel));
    }

    /** PMPR-S1-AR-01: sandbox-runtime physical module absent. */
    @Test
    void ar01SandboxRuntimeModuleAbsent() {
        assertFalse(exists("sandbox-runtime-module"), "sandbox-runtime-module must be retired");
        assertFalse(read("settings.gradle.kts").contains("sandbox-runtime-module"),
                "settings.gradle.kts must not include sandbox-runtime-module");
    }

    /** PMPR-S1-AR-02: PluginRuntime single execution authority. */
    @Test
    void ar02PluginRuntimeSingleAuthority() {
        String cfg = read("extension-module/src/main/java/com/example/platform/extension/infrastructure/PluginRuntimeConfiguration.java");
        assertFalse(cfg.contains("ProviderExtensionSpiRuntimeAdapter"),
                "DefaultPluginRuntime must not depend on the SPI compatibility adapter");
    }

    /** PMPR-S1-AR-03: Sandbox Runtime peer @ApplicationModule absent. */
    @Test
    void ar03NoSandboxRuntimePeerModule() {
        assertFalse(exists("sandbox-runtime-module/src/main/java/com/example/platform/sandbox/package-info.java"),
                "sandbox-runtime peer module package-info must not exist");
    }

    /** PMPR-S1-AR-04: ProviderExtensionSpiRuntimeAdapter absent. */
    @Test
    void ar04CompatibilityAdapterAbsent() {
        assertFalse(exists("extension-module/src/main/java/com/example/platform/extension/runtime/internal/ProviderExtensionSpiRuntimeAdapter.java"),
                "ProviderExtensionSpiRuntimeAdapter must be deleted");
    }

    /** PMPR-S1-AR-05: no ProviderExtensionSPI independent execute authority. */
    @Test
    void ar05NoSpiExecuteAuthority() {
        assertFalse(exists("extension-module/src/main/java/com/example/platform/extension/domain/ProviderExtensionSPI.java"),
                "ProviderExtensionSPI must not remain an independent execution surface");
    }

    /** PMPR-S1-AR-06: sandbox-worker retained as deployment/security boundary. */
    @Test
    void ar06SandboxWorkerRetained() {
        org.junit.jupiter.api.Assertions.assertTrue(exists("sandbox-worker"), "sandbox-worker must be retained");
        org.junit.jupiter.api.Assertions.assertTrue(read("settings.gradle.kts").contains("sandbox-worker"),
                "sandbox-worker must remain registered in settings");
    }

    /** PMPR-S1-AR-07: platform-app old Gradle dependency absent. */
    @Test
    void ar07PlatformAppNoSandboxRuntimeDep() {
        String build = read("platform-app/build.gradle.kts");
        assertFalse(build.contains("project(\":sandbox-runtime-module\")"),
                "platform-app must not depend on sandbox-runtime-module");
    }

    /** PMPR-S1-AR-09: untrusted in-process execution denied. */
    @Test
    void ar09UntrustedInProcessDenied() {
        String trust = read("extension-module/src/main/java/com/example/platform/extension/runtime/internal/TrustPolicyEnforcer.java");
        assertFalse(trust.contains("ALLOW_UNTRUSTED_IN_PROCESS"),
                "untrusted in-process execution must be denied");
    }

}
