package com.example.platform.config.firstrender;

import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.planning.ExecutionPlanningEntry;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalPlanningEntryService;
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import com.example.platform.providerplugin.ProviderCandidateProjection;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries.RevisionSnapshotPayload;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * First Render planning-and-binding coordinator (Phase 5).
 *
 * <p>Consumes the Phase 3 {@link FirstRenderPlanningApplicationService} result and drives the typed
 * chain #20 → #21 → #22 to produce the complete {@link BoundGraphInputs}:
 *
 * <pre>
 *   FirstRenderPlanningResult
 *     → full TimelineRevision hydration + pinned EffectSemanticSnapshot
 *     → #20 RenderPlanningEntryService.plan(hydratedRevision, pinnedSnapshot, request, resolution, capabilities)
 *     → #21 PhysicalPlanningEntryService.plan(renderResult, executionPlanId) → PhysicalExecutionPlan
 *     → ProviderCandidateProjection.project(catalog) → declared candidates
 *     → #22 ProviderBindingEntryService.bind(physicalPlan, candidates, declarations)
 *     → executableTaskGraph().digest() → expectedExecutableTaskGraphDigest
 *     → BoundGraphInputs(physicalPlan, candidates, declarations, digest)
 * </pre>
 *
 * <p>It does NOT persist the bound-graph inputs, does NOT construct a {@code BoundGraphReference},
 * and does NOT start any workflow — those are Phase 6 behaviour.
 */
@Service
public class FirstRenderPlanningAndBindingService {

    private final com.example.platform.render.api.GraphRenderLifecycle lifecycle;
    private final FirstRenderPlanningApplicationService planningApplicationService;
    private final TimelineRevisionCommands timelineRevisionCommands;
    private final TimelineRevisionQueries timelineRevisionQueries;
    private final EffectSemanticSnapshotStore effectSemanticSnapshotStore;
    private final ProviderPluginCatalog providerPluginCatalog;
    private final RenderPlanningEntryService renderPlanningEntryService;
    private final PhysicalPlanningEntryService physicalPlanningEntryService;
    private final ProviderBindingEntryService providerBindingEntryService;

    public FirstRenderPlanningAndBindingService(
            FirstRenderPlanningApplicationService planningApplicationService,
            TimelineRevisionCommands timelineRevisionCommands,
            TimelineRevisionQueries timelineRevisionQueries,
            EffectSemanticSnapshotStore effectSemanticSnapshotStore,
            ProviderPluginCatalog providerPluginCatalog,
            RenderPlanningEntryService renderPlanningEntryService,
            PhysicalPlanningEntryService physicalPlanningEntryService,
            ProviderBindingEntryService providerBindingEntryService,
            com.example.platform.render.api.GraphRenderLifecycle lifecycle) {
        this.lifecycle = Objects.requireNonNull(lifecycle, "lifecycle");
        this.planningApplicationService = Objects.requireNonNull(
                planningApplicationService, "planningApplicationService");
        this.timelineRevisionCommands = Objects.requireNonNull(
                timelineRevisionCommands, "timelineRevisionCommands");
        this.timelineRevisionQueries = Objects.requireNonNull(
                timelineRevisionQueries, "timelineRevisionQueries");
        this.effectSemanticSnapshotStore = Objects.requireNonNull(
                effectSemanticSnapshotStore, "effectSemanticSnapshotStore");
        this.providerPluginCatalog = Objects.requireNonNull(
                providerPluginCatalog, "providerPluginCatalog");
        this.renderPlanningEntryService = Objects.requireNonNull(
                renderPlanningEntryService, "renderPlanningEntryService");
        this.physicalPlanningEntryService = Objects.requireNonNull(
                physicalPlanningEntryService, "physicalPlanningEntryService");
        this.providerBindingEntryService = Objects.requireNonNull(
                providerBindingEntryService, "providerBindingEntryService");
    }

    /**
     * Runs Phase 3 preparation, then the full typed planning + binding chain, returning the complete
     * {@link BoundGraphInputs} without persisting it or starting a workflow.
     */
    public FirstRenderPlanningAndBindingResult planAndBind(FirstRenderPlanningRequest request) {
        Objects.requireNonNull(request, "request");
        String tenantId = request.tenantId();
        String revisionId = request.revisionId();

        // Phase 3: input + context construction (unchanged API).
        FirstRenderPlanningResult planning = planningApplicationService.prepare(request);

        var stage = com.example.platform.render.api.GraphRenderLifecycle.PreparationStage.SNAPSHOT;
        try {
        lifecycle.preparationStage(tenantId, planning.renderJobId(), stage);
        // Complete hydration for #20.
        TimelineRevision revision = timelineRevisionCommands.findById(tenantId, revisionId);
        if (revision == null) {
            throw new IllegalArgumentException("Timeline revision not found: " + revisionId);
        }
        String projectId = revision.productId();
        RevisionSnapshotPayload payload = timelineRevisionQueries
                .getRevisionSnapshotPayload(projectId, tenantId, revisionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Timeline revision snapshot unavailable: " + revisionId));
        TimelineRevision hydratedRevision = revision.hydrate(
                TimelineDocumentJsonSerializer.deserialize(payload.canonicalTimelineJson()));

        // The pinned Effect semantic snapshot (loaded by the revision's own exact pin).
        EffectSemanticSnapshotReference pin = hydratedRevision.effectSemanticSnapshotReference();
        EffectSemanticSnapshot pinnedEffectSnapshot = effectSemanticSnapshotStore
                .findById(projectId, tenantId, pin.snapshotId())
                .orElseThrow(() -> new IllegalStateException(
                        "Pinned effect semantic snapshot unavailable: " + pin.snapshotId()));

        stage = com.example.platform.render.api.GraphRenderLifecycle.PreparationStage.PLANNING;
        lifecycle.preparationStage(tenantId, planning.renderJobId(), stage);
        // #20 logical render planning.
        RenderPlanningResult renderPlanningResult = renderPlanningEntryService.plan(
                hydratedRevision,
                pinnedEffectSnapshot,
                planning.renderRequest(),
                planning.sourceResolution(),
                planning.capabilityContext());

        // #21 provider-neutral execution planning.
        ExecutionPlanningEntry.PlanningResult planningResult = physicalPlanningEntryService.plan(
                renderPlanningResult, planning.executionPlanId());
        PhysicalExecutionPlan physicalPlan = planningResult.physicalExecutionPlan();

        stage = com.example.platform.render.api.GraphRenderLifecycle.PreparationStage.BINDING;
        lifecycle.preparationStage(tenantId, planning.renderJobId(), stage);
        // Declared provider candidates from the catalog projection.
        List<ProviderCandidate> candidates = ProviderCandidateProjection.project(providerPluginCatalog);

        // #22 provider binding (pure): the digest comes only from the real binding result.
        ProviderBindingEntryService.ProviderBindingOutcome outcome = providerBindingEntryService.bind(
                physicalPlan, candidates, planning.declarations());
        ExecutableTaskGraphDigest expectedDigest = outcome.executableTaskGraph().digest();

        BoundGraphInputs boundGraphInputs = new BoundGraphInputs(
                physicalPlan, candidates, planning.declarations(), expectedDigest);
        return new FirstRenderPlanningAndBindingResult(
                tenantId, planning.renderJobId(), planning.renderRequest().id(), boundGraphInputs);
        } catch (RuntimeException preparationFailure) {
            try { lifecycle.failPreparation(tenantId, planning.renderJobId(), stage); }
            catch (RuntimeException projectionFailure) { preparationFailure.addSuppressed(projectionFailure); }
            throw preparationFailure;
        }
    }
}
