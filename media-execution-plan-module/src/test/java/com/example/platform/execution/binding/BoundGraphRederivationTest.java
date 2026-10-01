package com.example.platform.execution.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import com.example.platform.execution.taskgraph.ProviderBoundExecutableTaskGraph;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Re-derivation of the provider-bound executable task graph from its durable
 * inputs: deterministic, digest-verified, and fail-closed on any mismatch.
 */
class BoundGraphRederivationTest {

    @Test
    void rederivationReproducesTheBindingDigestFromTheSameInputs() {
        BoundGraphInputs inputs = inputsForBoundPlan();

        ProviderBoundExecutableTaskGraph rederived = BoundGraphRederivation.rederive(inputs);

        assertThat(rederived.digest().sha256Hex())
                .isEqualTo(inputs.expectedExecutableTaskGraphDigest().sha256Hex());
        assertThat(rederived.tasks()).hasSize(inputs.physicalPlan().units().size());
        assertThat(rederived.sourcePhysicalPlan()).isEqualTo(inputs.physicalPlan());
    }

    @Test
    void rederivationIsDeterministicAcrossCalls() {
        BoundGraphInputs inputs = inputsForBoundPlan();

        String first = BoundGraphRederivation.rederive(inputs).digest().sha256Hex();
        String second = BoundGraphRederivation.rederive(inputs).digest().sha256Hex();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void digestMismatchFailsClosedWithBothDigests() {
        BoundGraphInputs trusted = inputsForBoundPlan();
        String tampered = "0".repeat(64);
        BoundGraphInputs altered = new BoundGraphInputs(
                trusted.physicalPlan(),
                trusted.candidates(),
                trusted.transitionDeclarations(),
                new ExecutableTaskGraphDigest(tampered));

        assertThatThrownBy(() -> BoundGraphRederivation.rederive(altered))
                .isInstanceOfSatisfying(BoundGraphDigestMismatchException.class, failure -> {
                    assertThat(failure.expectedDigest()).isEqualTo(tampered);
                    assertThat(failure.actualDigest())
                            .isEqualTo(trusted.expectedExecutableTaskGraphDigest().sha256Hex());
                });
    }

    @Test
    void rederivationRejectsNullInputs() {
        assertThatThrownBy(() -> BoundGraphRederivation.rederive(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void boundGraphReferenceCarriesOnlyIdentitiesAndCanonicalDigests() {
        BoundGraphReference reference = new BoundGraphReference(
                "tenant-1", "job-1", "render-binding-inputs/job-1",
                "a".repeat(64), "b".repeat(64));

        assertThat(reference.tenantId()).isEqualTo("tenant-1");
        assertThatThrownBy(() -> new BoundGraphReference(
                " ", "job-1", "ref", "a".repeat(64), "b".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new BoundGraphReference(
                "tenant-1", "job-1", "ref", "not-a-digest", "b".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("planDigest");
    }

    // ---------- fixture ----------

    private static BoundGraphInputs inputsForBoundPlan() {
        PhysicalExecutionPlan plan = plan();
        ProviderCandidate candidate = candidate("provider-a");
        ProviderBoundExecutableTaskGraph bound = new ProviderBindingEntryService()
                .bind(plan, List.of(candidate), List.of())
                .executableTaskGraph();
        return new BoundGraphInputs(
                plan, List.of(candidate), List.of(), bound.digest());
    }

    private static PhysicalExecutionPlan plan() {
        PhysicalPlanUnit unit = new PhysicalPlanUnit(
                new ExecutionStepId("unit-a"),
                "logical-unit-a",
                new RenderNodeId("render-unit-a"),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(),
                List.of(new OutputDeclaration(
                        new ExecutionOutputId("output-1"),
                        "logical-unit-a",
                        new RenderNodeId("render-unit-a"),
                        List.of(), List.of(), List.of(), List.of())),
                List.of(),
                null,
                null,
                List.of(),
                List.of(),
                null,
                true);
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("rederivation-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("rederivation-fingerprint"),
                List.of(unit),
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
        return new ProviderCandidate(
                binding,
                new ProviderDescriptor(providerId, implementationId, version, contractVersion, profileReference),
                new ProviderExecutionContract(
                        ProviderExecutionContractSchemaVersion.of(1), contractVersion, List.of()),
                new ProviderCapabilityProfile(profileReference, List.of()),
                new ProviderStaticCompatibility(
                        ProviderStaticCompatibility.Knowledge.DECLARED,
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                        ProviderStaticCompatibility.LoweringSupport.SUPPORTED));
    }
}
