package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.internal.DirtyScope;
import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.example.platform.render.domain.timeline.internal.IncrementalTask;
import com.example.platform.render.domain.timeline.internal.RenderImpactResult;
import com.example.platform.render.domain.timeline.internal.SemanticChange;
import com.example.platform.render.domain.timeline.internal.SemanticChangeType;
import com.example.platform.render.domain.timeline.internal.SemanticDiffResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Propagates semantic changes to dirty scopes and suggests incremental render tasks.
 */
@Service
public class RenderImpactAnalyzer {

    public RenderImpactResult analyze(SemanticDiffResult diff) {
        if (diff == null || !diff.hasChanges()) {
            return new RenderImpactResult(
                    diff,
                    Set.of(),
                    Set.of(),
                    List.of("all artifacts reusable when no semantic changes"),
                    List.of(),
                    false);
        }

        if (onlyRevisionChange(diff)) {
            return new RenderImpactResult(
                    diff,
                    Set.of(),
                    Set.of(),
                    List.of("revision bump only — reuse all cached artifacts"),
                    List.of(),
                    false);
        }

        Set<DirtyScope> scopes = new LinkedHashSet<>();
        Set<EntityRef> dirtyEntities = new LinkedHashSet<>();
        List<String> reuseHints = new ArrayList<>();
        List<IncrementalTask> tasks = new ArrayList<>();
        boolean fullRender = false;

        for (SemanticChange change : diff.changes()) {
            if (change.entity() != null) {
                dirtyEntities.add(change.entity());
            }
            switch (change.type()) {
                case PROJECT_TIMEBASE_CHANGED -> {
                    fullRender = true;
                    scopes.add(DirtyScope.FULL_TIMELINE);
                }
                case PROJECT_RESOLUTION_CHANGED, PROJECT_COLOR_CHANGED -> {
                    scopes.add(DirtyScope.PROJECT);
                    scopes.add(DirtyScope.LAYER);
                    scopes.add(DirtyScope.SEGMENT);
                    scopes.add(DirtyScope.OUTPUT);
                }
                case ASSET_URI_CHANGED, ASSET_PROBE_CHANGED -> {
                    scopes.add(DirtyScope.ASSET);
                    scopes.add(DirtyScope.CLIP);
                    scopes.add(DirtyScope.SEGMENT);
                }
                case CLIP_ADDED, CLIP_REMOVED, CLIP_RANGE_CHANGED, CLIP_SPEED_CHANGED, CLIP_EFFECT_CHANGED -> {
                    scopes.add(DirtyScope.CLIP);
                    scopes.add(DirtyScope.SEGMENT);
                }
                case LAYER_ADDED, LAYER_REMOVED, LAYER_CONTENT_CHANGED, LAYER_TRANSFORM_CHANGED -> {
                    scopes.add(DirtyScope.LAYER);
                    scopes.add(DirtyScope.SEGMENT);
                }
                case SUBTITLE_CUE_CHANGED, SUBTITLE_STYLE_CHANGED -> {
                    scopes.add(DirtyScope.LAYER);
                    scopes.add(DirtyScope.SEGMENT);
                }
                case AUDIO_BUS_CHANGED, AUDIO_STEM_CHANGED -> {
                    scopes.add(DirtyScope.STEM);
                    reuseHints.add("video segments may remain reusable — remux after stem re-render");
                }
                case TRANSITION_CHANGED -> scopes.add(DirtyScope.TRANSITION);
                case EXTERNAL_NODE_CHANGED -> {
                    scopes.add(DirtyScope.LAYER);
                    scopes.add(DirtyScope.SEGMENT);
                }
                case OUTPUT_PROFILE_CHANGED -> scopes.add(DirtyScope.OUTPUT);
                case PACKAGING_PARAM_CHANGED -> {
                    scopes.add(DirtyScope.PACKAGING);
                    reuseHints.add("mezzanine encode artifact reusable — PACKAGE_ONLY");
                }
                case FINAL_COMPOSER_CHANGED -> {
                    scopes.add(DirtyScope.SEGMENT);
                    scopes.add(DirtyScope.OUTPUT);
                }
                case REVISION_ONLY -> { /* handled above */ }
                default -> scopes.add(DirtyScope.SEGMENT);
            }
        }

        if (fullRender) {
            return RenderImpactResult.fullRender(diff, "project timebase changed");
        }

        tasks.addAll(suggestTasks(diff, scopes, dirtyEntities));

        return new RenderImpactResult(
                diff,
                Set.copyOf(scopes),
                Set.copyOf(dirtyEntities),
                List.copyOf(reuseHints),
                List.copyOf(tasks),
                false);
    }

    private static boolean onlyRevisionChange(SemanticDiffResult diff) {
        return diff.changes().stream().allMatch(c -> c.type() == SemanticChangeType.REVISION_ONLY);
    }

    private List<IncrementalTask> suggestTasks(SemanticDiffResult diff,
                                                 Set<DirtyScope> scopes,
                                                 Set<EntityRef> dirtyEntities) {
        List<IncrementalTask> tasks = new ArrayList<>();
        int seq = 1;

        if (scopes.contains(DirtyScope.PACKAGING) && scopes.size() == 1) {
            tasks.add(new IncrementalTask("t" + seq++, "PACKAGE_ONLY", "packaging",
                    List.of(), java.util.Map.of("mode", "INCREMENTAL")));
            return tasks;
        }

        if (scopes.contains(DirtyScope.STEM) && !scopes.contains(DirtyScope.LAYER)
                && !scopes.contains(DirtyScope.CLIP)) {
            tasks.add(new IncrementalTask("t" + seq++, "AUDIO_MIX", "mix_final",
                    List.of(), java.util.Map.of("reuseVideo", "true")));
            tasks.add(new IncrementalTask("t" + seq++, "MUX", "output",
                    List.of("t" + (seq - 2)), java.util.Map.of()));
            return tasks;
        }

        for (EntityRef entity : dirtyEntities) {
            if (entity.kind() == com.example.platform.render.domain.timeline.internal.EntityKind.LAYER) {
                tasks.add(new IncrementalTask("t" + seq++, "RENDER_LAYER", entity.key(),
                        List.of(), java.util.Map.of(
                        "baseRevision", String.valueOf(diff.oldRevision()),
                        "targetRevision", String.valueOf(diff.newRevision()))));
            }
        }

        if (scopes.contains(DirtyScope.SEGMENT) || scopes.contains(DirtyScope.CLIP)) {
            tasks.add(new IncrementalTask("t" + seq++, "COMPOSE_SEGMENT", "segments",
                    tasks.stream().map(IncrementalTask::taskId).toList(),
                    java.util.Map.of()));
        }

        if (scopes.contains(DirtyScope.OUTPUT)) {
            tasks.add(new IncrementalTask("t" + seq++, "ENCODE_ONLY", "mezzanine",
                    List.of("t" + Math.max(1, seq - 2)), java.util.Map.of()));
        }

        if (scopes.contains(DirtyScope.PACKAGING)) {
            tasks.add(new IncrementalTask("t" + seq, "PACKAGE", "packaging",
                    List.of("t" + Math.max(1, seq - 1)), java.util.Map.of("backend", "shaka")));
        }

        if (tasks.isEmpty()) {
            tasks.add(new IncrementalTask("t1", "INCREMENTAL_PLACEHOLDER", "timeline",
                    List.of(), java.util.Map.of("changeCount", String.valueOf(diff.changes().size()))));
        }

        return tasks;
    }
}
