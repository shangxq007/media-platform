package com.example.platform.render.app;

import com.example.platform.render.domain.RenderPlan;
import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.RenderStep;
import com.example.platform.render.domain.RenderStepStatus;
import com.example.platform.render.domain.RenderStepType;
import com.example.platform.shared.Ids;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for creating and managing {@link RenderPlan} instances.
 *
 * <p>Plans are stored in-memory for the current implementation. In a production
 * deployment, they would be persisted to the database.</p>
 */
@Service
public class RenderPlanService {

    private static final Logger log = LoggerFactory.getLogger(RenderPlanService.class);

    private final Map<String, RenderPlan> plans = new ConcurrentHashMap<>();

    /**
     * Creates a default render plan for the given job and profile.
     *
     * <p>The default plan includes: BUILD_TIMELINE → FFMPEG_TRANSCODE → REGISTER_ARTIFACT.</p>
     *
     * @param renderJobId the render job identifier
     * @param profile     the render profile
     * @return the created render plan
     */
    public RenderPlan createDefaultPlan(String renderJobId, RenderProfile profile) {
        String planId = Ids.newId("rp");
        List<RenderStep> steps = List.of(
                RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.BUILD_TIMELINE),
                RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.FFMPEG_TRANSCODE),
                RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.REGISTER_ARTIFACT)
        );
        RenderPlan plan = RenderPlan.create(planId, renderJobId, profile, steps);
        plans.put(planId, plan);
        log.info("Created default render plan {} for job {}", planId, renderJobId);
        return plan;
    }

    /**
     * Creates a custom render plan with the specified steps.
     *
     * @param renderJobId the render job identifier
     * @param profile     the render profile
     * @param stepTypes   ordered list of step types
     * @return the created render plan
     */
    public RenderPlan createCustomPlan(String renderJobId, RenderProfile profile,
            List<RenderStepType> stepTypes) {
        String planId = Ids.newId("rp");
        List<RenderStep> steps = new ArrayList<>();
        for (RenderStepType type : stepTypes) {
            steps.add(RenderStep.pending(Ids.newId("rs"), planId, type));
        }
        RenderPlan plan = RenderPlan.create(planId, renderJobId, profile, steps);
        plans.put(planId, plan);
        log.info("Created custom render plan {} for job {} with {} steps",
                planId, renderJobId, steps.size());
        return plan;
    }

    /**
     * Creates a plan that includes MLT timeline rendering.
     *
     * @param renderJobId the render job identifier
     * @param profile     the render profile
     * @return the created render plan
     */
    public RenderPlan createMltPlan(String renderJobId, RenderProfile profile) {
        String planId = Ids.newId("rp");
        List<RenderStep> steps = List.of(
                RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.BUILD_TIMELINE),
                RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.MLT_RENDER_TIMELINE),
                RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.REGISTER_ARTIFACT)
        );
        RenderPlan plan = RenderPlan.create(planId, renderJobId, profile, steps);
        plans.put(planId, plan);
        return plan;
    }

    /**
     * Creates a plan that includes GPAC packaging.
     *
     * @param renderJobId  the render job identifier
     * @param profile      the render profile
     * @param packageHls   whether to include HLS packaging
     * @param packageDash  whether to include DASH packaging
     * @return the created render plan
     */
    public RenderPlan createPackagingPlan(String renderJobId, RenderProfile profile,
            boolean packageHls, boolean packageDash) {
        String planId = Ids.newId("rp");
        List<RenderStep> steps = new ArrayList<>();
        steps.add(RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.BUILD_TIMELINE));
        steps.add(RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.FFMPEG_TRANSCODE));
        if (packageHls) {
            steps.add(RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.GPAC_PACKAGE_HLS));
        }
        if (packageDash) {
            steps.add(RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.GPAC_PACKAGE_DASH));
        }
        steps.add(RenderStep.pending(Ids.newId("rs"), planId, RenderStepType.REGISTER_ARTIFACT));
        RenderPlan plan = RenderPlan.create(planId, renderJobId, profile, steps);
        plans.put(planId, plan);
        return plan;
    }

    /**
     * Finds a plan by its ID.
     */
    public Optional<RenderPlan> findById(String planId) {
        return Optional.ofNullable(plans.get(planId));
    }

    /**
     * Saves an updated plan.
     */
    public RenderPlan save(RenderPlan plan) {
        plans.put(plan.id(), plan);
        return plan;
    }

    /**
     * Returns all plans for a given render job.
     */
    public List<RenderPlan> findByRenderJobId(String renderJobId) {
        return plans.values().stream()
                .filter(p -> p.renderJobId().equals(renderJobId))
                .toList();
    }
}
