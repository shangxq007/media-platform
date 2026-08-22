package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import java.util.Objects;

/**
 * C12/C13 architecture correction (FROZEN, Option A): typed #20 execution
 * coverage — the render/timeline-coordinate interval a RenderNode contributes
 * to the requested execution extent.
 *
 * <p>Coordinate domain = RenderExtent / timeline execution domain. This is
 * DISTINCT from {@link RenderSampleWindow} (source-media sampling domain).
 * A node may carry both: ExecutionCoverage (timeline coords) and
 * RenderSampleWindow (source coords) — they are different typed projections
 * and MUST NEVER be compared directly.
 *
 * <p>null coverage on a RenderNode = the node has no single coverage interval
 * (e.g. OUTPUT / MUX / multi-input composites) => never pruned by coverage
 * reasoning. Exact rational time only; deterministic; provider-neutral.
 * NOT a new Timeline authority; NOT TemporalMapping redefinition.
 *
 * <p>OWNER=#20 (renderplan domain). #21 consumes only.
 */
public record RenderExecutionCoverage(MediaTime start, MediaTime end, FrameRate frameRate) {

    public RenderExecutionCoverage {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(frameRate, "frameRate");
        if (!end.isGreaterThan(start)) {
            throw new IllegalArgumentException("coverage end must be > start");
        }
    }
}
