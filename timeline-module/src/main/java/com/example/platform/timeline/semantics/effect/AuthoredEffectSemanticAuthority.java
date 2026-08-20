package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * ROADMAP20 correction R6-A: THE authoritative issuer of
 * {@link EffectSemanticBinding} — verified against REVISION-OWNED authored
 * membership, never a temporal heuristic.
 *
 * <p>Frozen principle
 * {@code EFFECT_MEMBERSHIP_IS_EXPLICIT_TYPED_AUTHORED_RELATION_NOT_TEMPORAL_HEURISTIC_V1}:
 * the ONLY way an effect can belong to a revision is an explicit authored
 * membership entry in the revision-owned {@link RevisionOwnedEffectProjection}
 * (derived from {@code TimelineCandidate.Clip.effects}).
 *
 * <p>Issuance verifies, per effect:
 * <ol>
 *   <li>the instance carries an explicit {@link EffectTarget} (fail closed if
 *       absent),</li>
 *   <li>the (trackId, clipId, effectInstanceId) membership EXISTS in the
 *       revision-owned projection — a caller cannot attach a foreign effect
 *       merely because time ranges overlap, cannot forge a clip id, and cannot
 *       reassign an effect to a different same-range clip,</li>
 *   <li>the target clip/track actually exist in the revision's canonical
 *       timeline (R6-A5),</li>
 *   <li>effectInstanceId is unique across the snapshot (R6-J),</li>
 *   <li>definition exists, definition version matches, mediaType is supported
 *       by the definition (R6-I),</li>
 *   <li>the content digest is computed by the single Effect domain authority
 *       ({@link EffectSemanticStateCanonicalSemantics}), which includes the
 *       typed target in the semantic bytes (R6-F).</li>
 * </ol>
 */
public final class AuthoredEffectSemanticAuthority {

    private AuthoredEffectSemanticAuthority() {
    }

    /**
     * Issues the authoritative binding for the given authored revision and its
     * revision-owned Effect semantic state.
     *
     * @param timelineRevision the authoritative Timeline revision (revision id
     *                         + canonical clips are read from THIS object)
     * @param projection       the revision-owned authored effect membership
     *                         projection (derived from the revision's
     *                         wire/candidate aggregate)
     * @param effects          typed effect instances claimed to belong to the
     *                         revision (each MUST carry an explicit target)
     * @param effectDefinitions the effect definition catalog
     * @return authoritative immutable binding
     * @throws IllegalArgumentException on ANY membership / identity /
     *                                  definition / digest integrity failure
     *                                  (fail closed)
     */
    public static EffectSemanticBinding issue(
            TimelineRevision timelineRevision,
            RevisionOwnedEffectProjection projection,
            List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> effectDefinitions) {
        Objects.requireNonNull(timelineRevision, "timelineRevision");
        Objects.requireNonNull(projection, "projection");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(effectDefinitions, "effectDefinitions");

        String revisionId = timelineRevision.revisionId();

        // ── R6-J: unique effect instance ids (no node/path identity ambiguity).
        Set<String> seenIds = new HashSet<>();
        for (EffectInstance effect : effects) {
            if (!seenIds.add(effect.effectInstanceId())) {
                throw new IllegalArgumentException(
                        "Duplicate effectInstanceId '" + effect.effectInstanceId()
                                + "' in the effect semantic snapshot (R6-J fail closed)");
            }
        }

        // ── per-effect membership + identity verification ──
        for (EffectInstance effect : effects) {
            // 1. explicit typed target required.
            if (effect.target() == null) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " has no explicit authored "
                                + "EffectTarget (R6-A fail closed: target-less instances carry "
                                + "no membership)");
            }
            if (!(effect.target() instanceof ClipEffectTarget clipTarget)) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " has unsupported EffectTarget "
                                + "variant " + effect.target().getClass().getSimpleName()
                                + " (R6 bounded scope: ClipEffectTarget only)");
            }

            // 2. authentic membership in the revision-owned projection.
            if (!projection.contains(clipTarget, effect.effectInstanceId())) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " is NOT an authored member of "
                                + "clip " + clipTarget.clipId() + " on track " + clipTarget.trackId()
                                + " in revision " + revisionId + " — membership must come from "
                                + "the revision-owned wire aggregate; temporal overlap is not "
                                + "ownership (R6-A fail closed)");
            }

            // 3. target clip/track exist in the revision's canonical timeline.
            if (!clipExists(timelineRevision, clipTarget)) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " target clip "
                                + clipTarget.clipId() + " / track " + clipTarget.trackId()
                                + " does not exist in revision " + revisionId
                                + " (R6-A5 fail closed)");
            }

            // 4. definition exists + version matches.
            EffectInstance.EffectDefinition definition = findDefinition(
                    effectDefinitions, effect.effectDefinitionId());
            if (definition == null) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " references unknown "
                                + "effectDefinitionId '" + effect.effectDefinitionId() + "'");
            }
            if (!definition.version().equals(effect.effectDefinitionVersion())) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " version mismatch: instance '"
                                + effect.effectDefinitionVersion() + "' vs definition '"
                                + definition.version() + "'");
            }

            // 5. mediaType supported by the definition (R6-I).
            boolean mediaSupported = definition.supportedMediaTypes().stream()
                    .anyMatch(mt -> mt == effect.mediaType());
            if (!mediaSupported) {
                throw new IllegalArgumentException(
                        "Effect " + effect.effectInstanceId() + " mediaType " + effect.mediaType()
                                + " is not in definition " + definition.definitionId()
                                + " supportedMediaTypes (R6-I fail closed)");
            }
        }

        // ── R6-J: unique (definitionId, version) catalog identity.
        Set<String> seenDefinitions = new HashSet<>();
        for (EffectInstance.EffectDefinition definition : effectDefinitions) {
            String identity = definition.definitionId() + "@" + definition.version();
            if (!seenDefinitions.add(identity)) {
                throw new IllegalArgumentException(
                        "Duplicate effect definition identity '" + identity
                                + "' in the catalog (R6-J fail closed)");
            }
        }

        // ── digest via the single domain authority (target participates). ──
        String canonical = EffectSemanticStateCanonicalSemantics.canonicalEffectState(
                effects, effectDefinitions);
        ContentDigest digest = ContentDigest.sha256(
                EffectSemanticStateCanonicalSemantics.sha256Hex(canonical));

        return EffectSemanticBinding.create(
                revisionId, digest, EffectSemanticBinding.CONTRACT_VERSION);
    }

    /** Definition lookup — fails deterministically (no findFirst-on-duplicate). */
    private static EffectInstance.EffectDefinition findDefinition(
            List<EffectInstance.EffectDefinition> definitions, String definitionId) {
        EffectInstance.EffectDefinition found = null;
        for (EffectInstance.EffectDefinition definition : definitions) {
            if (definition.definitionId().equals(definitionId)) {
                if (found != null) {
                    // duplicate definition id — catalog identity ambiguous.
                    throw new IllegalArgumentException(
                            "Duplicate effectDefinitionId '" + definitionId
                                    + "' in the catalog (R6-J fail closed)");
                }
                found = definition;
            }
        }
        return found;
    }

    /** Target clip/track existence check against the revision's canonical timeline. */
    private static boolean clipExists(
            TimelineRevision revision, ClipEffectTarget target) {
        if (revision.canonicalTimeline() == null) {
            return false;
        }
        for (var track : revision.canonicalTimeline().getTracks()) {
            if (!track.trackId().equals(target.trackId())) {
                continue;
            }
            for (var clip : track.clips()) {
                if (clip.getClipId().value().equals(target.clipId())) {
                    return true;
                }
            }
        }
        return false;
    }
}
