package com.example.platform.render.domain.renderplan;

/**
 * ROADMAP20 correction F1: typed, immutable, deterministic, provider-neutral
 * logical materialization requirement carried by a {@link RenderNode}.
 *
 * <p>This is a DERIVED MATERIALIZED PROJECTION of authoritative authored
 * semantics (Timeline / Effect / Audio / TimedText) — never a second authored
 * canonical authority. It exists so a future #22 Physical Planner can recover
 * the complete supported logical WHAT from the Logical RenderPlan alone,
 * without re-reading authored domain objects.
 *
 * <p>Sealed: the bounded supported slice has exactly three materialization
 * kinds (effect, audio process, timed text). No {@code Map<String,Object>}
 * escape hatch; no arbitrary untyped payload blob.
 */
public sealed interface RenderMaterializationRequirement
        permits EffectMaterializationRequirement,
                AudioProcessMaterializationRequirement,
                TimedTextMaterializationRequirement {

    /** Canonical variant key used for deterministic ordering/identity. */
    String variantKey();
}
