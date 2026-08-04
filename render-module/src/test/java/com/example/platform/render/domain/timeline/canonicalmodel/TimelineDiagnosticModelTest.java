package com.example.platform.render.domain.timeline.canonicalmodel;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimelineDiagnosticModelTest {

    @Test
    void modelPathRendersRootFieldIndexAndEscapedIdentifierSegments() {
        TimelineModelPath path = TimelineModelPath.root()
                .field("tracks")
                .id("track=1]\\a")
                .field("clips")
                .index(0);

        assertEquals("$.tracks[id=track\\=1\\]\\\\a].clips[0]", path.render());
        assertEquals(path, TimelineModelPath.root().field("tracks").id("track=1]\\a").field("clips").index(0));
    }

    @Test
    void diagnosticsAreOrderedBySeverityCodePathAndIdentifierNotMessage() {
        TimelineDiagnostic later = TimelineDiagnostic.error(TimelineDiagnosticCode.TIMELINE_CLIP_OVERLAP,
                TimelineModelPath.root().field("tracks").id("b"), "b", "later message");
        TimelineDiagnostic earlier = TimelineDiagnostic.error(TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE,
                TimelineModelPath.root().field("tracks").id("a"), "a", "earlier message");

        TimelineValidationResult result = TimelineValidationResult.of(List.of(later, earlier));

        assertEquals(List.of(TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE,
                        TimelineDiagnosticCode.TIMELINE_CLIP_OVERLAP),
                result.diagnostics().stream().map(TimelineDiagnostic::code).toList());
    }

    @Test
    void canonicalProfileIsExplicitAndNotPersistedAsModelVersionField() {
        assertEquals(TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                TimelineCanonicalProfile.valueOf("CANONICAL_TIMELINE_FOUNDATION_V1"));
        assertTrue(List.of(TimelineCanonicalModel.class.getRecordComponents()).stream()
                .noneMatch(component -> component.getName().equals("modelVersion")));
    }
}
