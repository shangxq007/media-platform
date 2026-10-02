package com.example.platform.providerplugin;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderFeasibilityView.UnitCandidates;
import com.example.platform.execution.compatibility.StaticProviderCompatibilityProof;
import com.example.platform.execution.composition.ExecutableTaskMembership;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.taskgraph.ExecutableTask;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.workerfabric.domain.BoundedV1ReservationFeasibility;
import com.example.platform.workerfabric.domain.PendingNativeWorkCandidate;
import com.example.platform.workerfabric.domain.PendingNativeWorkCandidate.ClaimState;
import com.example.platform.workerfabric.domain.ProviderBackendExecutionSupport;
import com.example.platform.workerfabric.domain.ProviderHardwareRequirement;
import com.example.platform.workerfabric.domain.ProviderProbeRequirement;
import com.example.platform.workerfabric.domain.RuntimeResourceDemand;
import com.example.platform.workerfabric.domain.TaskResourceDemandDeriver;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * P2-5b-2a-2a-3b: projects provider-bound executable tasks into the 13-field
 * {@link PendingNativeWorkCandidate} the Native Pull admission feed consumes.
 *
 * <p>Pure and stateless. Every value is taken from exactly two authorities and nothing is invented:
 * the bound graph (the task, and the Stage-1 {@link ProviderCandidate} owned by its feasibility
 * view) and the provider's own typed declarations. The catalog is deliberately not consulted — the
 * candidate a task was bound against travels with the durable bound-graph inputs, so a catalog feed
 * is not required to project a candidate.
 *
 * <p><b>Fail closed.</b> A missing provider declaration ({@code providerHardwareRequirement},
 * {@code providerBackendExecutionSupport}, {@code resourceProfile}) or a task whose binding pin has
 * no statically feasible candidate rejects the whole projection with
 * {@link PendingNativeWorkProjectionException}. Nothing is defaulted and no candidate is silently
 * skipped: an unprojectable task means the request is not admissible.
 *
 * <p>Bounded V1 policy values are explicit here rather than derived: {@code claimState} is
 * {@link ClaimState#PENDING} (a freshly projected candidate has no active lease) and
 * {@code providerProbeRequirement} is {@link ProviderProbeRequirement#NOT_REQUIRED} (bounded V1 has
 * no provider-probe mechanism), so {@code providerProbeResult} stays empty rather than synthetic.
 */
public final class PendingNativeWorkProjection {

    private PendingNativeWorkProjection() {
    }

    /**
     * Projects every task of the bound graph, in graph order.
     *
     * @throws PendingNativeWorkProjectionException if any task cannot be projected
     */
    public static List<PendingNativeWorkCandidate> projectAll(
            ProviderBoundExecutableTaskGraph graph,
            ProviderPluginContribution contribution) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(contribution, "contribution");
        List<PendingNativeWorkCandidate> candidates = new ArrayList<>(graph.tasks().size());
        for (ExecutableTask task : graph.tasks()) {
            candidates.add(project(task, graph, contribution));
        }
        return List.copyOf(candidates);
    }

    /**
     * Projects one task of the bound graph.
     *
     * @throws PendingNativeWorkProjectionException if a declaration is undeclared or the task's
     *     binding pin has no statically feasible candidate
     */
    public static PendingNativeWorkCandidate project(
            ExecutableTask task,
            ProviderBoundExecutableTaskGraph graph,
            ProviderPluginContribution contribution) {
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(contribution, "contribution");
        ProviderCandidate providerCandidate = requireStaticallyCompatibleCandidate(task, graph);
        ProviderHardwareRequirement hardwareRequirement = requireDeclared(
                contribution.providerHardwareRequirement(),
                "PROVIDER_HARDWARE_REQUIREMENT_UNDECLARED",
                "provider declares no hardware requirement");
        ProviderBackendExecutionSupport backendExecutionSupport = requireDeclared(
                contribution.providerBackendExecutionSupport(),
                "PROVIDER_BACKEND_EXECUTION_SUPPORT_UNDECLARED",
                "provider declares no backend execution support");
        RuntimeResourceDemand resourceDemand = requireDeclared(
                TaskResourceDemandDeriver.derive(contribution.resourceProfile()),
                "PROVIDER_RESOURCE_PROFILE_UNDECLARED",
                "provider declares no resource profile");
        return new PendingNativeWorkCandidate(
                graph,
                task,
                providerCandidate,
                hardwareRequirement,
                contribution.runtimeDependencyRequirements(),
                backendExecutionSupport,
                ClaimState.PENDING,
                resourceDemand,
                BoundedV1ReservationFeasibility.value(),
                contribution.sandboxRequirement(),
                Optional.of(Objects.requireNonNull(
                        contribution.workerRuntimeSupportRequirement(),
                        "workerRuntimeSupportRequirement")),
                ProviderProbeRequirement.NOT_REQUIRED,
                Optional.empty());
    }

    /**
     * Resolves the Stage-1 candidate this task was bound against from the graph's own feasibility
     * view. The candidate never comes from a catalog: a task's membership resolves to one physical
     * plan unit whose compatibility proofs own the exact feasible provider candidate.
     */
    private static ProviderCandidate requireStaticallyCompatibleCandidate(
            ExecutableTask task, ProviderBoundExecutableTaskGraph graph) {
        ProviderBindingPin bindingPin = task.providerBindingPin();
        List<PhysicalPlanUnit> taskUnits = task.memberships().stream()
                .map(ExecutableTaskMembership::physicalPlanUnit)
                .toList();
        for (UnitCandidates node : graph.providerFeasibilityView().unitCandidates()) {
            if (!taskUnits.contains(node.compatibilityRequest().physicalPlanUnit())) {
                continue;
            }
            for (StaticProviderCompatibilityProof proof : node.compatibilityProofs()) {
                ProviderCandidate candidate = proof.providerCandidate();
                if (candidate.bindingPin().equals(bindingPin)) {
                    return candidate;
                }
            }
        }
        throw new PendingNativeWorkProjectionException(
                "STATICALLY_COMPATIBLE_CANDIDATE_ABSENT",
                "no statically feasible candidate is bound to this task in the graph's "
                        + "feasibility view");
    }

    private static <T> T requireDeclared(Optional<T> declared, String code, String detail) {
        return declared.orElseThrow(() -> new PendingNativeWorkProjectionException(code, detail));
    }
}
