package com.example.platform.artifact.domain.typed;

import java.util.List;
import java.util.Set;

public record ArtifactRequirement(Set<LogicalArtifactKind> kinds, Set<String> mimeTypes, Set<String> formats,
                                  Set<String> codecs, VersionRange schemaVersions, int minimumCount, int maximumCount) {
    public ArtifactRequirement {
        if (kinds == null || kinds.isEmpty() || minimumCount < 1 || maximumCount < minimumCount) throw new IllegalArgumentException("invalid artifact requirement");
        if (schemaVersions == null) throw new IllegalArgumentException("schemaVersions is required");
        kinds = Set.copyOf(kinds); mimeTypes = mimeTypes == null ? Set.of() : Set.copyOf(mimeTypes);
        formats = formats == null ? Set.of() : Set.copyOf(formats); codecs = codecs == null ? Set.of() : Set.copyOf(codecs);
    }
    public boolean accepts(TypedArtifact artifact) {
        return kinds.contains(artifact.kind()) && (mimeTypes.isEmpty() || mimeTypes.contains(artifact.mimeType()))
            && (formats.isEmpty() || formats.contains(artifact.containerFormat())) && (codecs.isEmpty() || codecs.contains(artifact.encoding()))
            && schemaVersions.accepts(artifact.schemaVersion());
    }
}
