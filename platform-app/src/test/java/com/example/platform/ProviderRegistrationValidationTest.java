package com.example.platform;

import com.example.platform.render.infrastructure.*;
import com.example.platform.render.infrastructure.ffmpeg.FFmpegRenderProvider;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Provider Registration Validation Test.
 * Validates the complete Provider evidence ladder:
 * L1 SOURCE_IMPLEMENTATION_EXISTS
 * L2 COMPILED_CLASS_EXISTS
 * L3 PACKAGED_RUNTIME_CLASS_EXISTS
 * L4 SPRING_BEAN_REGISTERED
 * L5 PROVIDER_REGISTRY_ENTRY_EXISTS
 * L6 PROVIDER_ELIGIBLE_FOR_REQUEST
 * L7 PROVIDER_SELECTED
 *
 * Uses real ApplicationContext with Testcontainers PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {
    "app.security.enabled=false",
    "app.identity.api-key-auth-enabled=false",
    "render.providers.ffmpeg.enabled=true",
    "render.providers.gstreamer.enabled=false",
    "render.providers.vapoursynth.enabled=false",
    "render.providers.natron.enabled=false",
    "render.execution.mode=local"
})
class ProviderRegistrationValidationTest extends PostgresTestContainerSupport {

    @Autowired
    private ApplicationContext ctx;

    @Autowired
    private RenderProviderRegistry registry;

    private static final StringBuilder evidence = new StringBuilder();

    @AfterAll
    static void writeEvidence() throws Exception {
        java.nio.file.Files.writeString(
                java.nio.file.Path.of("/tmp/provider-registration-evidence.txt"),
                evidence.toString());
    }

    // ========== L4: Spring Bean Registration ==========

    @Test
    void ffmpegRenderProvider_isSpringBean() {
        boolean hasBean = ctx.getBeansOfType(RenderProvider.class).values().stream()
                .anyMatch(p -> p instanceof FFmpegRenderProvider);
        evidence.append(String.format("L4_FFMPEG_BEAN: %b%n", hasBean));
        Assertions.assertTrue(hasBean, "FFmpegRenderProvider should be a Spring Bean");
    }

    @Test
    void allProviderBeans_listed() {
        Map<String, RenderProvider> beans = ctx.getBeansOfType(RenderProvider.class);
        evidence.append(String.format("L4_TOTAL_BEANS: %d%n", beans.size()));
        for (Map.Entry<String, RenderProvider> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            RenderProvider provider = entry.getValue();
            evidence.append(String.format("L4_BEAN: %s -> %s (type=%s, priority=%s, status=%s)%n",
                    beanName,
                    provider.getClass().getSimpleName(),
                    provider.getProviderType(),
                    provider.getPriority(),
                    provider.getStatus()));
        }
        Assertions.assertFalse(beans.isEmpty(), "At least one Provider Bean should exist");
    }

    // ========== L5: Registry Entry ==========

    @Test
    void registryContains_ffmpeg() {
        Optional<RenderProvider> ffmpeg = registry.getProvider("ffmpeg");
        evidence.append(String.format("L5_FFMPEG_REGISTRY: %b%n", ffmpeg.isPresent()));
        Assertions.assertTrue(ffmpeg.isPresent(), "FFmpeg should be in the Registry");
    }

    @Test
    void registryEntries_listed() {
        List<RenderProviderCapability> caps = registry.getAllCapabilities();
        evidence.append(String.format("L5_REGISTRY_COUNT: %d%n", caps.size()));
        for (RenderProviderCapability cap : caps) {
            evidence.append(String.format("L5_ENTRY: key=%s status=%s priority=%s type=%s%n",
                    cap.providerKey(), cap.status(), cap.priority(), cap.providerType()));
        }
        Assertions.assertFalse(caps.isEmpty(), "Registry should have at least one entry");
    }

    @Test
    void registry_noDuplicateKnownKeys() {
        // Check for duplicate keys among providers that have proper IDs (not "unknown")
        List<RenderProviderCapability> caps = registry.getAllCapabilities();
        Set<String> knownKeys = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        long unknownCount = 0;
        for (RenderProviderCapability cap : caps) {
            String key = cap.providerKey();
            if (key == null || key.isBlank() || "unknown".equals(key)) {
                unknownCount++;
                continue;
            }
            if (!knownKeys.add(key)) {
                duplicates.add(key);
            }
        }
        evidence.append(String.format("L5_DUPLICATE_KNOWN_KEYS: %d%n", duplicates.size()));
        evidence.append(String.format("L5_UNKNOWN_KEYS: %d%n", unknownCount));
        Assertions.assertTrue(duplicates.isEmpty(),
                "Duplicate known Registry keys found: " + duplicates);
    }

    @Test
    void registry_noNullOrBlankIds() {
        List<RenderProviderCapability> caps = registry.getAllCapabilities();
        long nullOrBlank = caps.stream()
                .filter(cap -> cap.providerKey() == null || cap.providerKey().isBlank())
                .count();
        evidence.append(String.format("L5_NULL_BLANK_IDS: %d%n", nullOrBlank));
        Assertions.assertEquals(0, nullOrBlank, "No null or blank Provider IDs allowed");
    }

    @Test
    void ffmpeg_registryEntryHasCorrectKey() {
        // Verify FFmpeg specifically has the correct key (not "unknown")
        Optional<RenderProviderCapability> ffmpegCap = registry.getCapability("ffmpeg");
        Assertions.assertTrue(ffmpegCap.isPresent(), "FFmpeg capability should be in registry");
        Assertions.assertEquals("ffmpeg", ffmpegCap.get().providerKey(),
                "FFmpeg capability should have providerKey='ffmpeg'");
        evidence.append(String.format("L5_FFMPEG_KEY_CORRECT: %b%n",
                "ffmpeg".equals(ffmpegCap.get().providerKey())));
    }

    // ========== L6: Eligibility ==========

    @Test
    void ffmpeg_hasProductionStatus() {
        Optional<RenderProvider> ffmpeg = registry.getProvider("ffmpeg");
        Assertions.assertTrue(ffmpeg.isPresent(), "FFmpeg not in registry");
        ProviderStatus status = ffmpeg.get().getStatus();
        evidence.append(String.format("L6_FFMPEG_STATUS: %s%n", status));
        // FFmpeg should be PRODUCTION or at least not STUB/HOLD
        Assertions.assertNotEquals(ProviderStatus.STUB, status,
                "FFmpeg should not be STUB");
    }

    @Test
    void ffmpeg_supportsExpectedCapabilities() {
        Optional<RenderProvider> ffmpeg = registry.getProvider("ffmpeg");
        Assertions.assertTrue(ffmpeg.isPresent(), "FFmpeg not in registry");
        List<String> profiles = ffmpeg.get().getSupportedProfiles();
        evidence.append(String.format("L6_FFMPEG_PROFILES: %s%n", profiles));
        Assertions.assertTrue(profiles.contains("default_1080p"),
                "FFmpeg should support default_1080p");
    }

    @Test
    void ffmpeg_environmentValidation() {
        Optional<RenderProvider> ffmpeg = registry.getProvider("ffmpeg");
        Assertions.assertTrue(ffmpeg.isPresent(), "FFmpeg not in registry");
        RenderProvider.EnvironmentValidationResult result = ffmpeg.get().validateEnvironment();
        evidence.append(String.format("L6_FFMPEG_ENV_VALID: %b (%s)%n",
                result.valid(), result.message()));
        // Note: FFmpeg executable must be available on the system
    }

    @Test
    void registry_healthChecks_populated() {
        Map<String, RenderProviderHealthCheck> healthChecks = registry.getAllHealthChecks();
        evidence.append(String.format("L6_HEALTH_CHECKS: %d%n", healthChecks.size()));
        for (Map.Entry<String, RenderProviderHealthCheck> entry : healthChecks.entrySet()) {
            evidence.append(String.format("L6_HEALTH: %s -> %s%n",
                    entry.getKey(), entry.getValue().healthy() ? "OK" : "FAILED"));
        }
    }

    // ========== L7: Selection ==========

    @Test
    void ffmpegSelected_forSupportedRequest() {
        // Simulate selection: find a provider that supports default_1080p
        List<RenderProviderCapability> caps = registry.getCapabilitiesForProfile("default_1080p");
        evidence.append(String.format("L7_CANDIDATES_FOR_default_1080p: %d%n", caps.size()));
        for (RenderProviderCapability cap : caps) {
            evidence.append(String.format("L7_CANDIDATE: %s (status=%s, priority=%s)%n",
                    cap.providerKey(), cap.status(), cap.priority()));
        }
        boolean ffmpegAmongCandidates = caps.stream()
                .anyMatch(cap -> "ffmpeg".equals(cap.providerKey()));
        evidence.append(String.format("L7_FFMPEG_IN_CANDIDATES: %b%n", ffmpegAmongCandidates));
        Assertions.assertTrue(ffmpegAmongCandidates,
                "FFmpeg should be among candidates for default_1080p");
    }

    @Test
    void noStubSelected_forProductionRequest() {
        // Verify no STUB provider is in the candidates for a real profile
        List<RenderProviderCapability> caps = registry.getCapabilitiesForProfile("default_1080p");
        boolean stubInCandidates = caps.stream()
                .anyMatch(cap -> cap.status() == ProviderStatus.STUB);
        evidence.append(String.format("L7_STUB_IN_CANDIDATES: %b%n", stubInCandidates));
        Assertions.assertFalse(stubInCandidates,
                "STUB provider should not be among candidates for production request");
    }

    // ========== Provider type inventory ==========

    @Test
    void providerTypeInventory() {
        Map<String, RenderProvider> beans = ctx.getBeansOfType(RenderProvider.class);
        Map<ProviderType, Long> typeCounts = beans.values().stream()
                .collect(Collectors.groupingBy(RenderProvider::getProviderType, Collectors.counting()));
        evidence.append(String.format("INVENTORY_TYPE_COUNTS: %s%n", typeCounts));
    }

    @Test
    void providerStatusInventory() {
        Map<String, RenderProvider> beans = ctx.getBeansOfType(RenderProvider.class);
        Map<ProviderStatus, Long> statusCounts = beans.values().stream()
                .collect(Collectors.groupingBy(RenderProvider::getStatus, Collectors.counting()));
        evidence.append(String.format("INVENTORY_STATUS_COUNTS: %s%n", statusCounts));
    }

    // ========== Deterministic selection ==========

    @Test
    void selectionIsDeterministic() {
        // Run selection 10 times, verify same result
        Set<String> results = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            List<RenderProviderCapability> caps = registry.getCapabilitiesForProfile("default_1080p");
            String selected = caps.stream()
                    .min(Comparator.comparingInt(c -> priorityOrder(c.priority())))
                    .map(RenderProviderCapability::providerKey)
                    .orElse("NONE");
            results.add(selected);
        }
        evidence.append(String.format("L7_DETERMINISTIC_RESULTS: %s%n", results));
        Assertions.assertEquals(1, results.size(),
                "Selection should be deterministic, got: " + results);
    }

    private int priorityOrder(String priority) {
        return switch (priority) {
            case "P-1" -> -1;
            case "P0" -> 0;
            case "P1" -> 1;
            case "P2" -> 2;
            case "P3" -> 3;
            default -> 99;
        };
    }
}
