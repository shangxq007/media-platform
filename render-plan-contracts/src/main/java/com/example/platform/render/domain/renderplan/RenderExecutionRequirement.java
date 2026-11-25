package com.example.platform.render.domain.renderplan;

/**
 * HOOK: typed execution-requirement values on a render node (C19). Declared but
 * NOT consumed in the #20 slice; NOT a fingerprint input. Default =
 * (NONE, DETERMINISM, false).
 *
 * @param gpu              GPU requirement
 * @param determinism      determinism class
 * @param sandboxedIntent  whether the node intends sandboxed execution
 */
public record RenderExecutionRequirement(
        GpuRequirement gpu,
        RenderDeterminismClass determinism,
        boolean sandboxedIntent) {

    public static final RenderExecutionRequirement DEFAULT =
            new RenderExecutionRequirement(GpuRequirement.NONE, RenderDeterminismClass.DETERMINISTIC, false);

    public RenderExecutionRequirement {
        if (gpu == null) {
            gpu = GpuRequirement.NONE;
        }
        if (determinism == null) {
            determinism = RenderDeterminismClass.DETERMINISTIC;
        }
    }

    /** GPU requirement level. */
    public enum GpuRequirement {
        NONE,
        OPTIONAL
    }

    /** Determinism class (C20 precedent). */
    public enum RenderDeterminismClass {
        DETERMINISTIC,
        CONDITIONALLY_DETERMINISTIC,
        NON_DETERMINISTIC
    }
}
