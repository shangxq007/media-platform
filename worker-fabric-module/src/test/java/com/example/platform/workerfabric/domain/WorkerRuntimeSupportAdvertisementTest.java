package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkerRuntimeSupportAdvertisementTest {

    private static final WorkerRuntimeId RUNTIME_ID = WorkerRuntimeId.of("runtime-ffmpeg-cpu");
    private static final RuntimeSupportIdentifier FFMPEG_TRANSCODE =
            RuntimeSupportIdentifier.of("ffmpeg.cpu.transcode.v1");

    @Test
    void advertisement_is_immutable_candidate_evidence_without_capacity_or_authority() {
        WorkerRuntimeSupportAdvertisement advertisement = advertisement(RUNTIME_ID);

        assertThat(advertisement.staticSupportEvidence()).containsOnlyKeys(FFMPEG_TRANSCODE);
        assertThat(Arrays.stream(WorkerRuntimeSupportAdvertisement.class.getRecordComponents())
                .map(RecordComponent::getName))
                .noneMatch(name -> name.matches("(?i).*(canRun|capacity|availability|reservation|usage).*"));
        assertThat(Arrays.stream(WorkerRuntimeSupportAdvertisement.class.getDeclaredMethods())
                .map(method -> method.getName()))
                .noneMatch(name -> name.matches("(?i).*(authorize|eligible|canRun|reserve).*"));
    }

    @Test
    void exact_runtime_and_provider_requirement_accepts_matching_candidate_evidence() {
        WorkerRuntimeDescriptor runtime = WorkerRuntimeDescriptor.local(
                RUNTIME_ID, RuntimeLifecycleKind.EPHEMERAL_TASK, PhysicalHostId.of("host-a"));
        WorkerRuntimeSupportRequirement requirement = new WorkerRuntimeSupportRequirement(
                TaskBTestFixture.scenario("provider-a", "unit-a").task().providerBindingPin(),
                RuntimeLifecycleKind.EPHEMERAL_TASK,
                FFMPEG_TRANSCODE);

        RuntimeSupportAdvertisementDecision decision = RuntimeSupportAdvertisementEvaluator.evaluate(
                runtime, Optional.of(advertisement(RUNTIME_ID)), Optional.of(requirement));

        assertThat(decision.acceptedAsCandidateEvidence()).isTrue();
        assertThat(decision.authorizesExecution()).isFalse();
        assertThat(decision.reason()).isEqualTo(RuntimeSupportAdvertisementReason.ACCEPTED_CANDIDATE_EVIDENCE);
    }

    @Test
    void missing_unsupported_and_runtime_mismatch_fail_closed() {
        WorkerRuntimeDescriptor runtime = WorkerRuntimeDescriptor.local(
                RUNTIME_ID, RuntimeLifecycleKind.EPHEMERAL_TASK, PhysicalHostId.of("host-a"));
        WorkerRuntimeSupportRequirement requirement = new WorkerRuntimeSupportRequirement(
                TaskBTestFixture.scenario("provider-a", "unit-a").task().providerBindingPin(),
                RuntimeLifecycleKind.EPHEMERAL_TASK,
                FFMPEG_TRANSCODE);

        assertThat(RuntimeSupportAdvertisementEvaluator.evaluate(
                runtime, Optional.empty(), Optional.of(requirement)).reason())
                .isEqualTo(RuntimeSupportAdvertisementReason.MISSING);
        assertThat(RuntimeSupportAdvertisementEvaluator.evaluate(
                runtime,
                Optional.of(new WorkerRuntimeSupportAdvertisement(
                        RUNTIME_ID, RuntimeLifecycleKind.EPHEMERAL_TASK, Map.of())),
                Optional.of(requirement)).reason())
                .isEqualTo(RuntimeSupportAdvertisementReason.UNSUPPORTED);
        assertThat(RuntimeSupportAdvertisementEvaluator.evaluate(
                runtime,
                Optional.of(advertisement(WorkerRuntimeId.of("runtime-foreign"))),
                Optional.of(requirement)).reason())
                .isEqualTo(RuntimeSupportAdvertisementReason.RUNTIME_MISMATCH);
    }

    @Test
    void advertisement_alone_never_replaces_provider_requirement() {
        WorkerRuntimeDescriptor runtime = WorkerRuntimeDescriptor.local(
                RUNTIME_ID, RuntimeLifecycleKind.EPHEMERAL_TASK, PhysicalHostId.of("host-a"));

        RuntimeSupportAdvertisementDecision decision = RuntimeSupportAdvertisementEvaluator.evaluate(
                runtime, Optional.of(advertisement(RUNTIME_ID)), Optional.empty());

        assertThat(decision.acceptedAsCandidateEvidence()).isFalse();
        assertThat(decision.authorizesExecution()).isFalse();
        assertThat(decision.reason()).isEqualTo(RuntimeSupportAdvertisementReason.REQUIREMENT_MISSING);
    }

    @Test
    void support_evidence_cannot_smuggle_mutable_runtime_authority() {
        for (String forbidden : List.of(
                "capacity", "availability", "reservation", "usage", "can-run")) {
            assertThatIllegalArgumentException().isThrownBy(
                    () -> new RuntimeSupportEvidence(forbidden, "claimed"));
        }
    }

    private static WorkerRuntimeSupportAdvertisement advertisement(WorkerRuntimeId runtimeId) {
        return new WorkerRuntimeSupportAdvertisement(
                runtimeId,
                RuntimeLifecycleKind.EPHEMERAL_TASK,
                Map.of(FFMPEG_TRANSCODE, new RuntimeSupportEvidence(
                        "provider-module", "ffmpeg-provider-module:v1")));
    }
}
