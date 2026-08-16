package com.example.platform.render.app;

import com.example.platform.render.domain.legacy.TimelineClip;
import com.example.platform.render.domain.legacy.TimelineClipEffect;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.legacy.TimelineTrack;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class EffectTimelineInspector {

    private final TimelineScriptParser timelineScriptParser;

    public EffectTimelineInspector(TimelineScriptParser timelineScriptParser) {
        this.timelineScriptParser = timelineScriptParser;
    }

    public EffectUsage extractFromScript(String script) {
        Optional<TimelineSpec> timeline = timelineScriptParser.parse(script);
        if (timeline.isEmpty()) {
            return new EffectUsage(List.of(), List.of());
        }
        Set<String> effectKeys = new LinkedHashSet<>();
        Set<String> packIds = new LinkedHashSet<>();
        if (timeline.get().tracks() != null) {
            for (TimelineTrack track : timeline.get().tracks()) {
                if (track.clips() == null) {
                    continue;
                }
                for (TimelineClip clip : track.clips()) {
                    if (clip.effects() == null) {
                        continue;
                    }
                    for (TimelineClipEffect effect : clip.effects()) {
                        if (effect.effectKey() != null && !effect.effectKey().isBlank()) {
                            effectKeys.add(effect.effectKey());
                        }
                        if (effect.packId() != null && !effect.packId().isBlank()) {
                            packIds.add(effect.packId());
                        }
                    }
                }
            }
        }
        return new EffectUsage(new ArrayList<>(effectKeys), new ArrayList<>(packIds));
    }

    public record EffectUsage(List<String> effectKeys, List<String> packIds) {}
}
