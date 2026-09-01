package com.example.platform.render.infrastructure;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RenderArtifactStorageServiceTest {

    @Test
    void rejectsLegacyPhysicalPlacementWriterBeforeAnySideEffect() {
        RenderArtifactStorageService service = new RenderArtifactStorageService();

        assertThrows(UnsupportedOperationException.class, () ->
                service.uploadJobOutput(
                        "job-a", "project-a", "artifact-a", "output.mp4", "video/mp4"));
    }
}
