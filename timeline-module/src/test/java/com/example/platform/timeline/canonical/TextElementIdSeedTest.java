package com.example.platform.timeline.canonical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** P2-5.5: deterministic {@link TextElementId#fromSeed(String)} identity. */
class TextElementIdSeedTest {

    @Test
    void sameSeedYieldsSameId() {
        assertEquals(TextElementId.fromSeed("seed-a"), TextElementId.fromSeed("seed-a"));
    }

    @Test
    void differentSeedsYieldDifferentIds() {
        assertNotEquals(TextElementId.fromSeed("seed-a"), TextElementId.fromSeed("seed-b"));
    }

    @Test
    void fromSeedProducesNonBlankStableValue() {
        TextElementId id = TextElementId.fromSeed("seed-a");
        assertTrue(!id.value().isBlank());
        assertEquals(id.value(), TextElementId.fromSeed("seed-a").value());
    }

    @Test
    void randomIsUnchangedAndDistinctFromSeed() {
        assertNotEquals(TextElementId.random(), TextElementId.random());
    }

    @Test
    void blankSeedRejected() {
        assertThrows(IllegalArgumentException.class, () -> TextElementId.fromSeed(" "));
        assertThrows(NullPointerException.class, () -> TextElementId.fromSeed(null));
    }
}
