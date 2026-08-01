package com.example.platform.render.ir;

import java.util.Objects;

/**
 * Declaration of a concrete output artifact produced by a render.
 */
public record ArtifactDeclaration(String id, String outputSpecId, String filename) {
    public ArtifactDeclaration {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(outputSpecId, "outputSpecId must not be null");
        Objects.requireNonNull(filename, "filename must not be null");
    }
}
