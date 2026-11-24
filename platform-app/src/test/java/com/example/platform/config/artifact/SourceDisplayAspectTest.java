package com.example.platform.config.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class SourceDisplayAspectTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void usesExactSampleAspectAndDisplayMatrixAxisOrder() throws Exception {
        var source = JSON.readTree("""
                {"width": 256, "height": 144, "sample_aspect_ratio": "2:1", "display_aspect_ratio": "32:9"}
                """);
        assertThat(SourceDisplayAspect.fromProbe(source)).contains("32:9");
        var rotated = JSON.readTree("""
                {"width": 256, "height": 144, "sample_aspect_ratio": "2:1", "display_aspect_ratio": "32:9",
                 "side_data_list":[{"side_data_type":"Display Matrix","rotation":90,
                  "displaymatrix":"00000000: 0 -65536 0\\n00000001: 65536 0 0\\n00000002: 0 0 1073741824"}]}
                """);
        assertThat(SourceDisplayAspect.fromProbe(rotated)).contains("9:32");
    }

    @Test
    void unavailableFactsNeverAssumeSquarePixelsOrIgnoreOrientation() throws Exception {
        for (String stream : new String[]{
                "{\"width\":256,\"height\":144}",
                "{\"width\":256,\"height\":144,\"sample_aspect_ratio\":\"2:1\",\"display_aspect_ratio\":\"4:3\"}",
                "{\"width\":256,\"height\":144,\"sample_aspect_ratio\":\"2:1\",\"tags\":{\"rotate\":\"45\"}}",
                "{\"width\":256,\"height\":144,\"sample_aspect_ratio\":\"2:1\",\"side_data_list\":[{\"side_data_type\":\"Display Matrix\"}]}"
        }) {
            assertThat(SourceDisplayAspect.fromProbe(JSON.readTree(stream))).isEmpty();
        }
    }
}
