package com.example.platform.render.domain.timeline.semantics.clip;

/**
 * ROADMAP_17 (S1/S4): canonical Timeline composition-side source binding root.
 *
 * <p>{@code TimelineSourceBinding} is the typed, closed source-kind abstraction of
 * WHAT immutable source semantics a Timeline clip references. It is:
 * <ul>
 *   <li><b>source-agnostic</b> — it does NOT assume every source has a MediaStream,
 *       MediaAsset, Artifact, intrinsic duration, storage URI or external locator;</li>
 *   <li><b>typed</b> — each concrete kind is a sealed variant with its own canonical
 *       semantics; no nullable universal/god-object, no raw {@code Map<String,Object>};</li>
 *   <li><b>kind-discriminated</b> — {@link #sourceKind()} is deterministic and
 *       participates in canonical serialization and content hashing;</li>
 *   <li><b>immutable</b> — historical revisions bind immutable source semantics, never
 *       "latest" resolution (TIMELINE_SOURCE_BINDINGS_PIN_IMMUTABLE_SOURCE_SEMANTICS_V1).</li>
 * </ul>
 *
 * <p>Timeline placement (WHERE/WHEN) and TemporalMapping (HOW timeline time maps to
 * source time) are separate concerns and are NOT part of this binding. Exact source
 * TimeRange currently remains inside {@link MediaStreamSourceBinding} until the
 * Temporal Mapping foundation lands (S10).
 *
 * <p>Future source kinds (Scene, Generated, Procedural, ...) extend this sealed root
 * without redefining Timeline source fundamentals; no fake MediaAsset/MediaStream is
 * ever introduced for them (S17).
 */
public sealed interface TimelineSourceBinding
        permits MediaStreamSourceBinding {

    /** Deterministic source-kind discriminator (S12/S13). */
    SourceKind sourceKind();

    /** Canonical source kinds. {@code MEDIA_STREAM} is the first concrete kind. */
    enum SourceKind {
        MEDIA_STREAM
    }
}
