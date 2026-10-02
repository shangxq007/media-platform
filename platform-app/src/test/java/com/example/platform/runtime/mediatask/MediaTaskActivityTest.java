package com.example.platform.runtime.mediatask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.execution.binding.BoundGraphDigestMismatchException;
import com.example.platform.execution.binding.BoundGraphInputStore;
import com.example.platform.execution.binding.BoundGraphInputs;
import com.example.platform.execution.binding.BoundGraphReference;
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
import com.example.platform.execution.taskgraph.ExecutableTaskGraphDigest;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P2-5b-1-R2: the preparation half of the media task activity — load, re-derive, digest-verify —
 * is fail-closed and never repairs.
 */
class MediaTaskActivityTest {

    private static final String TENANT = "tenant-a";
    private static final String JOB = "job-1";

    @Test
    void prepareTaskLoadsRederivesAndVerifiesTheDigest() {
        BoundGraphInputs inputs = boundInputs();
        MediaTaskActivity activity = new MediaTaskActivity(new FixedStore(JOB, inputs));

        PreparedTask prepared = activity.prepareTask(
                reference(inputs.expectedExecutableTaskGraphDigest().sha256Hex()), TENANT);

        assertEquals(inputs.expectedExecutableTaskGraphDigest(),
                prepared.executableTaskGraphDigest());
        assertEquals(prepared.executableTaskGraphDigest(),
                prepared.executableTaskGraph().digest());
        assertEquals(1, prepared.executableTaskGraph().tasks().size());
        assertEquals(1, prepared.executableTaskGraph().topologicalTaskOrder().size());
        assertEquals(inputs, prepared.boundGraphInputs());
    }

    @Test
    void prepareTaskFailsClosedWhenTheReferenceDigestDisagreesWithTheDerivedGraph() {
        BoundGraphInputs inputs = boundInputs();
        MediaTaskActivity activity = new MediaTaskActivity(new FixedStore(JOB, inputs));

        BoundGraphReference tampered = reference("0".repeat(64));

        assertThrows(BoundGraphDigestMismatchException.class,
                () -> activity.prepareTask(tampered, TENANT));
    }

    @Test
    void prepareTaskFailsClosedWhenTheStoredExpectationDisagreesWithTheDerivedGraph() {
        BoundGraphInputs inputs = boundInputs();
        BoundGraphInputs inconsistent = new BoundGraphInputs(
                inputs.physicalPlan(),
                inputs.candidates(),
                inputs.transitionDeclarations(),
                new ExecutableTaskGraphDigest("b".repeat(64)));
        MediaTaskActivity activity = new MediaTaskActivity(new FixedStore(JOB, inconsistent));

        assertThrows(BoundGraphDigestMismatchException.class,
                () -> activity.prepareTask(reference("b".repeat(64)), TENANT));
    }

    @Test
    void prepareTaskFailsClosedWhenTheTenantScopeDoesNotMatch() {
        MediaTaskActivity activity = new MediaTaskActivity(new FixedStore(JOB, boundInputs()));
        BoundGraphReference reference = reference("a".repeat(64));

        assertThrows(IllegalArgumentException.class,
                () -> activity.prepareTask(reference, "tenant-b"));
        assertThrows(IllegalArgumentException.class, () -> activity.prepareTask(reference, " "));
    }

    @Test
    void prepareTaskFailsClosedWhenNoRecordExistsForTheReference() {
        MediaTaskActivity activity = new MediaTaskActivity(new FixedStore("other-job", boundInputs()));

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> activity.prepareTask(reference("a".repeat(64)), TENANT));
        assertTrue(failure.getMessage().contains("no bound-graph inputs"));
    }

    // ---------- fixture ----------

    private static BoundGraphReference reference(String expectedEtgDigest) {
        return new BoundGraphReference(TENANT, JOB, "render-binding-inputs/" + TENANT + "/" + JOB,
                "a".repeat(64), expectedEtgDigest);
    }

    private static BoundGraphInputs boundInputs() {
        PhysicalExecutionPlan plan = plan();
        ProviderCandidate candidate = candidate("provider-a");
        var bound = new ProviderBindingEntryService()
                .bind(plan, List.of(candidate), List.of())
                .executableTaskGraph();
        return new BoundGraphInputs(plan, List.of(candidate), List.of(), bound.digest());
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

    /** Test double: no database, no codec — the store contract only. */
    private static final class FixedStore implements BoundGraphInputStore {

        private final String expectedJobId;
        private final BoundGraphInputs inputs;

        private FixedStore(String expectedJobId, BoundGraphInputs inputs) {
            this.expectedJobId = expectedJobId;
            this.inputs = inputs;
        }

        @Override
        public BoundGraphReference save(BoundGraphInputs inputs, String tenantId, String renderJobId) {
            throw new UnsupportedOperationException("read-only test double");
        }

        @Override
        public BoundGraphInputs load(BoundGraphReference reference) {
            if (!expectedJobId.equals(reference.renderJobId())) {
                throw new IllegalArgumentException(
                        "no bound-graph inputs stored for " + reference.planRef());
            }
            return inputs;
        }
    }
}
