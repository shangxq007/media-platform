package com.example.platform.render.testsupport;

import com.example.platform.timeline.canonical.TimelineDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * FIELD-PRESERVATION-P0: unified assertion that the 8 canonical {@link TimelineDocument}
 * fields are preserved by a rebuild sink. Exactly one field may legitimately change
 * (the one the operation authors); every other field must equal the base value.
 */
public final class CanonicalFieldsAssert {

    private CanonicalFieldsAssert() {
    }

    /** The 8 canonical fields of {@link TimelineDocument} (one per @JsonProperty). */
    public enum Field {
        SCHEMA, TRACKS, METADATA, AUDIO_MIX,
        SEMANTIC_RELATIONSHIPS, TEXT_ELEMENTS, TRANSITIONS, AUTOMATIONS
    }

    public static void assertPreserved(TimelineDocument base, TimelineDocument result, Field changed) {
        assertNotNull(result, "rebuild must produce a document");
        if (changed != Field.SCHEMA) {
            assertEquals(base.getSchemaVersion(), result.getSchemaVersion(), "schemaVersion preserved");
        }
        if (changed != Field.TRACKS) {
            assertEquals(base.getTracks(), result.getTracks(), "tracks preserved");
        }
        if (changed != Field.METADATA) {
            assertEquals(base.getMetadata(), result.getMetadata(), "metadata preserved");
        }
        if (changed != Field.AUDIO_MIX) {
            assertEquals(base.getAudioMix(), result.getAudioMix(), "audioMix preserved");
        }
        if (changed != Field.SEMANTIC_RELATIONSHIPS) {
            assertEquals(base.getSemanticRelationships(), result.getSemanticRelationships(),
                    "semanticRelationships preserved");
        }
        if (changed != Field.TEXT_ELEMENTS) {
            assertEquals(base.getTextElements(), result.getTextElements(), "textElements preserved");
        }
        if (changed != Field.TRANSITIONS) {
            assertEquals(base.getTransitions(), result.getTransitions(), "transitions preserved");
        }
        if (changed != Field.AUTOMATIONS) {
            assertEquals(base.getAutomations(), result.getAutomations(), "automations preserved");
        }
    }
}
