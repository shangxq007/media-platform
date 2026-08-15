package com.example.platform.render.domain.timeline.semantics.relationship;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1 (SR9): sealed typed canonical
 * relationship root. V1 permits exactly TWO variants: SyncRelationship and
 * GroupRelationship. No generic edge map, no provenance/derived/Canvas/
 * Workflow variants, no universal RelationshipId.
 */
public sealed interface SemanticRelationship permits
        SyncRelationship,
        GroupRelationship {

    Kind kind();

    enum Kind {
        SYNC,
        GROUP
    }
}
