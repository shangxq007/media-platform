package com.example.platform.execution.taskgraph;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import java.util.Objects;
import java.util.Optional;

/** Render-neutral execution input identity exposed to downstream runtime modules. */
public record ExecutableInputProjection(
        ExecutionInputId inputId,
        ExecutionStepId consumerStepId,
        Optional<ExecutionStepId> producerStepId,
        SourceArtifactPresence sourceArtifactPresence) {

    public ExecutableInputProjection {
        Objects.requireNonNull(inputId, "inputId");
        Objects.requireNonNull(consumerStepId, "consumerStepId");
        Objects.requireNonNull(producerStepId, "producerStepId");
        Objects.requireNonNull(sourceArtifactPresence, "sourceArtifactPresence");
    }

    static ExecutableInputProjection from(InputBinding input) {
        Objects.requireNonNull(input, "input");
        return new ExecutableInputProjection(
                input.inputId(),
                input.consumerStepId(),
                Optional.ofNullable(input.producerStepId()),
                input.sourceArtifact() == null
                        ? SourceArtifactPresence.ABSENT
                        : SourceArtifactPresence.PRESENT);
    }

    /** Bounded indication only; the render-domain source artifact is deliberately absent. */
    public enum SourceArtifactPresence {
        ABSENT,
        PRESENT
    }
}
