package com.example.platform.timeline.semantics.effect;

import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaClip;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * ROADMAP20 authority-integration correction: package-private implementation
 * of the domain minting rules (not a public issuance surface). Contains the
 * deterministic legacy hydration, parameter schema validation, mediaType
 * derivation from canonical TrackType, definition version resolution and the
 * value-bound digest computation.
 */
final class EffectSemanticSnapshotAuthorityInternal {

    private EffectSemanticSnapshotAuthorityInternal() {
    }

    /** Authoritative EMPTY snapshot (deterministic empty digest, generated id). */
    static EffectSemanticSnapshot mintEmpty(EffectDefinitionVersionRegistry registry) {
        return mintFromEntries(List.of(), registry, EffectSemanticSnapshotId.generate());
    }

    /** Mint from already-constructed typed entries (authority-owned ordering). */
    static EffectSemanticSnapshot mintFromEntries(
            List<EffectSemanticEntry> entries,
            EffectDefinitionVersionRegistry registry,
            EffectSemanticSnapshotId snapshotId) {
        Objects.requireNonNull(entries, "entries");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(snapshotId, "snapshotId");
        // I1: duplicate effectInstanceId -> fail closed.
        java.util.Set<String> seenInstanceIds = new java.util.HashSet<>();
        for (EffectSemanticEntry entry : entries) {
            if (!seenInstanceIds.add(entry.effectInstanceId())) {
                throw new IllegalArgumentException(
                        "I1: duplicate effectInstanceId '" + entry.effectInstanceId() + "' in snapshot");
            }
        }
        // Definition version immutability across snapshots (D1, §39):
        for (EffectSemanticEntry entry : entries) {
            EffectDefinitionSnapshot ds = entry.definitionSnapshot();
            EffectDefinitionCanonicalSemantics.verifyDefinitionDigest(ds);
            registry.register(ds);
        }
        // Value-bound digest: canonical semantic content only (id excluded).
        EffectSemanticSnapshot provisional = new EffectSemanticSnapshot(
                snapshotId, EffectSemanticContractVersion.current(), entries, "pending");
        String digest = EffectSemanticSnapshotCanonicalSemantics.snapshotContentDigest(provisional);
        return new EffectSemanticSnapshot(
                snapshotId, EffectSemanticContractVersion.current(), entries, digest);
    }

    /**
     * Constructs the snapshot WITHOUT registering definition identities
     * (caller registers transaction-scoped via registerTx — used by the
     * production mintAndPersistTx path so no second connection is opened).
     */
    static EffectSemanticSnapshot mintFromEntriesNoRegister(
            java.util.List<EffectSemanticEntry> entries, EffectSemanticSnapshotId snapshotId) {
        java.util.Objects.requireNonNull(entries, "entries");
        java.util.Objects.requireNonNull(snapshotId, "snapshotId");
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        for (EffectSemanticEntry entry : entries) {
            if (!seenIds.add(entry.effectInstanceId())) {
                throw new IllegalArgumentException(
                        "I1: duplicate effectInstanceId '" + entry.effectInstanceId() + "' in snapshot");
            }
        }
        // Definition digest verification (D1) without durable registration:
        for (EffectSemanticEntry entry : entries) {
            EffectDefinitionCanonicalSemantics.verifyDefinitionDigest(entry.definitionSnapshot());
        }
        EffectSemanticSnapshot provisional = new EffectSemanticSnapshot(
                snapshotId, EffectSemanticContractVersion.current(), entries, "pending");
        String digest = EffectSemanticSnapshotCanonicalSemantics.snapshotContentDigest(provisional);
        return new EffectSemanticSnapshot(
                snapshotId, EffectSemanticContractVersion.current(), entries, digest);
    }

    /**
     * Deterministic typed entry construction: maps authored effect state to a typed
     * entry. enabled = TRUE (LEGACY_EFFECT_ENABLED_DEFAULT_V1); applicationRange
     * = DERIVED clip extent; mediaType = DERIVED (track kind ∩ supportedMediaTypes,
     * incompatible FAIL CLOSED); automationBindings = EMPTY; definition
     * unresolvable FAIL CLOSED; unknown parameters FAIL CLOSED against the
     * pinned definition schema.
     */
    static EffectSemanticEntry buildEntry(
            String wireEffectId,
            String effectKey,
            Map<String, Object> wireParameters,
            String trackId,
            String clipId,
            TrackType trackType,
            MediaClip.TimeRange clipExtent,
            List<EffectInstance.EffectDefinition> authoritativeDefinitions) {
        return buildEntry(wireEffectId, effectKey, wireParameters, trackId, clipId,
                trackType, clipExtent, authoritativeDefinitions, true);
    }

    /** Internal legacy-entry factory with explicit enabled flag (default TRUE for wire). */
    private static EffectSemanticEntry buildEntry(
            String wireEffectId,
            String effectKey,
            Map<String, Object> wireParameters,
            String trackId,
            String clipId,
            TrackType trackType,
            MediaClip.TimeRange clipExtent,
            List<EffectInstance.EffectDefinition> authoritativeDefinitions,
            boolean enabled) {
        Objects.requireNonNull(wireEffectId, "wireEffectId");
        Objects.requireNonNull(effectKey, "effectKey");
        Map<String, Object> params = wireParameters == null ? Map.of() : wireParameters;

        // definition resolution (D6/L5): exact definitionId = effectKey, version
        // MUST NOT be invented — unresolvable FAIL CLOSED.
        EffectInstance.EffectDefinition definition = null;
        for (EffectInstance.EffectDefinition candidate : authoritativeDefinitions) {
            if (candidate.definitionId().equals(effectKey)) {
                definition = candidate;
                break;
            }
        }
        if (definition == null) {
            throw new IllegalArgumentException(
                    "L5/D6: legacy wire effect '" + wireEffectId + "' references definition '"
                            + effectKey + "' which cannot be resolved exactly — FAIL CLOSED");
        }

        // parameter schema validation (§26): unknown parameter -> FAIL CLOSED
        // (closed schema); type/shape where expressible.
        validateParametersAgainstSchema(effectKey, params, definition.parameterSchema());

        EffectDefinitionSnapshot ds = definitionSnapshot(definition);
        ClipEffectTarget clipTarget = new ClipEffectTarget(trackId, clipId);

        // mediaType DERIVED from canonical track type (blocker 5): trackId string
        // is irrelevant — only TimelineTrack.type participates.
        String derivedMediaType = deriveMediaType(trackType, ds.supportedMediaTypes());
        if (!ds.supportedMediaTypes().contains(derivedMediaType)) {
            throw new IllegalArgumentException(
                    "SA4/I3: track type " + trackType + " (derived mediaType " + derivedMediaType
                            + ") incompatible with definition '" + effectKey + "' supportedMediaTypes "
                            + ds.supportedMediaTypes() + " — FAIL CLOSED");
        }
        // applicationRange = DERIVED clip extent (APPLICATION_RANGE_AUTHORITY_V1);
        // automationBindings = EMPTY (V1; non-empty fail closed by construction).
        return new EffectSemanticEntry(
                wireEffectId, clipTarget, ds, enabled,
                parametersToList(params), List.of());
    }

    /**
     * Typed authored-state entry factory (used by mintFromAuthoredState): like
     * buildEntry but preserves the authored {@code enabled} flag.
     */
    static EffectSemanticEntry authoredEntry(
            String wireEffectId,
            String effectKey,
            Map<String, Object> wireParameters,
            String trackId,
            String clipId,
            TrackType trackType,
            MediaClip.TimeRange clipExtent,
            List<EffectInstance.EffectDefinition> authoritativeDefinitions,
            boolean enabled) {
        EffectSemanticEntry base = buildEntry(
                wireEffectId, effectKey, wireParameters, trackId, clipId,
                trackType, clipExtent, authoritativeDefinitions);
        return new EffectSemanticEntry(
                base.effectInstanceId(), base.target(), base.definitionSnapshot(), enabled,
                base.parameters(), base.automationBindings());
    }

    /** Derives mediaType from the CANONICAL TrackType — never from trackId strings. */
    static String deriveMediaType(TrackType trackType, List<String> supportedMediaTypes) {
        String trackKind = trackType == TrackType.AUDIO ? "AUDIO" : "VIDEO";
        if (!supportedMediaTypes.contains(trackKind)) {
            throw new IllegalArgumentException(
                    "SA4: derived mediaType '" + trackKind + "' (from canonical track type "
                            + trackType + ") not supported by definition " + supportedMediaTypes
                            + " — FAIL CLOSED");
        }
        return trackKind;
    }

    /** §26: validate parameters against the exact pinned definition schema. */
    static void validateParametersAgainstSchema(
            String definitionId,
            Map<String, Object> parameters,
            Map<String, EffectInstance.ParameterSchema> schema) {
        for (String key : parameters.keySet()) {
            if (!schema.containsKey(key)) {
                throw new IllegalArgumentException(
                        "PV2: unknown parameter '" + key + "' for definition '" + definitionId
                                + "' — the pinned definition schema is closed; FAIL CLOSED");
            }
        }
        for (Map.Entry<String, EffectInstance.ParameterSchema> entry : schema.entrySet()) {
            if (!parameters.containsKey(entry.getKey())) {
                continue; // requiredness is not expressible in the current schema — documented limitation
            }
            Object value = parameters.get(entry.getKey());
            String valueType = entry.getValue().valueType();
            boolean typeOk = switch (valueType) {
                case "string" -> value instanceof String;
                case "integer", "number" -> value instanceof Number;
                case "boolean" -> value instanceof Boolean;
                default -> true; // untyped schema entry: no expressible constraint
            };
            if (!typeOk) {
                throw new IllegalArgumentException(
                        "PV3: parameter '" + entry.getKey() + "' for definition '" + definitionId
                                + "' has type " + (value == null ? "null" : value.getClass().getSimpleName())
                                + " but schema declares '" + valueType + "' — FAIL CLOSED");
            }
        }
    }

    private static EffectDefinitionSnapshot definitionSnapshot(EffectInstance.EffectDefinition definition) {
        List<String> supported = new ArrayList<>();
        for (Object mt : definition.supportedMediaTypes()) {
            supported.add(mt instanceof Enum<?> e ? e.name() : String.valueOf(mt));
        }
        List<EffectDefinitionSnapshot.EffectParameterSchemaEntry> schemaEntries = new ArrayList<>();
        for (Map.Entry<String, EffectInstance.ParameterSchema> e
                : new java.util.TreeMap<>(definition.parameterSchema()).entrySet()) {
            schemaEntries.add(new EffectDefinitionSnapshot.EffectParameterSchemaEntry(
                    e.getKey(), e.getValue().valueType(), e.getValue().defaultValue()));
        }
        List<String> deterministic = new ArrayList<>(definition.deterministicProperties());
        List<String> capabilities = new ArrayList<>(definition.requiredCapabilities());
        // digest computed from the canonical semantic fields (id/version content
        // immutability — D1); supportedBackendCapabilities is excluded.
        EffectDefinitionSnapshot provisional = new EffectDefinitionSnapshot(
                definition.definitionId(), definition.version(), definition.category().name(),
                supported, schemaEntries,
                definition.temporalBehavior() == null ? null : definition.temporalBehavior().name(),
                deterministic, capabilities, "");
        String digest = EffectDefinitionCanonicalSemantics.definitionContentDigest(provisional);
        return new EffectDefinitionSnapshot(
                definition.definitionId(), definition.version(), definition.category().name(),
                supported, schemaEntries,
                definition.temporalBehavior() == null ? null : definition.temporalBehavior().name(),
                deterministic, capabilities, digest);
    }

    private static List<EffectSemanticEntry.EffectParameter> parametersToList(Map<String, Object> params) {
        List<EffectSemanticEntry.EffectParameter> result = new ArrayList<>();
        for (Map.Entry<String, Object> e : new java.util.TreeMap<>(params).entrySet()) {
            result.add(new EffectSemanticEntry.EffectParameter(e.getKey(), String.valueOf(e.getValue())));
        }
        return result;
    }
}
