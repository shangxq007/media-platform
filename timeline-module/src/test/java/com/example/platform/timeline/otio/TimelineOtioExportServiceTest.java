package com.example.platform.timeline.otio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.example.platform.timeline.api.revision.TimelineRevisionQueries;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TimelineOtioExportServiceTest {

    @Test
    void readsThePersistedCanonicalSnapshotAndDoesNotUseAWriteSurface() throws Exception {
        TimelineRevisionQueries queries = mock(TimelineRevisionQueries.class);
        when(queries.getRevisionSnapshotPayload("project", "tenant", "revision"))
                .thenReturn(Optional.of(new TimelineRevisionQueries.RevisionSnapshotPayload(
                        "snap", canonicalJson(), "timeline-1.0")));

        String otio = new TimelineOtioExportService(queries)
                .export("tenant", "project", "revision");

        JsonNode root = new ObjectMapper().readTree(otio);
        assertThat(root.path("OTIO_SCHEMA").asText()).isEqualTo("Timeline.1");
        assertThat(root.path("metadata").path("media_platform.revision_id").asText()).isEqualTo("revision");
        assertThat(otio).contains("urn:media-platform:artifact:");

        // Only the formal read method is consumed; no revision write is invoked.
        verify(queries, times(1)).getRevisionSnapshotPayload("project", "tenant", "revision");
        verifyNoMoreInteractions(queries);
    }

    @Test
    void missingRevisionFailsExplicitly() {
        TimelineRevisionQueries queries = mock(TimelineRevisionQueries.class);
        when(queries.getRevisionSnapshotPayload("project", "tenant", "missing"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> new TimelineOtioExportService(queries)
                .export("tenant", "project", "missing"))
                .isInstanceOf(OtioExportException.class)
                .hasMessageContaining("not found");
    }

    private static String canonicalJson() throws Exception {
        try (InputStream input = TimelineOtioExportServiceTest.class.getResourceAsStream(
                "/otio/e2e14-rev8-canonical-timeline.json")) {
            assertThat(input).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
