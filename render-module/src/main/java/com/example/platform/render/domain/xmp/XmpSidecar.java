package com.example.platform.render.domain.xmp;

/**
 * Top-level XMP sidecar container — aggregates all XMP namespace metadata.
 *
 * <p>This is a portable metadata envelope intended for sidecar JSON or
 * future XML/XMP mapping. No XMP SDK dependency is required.</p>
 *
 * @param schemaVersion XMP sidecar schema version
 * @param asset         asset identity and storage metadata
 * @param ai            AI generation metadata (nullable)
 * @param lineage       lineage provenance metadata (nullable)
 * @param governance    governance and compliance metadata (nullable)
 */
public record XmpSidecar(
        String schemaVersion,
        XmpAssetMetadata asset,
        XmpAiMetadata ai,
        XmpLineageMetadata lineage,
        XmpGovernanceMetadata governance) {

    public static final String SCHEMA_V1 = "1.0.0";

    public static XmpSidecar of(XmpAssetMetadata asset) {
        return new XmpSidecar(SCHEMA_V1, asset, null, null, null);
    }
}
