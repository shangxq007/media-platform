package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.internal.IncrementalTask;
import com.example.platform.render.domain.timeline.internal.RenderImpactResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Human-readable explanation of incremental render impact and suggested tasks.
 */
@Service
public class IncrementalPlanExplainer {

    public Map<String, Object> explain(RenderImpactResult impact) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (impact == null) {
            body.put("summary", "No impact result");
            return body;
        }
        body.put("fullReRenderRequired", impact.fullReRenderRequired());
        body.put("dirtyScopes", impact.dirtyScopes().stream().map(Enum::name).toList());
        body.put("dirtyEntityCount", impact.dirtyEntities().size());
        body.put("reusableArtifactHints", impact.reusableArtifactHints());
        body.put("suggestedTaskCount", impact.suggestedTasks().size());
        body.put("tasks", impact.suggestedTasks().stream().map(this::taskMap).toList());
        if (impact.diff() != null) {
            body.put("changeCount", impact.diff().changes().size());
            body.put("oldRevision", impact.diff().oldRevision());
            body.put("newRevision", impact.diff().newRevision());
            body.put("changes", impact.diff().changes().stream()
                    .map(c -> Map.of(
                            "type", c.type().name(),
                            "entity", c.entity() != null ? c.entity().key() : "",
                            "summary", c.summary()))
                    .toList());
        }
        body.put("summary", buildSummary(impact));
        return body;
    }

    private Map<String, Object> taskMap(IncrementalTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("taskId", t.taskId());
        m.put("type", t.type());
        m.put("target", t.targetEntityKey());
        m.put("dependsOn", t.dependsOn());
        m.put("parameters", t.parameters());
        return m;
    }

    private String buildSummary(RenderImpactResult impact) {
        if (impact.fullReRenderRequired()) {
            return "Full timeline re-render required (timebase or structural breaking change).";
        }
        if (impact.dirtyScopes().isEmpty()) {
            return "No dirty scopes — safe to reuse all cached artifacts.";
        }
        if (impact.dirtyScopes().size() == 1
                && impact.dirtyScopes().iterator().next().name().equals("PACKAGING")) {
            return "Packaging-only change — reuse mezzanine, re-run packager.";
        }
        if (impact.dirtyScopes().stream().anyMatch(s -> s.name().equals("STEM"))
                && !impact.dirtyScopes().stream().anyMatch(s -> s.name().equals("LAYER"))) {
            return "Audio stem change — re-mix audio and remux; video segments likely reusable.";
        }
        return "Incremental render: " + impact.dirtyScopes().size() + " scope(s), "
                + impact.suggestedTasks().size() + " suggested task(s).";
    }
}
