package com.example.platform.execution.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration.Declaration;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link ProviderBindingEntryService} — the typed-chain #22
 * production entry. Verifies candidate binding, per-unit executable task
 * creation, derived inter-task Artifact boundaries, and typed fail-closed
 * behaviour when the plan is un-bindable.
 *
 * <p>Provider candidates here are TEST-ONLY declarations (no production provider
 * declares static compatibility yet); they exist solely to exercise the entry.
 */
class ProviderBindingEntryServiceTest {

    private static final BoundaryContractId BOUNDARY_CONTRACT = BoundaryContractId.of("test.boundary.v1");

    private final ProviderBindingEntryService service = new ProviderBindingEntryService();

    // ---------- fixtures ----------

    private static PhysicalExecutionPlan singleUnitPlan() {
        PhysicalPlanUnit unit = unit("unit-a", List.of(), List.of(output()), List.of(), List.of());
        return plan(unit);
    }

    private static PhysicalExecutionPlan dependentPlan() {
        LogicalDependencyEdge edge = new LogicalDependencyEdge(
                new ExecutionEdgeId("edge-a-b"),
                "logical-unit-a", "logical-unit-b",
                new RenderNodeId("render-unit-a"), new RenderNodeId("render-unit-b"),
                new RenderDependency.DecodedFrames());
        PhysicalPlanUnit producer = unit(
                "unit-a", List.of(), List.of(output()), List.of(edge), List.of());
        InputBinding input = new InputBinding(
                new ExecutionInputId("input-a-b"),
                "logical-unit-b",
                new ExecutionStepId("unit-b"),
                new RenderNodeId("render-unit-b"),
                "logical-unit-a",
                new ExecutionStepId("unit-a"),
                new RenderNodeId("render-unit-a"),
                edge.dependencyVariant(),
                null,
                null);
        PhysicalPlanUnit consumer = unit(
                "unit-b", List.of(input), List.of(output()), List.of(edge), List.of());
        return plan(producer, consumer);
    }

    private static PhysicalPlanUnit unit(
            String id,
            List<InputBinding> inputs,
            List<OutputDeclaration> outputs,
            List<LogicalDependencyEdge> dependencies,
            List<com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef> capabilities) {
        return new PhysicalPlanUnit(
                new ExecutionStepId(id),
                "logical-" + id,
                new RenderNodeId("render-" + id),
                new RenderNodeKind.Decode(),
                "decode",
                inputs,
                outputs,
                dependencies,
                null,
                null,
                capabilities,
                List.of(),
                null,
                true);
    }

    private static OutputDeclaration output() {
        return new OutputDeclaration(
                new ExecutionOutputId("output-1"),
                "logical-unit-a",
                new RenderNodeId("render-unit-a"),
                List.of(), List.of(), List.of(), List.of());
    }

    private static PhysicalExecutionPlan plan(PhysicalPlanUnit... units) {
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("binding-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("binding-fingerprint"),
                List.of(units),
                null,
                new PhysicalExecutionPlanDigest("declared-digest"));
    }

    private static ProviderCandidate candidate(String provider) {
        ProviderId providerId = ProviderId.of(provider);
        ProviderImplementationId implementationId = ProviderImplementationId.of(provider + ".native");
        ProviderVersion version = ProviderVersion.of("1.0.0");
        ProviderExecutionContractVersion contractVersion = ProviderExecutionContractVersion.of(1, 0);
        ProviderCapabilityProfileVersionOrDigest profileReference =
                ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0));
        ProviderBindingPin binding = new ProviderBindingPin(
                providerId, implementationId, version, contractVersion, profileReference, List.of());
        ProviderDescriptor descriptor = new ProviderDescriptor(
                providerId, implementationId, version, contractVersion, profileReference);
        ProviderExecutionContract contract = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1), contractVersion, List.of());
        ProviderCapabilityProfile profile = new ProviderCapabilityProfile(profileReference, List.of());
        ProviderStaticCompatibility staticCompatibility = new ProviderStaticCompatibility(
                ProviderStaticCompatibility.Knowledge.DECLARED,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(BOUNDARY_CONTRACT),
                ProviderStaticCompatibility.LoweringSupport.SUPPORTED);
        return new ProviderCandidate(binding, descriptor, contract, profile, staticCompatibility);
    }

    private static ProviderCandidate unknownCandidate(String provider) {
        ProviderCandidate declared = candidate(provider);
        return new ProviderCandidate(
                declared.bindingPin(),
                declared.descriptor(),
                declared.executionContract(),
                declared.capabilityProfile(),
                ProviderStaticCompatibility.unknown());
    }

    private static ProviderBoundaryCompatibilityDeclaration declaration(
            PhysicalExecutionPlan plan, ProviderCandidate candidate, Declaration declaration) {
        LogicalDependencyEdge edge = plan.units().get(0).typedDependencies().getFirst();
        return new ProviderBoundaryCompatibilityDeclaration(
                edge, candidate.bindingPin(), candidate.bindingPin(), BOUNDARY_CONTRACT, declaration);
    }

    // ---------- tests ----------

    @Test
    void bindProducesOneTaskPerUnitWithTheExactBindingPin() {
        ProviderCandidate candidate = candidate("provider-a");

        ProviderBindingEntryService.ProviderBindingOutcome outcome =
                service.bind(singleUnitPlan(), List.of(candidate), List.of());

        assertNotNull(outcome.executableTaskGraph());
        assertEquals(1, outcome.tasks().size(), "one physical plan unit projects to one executable task");
        assertEquals(candidate.bindingPin(), outcome.tasks().getFirst().providerBindingPin(),
                "the task carries the exact declared provider binding pin");
        assertEquals(1, outcome.sourcePhysicalPlan().units().size());
        assertTrue(outcome.executableTaskGraph().executionArtifactBoundaries().isEmpty(),
                "a plan without dependencies needs no inter-task boundary");
        assertNotNull(outcome.executableTaskGraph().digest());
    }

    @Test
    void bindDerivesInterTaskBoundaryForSameBindingDependency() {
        ProviderCandidate candidate = candidate("provider-a");

        ProviderBindingEntryService.ProviderBindingOutcome outcome =
                service.bind(dependentPlan(), List.of(candidate), List.of());

        assertEquals(2, outcome.tasks().size(), "both units are bound");
        assertEquals(1, outcome.executableTaskGraph().executionArtifactBoundaries().size(),
                "an inter-task transfer without a direct-interoperability declaration materializes");
        assertEquals(ExecutionArtifactBoundaryReason.INTER_TASK_RUNTIME_BOUNDARY_UNPROVEN,
                ExecutionArtifactBoundaryReason.of(
                        outcome.executableTaskGraph().executionArtifactBoundaries().getFirst()),
                "same binding uses the unproven inter-task runtime boundary reason");
        assertEquals(1, outcome.executableTaskGraph().taskDependencies().size(),
                "the cross-task dependency is retained");
    }

    @Test
    void bindProducesNoBoundaryForDeclaredDirectInteroperability() {
        PhysicalExecutionPlan plan = dependentPlan();
        ProviderCandidate candidate = candidate("provider-a");

        ProviderBindingEntryService.ProviderBindingOutcome outcome = service.bind(
                plan,
                List.of(candidate),
                List.of(declaration(plan, candidate, Declaration.DIRECT_INTEROPERABILITY_ALLOWED)));

        assertEquals(2, outcome.tasks().size());
        assertTrue(outcome.executableTaskGraph().executionArtifactBoundaries().isEmpty(),
                "a declared direct transition carries no materialization boundary");
    }

    @Test
    void bindIsDeterministic() {
        ProviderCandidate candidate = candidate("provider-a");

        ProviderBindingEntryService.ProviderBindingOutcome first =
                service.bind(dependentPlan(), List.of(candidate), List.of());
        ProviderBindingEntryService.ProviderBindingOutcome second =
                service.bind(dependentPlan(), List.of(candidate), List.of());

        assertEquals(first.executableTaskGraph().digest(), second.executableTaskGraph().digest());
        assertEquals(
                first.tasks().stream().map(task -> task.id()).toList(),
                second.tasks().stream().map(task -> task.id()).toList());
    }

    @Test
    void bindFailsClosedWhenNoCandidatesSupplied() {
        ProviderBindingException failure = assertThrows(ProviderBindingException.class,
                () -> service.bind(singleUnitPlan(), List.of(), List.of()));
        assertEquals(ProviderBindingException.Reason.NO_CANDIDATES, failure.reason());
    }

    @Test
    void bindFailsClosedWhenNoCandidateIsFeasible() {
        ProviderBindingException failure = assertThrows(ProviderBindingException.class,
                () -> service.bind(singleUnitPlan(), List.of(unknownCandidate("provider-a")), List.of()));
        assertEquals(ProviderBindingException.Reason.UNIT_UNBINDABLE, failure.reason());
    }

    @Test
    void bindFailsClosedWhenSeveralCandidatesAreFeasible() {
        ProviderBindingException failure = assertThrows(ProviderBindingException.class,
                () -> service.bind(
                        singleUnitPlan(),
                        List.of(candidate("provider-a"), candidate("provider-b")),
                        List.of()));
        assertEquals(ProviderBindingException.Reason.UNIT_AMBIGUOUS, failure.reason());
    }

    @Test
    void bindFailsClosedOnDeclaredIncompatibleTransition() {
        PhysicalExecutionPlan plan = dependentPlan();
        ProviderCandidate candidate = candidate("provider-a");

        ProviderBindingException failure = assertThrows(ProviderBindingException.class,
                () -> service.bind(
                        plan,
                        List.of(candidate),
                        List.of(declaration(plan, candidate, Declaration.INCOMPATIBLE))));
        assertEquals(ProviderBindingException.Reason.TRANSITION_INCOMPATIBLE, failure.reason());
    }

    @Test
    void bindRejectsNullArguments() {
        ProviderCandidate candidate = candidate("provider-a");
        assertThrows(NullPointerException.class, () -> service.bind(null, List.of(candidate), List.of()));
        assertThrows(NullPointerException.class, () -> service.bind(singleUnitPlan(), null, List.of()));
        assertThrows(NullPointerException.class, () -> service.bind(singleUnitPlan(), List.of(candidate), null));
    }

    /** Test-local view of the boundary materialization reason (keeps assertions explicit). */
    private enum ExecutionArtifactBoundaryReason {
        EXPLICIT_MATERIALIZATION_REQUIREMENT,
        PROVIDER_BINDING_CHANGE,
        INTER_TASK_RUNTIME_BOUNDARY_UNPROVEN;

        static ExecutionArtifactBoundaryReason of(
                com.example.platform.execution.taskgraph.ExecutionArtifactBoundary boundary) {
            return valueOf(boundary.reason().name());
        }
    }
}
