package com.example.platform.render.ir;

import java.util.List;
import java.util.Map;

/**
 * Top-level declarative IR model for a media project.
 *
 * <p>Supported schema version: {@code media-project/v1}.
 * All fields are required unless explicitly noted as optional.
 *
 * <p>Null values are permitted at construction time; validation via
 * {@link IrValidator} will produce typed errors rather than NPE.
 *
 * @param schemaVersion the schema identifier (MUST be "media-project/v1")
 * @param project       the project declaration
 * @param assets        declared asset version references
 * @param timeline      the video timeline
 * @param outputs       output specifications
 * @param artifacts     artifact declarations for each output
 * @param extensions    optional extension map (reserved namespace, keys sorted lexicographically)
 */
public record MediaProjectIr(
    String schemaVersion,
    Project project,
    List<AssetVersionRef> assets,
    Timeline timeline,
    List<OutputSpec> outputs,
    List<ArtifactDeclaration> artifacts,
    Map<String, Object> extensions
) {
    /** Canonical schema version string. */
    public static final String SCHEMA_VERSION = "media-project/v1";

    public MediaProjectIr {
        // Defensive copy of lists to prevent external mutation
        if (assets != null) assets = List.copyOf(assets);
        if (outputs != null) outputs = List.copyOf(outputs);
        if (artifacts != null) artifacts = List.copyOf(artifacts);
    }

    /**
     * Convenience constructor without extensions.
     */
    public MediaProjectIr(
        String schemaVersion,
        Project project,
        List<AssetVersionRef> assets,
        Timeline timeline,
        List<OutputSpec> outputs,
        List<ArtifactDeclaration> artifacts
    ) {
        this(schemaVersion, project, assets, timeline, outputs, artifacts, null);
    }
}
