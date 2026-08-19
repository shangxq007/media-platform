package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E. Exact time (brief §13E): no double anywhere. DECODE sample window exactly
 * matches the extent-derived window using MediaTime equality.
 */
class ExactTimeTest {

    @Test
    void fullExtentWindowIsExact() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        RenderNode decode = firstDecode(result);
        RenderSampleWindow window = decode.requiredSampleWindow().orElseThrow();
        assertEquals(MediaTime.ofRational(0, 1), window.start());
        assertEquals(MediaTime.ofRational(2, 1), window.end());
    }

    @Test
    void subExtentWindowIsExact() {
        // extent [0/1, 2/3) at 30/1 -> sample window exact 0/1..2/3
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderRequest req = TestPlans.renderRequest();
        RenderRequest sub = new RenderRequest(req.id(),
                new RenderExtent(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 3), FrameRate.of(30, 1)),
                req.outputs());
        RenderPlanningInput input = new RenderPlanningInput(
                TestPlans.revisionRef(), List.of(TestPlans.mediaClip()),
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()), TestPlans.audioMix(),
                List.of(TestPlans.textElement()), sub,
                new SourceResolutionInput(Map.of(TestPlans.artifactId(), RenderSourceResolutionState.RESOLVED)),
                TestPlans.fullCapabilityContext());
        RenderPlanningResult result = planner.plan(input);
        RenderNode decode = firstDecode(result);
        RenderSampleWindow window = decode.requiredSampleWindow().orElseThrow();
        assertEquals(MediaTime.ofRational(0, 1), window.start());
        assertEquals(MediaTime.ofRational(2, 3), window.end());
    }

    @Test
    void reverseMappingYieldsSameWindow() {
        // ConstantRate REVERSE -> same exact window (direction only changes sample order)
        RenderPlanner planner = new DefaultRenderPlanner();
        MediaClip clip = TestPlans.mediaClip();
        MediaClip reverseClip = reverseClip(clip);
        RenderRequest req = TestPlans.renderRequest();
        RenderPlanningInput input = new RenderPlanningInput(
                TestPlans.revisionRef(), List.of(reverseClip),
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()), TestPlans.audioMix(),
                List.of(TestPlans.textElement()), req,
                new SourceResolutionInput(Map.of(TestPlans.artifactId(), RenderSourceResolutionState.RESOLVED)),
                TestPlans.fullCapabilityContext());
        RenderPlanningResult result = planner.plan(input);
        RenderNode decode = firstDecode(result);
        RenderSampleWindow window = decode.requiredSampleWindow().orElseThrow();
        assertEquals(MediaTime.ofRational(0, 1), window.start());
        assertEquals(MediaTime.ofRational(2, 1), window.end());
    }

    @Test
    void freezeMappingYieldsPointWindow() {
        MediaClip clip = TestPlans.mediaClip();
        MediaClip freezeClip = freezeClip(clip);
        RenderRequest req = TestPlans.renderRequest();
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput input = new RenderPlanningInput(
                TestPlans.revisionRef(), List.of(freezeClip),
                List.of(), List.of(), TestPlans.audioMix(),
                List.of(TestPlans.textElement()), req,
                new SourceResolutionInput(Map.of(TestPlans.artifactId(), RenderSourceResolutionState.RESOLVED)),
                TestPlans.fullCapabilityContext());
        RenderPlanningResult result = planner.plan(input);
        RenderNode decode = firstDecode(result);
        RenderSampleWindow window = decode.requiredSampleWindow().orElseThrow();
        assertTrue(window.isFreezePoint(), "freeze -> point window");
        assertEquals(window.start(), window.end(), "point window [p,p]");
    }

    private RenderNode firstDecode(RenderPlanningResult result) {
        return result.plan().nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Decode)
                .findFirst().orElseThrow();
    }

    private MediaClip reverseClip(MediaClip clip) {
        ConstantRateTemporalMapping rev = ConstantRateTemporalMapping.of(1, 1, PlaybackDirection.REVERSE);
        return new MediaClip(clip.clipId(), clip.trackId(), clip.timelineRange(), clip.sourceRange(),
                rev, clip.sourceBinding());
    }

    private MediaClip freezeClip(MediaClip clip) {
        FreezeTemporalMapping freeze = new FreezeTemporalMapping(MediaTime.ofRational(1, 1));
        return new MediaClip(clip.clipId(), clip.trackId(), clip.timelineRange(), clip.sourceRange(),
                freeze, clip.sourceBinding());
    }
}
