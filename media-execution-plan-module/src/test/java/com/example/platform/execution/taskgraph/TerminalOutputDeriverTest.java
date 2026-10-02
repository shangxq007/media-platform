package com.example.platform.execution.taskgraph;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.execution.binding.ProviderBindingEntryService;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
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
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.render.domain.renderplan.LogicalArtifactId;
import com.example.platform.render.domain.renderplan.RenderArtifactReference.IntermediateArtifactExpectation;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * TERMINAL-OUTPUT-DERIVER-001: the plan-driven derivation of a graph's terminal POST_EXECUTION
 * semantics, and its effect on the binding entry's authoritative outputs.
 */
class TerminalOutputDeriverTest {

    @Test
    void aSingleUnitGraphMakesItsDeclaredTerminalOutputAuthoritative() {
        PhysicalExecutionPlan plan = plan(List.of(unit("unit-only", true)));

        ProviderBoundExecutableTaskGraph graph = new ProviderBindingEntryService()
                .bind(plan, List.of(candidate()), List.of())
                .executableTaskGraph();

        ExecutableTask task = graph.tasks().getFirst();
        assertThat(task.authoritativeOutputIds())
                .containsExactly(new ExecutionOutputId("output-unit-only"));
        assertThat(task.boundaryActions()).hasSize(1);
        assertThat(task.boundaryActions().getFirst().phase())
                .isEqualTo(BoundaryAction.Phase.POST_EXECUTION);
        assertThat(task.boundaryActions().getFirst().deterministicOrder()).isZero();
    }

    @Test
    void aTerminalOutputWithoutADeclaredExpectationDerivesNoAction() {
        PhysicalExecutionPlan plan = plan(List.of(unit("unit-undeclared", false)));

        assertThat(TerminalOutputDeriver.derive(plan).get(new ExecutionStepId("unit-undeclared")))
                .isEmpty();
        ProviderBoundExecutableTaskGraph graph = new ProviderBindingEntryService()
                .bind(plan, List.of(candidate()), List.of())
                .executableTaskGraph();
        assertThat(graph.tasks().getFirst().authoritativeOutputIds()).isEmpty();
    }

    @Test
    void everyUnitWithoutAnInternalConsumerIsTerminal() {
        PhysicalExecutionPlan plan = plan(List.of(unit("unit-a", true), unit("unit-b", true)));

        assertThat(TerminalOutputDeriver.derive(plan))
                .containsOnlyKeys(new ExecutionStepId("unit-a"), new ExecutionStepId("unit-b"));
        assertThat(TerminalOutputDeriver.terminalActionsFor(unit("unit-b", true))).hasSize(1);
    }

    @Test
    void anEmptyPlanDerivesNothingAndTheDerivationIsDeterministic() {
        PhysicalExecutionPlan empty = plan(List.of());
        assertThat(TerminalOutputDeriver.derive(empty)).isEmpty();

        PhysicalExecutionPlan plan = plan(List.of(unit("unit-only", true)));
        assertThat(TerminalOutputDeriver.derive(plan))
                .isEqualTo(TerminalOutputDeriver.derive(plan));
    }

    // ---------- fixtures ----------

    private static PhysicalExecutionPlan plan(List<PhysicalPlanUnit> units) {
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("plan-terminal"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("fingerprint-terminal"),
                units,
                null,
                new PhysicalExecutionPlanDigest("digest-terminal"));
    }

    private static PhysicalPlanUnit unit(String stepId, boolean declaresTerminalExpectation) {
        List<IntermediateArtifactExpectation> expectations = declaresTerminalExpectation
                ? List.of(new IntermediateArtifactExpectation(
                        new LogicalArtifactId("logical-artifact-" + stepId),
                        RenderOutputRole.RENDER_MASTER))
                : List.of();
        return new PhysicalPlanUnit(
                new ExecutionStepId(stepId),
                "logical-" + stepId,
                new RenderNodeId("render-" + stepId),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(),
                List.of(new OutputDeclaration(
                        new ExecutionOutputId("output-" + stepId),
                        "logical-" + stepId,
                        new RenderNodeId("render-" + stepId),
                        List.of(), List.of(), expectations, List.of())),
                List.of(),
                null, null, List.of(), List.of(), null, true);
    }

    private static ProviderCandidate candidate() {
        ProviderId providerId = ProviderId.of("provider-terminal");
        ProviderImplementationId implementationId =
                ProviderImplementationId.of("provider-terminal.native");
        ProviderVersion version = ProviderVersion.of("1.0.0");
        ProviderExecutionContractVersion contractVersion = ProviderExecutionContractVersion.of(1, 0);
        ProviderCapabilityProfileVersionOrDigest profileReference =
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0));
        ProviderBindingPin binding = new ProviderBindingPin(
                providerId, implementationId, version, contractVersion, profileReference, List.of());
        return new ProviderCandidate(
                binding,
                new ProviderDescriptor(
                        providerId, implementationId, version, contractVersion, profileReference),
                new ProviderExecutionContract(
                        ProviderExecutionContractSchemaVersion.of(1), contractVersion, List.of()),
                new ProviderCapabilityProfile(profileReference, List.of()),
                new ProviderStaticCompatibility(
                        ProviderStaticCompatibility.Knowledge.DECLARED,
                        List.of(ProviderStaticCompatibility.ArtifactRequirementKind.INTERMEDIATE_OUTPUT),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        ProviderStaticCompatibility.LoweringSupport.SUPPORTED));
    }
}
