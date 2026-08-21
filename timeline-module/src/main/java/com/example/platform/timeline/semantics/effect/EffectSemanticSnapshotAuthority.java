package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaClip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ROADMAP20 authority-integration correction: the DOMAIN-OWNED Effect semantic
 * snapshot authority. Constructed with the authoritative dependencies —
 * {@link EffectDefinitionVersionRegistry} (durable) and
 * {@link EffectSemanticSnapshotStore} (durable) — so callers cannot substitute
 * registry/store/ID inputs (blocker 3 + blocker 4).
 *
 * <p>Minting is domain-authority only:
 * <ul>
 *   <li>{@link #mintEmpty()} — authoritative EMPTY semantics for new
 *       no-effect canonical revisions (KNOWN EMPTY, never MISSING);</li>
 *   <li>{@link #mintFromAuthoredState(List, List, TimelineDocument)} — typed
 *       typed authored-state minting bound to the canonical document (target context
 *       derived from canonical TimelineTrack.type — blocker 5).</li>
 * </ul>
 *
 * <p>Caller cannot choose: snapshotId (generated internally), registry
 * implementation, arbitrary clip-context function. Definitions are supplied by
 * an authoritative domain source (typed authoring input; a future
 * catalog authority replaces this — never a Render caller list).
 */
public final class EffectSemanticSnapshotAuthority {


    /** Typed target context derived from the canonical document (not trackId strings). */
    public record EffectTargetContext(
            String trackId, String clipId, TrackType trackType, MediaClip.TimeRange clipExtent) {
        public EffectTargetContext {
            Objects.requireNonNull(trackId, "trackId");
            Objects.requireNonNull(clipId, "clipId");
            Objects.requireNonNull(trackType, "trackType");
            Objects.requireNonNull(clipExtent, "clipExtent");
        }
    }

    private final EffectDefinitionVersionRegistry registry;
    private final EffectSemanticSnapshotStore store;

    public EffectSemanticSnapshotAuthority(
            EffectDefinitionVersionRegistry registry, EffectSemanticSnapshotStore store) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Durable registry (production authority dependency, fixed by construction). */
    public EffectDefinitionVersionRegistry registry() {
        return registry;
    }

    /** Durable store (production authority dependency, fixed by construction). */
    public EffectSemanticSnapshotStore store() {
        return store;
    }

    /**
     * Authoritative EMPTY semantics for a NEW no-effect canonical revision.
     * Deterministic empty content digest; generated domain-owned id.
     */
    public EffectSemanticSnapshot mintEmpty() {
        return EffectSemanticSnapshotAuthorityInternal.mintEmpty(registry);
    }

    /**
     * Domain-internal typed authored-state minting (future Effect authoring
     * entry; used today by domain fixtures). NOT a caller bypass: the snapshot
     * id is generated internally, the durable registry is fixed by
     * construction, and target context comes from the canonical document.
     */
    public EffectSemanticSnapshot mintFromAuthoredState(
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> authoritativeDefinitions,
            TimelineDocument document) {
        Objects.requireNonNull(document, "document");
        List<EffectInstance> instances = effects == null ? List.of() : effects;
        List<EffectInstance.EffectDefinition> defs =
                authoritativeDefinitions == null ? List.of() : List.copyOf(authoritativeDefinitions);
        if (instances.isEmpty()) {
            return mintEmpty();
        }
        // D1 mint-internal integrity: the authoritative definition collection
        // itself must not carry (id, version) -> conflicting content.
        java.util.Map<String, String> seenDefinitions = new java.util.HashMap<>();
        for (EffectInstance.EffectDefinition d : defs) {
            String key = d.definitionId() + "@" + d.version();
            String fingerprint = d.category() + "|" + d.parameterSchema()
                    + "|" + d.temporalBehavior() + "|" + d.deterministicProperties()
                    + "|" + d.requiredCapabilities() + "|" + d.supportedBackendCapabilities();
            String previous = seenDefinitions.putIfAbsent(key, fingerprint);
            if (previous != null && !previous.equals(fingerprint)) {
                throw new IllegalArgumentException(
                        "D1/I2: authoritative definition collection carries conflicting "
                                + "content for '" + key + "' — FAIL CLOSED "
                                + "(EFFECT_DEFINITION_VERSION_CONTENT_IS_IMMUTABLE_V1)");
            }
        }
        List<EffectSemanticEntry> entries = new ArrayList<>();
        for (EffectInstance effect : instances) {
            if (effect.automationBindings() != null && !effect.automationBindings().isEmpty()) {
                throw new IllegalArgumentException(
                        "UNVERIFIED_EFFECT_AUTOMATION_REFERENCES_FAIL_CLOSED_V1: effect "
                                + effect.effectInstanceId() + " carries non-empty automationBindings "
                                + "— unsupported in effect-semantics-v1 (SA5)");
            }
            if (!(effect.target() instanceof ClipEffectTarget clipTarget)) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " must carry a ClipEffectTarget");
            }
            // target context from the CANONICAL document (TrackType + clip extent)
            EffectTargetContext target = resolveTargetContext(
                    document, clipTarget.trackId(), clipTarget.clipId(),
                    effect.effectInstanceId(), effect.effectDefinitionId());
            EffectInstance.EffectDefinition definition = null;
            for (EffectInstance.EffectDefinition d : defs) {
                if (d.definitionId().equals(effect.effectDefinitionId())
                        && d.version().equals(effect.effectDefinitionVersion())) {
                    definition = d;
                    break;
                }
            }
            if (definition == null) {
                throw new IllegalArgumentException(
                        "D6: definition " + effect.effectDefinitionId() + "@"
                                + effect.effectDefinitionVersion() + " cannot be resolved exactly");
            }
            entries.add(EffectSemanticSnapshotAuthorityInternal.authoredEntry(
                    effect.effectInstanceId(), effect.effectDefinitionId(),
                    new java.util.LinkedHashMap<String, Object>(effect.parameters()),
                    target.trackId(), target.clipId(), target.trackType(),
                    target.clipExtent(), defs, effect.enabled()));
        }
        return EffectSemanticSnapshotAuthorityInternal.mintFromEntries(
                entries, registry, EffectSemanticSnapshotId.generate());
    }

    /**
     * ROADMAP20 authority-integration (§12/§21): mints the authoritative
     * Effect snapshot AND durably persists it inside the caller's physical
     * transaction, with definition-version identity enforced transaction-
     * scoped (advisory-lock serialization for the durable registry).
     * No-Effect state mints authoritative EMPTY.
     */
    public EffectSemanticSnapshot mintAndPersistTx(
            org.jooq.DSLContext tx, String projectId, String tenantId,
            java.util.List<EffectInstance> effects,
            java.util.List<EffectInstance.EffectDefinition> authoritativeDefinitions,
            TimelineDocument document) {
        Objects.requireNonNull(tx, "tx");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(tenantId, "tenantId");
        List<EffectInstance> instances = effects == null ? List.of() : effects;
        if (instances.isEmpty()) {
            EffectSemanticSnapshot empty = mintEmpty();
            store().storeTx(tx, projectId, tenantId, empty);
            return empty;
        }
        // mint (definition-version integrity joins THIS transaction via
        // registry.registerTx — advisory lock + durable scan; NO separate
        // connection is opened: entries are built and the snapshot is
        // constructed WITHOUT the non-transactional register path)
        EffectSemanticSnapshot snapshot = mintFromAuthoredStateTx(
                instances, authoritativeDefinitions, document, tx);
        store().storeTx(tx, projectId, tenantId, snapshot);
        return snapshot;
    }

    /** Internal mint: builds entries + snapshot without registering, then registers tx-scoped. */
    private EffectSemanticSnapshot mintFromAuthoredStateTx(
            java.util.List<EffectInstance> instances,
            java.util.List<EffectInstance.EffectDefinition> authoritativeDefinitions,
            TimelineDocument document, org.jooq.DSLContext tx) {
        List<EffectInstance.EffectDefinition> defs =
                authoritativeDefinitions == null ? List.of() : List.copyOf(authoritativeDefinitions);
        if (instances.isEmpty()) {
            return mintEmpty();
        }
        // D1 mint-internal integrity (same as mintFromAuthoredState)
        java.util.Map<String, String> seenDefinitions = new java.util.HashMap<>();
        for (EffectInstance.EffectDefinition d : defs) {
            String key = d.definitionId() + "@" + d.version();
            String fingerprint = d.category() + "|" + d.parameterSchema()
                    + "|" + d.temporalBehavior() + "|" + d.deterministicProperties()
                    + "|" + d.requiredCapabilities() + "|" + d.supportedBackendCapabilities();
            String previous = seenDefinitions.putIfAbsent(key, fingerprint);
            if (previous != null && !previous.equals(fingerprint)) {
                throw new IllegalArgumentException(
                        "D1/I2: authoritative definition collection carries conflicting "
                                + "content for '" + key + "' — FAIL CLOSED");
            }
        }
        List<EffectSemanticEntry> entries = new ArrayList<>();
        for (EffectInstance effect : instances) {
            if (effect.automationBindings() != null && !effect.automationBindings().isEmpty()) {
                throw new IllegalArgumentException(
                        "UNVERIFIED_EFFECT_AUTOMATION_REFERENCES_FAIL_CLOSED_V1: effect "
                                + effect.effectInstanceId() + " — unsupported in effect-semantics-v1");
            }
            if (!(effect.target() instanceof ClipEffectTarget clipTarget)) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " must carry a ClipEffectTarget");
            }
            EffectTargetContext target = resolveTargetContext(
                    document, clipTarget.trackId(), clipTarget.clipId(),
                    effect.effectInstanceId(), effect.effectDefinitionId());
            EffectInstance.EffectDefinition definition = null;
            for (EffectInstance.EffectDefinition d : defs) {
                if (d.definitionId().equals(effect.effectDefinitionId())
                        && d.version().equals(effect.effectDefinitionVersion())) {
                    definition = d;
                    break;
                }
            }
            if (definition == null) {
                throw new IllegalArgumentException(
                        "D6: definition " + effect.effectDefinitionId() + "@"
                                + effect.effectDefinitionVersion() + " cannot be resolved exactly");
            }
            entries.add(EffectSemanticSnapshotAuthorityInternal.authoredEntry(
                    effect.effectInstanceId(), effect.effectDefinitionId(),
                    new java.util.LinkedHashMap<String, Object>(effect.parameters()),
                    target.trackId(), target.clipId(), target.trackType(),
                    target.clipExtent(), defs, effect.enabled()));
        }
        EffectSemanticSnapshot snapshot = EffectSemanticSnapshotAuthorityInternal.mintFromEntriesNoRegister(
                entries, EffectSemanticSnapshotId.generate());
        // transaction-scoped definition-version identity (advisory lock +
        // durable scan on the SAME connection/transaction)
        for (var entry : snapshot.entries()) {
            registry.registerTx(tx, entry.definitionSnapshot());
        }
        return snapshot;
    }

    /** Resolves the typed target context from the canonical document (track type from TrackType). */
    private static EffectTargetContext resolveTargetContext(
            TimelineDocument document, String trackId, String clipId,
            String effectId, String effectKey) {
        // typed target from the canonical document — no wire encoding, no
        // trackId string heuristics
        if (trackId == null || clipId == null) {
            throw new IllegalArgumentException(
                    "Effect '" + effectId + "' must carry a typed ClipEffectTarget "
                            + "to bind target context canonically");
        }
        for (TimelineTrack track : document.getTracks()) {
            if (!track.trackId().equals(trackId)) {
                continue;
            }
            for (var clip : track.clips()) {
                if (!clip.getClipId().value().equals(clipId)) {
                    continue;
                }
                MediaClip.TimeRange extent = new MediaClip.TimeRange(
                        clip.getStartTime(), clip.getEndTime());
                return new EffectTargetContext(trackId, clipId, track.type(), extent);
            }
        }
        throw new IllegalArgumentException(
                "Effect '" + effectId + "' targets clip " + trackId + "/" + clipId
                        + " which does not exist in the canonical document — FAIL CLOSED (T2/T5)");
    }
}
