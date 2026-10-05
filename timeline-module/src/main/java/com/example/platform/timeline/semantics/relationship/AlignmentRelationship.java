package com.example.platform.timeline.semantics.relationship;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * L5 lineage: language-neutral, directional {@code sourceUnits -> derivedUnits} alignment.
 *
 * <p>Both endpoint sets must be non-empty (owner decision 4). Identity is the caller-minted
 * {@link AlignmentId} (stable across revisions and membership changes) — never derived from the
 * endpoint sets. {@code sourceBaseline} is provenance materialised by the owner layer, not intent.
 *
 * <p>Determinism: endpoint sets are stored in {@link TreeSet} order and attributes are sorted by
 * tag, so canonical serialization is deterministic.
 */
public record AlignmentRelationship(
        AlignmentId alignmentId,
        Set<SourceUnitId> sourceUnits,
        Set<SourceUnitId> derivedUnits,
        SourceBaseline sourceBaseline,
        List<AlignmentAttribute> attributes) implements SemanticRelationship {

    public AlignmentRelationship {
        Objects.requireNonNull(alignmentId, "alignmentId");
        Objects.requireNonNull(sourceUnits, "sourceUnits");
        Objects.requireNonNull(derivedUnits, "derivedUnits");
        Objects.requireNonNull(sourceBaseline, "sourceBaseline");
        if (sourceUnits.isEmpty()) {
            throw new IllegalArgumentException("AlignmentRelationship.sourceUnits must not be empty");
        }
        if (derivedUnits.isEmpty()) {
            throw new IllegalArgumentException("AlignmentRelationship.derivedUnits must not be empty");
        }
        sourceUnits = new TreeSet<>(sourceUnits);
        derivedUnits = new TreeSet<>(derivedUnits);
        attributes = attributes == null
                ? List.of()
                : attributes.stream().sorted(Comparator.comparing(AlignmentAttribute::tag)).toList();
    }

    @Override
    public Kind kind() {
        return Kind.ALIGNMENT;
    }

    /** Semantic identity key: kind + alignment identity (endpoints excluded). */
    public String identityKey() {
        return "ALIGNMENT(" + alignmentId.value() + ")";
    }
}
