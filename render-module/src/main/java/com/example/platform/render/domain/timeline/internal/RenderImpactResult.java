package com.example.platform.render.domain.timeline.internal;

import java.util.List;
import java.util.Set;

public record RenderImpactResult(
        SemanticDiffResult diff,
        Set<DirtyScope> dirtyScopes,
        Set<EntityRef> dirtyEntities,
        List<String> reusableArtifactHints,
        List<IncrementalTask> suggestedTasks,
        boolean fullReRenderRequired) {

    public static RenderImpactResult fullRender(SemanticDiffResult diff, String reason) {
        return new RenderImpactResult(
                diff,
                Set.of(DirtyScope.FULL_TIMELINE),
                Set.of(),
                List.of(),
                List.of(IncrementalTask.fullRender(reason)),
                true);
    }
}
