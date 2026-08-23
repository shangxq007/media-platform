package com.example.platform.execution.taskgraph;

import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import java.util.List;
import java.util.Objects;

/** Exact #21 mandatory materialization semantics retained by the ETG. */
public record MandatoryArtifactBoundary(
        ExecutionStepId producerUnitId,
        OutputDeclaration outputDeclaration,
        RenderMaterializationRequirement materializationRequirement,
        List<LogicalDependencyEdge> downstreamDependencies) {

    public MandatoryArtifactBoundary {
        Objects.requireNonNull(producerUnitId, "producerUnitId");
        Objects.requireNonNull(outputDeclaration, "outputDeclaration");
        Objects.requireNonNull(materializationRequirement, "materializationRequirement");
        Objects.requireNonNull(downstreamDependencies, "downstreamDependencies");
        if (!outputDeclaration.materializationRequirements().contains(materializationRequirement)) {
            throw new IllegalArgumentException("boundary must reference a declared materialization");
        }
        downstreamDependencies = List.copyOf(downstreamDependencies);
        downstreamDependencies.forEach(value -> Objects.requireNonNull(
                value, "downstreamDependencies element"));
    }
}
