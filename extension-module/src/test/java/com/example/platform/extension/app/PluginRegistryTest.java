package com.example.platform.extension.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginDiagnosticCode;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginHealth;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.extension.domain.ResourceRequirement;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * GREEN: registry (frozen contract A091). Reconciled from the authentic RED
 * proof: the baseline had no PluginRegistry descriptor authority; these
 * assertions prove startup registration, deterministic enumeration, immutable
 * snapshots, thread safety, duplicate rejection and capability candidate
 * queries now exist.
 */
class PluginRegistryTest {

    private PluginRegistryImpl registry;

    @BeforeEach
    void setUp() {
        registry = new PluginRegistryImpl();
    }

    private static PluginDescriptor plugin(String id, String version) {
        return new PluginDescriptor(
                id, version, "1", "media-platform",
                List.of(new CapabilityDescriptor(
                        "media.render", "1.0", "render", "RenderExecutionPlan",
                        "ArtifactReference", CapabilityDescriptor.InvocationMode.SYNC_ONLY)),
                List.of(new HandledObjectDescriptor(
                        "RenderExecutionPlan", "1",
                        "com.example.platform.render.domain.timeline.compile.executionplan.RenderExecutionPlan",
                        List.of("profile", "timelineSnapshotId"), List.of(),
                        HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
                InvocationContract.syncOnlyDefault(),
                List.of(new PermissionDescriptor("ffmpeg.execute")),
                ResourceRequirement.ffmpegDefaults(),
                PluginRuntimeRequirement.trustedInProcess(),
                PluginGuarantee.ffmpegDefaults());
    }

    @Test
    void startupRegistration() {
        assertTrue(registry.register(plugin("media.render.ffmpeg", "1.0.0")).isEmpty());
        assertEquals(1, registry.enumerate().size());
        assertTrue(registry.findByPluginId("media.render.ffmpeg").isPresent());
    }

    @Test
    void deterministicEnumerationStableIdOrder() {
        registry.register(plugin("media.render.zzz", "1.0.0"));
        registry.register(plugin("media.render.aaa", "1.0.0"));
        registry.register(plugin("media.render.mmm", "1.0.0"));
        List<String> ids = registry.enumerate().stream().map(PluginDescriptor::pluginId).toList();
        assertEquals(List.of("media.render.aaa", "media.render.mmm", "media.render.zzz"), ids);
        // Repeated enumeration is stable.
        assertEquals(ids, registry.enumerate().stream().map(PluginDescriptor::pluginId).toList());
    }

    @Test
    void duplicatePluginRejectedPlg015() {
        assertTrue(registry.register(plugin("media.render.ffmpeg", "1.0.0")).isEmpty());
        var issues = registry.register(plugin("media.render.ffmpeg", "1.0.0"));
        assertEquals(PluginDiagnosticCode.PLG_015, issues.get(0).code());
        // Same pluginId different version = version conflict rejection in P1.
        var versionConflict = registry.register(plugin("media.render.ffmpeg", "2.0.0"));
        assertEquals(PluginDiagnosticCode.PLG_015, versionConflict.get(0).code());
        assertEquals(1, registry.enumerate().size());
    }

    @Test
    void immutableDescriptorSnapshot() {
        registry.register(plugin("media.render.ffmpeg", "1.0.0"));
        List<PluginDescriptor> snapshot = registry.enumerate();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.clear());
    }

    @Test
    void stableLookup() {
        registry.register(plugin("media.render.ffmpeg", "1.0.0"));
        assertEquals("media.render.ffmpeg",
                registry.findByPluginIdAndVersion("media.render.ffmpeg", "1.0.0")
                        .orElseThrow().pluginId());
        assertTrue(registry.findByPluginId("media.render.other").isEmpty());
    }

    @Test
    void capabilityCandidateQuery() {
        registry.register(plugin("media.render.ffmpeg", "1.0.0"));
        registry.register(plugin("media.render.other", "1.0.0"));
        var candidates = registry.findCapabilityCandidates("media.render", "1.0");
        assertEquals(2, candidates.size());
        assertEquals(List.of("media.render.ffmpeg", "media.render.other"),
                candidates.stream().map(PluginDescriptor::pluginId).toList());
        assertTrue(registry.findCapabilityCandidates("media.render", "2").isEmpty());
    }

    @Test
    void threadSafeConcurrentRegistrationAndRead() throws Exception {
        int threads = 8;
        int perThread = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger();
        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int threadIdx = t;
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < perThread; i++) {
                    String id = "plugin." + threadIdx + "." + i;
                    var issues = registry.register(plugin(id, "1.0.0"));
                    if (!issues.isEmpty()) {
                        failures.incrementAndGet();
                    }
                    // Concurrent reads during registration must be safe.
                    registry.enumerate();
                    registry.healthOf(id);
                }
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertEquals(0, failures.get());
        assertEquals(threads * perThread, registry.enumerate().size());
        // Deterministic enumeration holds under concurrency.
        assertEquals(registry.enumerate(),
                registry.enumerate().stream().sorted(
                        java.util.Comparator.comparing(PluginDescriptor::pluginId)).toList());
    }

    @Test
    void healthAssociationQuery() {
        registry.register(plugin("media.render.ffmpeg", "1.0.0"));
        PluginHealth health = registry.healthOf("media.render.ffmpeg");
        assertEquals(PluginHealth.State.UNKNOWN, health.state());
        // UNKNOWN is eligible (derived-lazy health).
        assertTrue(health.eligible());
    }
}
