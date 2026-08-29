package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class RuntimeDependencyCoordinateTest {

    @Test
    void coordinate_is_a_normalized_implementation_local_name() {
        RuntimeDependencyCoordinate coordinate = RuntimeDependencyCoordinate.of("ffmpeg.libavcodec");

        assertThat(coordinate.value()).isEqualTo("ffmpeg.libavcodec");
        assertThat(coordinate.toString()).isEqualTo("ffmpeg.libavcodec");
    }

    @Test
    void coordinate_rejects_blank_noncanonical_and_path_values() {
        for (String invalid : new String[] {
            "", " ", "FFmpeg", " ffmpeg", "ffmpeg ", "ffmpeg/libavcodec", "ffmpeg..codec", "/opt/ffmpeg"
        }) {
            assertThatIllegalArgumentException()
                    .as("reject %s", invalid)
                    .isThrownBy(() -> RuntimeDependencyCoordinate.of(invalid));
        }
    }
}
