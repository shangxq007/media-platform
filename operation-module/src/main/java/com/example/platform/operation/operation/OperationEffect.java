package com.example.platform.operation.operation;

/**
 * Fixed effect semantics of an {@link OperationDefinition}
 * (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b, DOM-OPERATION-002).
 *
 * <p>The effect is owned by the operation definition and is NEVER a
 * caller-selectable parameter. {@code COVER_OF} / {@code THUMBNAIL} are effects,
 * never capability parameters.</p>
 */
public enum OperationEffect {

    /** No produced-artifact semantics (e.g. a pure timeline edit). */
    NONE,

    /**
     * Produces an image Artifact related to the subject through
     * {@code ProvenanceRelationType.COVER_OF}.
     */
    COVER_OF,

    /** Produces an image Artifact with {@code ArtifactKind.THUMBNAIL}. */
    THUMBNAIL
}
