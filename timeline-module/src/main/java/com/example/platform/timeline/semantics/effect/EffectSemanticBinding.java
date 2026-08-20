package com.example.platform.timeline.semantics.effect;

import com.example.platform.shared.digest.ContentDigest;
import java.util.Objects;

/**
 * ROADMAP20 correction R5-A: immutable authoritative authored Effect semantic
 * binding.
 *
 * <p>Binds ONE authored Timeline revision identity to ONE immutable authored
 * Effect semantic state digest. Construction is RESTRICTED:
 * <ul>
 *   <li>the constructor is private,</li>
 *   <li>there is NO public caller-mintable {@code of(revisionId, effects, defs)}
 *       path — a planning caller cannot label arbitrary effect state with an
 *       arbitrary revision id (relabel attack eliminated),</li>
 *   <li>the ONLY issuance path is
 *       {@link AuthoredEffectSemanticAuthority#issue(com.example.platform.timeline.version.TimelineRevision, java.util.List, java.util.List)},
 *       which extracts the revision identity FROM the authoritative
 *       {@code TimelineRevision} object, validates that every effect's
 *       application range overlaps the revision's actual clips (ownership
 *       check), and delegates digest computation to the single Effect domain
 *       canonical authority.</li>
 * </ul>
 *
 * <p>Contract version: {@code effect-semantics-v1}. The digest covers the
 * complete authored Effect semantic state (instances + definitions, semantic
 * fields only; provenance fields excluded per R4-A5).
 */
public final class EffectSemanticBinding {

    public static final String CONTRACT_VERSION = "effect-semantics-v1";

    private final String revisionId;
    private final ContentDigest effectStateDigest;
    private final String semanticContractVersion;

    private EffectSemanticBinding(String revisionId, ContentDigest effectStateDigest,
            String semanticContractVersion) {
        this.revisionId = Objects.requireNonNull(revisionId, "revisionId");
        this.effectStateDigest = Objects.requireNonNull(effectStateDigest, "effectStateDigest");
        this.semanticContractVersion = Objects.requireNonNull(
                semanticContractVersion, "semanticContractVersion");
        if (revisionId.isBlank()) {
            throw new IllegalArgumentException("revisionId must not be blank");
        }
    }

    /** Package-private construction for the domain authority (same package). */
    static EffectSemanticBinding create(String revisionId, ContentDigest effectStateDigest,
            String semanticContractVersion) {
        return new EffectSemanticBinding(revisionId, effectStateDigest, semanticContractVersion);
    }

    /** The authored revision this Effect semantic state is bound to. */
    public String revisionId() {
        return revisionId;
    }

    /** Immutable content digest of the authored Effect semantic state. */
    public ContentDigest effectStateDigest() {
        return effectStateDigest;
    }

    /** Effect semantic contract version (schema identity). */
    public String semanticContractVersion() {
        return semanticContractVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EffectSemanticBinding b)) {
            return false;
        }
        return revisionId.equals(b.revisionId)
                && effectStateDigest.equals(b.effectStateDigest)
                && semanticContractVersion.equals(b.semanticContractVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(revisionId, effectStateDigest, semanticContractVersion);
    }

    @Override
    public String toString() {
        return "EffectSemanticBinding(revision=" + revisionId
                + ", digest=" + effectStateDigest + ", contract=" + semanticContractVersion + ")";
    }
}
