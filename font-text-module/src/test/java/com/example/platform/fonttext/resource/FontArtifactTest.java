package com.example.platform.fonttext.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.identity.ArtifactId;
import org.junit.jupiter.api.Test;

/**
 * FONT-V1 Phase 1: locks the orthogonality of the three artifact identities
 * (business/source identity, content identity, storage identity). Assertions are
 * on identity semantics — not field getters.
 */
class FontArtifactTest {

    private static final ArtifactId A1 = new ArtifactId("artifact-1");
    private static final ArtifactId A2 = new ArtifactId("artifact-2");
    private static final FontContentDigest SOURCE = FontContentDigest.ofText("source-bytes");
    private static final FontContentDigest CONTENT_D1 = FontContentDigest.ofText("sanitized-v1");
    private static final FontContentDigest CONTENT_D2 = FontContentDigest.ofText("sanitized-v2");

    private static FontArtifact artifact(ArtifactId id, FontContentDigest source, FontContentDigest content) {
        return new FontArtifact(id, source, content, 1024L, "font/ttf", FontFormat.TRUETYPE);
    }

    @Test
    void sameArtifactIdDifferentContentDigestAreDifferentContentIdentity() {
        FontArtifact first = artifact(A1, SOURCE, CONTENT_D1);
        FontArtifact second = artifact(A1, SOURCE, CONTENT_D2);
        // Same business identity ...
        assertEquals(first.artifactId(), second.artifactId());
        // ... but different content identity.
        assertNotEquals(first.contentDigest(), second.contentDigest());
    }

    @Test
    void differentArtifactIdSameContentDigestCanCoexist() {
        FontArtifact first = artifact(A1, SOURCE, CONTENT_D1);
        FontArtifact second = artifact(A2, SOURCE, CONTENT_D1);
        assertNotEquals(first.artifactId(), second.artifactId());
        assertEquals(first.contentDigest(), second.contentDigest());
    }

    @Test
    void sameContentDigestMeansSameContent() {
        FontArtifact first = artifact(A1, SOURCE, CONTENT_D1);
        FontArtifact second = artifact(A2, FontContentDigest.ofText("other-source"), CONTENT_D1);
        assertEquals(first.contentDigest(), second.contentDigest());
    }

    @Test
    void sourceDigestAndContentDigestAreIndependent() {
        FontArtifact first = artifact(A1, SOURCE, CONTENT_D1);
        FontArtifact second = artifact(A1, SOURCE, CONTENT_D2);
        // Identical source bytes, different sanitized content ⇒ independent.
        assertEquals(first.sourceDigest(), second.sourceDigest());
        assertNotEquals(first.contentDigest(), second.contentDigest());
    }

    @Test
    void nullArtifactIdRejected() {
        assertThrows(NullPointerException.class,
                () -> new FontArtifact(null, SOURCE, CONTENT_D1, 1024L, "font/ttf", FontFormat.TRUETYPE));
    }

    @Test
    void nullDigestsRejected() {
        assertThrows(NullPointerException.class,
                () -> new FontArtifact(A1, null, CONTENT_D1, 1024L, "font/ttf", FontFormat.TRUETYPE));
        assertThrows(NullPointerException.class,
                () -> new FontArtifact(A1, SOURCE, null, 1024L, "font/ttf", FontFormat.TRUETYPE));
    }

    @Test
    void nullMediaTypeRejected() {
        assertThrows(NullPointerException.class,
                () -> new FontArtifact(A1, SOURCE, CONTENT_D1, 1024L, null, FontFormat.TRUETYPE));
    }

    @Test
    void nullFormatRejected() {
        assertThrows(NullPointerException.class,
                () -> new FontArtifact(A1, SOURCE, CONTENT_D1, 1024L, "font/ttf", null));
    }

    @Test
    void negativeByteSizeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new FontArtifact(A1, SOURCE, CONTENT_D1, -1L, "font/ttf", FontFormat.TRUETYPE));
    }

    @Test
    void zeroByteSizeAccepted() {
        FontArtifact empty = new FontArtifact(A1, SOURCE, CONTENT_D1, 0L, "font/ttf", FontFormat.TRUETYPE);
        assertEquals(0L, empty.byteSize());
        assertTrue(empty.byteSize() >= 0);
    }
}
