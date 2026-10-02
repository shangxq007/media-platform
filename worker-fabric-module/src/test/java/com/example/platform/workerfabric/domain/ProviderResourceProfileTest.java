package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** P2-5b-2a-2a-2-R3: the provider resource footprint and its demand derivation. */
class ProviderResourceProfileTest {

    /** Owner-approved bounded V1 FFmpeg footprint: 2 cores / 1 GiB memory / 2 GiB scratch, no devices. */
    private static final ProviderResourceProfile FFMPEG_BOUNDED_V1 =
            new ProviderResourceProfile(2000L, 1073741824L, 2147483648L, Map.of());

    @Test
    void declaredFootprintIsRetainedExactly() {
        assertThat(FFMPEG_BOUNDED_V1.cpuMillicores()).isEqualTo(2000L);
        assertThat(FFMPEG_BOUNDED_V1.memoryBytes()).isEqualTo(1073741824L);
        assertThat(FFMPEG_BOUNDED_V1.temporaryStorageBytes()).isEqualTo(2147483648L);
        assertThat(FFMPEG_BOUNDED_V1.deviceDemands()).isEmpty();
    }

    @Test
    void negativeFootprintValuesAreRejected() {
        assertThatThrownBy(() -> new ProviderResourceProfile(-1L, 0L, 0L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cpuMillicores");
        assertThatThrownBy(() -> new ProviderResourceProfile(0L, -1L, 0L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memoryBytes");
        assertThatThrownBy(() -> new ProviderResourceProfile(0L, 0L, -1L, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temporaryStorageBytes");
    }

    @Test
    void demandIsMappedDirectlyFromTheDeclaredFootprint() {
        RuntimeResourceDemand demand = TaskResourceDemandDeriver.derive(FFMPEG_BOUNDED_V1);

        assertThat(demand.cpuMillicores()).isEqualTo(2000L);
        assertThat(demand.memoryBytes()).isEqualTo(1073741824L);
        assertThat(demand.temporaryStorageBytes()).isEqualTo(2147483648L);
        assertThat(demand.deviceDemands()).isEmpty();
        // Deterministic: same input, same output.
        assertThat(TaskResourceDemandDeriver.derive(FFMPEG_BOUNDED_V1)).isEqualTo(demand);
    }

    @Test
    void absentProfileYieldsNoDemand() {
        assertThat(TaskResourceDemandDeriver.derive(Optional.empty())).isEmpty();
        assertThat(TaskResourceDemandDeriver.derive(Optional.of(FFMPEG_BOUNDED_V1)))
                .contains(TaskResourceDemandDeriver.derive(FFMPEG_BOUNDED_V1));
    }
}
