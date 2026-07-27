package com.example.platform.render.domain.timeline.semantics.transition;

import com.example.platform.render.domain.timeline.semantics.time.MediaTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TransitionInstanceTest {

    @Test
    @DisplayName("Valid transition construction")
    void validTransition() {
        TransitionInstance t = new TransitionInstance(
            "tx-1", "def-1", "1.0",
            "clip-out", "clip-in",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 2),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            null
        );
        assertEquals("tx-1", t.transitionId());
        assertEquals("clip-out->clip-in", t.getCutAnchor());
    }

    @Test
    @DisplayName("Transition with AUDIO_VIDEO media type")
    void audioVideoTransition() {
        TransitionInstance t = new TransitionInstance(
            "tx-1", "def-1", "1.0",
            "clip-a", "clip-b",
            TransitionInstance.TransitionMediaType.AUDIO_VIDEO,
            MediaTime.ofRational(1, 4),
            TransitionInstance.TransitionAlignment.START_AT_CUT,
            TransitionInstance.TransitionTemporalPolicy.OVERLAP_TIMELINE,
            null
        );
        assertEquals(TransitionInstance.TransitionMediaType.AUDIO_VIDEO, t.mediaType());
    }

    @Test
    @DisplayName("Invalid: duration is zero")
    void invalidDuration() {
        assertThrows(IllegalArgumentException.class, () ->
            new TransitionInstance("tx-1", "def-1", "1.0",
                "clip-a", "clip-b",
                TransitionInstance.TransitionMediaType.VIDEO,
                MediaTime.ZERO,
                TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
                TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
                null)
        );
    }

    @Test
    @DisplayName("Invalid: outgoingClipId == incomingClipId")
    void sameEndpoints() {
        assertThrows(IllegalArgumentException.class, () ->
            new TransitionInstance("tx-1", "def-1", "1.0",
                "clip-same", "clip-same",
                TransitionInstance.TransitionMediaType.VIDEO,
                MediaTime.ofRational(1, 2),
                TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
                TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
                null)
        );
    }

    @Test
    @DisplayName("All alignment modes accepted")
    void allAlignments() {
        for (TransitionInstance.TransitionAlignment alignment : TransitionInstance.TransitionAlignment.values()) {
            assertDoesNotThrow(() -> new TransitionInstance(
                "tx-" + alignment.name(), "def-1", "1.0",
                "out", "in",
                TransitionInstance.TransitionMediaType.AUDIO,
                MediaTime.ofRational(1, 3),
                alignment,
                TransitionInstance.TransitionTemporalPolicy.INSERT_DURATION,
                null
            ));
        }
    }

    @Test
    @DisplayName("Parameters are immutable")
    void parametersImmutable() {
        java.util.Map<String, String> params = new java.util.HashMap<>();
        params.put("intensity", "0.5");
        TransitionInstance t = new TransitionInstance(
            "tx-1", "def-1", "1.0", "out", "in",
            TransitionInstance.TransitionMediaType.VIDEO,
            MediaTime.ofRational(1, 2),
            TransitionInstance.TransitionAlignment.CENTER_ON_CUT,
            TransitionInstance.TransitionTemporalPolicy.USE_SOURCE_HANDLES,
            params
        );
        assertEquals("0.5", t.parameters().get("intensity"));
        assertThrows(UnsupportedOperationException.class, () ->
            t.parameters().put("new-key", "value"));
    }
}
