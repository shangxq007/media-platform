package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.domain.provider.ProviderImplementationId;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkerFabricIdentitySeparationTest {

    @Test
    void physicalHostAndWorkerRuntimeAreDifferentIdentityAuthorities() {
        assertThat(PhysicalHostId.class).isNotEqualTo(WorkerRuntimeId.class);
        assertThat((Object) PhysicalHostId.of("shared-value"))
                .isNotEqualTo(WorkerRuntimeId.of("shared-value"));
    }

    @Test
    void physicalHostAndProviderImplementationAreDifferentIdentityAuthorities() {
        assertThat(PhysicalHostId.class).isNotEqualTo(ProviderImplementationId.class);
        assertThat((Object) PhysicalHostId.of("shared-value"))
                .isNotEqualTo(ProviderImplementationId.of("shared-value"));
    }

    @Test
    void workerRuntimeAndProviderImplementationAreDifferentIdentityAuthorities() {
        assertThat(WorkerRuntimeId.class).isNotEqualTo(ProviderImplementationId.class);
        assertThat((Object) WorkerRuntimeId.of("shared-value"))
                .isNotEqualTo(ProviderImplementationId.of("shared-value"));
    }

    @Test
    void deviceAndWorkerRuntimeAreDifferentIdentityAuthorities() {
        assertThat(DeviceId.class).isNotEqualTo(WorkerRuntimeId.class);
        assertThat((Object) DeviceId.of("shared-value"))
                .isNotEqualTo(WorkerRuntimeId.of("shared-value"));
    }

    @Test
    void stableAndIncarnationIdentitiesAreDifferentAuthorities() {
        assertThat(PhysicalHostId.class).isNotEqualTo(PhysicalHostIncarnationId.class);
        assertThat(WorkerRuntimeId.class).isNotEqualTo(WorkerRuntimeIncarnationId.class);
        assertThat((Object) PhysicalHostId.of("host-1"))
                .isNotEqualTo(PhysicalHostIncarnationId.of("host-1"));
        assertThat((Object) WorkerRuntimeId.of("runtime-1"))
                .isNotEqualTo(WorkerRuntimeIncarnationId.of("runtime-1"));
    }

    @Test
    void noProductSpecificCanonicalMachinePartitionExists()
            throws IOException, URISyntaxException {
        Path productionClasses = Path.of(
                PhysicalHostId.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Set<String> canonicalDomainTypes;
        try (var paths = Files.walk(productionClasses)) {
            canonicalDomainTypes = paths
                    .filter(path -> path.getFileName().toString().endsWith(".class"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.class$", ""))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        assertThat(canonicalDomainTypes)
                .doesNotContain("RenderWorker", "TranscodeWorker", "BlenderWorker");
    }
}
