package com.example.platform.render.app.renderplan;

import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.DefaultRenderPlanner;
import com.example.platform.render.domain.renderplan.RenderPlanner;
import com.example.platform.render.domain.renderplan.RenderPlanningInput;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderRequest;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.render.domain.renderplan.VerifiedRenderSemanticSnapshot;
import com.example.platform.render.domain.renderplan.VerifiedRenderSemanticSnapshotFactory;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.Objects;

/**
 * Production entry into typed-chain stage #20 (logical render planning).
 *
 * <p>This is the ONE application-facing boundary that turns an immutable
 * {@link TimelineRevision} into a {@link RenderPlanningResult}:
 *
 * <pre>
 *   TimelineRevision (hydrated, owns its exact Effect pin)
 *     + EffectSemanticSnapshot (loaded by the revision's own pin)
 *     → VerifiedRenderSemanticSnapshotFactory.verified(...)   (fail closed)
 *     → RenderPlanningInput (+ transient RenderRequest / SourceResolutionInput / CapabilityContext)
 *     → RenderPlanner.plan(...)
 *     → RenderPlanningResult(RenderPlan, RenderGraph, status, diagnostics)
 * </pre>
 *
 * <p>Boundary contract:
 * <ul>
 *   <li>The revision MUST be hydrated (its canonical document present) and MUST
 *       own a semantic context; {@link VerifiedRenderSemanticSnapshotFactory}
 *       fails closed otherwise (see {@code VALID_CANONICAL_REVISION_REQUIRES_SEMANTIC_CONTEXT_V1}).</li>
 *   <li>The caller supplies the {@link EffectSemanticSnapshot} loaded by the
 *       revision's own exact pin ({@link TimelineRevision#effectSemanticSnapshotReference()}).
 *       The factory re-verifies it against that pin, so a caller cannot pair a
 *       revision with a different (or semantically identical) snapshot.</li>
 *   <li>The transient inputs ({@link RenderRequest}, {@link SourceResolutionInput},
 *       {@link CapabilityContext}) are NOT authored revision truth and are never
 *       fingerprint inputs; they affect plan status and diagnostics only.</li>
 *   <li>No repository lookup, no mutable "latest" read, no provider/worker
 *       identity. Source/effect loading stays with the caller.</li>
 * </ul>
 *
 * <p>Scope: stage #20 only. Execution planning (#21) and provider binding /
 * executable task graph (#22) consume the returned {@link RenderPlanningResult}
 * through their own entries and are deliberately NOT invoked here.
 */
public final class RenderPlanningEntryService {

    private final RenderPlanner planner;
    private final TimelineContentDigester digester;

    /** Default wiring: the canonical pure planner and the timeline content digester. */
    public RenderPlanningEntryService() {
        this(new DefaultRenderPlanner(), new TimelineContentDigester());
    }

    public RenderPlanningEntryService(RenderPlanner planner, TimelineContentDigester digester) {
        this.planner = Objects.requireNonNull(planner, "planner");
        this.digester = Objects.requireNonNull(digester, "digester");
    }

    /**
     * Builds the verified, immutable planning input for one authored revision.
     *
     * <p>Fails closed (throws {@link IllegalArgumentException}) when the revision
     * is not hydratable, when its semantic context is absent/invalid, or when the
     * supplied effect snapshot does not match the revision's exact pin.
     *
     * @param hydratedRevision    authoritative immutable revision carrying its canonical document
     * @param pinnedEffectSnapshot effect snapshot loaded by the revision's own exact pin
     * @param request             exact render extent + output requirements (transient)
     * @param resolution          per-artifact source resolution state (transient)
     * @param capabilities        available platform capabilities (transient)
     */
    public RenderPlanningInput buildInput(
            TimelineRevision hydratedRevision,
            EffectSemanticSnapshot pinnedEffectSnapshot,
            RenderRequest request,
            SourceResolutionInput resolution,
            CapabilityContext capabilities) {
        Objects.requireNonNull(hydratedRevision, "hydratedRevision");
        Objects.requireNonNull(pinnedEffectSnapshot, "pinnedEffectSnapshot");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(capabilities, "capabilities");

        VerifiedRenderSemanticSnapshot authoredSnapshot =
                VerifiedRenderSemanticSnapshotFactory.verified(
                        hydratedRevision, digester, pinnedEffectSnapshot);
        return new RenderPlanningInput(authoredSnapshot, request, resolution, capabilities);
    }

    /** Runs the pure #20 planning pass over an already-built input. */
    public RenderPlanningResult planFromInput(RenderPlanningInput input) {
        Objects.requireNonNull(input, "input");
        return planner.plan(input);
    }

    /** Convenience: build the verified input from a revision, then run the #20 planning pass. */
    public RenderPlanningResult plan(
            TimelineRevision hydratedRevision,
            EffectSemanticSnapshot pinnedEffectSnapshot,
            RenderRequest request,
            SourceResolutionInput resolution,
            CapabilityContext capabilities) {
        return planFromInput(
                buildInput(hydratedRevision, pinnedEffectSnapshot, request, resolution, capabilities));
    }
}
