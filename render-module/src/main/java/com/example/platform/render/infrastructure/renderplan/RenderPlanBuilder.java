package com.example.platform.render.infrastructure.renderplan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * RenderPlan Builder - converts Timeline to RenderPlan IR.
 * 
 * <p>Creates deterministic DAG from timeline data.
 */
@Service
public class RenderPlanBuilder {

    private static final Logger log = LoggerFactory.getLogger(RenderPlanBuilder.class);

    /**
     * Build a render plan from timeline data.
     */
    public RenderPlan buildFromTimeline(String jobId, TimelineData timeline) {
        log.info("Building render plan for job {}", jobId);

        RenderPlan plan = RenderPlan.create(jobId);

        // Process clips
        for (TimelineClip clip : timeline.clips()) {
            RenderPlan.RenderNode clipNode = createClipNode(clip);
            plan = plan.addNode(clipNode);
        }

        // Process transitions
        for (TimelineTransition transition : timeline.transitions()) {
            RenderPlan.RenderNode transitionNode = createTransitionNode(transition);
            plan = plan.addNode(transitionNode);

            // Add edges from source clips to transition
            for (String sourceClipId : transition.sourceClipIds()) {
                plan = plan.addEdge(RenderPlan.RenderEdge.data(sourceClipId, transitionNode.id()));
            }

            // Add edge from transition to output
            plan = plan.addEdge(RenderPlan.RenderEdge.data(transitionNode.id(), "output"));
        }

        // Create output node
        RenderPlan.RenderNode outputNode = RenderPlan.RenderNode.output(
                "output",
                calculateInputHash(plan),
                Map.of("format", "mp4", "resolution", "1920x1080")
        );
        plan = plan.addNode(outputNode);

        // Add edges from clips without transitions to output
        for (TimelineClip clip : timeline.clips()) {
            boolean hasTransition = timeline.transitions().stream()
                    .anyMatch(t -> t.sourceClipIds().contains(clip.id()));
            if (!hasTransition) {
                plan = plan.addEdge(RenderPlan.RenderEdge.data(clip.id(), "output"));
            }
        }

        log.info("Built render plan with {} nodes", plan.size());
        return plan;
    }

    /**
     * Create a clip node from timeline clip.
     */
    private RenderPlan.RenderNode createClipNode(TimelineClip clip) {
        return RenderPlan.RenderNode.clip(
                clip.id(),
                clip.sourceUri(),
                Map.of(
                        "startTime", clip.startTime(),
                        "duration", clip.duration(),
                        "sourceUri", clip.sourceUri()
                )
        );
    }

    /**
     * Create a transition node from timeline transition.
     */
    private RenderPlan.RenderNode createTransitionNode(TimelineTransition transition) {
        return RenderPlan.RenderNode.transition(
                transition.id(),
                calculateTransitionInputHash(transition),
                Map.of(
                        "type", transition.type(),
                        "duration", transition.duration()
                )
        );
    }

    /**
     * Calculate input hash for the plan.
     */
    private String calculateInputHash(RenderPlan plan) {
        StringBuilder sb = new StringBuilder();
        for (RenderPlan.RenderNode node : plan.nodes()) {
            if (node.inputHash() != null) {
                sb.append(node.inputHash());
            }
        }
        return "hash-" + Integer.toHexString(sb.toString().hashCode());
    }

    /**
     * Calculate input hash for a transition.
     */
    private String calculateTransitionInputHash(TimelineTransition transition) {
        StringBuilder sb = new StringBuilder();
        for (String clipId : transition.sourceClipIds()) {
            sb.append(clipId);
        }
        return "hash-" + Integer.toHexString(sb.toString().hashCode());
    }

    // ---------------------------------------------------------------------------
    // Inner Types
    // ---------------------------------------------------------------------------

    public record TimelineData(
            String timelineId,
            List<TimelineClip> clips,
            List<TimelineTransition> transitions,
            Map<String, Object> metadata
    ) {}

    public record TimelineClip(
            String id,
            String sourceUri,
            double startTime,
            double duration,
            Map<String, Object> metadata
    ) {}

    public record TimelineTransition(
            String id,
            String type,
            double duration,
            List<String> sourceClipIds,
            Map<String, Object> metadata
    ) {}
}
