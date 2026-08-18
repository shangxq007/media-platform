package com.example.platform.timeline.semantics.relationship;

/**
 * SEMANTIC_RELATIONSHIP_SELECTION_FOUNDATION_V1 (SR9): sealed typed canonical
 * relationship root. V1 permits exactly TWO variants: SyncRelationship and
 * GroupRelationship. No generic edge map, no provenance/derived/Canvas/
 * Workflow variants, no universal RelationshipId.
 *
 * <p>CHECKPOINT_A: Jackson type metadata below is decode plumbing only — the
 * canonical field set stays owned by the domain records; no generic graph model.
 */
@com.fasterxml.jackson.annotation.JsonTypeInfo(
        use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
        include = com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY,
        property = "kind")
@com.fasterxml.jackson.annotation.JsonSubTypes({
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = SyncRelationship.class, name = "SYNC"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = GroupRelationship.class, name = "GROUP")
})
public sealed interface SemanticRelationship permits
        SyncRelationship,
        GroupRelationship {

    Kind kind();

    enum Kind {
        SYNC,
        GROUP
    }
}
