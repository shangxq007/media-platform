package com.example.platform.timeline.semantics.effect;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * ROADMAP20 final implementation: THE single deterministic canonical codec for
 * {@link EffectSemanticSnapshot} semantic content (EFFECT_SNAPSHOT_HANDLE_DOES_NOT_PARTICIPATE_IN_CANONICAL_SEMANTIC_DIGEST_V1).
 *
 * <p>Rules:
 * <ul>
 *   <li>the snapshot ID is EXCLUDED — digest is pure semantic content
 *       commitment (BI1: different ids, identical content ⇒ equal digest);</li>
 *   <li>per-target ordered stacks preserve authored order — reversing
 *       [e1,e2] to [e2,e1] changes the digest (SO1);</li>
 *   <li>cross-target ordering is deterministic (track canonical order →
 *       clip canonical order → ordered stack) so changing clip C2 never
 *       perturbs C1's canonical encoding (SO4 locality);</li>
 *   <li>definition semantic content embeds via
 *       {@link EffectDefinitionCanonicalSemantics} (same grammar everywhere);</li>
 *   <li>no runtime class names, no Java serialization, no reflection order,
 *       no Map iteration order;</li>
 *   <li>contract version participates in the commitment (BI5).</li>
 * </ul>
 */
public final class EffectSemanticSnapshotCanonicalSemantics {

    private EffectSemanticSnapshotCanonicalSemantics() {
    }

    /** Canonical semantic text of a snapshot (digest input; snapshot id EXCLUDED). */
    public static String canonicalSnapshot(EffectSemanticSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("semanticContractVersion=").append(snapshot.semanticContractVersion().value()).append('\n');
        // Deterministic cross-target ordering: track -> clip -> ordered stack.
        List<EffectSemanticEntry> ordered = orderEntries(snapshot.entries());
        sb.append("entries=").append(ordered.size()).append('\n');
        for (EffectSemanticEntry entry : ordered) {
            encodeEntry(sb, entry);
        }
        return sb.toString();
    }

    /** Deterministic SHA-256 hex content digest of the snapshot semantics. */
    public static String snapshotContentDigest(EffectSemanticSnapshot snapshot) {
        return sha256Hex(canonicalSnapshot(snapshot));
    }

    /** Verify the snapshot's stored digest against recomputation (BI3). */
    public static void verifySnapshotDigest(EffectSemanticSnapshot snapshot) {
        String recomputed = snapshotContentDigest(snapshot);
        if (!recomputed.equals(snapshot.contentDigest())) {
            throw new IllegalArgumentException(
                    "EffectSemanticSnapshot content digest mismatch for "
                            + snapshot.id().value() + ": stored '" + snapshot.contentDigest()
                            + "' recomputed '" + recomputed + "'");
        }
    }

    private static void encodeEntry(StringBuilder sb, EffectSemanticEntry entry) {
        EffectTarget target = entry.target();
        sb.append("entry{");
        sb.append("instance=").append(entry.effectInstanceId()).append(';');
        sb.append("target=").append(targetCanonical(target)).append(';');
        sb.append("enabled=").append(entry.enabled()).append(';');
        sb.append("definition=")
                .append(EffectDefinitionCanonicalSemantics.canonicalDefinition(entry.definitionSnapshot())
                        .replace("\n", "|"))
                .append(';');
        sb.append("parameters=");
        List<String> params = new ArrayList<>();
        for (EffectSemanticEntry.EffectParameter p : entry.parameters()) {
            params.add(p.key() + ":" + p.value());
        }
        params.sort(Comparator.naturalOrder());
        sb.append('[');
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(params.get(i));
        }
        sb.append(']');
        sb.append('}').append('\n');
    }

    private static String targetCanonical(EffectTarget target) {
        if (target instanceof ClipEffectTarget clip) {
            return "clip(" + clip.trackId() + "," + clip.clipId() + ")";
        }
        return "unknown(" + target.getClass().getName() + ")";
    }

    /**
     * Deterministic order: group by target; within a target keep the authored
     * stack order (never sort by instance/definition id); across targets order
     * by (trackId, clipId) natural order — locality preserved.
     */
    static List<EffectSemanticEntry> orderEntries(List<EffectSemanticEntry> entries) {
        List<EffectSemanticEntry> copy = new ArrayList<>(entries);
        copy.sort((a, b) -> {
            int byTarget = targetKey(a.target()).compareTo(targetKey(b.target()));
            return byTarget;
        });
        return copy;
    }

    private static String targetKey(EffectTarget target) {
        if (target instanceof ClipEffectTarget clip) {
            return "clip|" + clip.trackId() + "|" + clip.clipId();
        }
        return "other|" + target.getClass().getName();
    }

    public static String sha256Hex(String canonical) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
