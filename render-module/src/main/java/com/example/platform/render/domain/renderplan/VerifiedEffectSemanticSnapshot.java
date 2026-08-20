package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.semantics.effect.EffectDefinitionSnapshot;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticEntry;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ROADMAP20 final implementation: immutable integrity-bound authored EFFECT
 * semantic snapshot — the VERIFIED boundary over the timeline-domain
 * {@link EffectSemanticSnapshot} (EFFECT_SEMANTIC_SNAPSHOT_IS_PRIMARY_TYPED_AUTHORITY_WIRE_EFFECT_IS_DERIVED_PROJECTION_V1).
 *
 * <p>This value can ONLY be produced by
 * {@link VerifiedEffectSemanticSnapshotFactory} after verifying, against the
 * revision's exact pin: snapshot id, content digest (stored AND recomputed),
 * contract version, definition digest integrity and structure (RP1/RP2/BI2/BI3).
 * It is NOT a caller-assembled authority — there is no public construction
 * path from arbitrary {@code List<EffectInstance>} + {@code List<EffectDefinition>}.
 *
 * <p>Derived views: {@link #effects()} and {@link #effectDefinitions()} are
 * DERIVED projections of the snapshot's typed entries (applicationRange is
 * derived from target clip extent; mediaType derived; automation empty per V1)
 * — they are convenience views, never independent authority.
 */
public final class VerifiedEffectSemanticSnapshot {

    private final EffectSemanticSnapshot snapshot;
    private final EffectSemanticSnapshotReference reference;
    private final String revisionId;

    /** Restricted constructor — factory-only (see {@link VerifiedEffectSemanticSnapshotFactory}). */
    private VerifiedEffectSemanticSnapshot(
            EffectSemanticSnapshot snapshot,
            EffectSemanticSnapshotReference reference,
            String revisionId) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.revisionId = Objects.requireNonNull(revisionId, "revisionId");
    }

    static VerifiedEffectSemanticSnapshot create(
            EffectSemanticSnapshot snapshot,
            EffectSemanticSnapshotReference reference,
            String revisionId) {
        return new VerifiedEffectSemanticSnapshot(snapshot, reference, revisionId);
    }

    /** The timeline-domain immutable Effect semantic snapshot (primary authority). */
    public EffectSemanticSnapshot snapshot() {
        return snapshot;
    }

    /** The exact revision pin (snapshotId + contentDigest + contractVersion). */
    public EffectSemanticSnapshotReference reference() {
        return reference;
    }

    /**
     * DERIVED typed view of the snapshot entries. {@code applicationRange} is
     * null in this view when no clip context is available; use
     * {@link #effectsForClip(com.example.platform.timeline.semantics.clip.MediaClip)}
     * for materialization with the derived clip-extent range.
     */
    public List<EffectInstance> effects() {
        List<EffectInstance> result = new ArrayList<>();
        for (EffectSemanticEntry entry : snapshot.entries()) {
            result.add(toEffectInstance(entry, null));
        }
        return result;
    }

    /** DERIVED view with applicationRange derived from the given clip extent. */
    public List<EffectInstance> effectsForClip(com.example.platform.timeline.semantics.clip.MediaClip clip) {
        List<EffectInstance> result = new ArrayList<>();
        for (EffectSemanticEntry entry : snapshot.entries()) {
            if (entry.target() instanceof com.example.platform.timeline.semantics.effect.ClipEffectTarget target
                    && target.trackId().equals(clip.trackId())
                    && target.clipId().equals(clip.clipId())) {
                result.add(toEffectInstance(entry, clip.timelineRange()));
            }
        }
        return result;
    }

    /** DERIVED typed definition catalog view (from embedded D1 snapshots). */
    public List<EffectInstance.EffectDefinition> effectDefinitions() {
        List<EffectInstance.EffectDefinition> result = new ArrayList<>();
        for (EffectSemanticEntry entry : snapshot.entries()) {
            EffectDefinitionSnapshot ds = entry.definitionSnapshot();
            if (result.stream().noneMatch(d -> d.definitionId().equals(ds.definitionId()))) {
                result.add(toEffectDefinition(ds));
            }
        }
        return result;
    }

    /**
     * Value-bound semantic content pin — the snapshot content digest.
     * Two snapshots with identical semantic content share this pin regardless
     * of snapshot id (BI1).
     */
    public String contentPin() {
        return snapshot.contentDigest();
    }

    /** The typed reference carried into the final RenderPlan. */
    public EffectSemanticReference toReference() {
        return new EffectSemanticReference(reference, revisionId);
    }

    private static EffectInstance toEffectInstance(
            EffectSemanticEntry entry, com.example.platform.timeline.semantics.clip.MediaClip.TimeRange clipRange) {
        EffectDefinitionSnapshot ds = entry.definitionSnapshot();
        // derived local view (NOT caller authority, NOT a canonical source):
        // deterministic parameter map — read-only, sorted by key.
        Map<String, String> params = new java.util.LinkedHashMap<>();
        entry.parameters().stream()
                .sorted(java.util.Comparator.comparing(EffectSemanticEntry.EffectParameter::key))
                .forEach(p -> params.put(p.key(), p.value()));
        return new EffectInstance(
                entry.effectInstanceId(),
                ds.definitionId(),
                ds.version(),
                EffectInstance.EffectMediaType.valueOf(mediaTypeOf(ds)),
                entry.enabled(),
                clipRange,
                params,
                Map.of(),
                entry.target(),
                EffectInstance.EffectProvenance.untracked());
    }

    private static String mediaTypeOf(EffectDefinitionSnapshot ds) {
        if (ds.supportedMediaTypes().contains("AUDIO")) {
            return "AUDIO";
        }
        if (ds.supportedMediaTypes().contains("VIDEO")) {
            return "VIDEO";
        }
        return "VIDEO";
    }

    private static EffectInstance.EffectDefinition toEffectDefinition(EffectDefinitionSnapshot ds) {
        List<EffectInstance.EffectMediaType> mediaTypes = new ArrayList<>();
        for (String mt : ds.supportedMediaTypes()) {
            mediaTypes.add(EffectInstance.EffectMediaType.valueOf(mt));
        }
        Map<String, EffectInstance.ParameterSchema> schema = new LinkedHashMap<>();
        for (EffectDefinitionSnapshot.EffectParameterSchemaEntry e : ds.parameterSchema()) {
            schema.put(e.name(), new EffectInstance.ParameterSchema(e.name(), e.type(), null, null, null, List.of()));
        }
        return new EffectInstance.EffectDefinition(
                ds.definitionId(),
                ds.version(),
                EffectInstance.EffectCategory.valueOf(ds.category()),
                mediaTypes,
                schema,
                EffectInstance.EffectTemporalBehavior.valueOf(ds.temporalBehavior()),
                new ArrayList<>(ds.deterministicProperties()),
                new ArrayList<>(ds.requiredCapabilities()),
                List.of());
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VerifiedEffectSemanticSnapshot s)) {
            return false;
        }
        return snapshot.contentDigest().equals(s.snapshot.contentDigest());
    }

    @Override
    public int hashCode() {
        return snapshot.contentDigest().hashCode();
    }

    @Override
    public String toString() {
        return "VerifiedEffectSemanticSnapshot(id=" + snapshot.id().value()
                + ", digest=" + snapshot.contentDigest() + ")";
    }
}
