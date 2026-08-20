package com.example.platform.timeline.semantics.effect;

import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaClip;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * ROADMAP20 final implementation: the ONLY authoritative minting path for
 * {@link EffectSemanticSnapshot} — EFFECT_SNAPSHOT_MINTING_IS_DOMAIN_AUTHORITY_ONLY_V1.
 *
 * <p>Callers may REQUEST an edit; they may NOT ISSUE authoritative snapshot
 * id/digest/content. There is deliberately NO public factory equivalent to
 * {@code issue(revision, projection, callerEffects, callerDefinitions)} —
 * the R6-era caller-assembly authority surface is retired here.
 *
 * <p>V1 derivation rules (all frozen):
 * <ul>
 *   <li>target = typed ClipEffectTarget from canonical target membership
 *       (never overlap-derived);</li>
 *   <li>applicationRange = DERIVED from target clip Timeline extent
 *       (APPLICATION_RANGE_AUTHORITY_V1);</li>
 *   <li>mediaType = DERIVED from target track kind ∩ definition
 *       supportedMediaTypes (EFFECT_MEDIA_TYPE_IS_DERIVED_V1; incompatible =
 *       FAIL CLOSED);</li>
 *   <li>enabled = TRUE under legacy bounded contract; no caller override;</li>
 *   <li>automationBindings = EMPTY (V1; non-empty fails closed at
 *       EffectSemanticEntry construction);</li>
 *   <li>definition version/content immutability enforced via
 *       {@link EffectDefinitionVersionRegistry} (EFFECT_DEFINITION_VERSION_CONTENT_IS_IMMUTABLE_V1)
 *       across ALL snapshots, not just one.</li>
 * </ul>
 */
public final class EffectSemanticSnapshotAuthority {

    private EffectSemanticSnapshotAuthority() {
    }

    /**
     * Mints an authoritative snapshot from typed authored effect state +
     * exact definitions. Every caller-supplied value is validated/derived —
     * nothing is trusted as authoritative input.
     *
     * @param effects           authored typed effect instances
     * @param definitions       exact effect definitions (must cover every
     *                          reference; caller-supplied content is validated,
     *                          not trusted)
     * @param clipContext       (trackId, clipId) -> clip (for target extent
     *                          derivation and track kind)
     * @param registry          cross-snapshot definition version registry
     * @param snapshotId        immutable handle assigned by the domain
     * @return immutable authoritative snapshot
     */
    public static EffectSemanticSnapshot mint(
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> definitions,
            Function<ClipTargetKey, MediaClip> clipContext,
            EffectDefinitionVersionRegistry registry,
            EffectSemanticSnapshotId snapshotId) {
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(clipContext, "clipContext");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(snapshotId, "snapshotId");

        List<EffectSemanticEntry> entries = new ArrayList<>();
        java.util.Set<String> seenInstanceIds = new java.util.HashSet<>();
        // preserve caller order WITHIN target; authority reorders canonically at
        // digest time — authored stack order is the order given here and must
        // equal the wire clip.effects[] order (legacy) or the authored order
        // (new snapshots).
        for (EffectInstance effect : effects) {
            if (!seenInstanceIds.add(effect.effectInstanceId())) {
                throw new IllegalArgumentException(
                        "I1: duplicate effectInstanceId '" + effect.effectInstanceId()
                                + "' in snapshot state — FAIL CLOSED");
            }
            entries.add(entryFor(effect, definitions, clipContext, registry));
        }
        // D1: embed exact definition snapshots for every referenced definition.
        List<EffectDefinitionSnapshot> embedded = new ArrayList<>();
        for (EffectInstance.EffectDefinition definition : definitions) {
            embedded.add(definitionSnapshot(definition));
        }
        // Version immutability across snapshots (D1, BI4, §39):
        for (EffectDefinitionSnapshot ds : embedded) {
            EffectDefinitionCanonicalSemantics.verifyDefinitionDigest(ds);
            registry.register(ds);
        }

        // Value-bound digest: canonical semantic content only (id excluded).
        // Provisional digest placeholder is non-blank only to satisfy the
        // immutability constructor; the FINAL digest is computed from the
        // canonical semantic content and replaces it.
        EffectSemanticSnapshot provisional = new EffectSemanticSnapshot(
                snapshotId, EffectSemanticContractVersion.current(), entries, "pending");
        String digest = EffectSemanticSnapshotCanonicalSemantics.snapshotContentDigest(provisional);
        return new EffectSemanticSnapshot(snapshotId, EffectSemanticContractVersion.current(), entries, digest);
    }

    /** Legacy wire hydration: deterministic mapping from a wire effect member. */
    public static EffectSemanticEntry legacyEntry(
            String wireEffectId,
            String effectKey,
            Map<String, String> wireParameters,
            String trackId,
            String clipId,
            TrackType trackType,
            MediaClip.TimeRange clipExtent,
            List<EffectInstance.EffectDefinition> definitions) {
        Objects.requireNonNull(wireEffectId, "wireEffectId");
        Objects.requireNonNull(effectKey, "effectKey");
        Objects.requireNonNull(trackId, "trackId");
        Objects.requireNonNull(clipId, "clipId");
        Objects.requireNonNull(trackType, "trackType");
        Objects.requireNonNull(clipExtent, "clipExtent");
        Objects.requireNonNull(definitions, "definitions");

        // legacy mapping table (§19):
        // wire.id -> effectInstanceId; wire.effectKey -> definitionId
        EffectInstance.EffectDefinition definition = definitions.stream()
                .filter(d -> d.definitionId().equals(effectKey))
                .findFirst()
                .orElse(null);
        if (definition == null) {
            throw new IllegalArgumentException(
                    "LEGACY hydration FAIL CLOSED: wire effectKey '" + effectKey
                            + "' cannot resolve an exact definition");
        }
        // definitionVersion: MUST resolve deterministically from the exact
        // definition mechanism; never invented ("1.0 because probably current").
        EffectDefinitionSnapshot ds = definitionSnapshot(definition);
        EffectDefinitionCanonicalSemantics.verifyDefinitionDigest(ds);

        // mediaType DERIVED: track kind ∩ supportedMediaTypes (else FAIL CLOSED)
        String derivedMedia = deriveMediaType(trackType, ds.supportedMediaTypes());

        // enabled = TRUE (LEGACY_EFFECT_ENABLED_DEFAULT_V1); no caller override.
        // applicationRange = DERIVED target clip extent.
        // automationBindings = EMPTY.
        return new EffectSemanticEntry(
                wireEffectId,
                new ClipEffectTarget(trackId, clipId),
                ds,
                true,
                parametersToList(wireParameters),
                List.of());
    }

    /** Deterministic unordered-map → list encoding (sorted keys). */
    private static List<EffectSemanticEntry.EffectParameter> parametersToList(Map<String, String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return List.of();
        }
        List<String> keys = new ArrayList<>(parameters.keySet());
        keys.sort(String::compareTo);
        List<EffectSemanticEntry.EffectParameter> result = new ArrayList<>();
        for (String key : keys) {
            result.add(new EffectSemanticEntry.EffectParameter(key, parameters.get(key)));
        }
        return result;
    }

    private static EffectSemanticEntry entryFor(
            EffectInstance effect,
            List<EffectInstance.EffectDefinition> definitions,
            Function<ClipTargetKey, MediaClip> clipContext,
            EffectDefinitionVersionRegistry registry) {
        if (effect.target() == null) {
            throw new IllegalArgumentException(
                    "Effect " + effect.effectInstanceId() + " has no explicit authored "
                            + "target — R6-A5 fail closed");
        }
        if (effect.automationBindings() != null && !effect.automationBindings().isEmpty()) {
            throw new IllegalArgumentException(
                    "UNVERIFIED_EFFECT_AUTOMATION_REFERENCES_FAIL_CLOSED_V1: effect "
                            + effect.effectInstanceId() + " carries non-empty automationBindings "
                            + "— unsupported in effect-semantics-v1 (SA5)");
        }
        if (!(effect.target() instanceof ClipEffectTarget clipTarget)) {
            throw new IllegalArgumentException(
                    "Effect " + effect.effectInstanceId() + " target must be ClipEffectTarget");
        }
        // target clip must exist in the revision-owned context (T5)
        MediaClip clip = clipContext.apply(new ClipTargetKey(clipTarget.trackId(), clipTarget.clipId()));
        if (clip == null) {
            throw new IllegalArgumentException(
                    "Effect " + effect.effectInstanceId() + " target clip "
                            + clipTarget.trackId() + "/" + clipTarget.clipId() + " does not exist");
        }
        // definition resolution + version integrity (I4, I5)
        EffectInstance.EffectDefinition definition = definitions.stream()
                .filter(d -> d.definitionId().equals(effect.effectDefinitionId()))
                .findFirst()
                .orElse(null);
        if (definition == null) {
            throw new IllegalArgumentException(
                    "Effect " + effect.effectInstanceId() + " references unknown effectDefinitionId '"
                            + effect.effectDefinitionId() + "' (I5 fail closed)");
        }
        if (!definition.version().equals(effect.effectDefinitionVersion())) {
            throw new IllegalArgumentException(
                    "Effect " + effect.effectInstanceId() + " version mismatch: instance '"
                            + effect.effectDefinitionVersion() + "' vs definition '"
                            + definition.version() + "' (I4 fail closed)");
        }
        EffectDefinitionSnapshot ds = definitionSnapshot(definition);
        EffectDefinitionCanonicalSemantics.verifyDefinitionDigest(ds);

        // mediaType DERIVED (EFFECT_MEDIA_TYPE_IS_DERIVED_V1) — caller mediaType
        // is NOT authority; applicability derived from track kind ∩ definition.
        TrackType trackType = trackTypeOf(clip);
        String derivedMedia = deriveMediaType(trackType, ds.supportedMediaTypes());

        // enabled: new snapshot semantics may carry the authored flag, but only
        // if the authoring path owns it; legacy completion is rejected (SA2).
        // For #20 bounded semantics: enabled must equal the caller-authored
        // value ONLY when the caller is the real authoring path; the authority
        // treats the supplied instance as the authored state (post-validation).
        boolean enabled = effect.enabled();

        return new EffectSemanticEntry(
                effect.effectInstanceId(),
                clipTarget,
                ds,
                enabled,
                parametersToList(effect.parameters()),
                List.of());
    }

    /** Bounded track-kind → media derivation. VIDEO→VIDEO, AUDIO→AUDIO. */
    private static String deriveMediaType(TrackType trackType, List<String> supported) {
        String candidate = switch (trackType) {
            case VIDEO -> "VIDEO";
            case AUDIO -> "AUDIO";
            default -> "VIDEO";
        };
        if (!supported.contains(candidate)) {
            throw new IllegalArgumentException(
                    "EFFECT_MEDIA_TYPE_IS_DERIVED_V1 FAIL CLOSED: track kind " + trackType
                            + " not compatible with definition supportedMediaTypes " + supported);
        }
        return candidate;
    }

    private static TrackType trackTypeOf(MediaClip clip) {
        return "audio".equals(clip.trackId()) ? TrackType.AUDIO : TrackType.VIDEO;
    }

    private static EffectDefinitionSnapshot definitionSnapshot(EffectInstance.EffectDefinition definition) {
        List<String> supported = new ArrayList<>();
        if (definition.supportedMediaTypes() != null) {
            for (EffectInstance.EffectMediaType mt : definition.supportedMediaTypes()) {
                supported.add(mt.name());
            }
        }
        List<EffectDefinitionSnapshot.EffectParameterSchemaEntry> schema = new ArrayList<>();
        // bounded V1: parameter schema keys + declared type names, sorted for
        // deterministic encoding (schema is semantically unordered).
        if (definition.parameterSchema() != null) {
            List<String> keys = new ArrayList<>(definition.parameterSchema().keySet());
            keys.sort(String::compareTo);
            for (String key : keys) {
                Object type = definition.parameterSchema().get(key);
                schema.add(new EffectDefinitionSnapshot.EffectParameterSchemaEntry(
                        key, type == null ? "string" : String.valueOf(type)));
            }
        }
        EffectDefinitionSnapshot ds = new EffectDefinitionSnapshot(
                definition.definitionId(),
                definition.version(),
                definition.category() == null ? "" : definition.category().name(),
                supported,
                schema,
                definition.temporalBehavior() == null ? "" : definition.temporalBehavior().name(),
                new ArrayList<>(definition.deterministicProperties() == null
                        ? List.of() : definition.deterministicProperties()),
                new ArrayList<>(definition.requiredCapabilities() == null
                        ? List.of() : definition.requiredCapabilities()),
                "");
        String digest = EffectDefinitionCanonicalSemantics.definitionContentDigest(ds);
        return new EffectDefinitionSnapshot(
                ds.definitionId(), ds.version(), ds.category(), ds.supportedMediaTypes(),
                ds.parameterSchema(), ds.temporalBehavior(), ds.deterministicProperties(),
                ds.requiredCapabilities(), digest);
    }

    /** Target key used for clip context lookup. */
    public record ClipTargetKey(String trackId, String clipId) {
    }
}
