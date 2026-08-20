package com.example.platform.timeline.semantics.effect;

/**
 * ROADMAP20 correction R6-A: explicit typed authored Effect target — WHERE the
 * effect applies (WHICH authored entity), fully separated from
 * {@code applicationRange} (WHEN).
 *
 * <p>Frozen principle
 * {@code EFFECT_MEMBERSHIP_IS_EXPLICIT_TYPED_AUTHORED_RELATION_NOT_TEMPORAL_HEURISTIC_V1}:
 * an effect's membership in a revision / clip is a typed authored relation,
 * never a temporal-overlap heuristic. A planning caller cannot attach an
 * effect to a clip merely because their time ranges overlap.
 *
 * <p>Bounded variants: repository reality currently models clip-scoped
 * authored effects (wire {@code clip.effects[]}), so only
 * {@link ClipEffectTarget} is implemented; the sealed root leaves room for a
 * future {@code TrackEffectTarget} without changing consumers.
 */
public sealed interface EffectTarget permits ClipEffectTarget {
}
