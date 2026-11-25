package com.example.platform.config.render;

import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.ProviderSelectionPolicy;
import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.planning.ExecutionPlanningEntry;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalPlanningEntryService;
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import com.example.platform.providerplugin.ProviderCandidateProjection;
import com.example.platform.providerplugin.ProviderPluginCatalog;
import com.example.platform.render.app.renderplan.RenderPlanningEntryService;
import com.example.platform.render.domain.renderplan.RenderPlanningDiagnosticCode;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries;
import com.example.platform.timeline.api.revision.TimelineRevisionCommands;
import com.example.platform.timeline.api.revision.TimelineRevisionQueries.RevisionSnapshotPayload;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.execution.binding.ProviderBindingException;
import com.example.platform.execution.planning.ExecutionPlanningException;
import com.example.platform.render.api.GraphRenderLifecycle.PreparationStage;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.timeline.canonical.TimelineDocument;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

/**
 * Revision-pinned RenderJob planning-and-binding coordinator.
 *
 * <p>Consumes the Phase 3 {@link RevisionRenderPlanningApplicationService} result and drives the typed
 * chain #20 → #21 → #22 to produce the complete {@link BoundGraphInputs}:
 *
 * <pre>
 *   RevisionRenderPlanningResult
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
public class RevisionRenderPlanningAndBindingService {

    private final com.example.platform.render.api.GraphRenderLifecycle lifecycle;
    private final RevisionRenderPlanningApplicationService planningApplicationService;
    private final TimelineRevisionCommands timelineRevisionCommands;
    private final TimelineRevisionQueries timelineRevisionQueries;
    private final EffectSemanticSnapshotStore effectSemanticSnapshotStore;
    private final ProviderPluginCatalog providerPluginCatalog;
    private final com.example.platform.artifact.app.ArtifactSourceFactsQuery sourceFacts;
    private final RenderPlanningEntryService renderPlanningEntryService;
    private final PhysicalPlanningEntryService physicalPlanningEntryService;
    private final ProviderBindingEntryService providerBindingEntryService;

    /** Internal planning input: the selected media provider is persisted in each bound task pin. */
    @Value("${platform.render.media-provider:}")
    private String mediaProvider = "";

    public RevisionRenderPlanningAndBindingService(
            RevisionRenderPlanningApplicationService planningApplicationService,
            TimelineRevisionCommands timelineRevisionCommands,
            TimelineRevisionQueries timelineRevisionQueries,
            EffectSemanticSnapshotStore effectSemanticSnapshotStore,
            ProviderPluginCatalog providerPluginCatalog,
            RenderPlanningEntryService renderPlanningEntryService,
            PhysicalPlanningEntryService physicalPlanningEntryService,
            ProviderBindingEntryService providerBindingEntryService,
            com.example.platform.render.api.GraphRenderLifecycle lifecycle,
            com.example.platform.artifact.app.ArtifactSourceFactsQuery sourceFacts) {
        this.sourceFacts = Objects.requireNonNull(sourceFacts, "sourceFacts");
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
    public RevisionRenderPlanningAndBindingResult planAndBind(RevisionRenderPlanningRequest request) {
        Objects.requireNonNull(request, "request");
        String tenantId = request.tenantId();
        String revisionId = request.revisionId();

        // Phase 3: input + context construction (unchanged API).
        RevisionRenderPlanningResult planning = planningApplicationService.prepare(request);

        var stage = new java.util.concurrent.atomic.AtomicReference<>(PreparationStage.SNAPSHOT);
        try {
        lifecycle.preparationStage(tenantId, planning.renderJobId(), stage.get());
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
        var document = TimelineDocumentJsonSerializer.deserialize(payload.canonicalTimelineJson());
        TimelineRevision hydratedRevision = revision.hydrate(document);

        var inputs = new RevisionRenderPlanningApplicationService.PlanningInputs(planning.renderRequest(),
                planning.executionPlanId(), planning.sourceResolution(), planning.capabilityContext(), planning.declarations());
        BoundGraphInputs boundGraphInputs = bind(tenantId, projectId, hydratedRevision, document, inputs,
                next -> {
                    stage.set(next);
                    lifecycle.preparationStage(tenantId, planning.renderJobId(), next);
                });
        return new RevisionRenderPlanningAndBindingResult(
                tenantId, planning.renderJobId(), planning.renderRequest().id(), boundGraphInputs);
        } catch (RuntimeException preparationFailure) {
            try { lifecycle.failPreparation(tenantId, planning.renderJobId(), stage.get()); }
            catch (RuntimeException projectionFailure) { preparationFailure.addSuppressed(projectionFailure); }
            if (preparationFailure instanceof com.example.platform.execution.binding.ProviderBindingException) {
                throw new IllegalArgumentException("No supported execution for the requested media requirements and exact output raster",
                        preparationFailure);
            }
            throw preparationFailure;
        }
    }
    private BoundGraphInputs bind(String tenantId, String projectId, TimelineRevision hydratedRevision,
            TimelineDocument document, RevisionRenderPlanningApplicationService.PlanningInputs planning,
            java.util.function.Consumer<PreparationStage> onStage) {
        // The pinned Effect semantic snapshot (loaded by the revision's own exact pin).
        EffectSemanticSnapshotReference pin = hydratedRevision.effectSemanticSnapshotReference();
        EffectSemanticSnapshot pinnedEffectSnapshot = effectSemanticSnapshotStore
                .findById(projectId, tenantId, pin.snapshotId())
                .orElseThrow(() -> new IllegalStateException(
                        "Pinned effect semantic snapshot unavailable: " + pin.snapshotId()));

        onStage.accept(PreparationStage.PLANNING);
        // #20 logical render planning.
        RenderPlanningResult renderPlanningResult = renderPlanningEntryService.plan(
                hydratedRevision,
                pinnedEffectSnapshot,
                planning.renderRequest(),
                planning.sourceResolution(),
                planning.capabilityContext());
        var rasterRejection = renderPlanningResult.diagnostics().stream()
                .filter(diagnostic -> diagnostic.code() == RenderPlanningDiagnosticCode.OUTPUT_RASTER_ASPECT_MISMATCH
                        || diagnostic.code() == RenderPlanningDiagnosticCode.OUTPUT_RASTER_ASPECT_UNSUPPORTED)
                .findFirst();
        if (rasterRejection.isPresent()) {
            throw new SemanticRejection(rasterRejection.get().code().name(), rasterRejection.get().message());
        }

        // #21 provider-neutral execution planning.
        ExecutionPlanningEntry.PlanningResult planningResult = physicalPlanningEntryService.plan(
                renderPlanningResult, planning.executionPlanId());
        PhysicalExecutionPlan physicalPlan = planningResult.physicalExecutionPlan();

        onStage.accept(PreparationStage.BINDING);
        RenderSourceArtifacts.requireExecutableStreams(document, renderPlanningResult.graph(), tenantId, projectId, sourceFacts);
        // Declared provider candidates from the catalog projection.
        List<ProviderCandidate> candidates = ProviderCandidateProjection.project(providerPluginCatalog);
        ProviderSelectionPolicy policy = selectionPolicy();

        // #22 provider binding (pure): the digest comes only from the real binding result.
        ProviderBindingEntryService.ProviderBindingOutcome outcome = providerBindingEntryService.bind(
                physicalPlan, candidates, planning.declarations(), policy);
        ExecutableTaskGraphDigest expectedDigest = outcome.executableTaskGraph().digest();

        return new BoundGraphInputs(
                physicalPlan, candidates, planning.declarations(), expectedDigest, policy);
    }

    public record StaticFeasibility(String revisionId, String contentHash, boolean feasible, String reason,
            String logicalFingerprint, List<String> requiredCapabilities) {}

    public StaticFeasibility feasibility(String tenantId, String projectId, String revisionId,
            String contentHash, RenderExtent extent) {
        var revision = timelineRevisionCommands.findById(tenantId, revisionId);
        if (revision == null || !projectId.equals(revision.productId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Revision unavailable");
        }
        if (!revision.semanticContext().timelineContentDigest().equals(contentHash)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "Revision content hash mismatch");
        }
        var payload = timelineRevisionQueries.getRevisionSnapshotPayload(projectId, tenantId, revisionId)
                .orElseThrow(() -> new IllegalStateException("Revision snapshot unavailable"));
        var document = TimelineDocumentJsonSerializer.deserialize(payload.canonicalTimelineJson());
        var inputs = planningApplicationService.planningInputs(tenantId, payload, extent);
        try {
            var bound = bind(tenantId, projectId, revision.hydrate(document), document, inputs, ignored -> {});
            return new StaticFeasibility(revisionId, contentHash, true, "STATICALLY_FEASIBLE",
                    bound.physicalPlan().planFingerprint().sha256Hex(), bound.physicalPlan().units().stream()
                            .flatMap(unit -> unit.capabilityRequirementRefs().stream())
                            .filter(ref -> ref.declaration().required())
                            .map(ref -> ref.declaration().capabilityId().value()).distinct().sorted().toList());
        } catch (ProviderBindingException rejected) {
            return new StaticFeasibility(revisionId, contentHash, false, rejected.reason().name(), null, List.of());
        } catch (ExecutionPlanningException rejected) {
            return new StaticFeasibility(revisionId, contentHash, false, rejected.reason().name(), null, List.of());
        } catch (SemanticRejection rejected) {
            return new StaticFeasibility(revisionId, contentHash, false, rejected.code, null, List.of());
        }
    }

    static final class SemanticRejection extends IllegalArgumentException {
        final String code;
        SemanticRejection(String code, String message) {
            super(message);
            this.code = code;
        }
    }

    private ProviderSelectionPolicy selectionPolicy() {
        return mediaProvider.isEmpty() ? ProviderSelectionPolicy.PREFERRED : ProviderSelectionPolicy.restrictedTo(mediaProvider);
    }

    public record RasterLimits(int minWidth, int minHeight, int maxWidth, int maxHeight,
            int maxPixels, int alignment) {}

    public List<RasterLimits> outputRasters() {
        var required = selectionPolicy().requiredProvider();
        return ProviderCandidateProjection.project(providerPluginCatalog).stream()
                .filter(c -> required.map(c.bindingPin().providerId()::equals).orElse(true))
                .filter(c -> c.capabilityProfile().supportDeclarations().stream()
                        .anyMatch(d -> d.capabilityId().value().equals("render.output")))
                .flatMap(c -> c.staticCompatibility().mediaLowering().outputRasters().stream())
                .map(p -> new RasterLimits(p.minWidth(), p.minHeight(), p.maxWidth(), p.maxHeight(), p.maxPixels(), p.alignment()))
                .distinct().toList();
    }

}
