package com.example.platform.render.infrastructure.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.fonttext.manifest.FontFaceManifest;
import com.example.platform.fonttext.resource.FaceIndex;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.FontFormat;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Unit tests for the FONT-V1 Phase 6b one-way projection {@link FontAssetProjection}. */
class FontAssetProjectionTest {

    private static final String CONTENT_HEX =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void mapsContentDigestToSha256() {
        FontFaceManifest manifest = manifest();

        FontAsset asset = FontAssetProjection.from(manifest);

        // sha256 is the sanitized contentDigest string, never a source digest.
        assertEquals(manifest.contentDigest().sha256Hex(), asset.sha256());
        assertEquals(CONTENT_HEX, asset.sha256());
        // id is the same executed-content identity (no separate business id exists on the manifest).
        assertEquals(manifest.contentDigest().sha256Hex(), asset.id());
    }

    @Test
    void mapsFamilyName() {
        FontAsset asset = FontAssetProjection.from(manifest());

        assertEquals("Test Family", asset.fontFamily());
        assertEquals(FontFormat.TRUETYPE.name(), asset.format());
    }

    @Test
    void operationalFieldsRemainEmptyAccordingToExistingContract() {
        FontAsset asset = FontAssetProjection.from(manifest());

        assertNull(asset.fileName(), "manifest carries no file name");
        assertNull(asset.fontSubfamily(), "known FONT-FACEMANIFEST-ACCESSOR-GAP; not addressed in Phase 6b");
        assertNull(asset.storageUri(), "no storage binding in Phase 6b");
        assertNull(asset.securityResult(), "security not evaluated in this projection");
        assertNull(asset.validationResult(), "validation not evaluated in this projection");
        assertNull(asset.subsetResult(), "no subset produced");
        assertEquals(0L, asset.fileSize(), "V1 bridge limitation: sanitized bytes size is not exposed");
    }

    @Test
    void statusIsTheSelectedReadyValue() {
        FontAsset asset = FontAssetProjection.from(manifest());

        assertEquals(FontAssetStatus.READY, asset.status());
        assertTrue(asset.isReadyForRender());
    }

    @Test
    void nullManifestRejected() {
        assertThrows(NullPointerException.class, () -> FontAssetProjection.from(null));
    }

    private static FontFaceManifest manifest() {
        return new FontFaceManifest(
                FontContentDigest.of(CONTENT_HEX),
                new FaceIndex(0),
                FontFormat.TRUETYPE,
                "Test Family",
                "Regular",
                400,
                5,
                "UPRIGHT",
                123,
                Set.of(65, 66, 67),
                Set.of(),
                true,
                true,
                true,
                List.of(),
                List.of(),
                false,
                false,
                List.of(),
                "PARSED",
                "NOT_EVALUATED");
    }
}
