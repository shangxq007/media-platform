package com.example.platform.execution.taskgraph;

import com.example.platform.render.domain.renderplan.RenderOutputRole;
import java.util.List;

/** Exact logical final-output declaration mapped through physical membership to its owning task. */
public final class GraphFinalOutput {
    private GraphFinalOutput() {}
    public static ExecutableTask requireRenderMaster(ProviderBoundExecutableTaskGraph graph) {
        var outputs=graph.sourcePhysicalPlan().units().stream().flatMap(u->u.typedOutputs().stream())
                .filter(o->o.finalArtifactExpectations().stream().anyMatch(f->f.role()==RenderOutputRole.RENDER_MASTER)).toList();
        if(outputs.size()!=1)throw new IllegalArgumentException("A render graph requires exactly one declared final render master");
        var owners=graph.tasks().stream().filter(t->t.authoritativeOutputIds().contains(outputs.getFirst().outputId())).toList();
        if(owners.size()!=1)throw new IllegalArgumentException("final graph output must have exactly one materializing task");
        return owners.getFirst();
    }
}
