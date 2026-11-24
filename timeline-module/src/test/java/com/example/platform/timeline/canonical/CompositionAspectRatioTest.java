package com.example.platform.timeline.canonical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositionAspectRatioTest {
    @Test
    void equalsAndHashesByTheReducedPair() throws Exception {
        var ratio = new CompositionAspectRatio(16, 9);
        var mapper = TimelineDocumentJsonSerializer.mapper();
        for (var equivalent : List.of(new CompositionAspectRatio(1920, 1080), new CompositionAspectRatio(3840, 2160))) {
            assertThat(equivalent).isEqualTo(ratio).hasSameHashCodeAs(ratio);
            assertThat(mapper.writeValueAsString(equivalent)).isEqualTo(mapper.writeValueAsString(ratio));
        }
        assertThat(new CompositionAspectRatio(1080, 1920)).isEqualTo(new CompositionAspectRatio(9, 16));
        assertThat(new CompositionAspectRatio(239, 100).matches(478, 200)).isTrue();
        assertThat(new CompositionAspectRatio(1000, 777)).isNotEqualTo(ratio);
        assertThat(new CompositionAspectRatio(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)
                .matches(Integer.MAX_VALUE, Integer.MAX_VALUE - 1)).isFalse();
        assertThat(new CompositionAspectRatio(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)
                .matches(Integer.MAX_VALUE - 1, Integer.MAX_VALUE)).isTrue();
    }

    @Test
    void oldNamedNullNonIntegralAndAmbiguousSnapshotAspectsFailClosed() {
        var original = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION, List.of(), TimelineMetadata.empty());
        var legacyJson = TimelineDocumentJsonSerializer.serialize(original);
        assertThat(legacyJson).doesNotContain("compositionAspectRatio");
        assertThat(TimelineDocumentJsonSerializer.serialize(TimelineDocumentJsonSerializer.deserialize(legacyJson)))
                .isEqualTo(legacyJson);
        var valid = "{\"format\":\"composition-aspect-rational-v1\",\"numerator\":13,\"denominator\":11}";
        String prefix = "{\"schemaVersion\":\"timeline-1.0\",\"tracks\":[],\"metadata\":{\"compositionAspectRatio\":";
        var explicit = TimelineDocumentJsonSerializer.deserialize(prefix + valid + "}}");
        assertThat(explicit.getMetadata().effectiveCompositionAspectRatio()).isEqualTo(new CompositionAspectRatio(13, 11));
        for (String invalid : List.of("\"PORTRAIT_3_4\"", "null", valid.replace(":13", ":13.0"),
                valid.replace(":13", ":2147483648"), valid.replace(":13", ":0"), valid.replace(":13", ":-1"),
                valid.replace(":13", ":22"), valid.replace(":13", ":13,\"numerator\":20"),
                valid.replace("rational-v1", "rational-v999"))) {
            assertThatThrownBy(() -> TimelineDocumentJsonSerializer.deserialize(prefix + invalid + "}}"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
