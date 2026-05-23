package com.example.platform.render.infrastructure.natron;

import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link NatronPocJob} from timeline JSON for the configured POC effect key.
 */
@Component
public class NatronPocJobExtractor {

    private final TimelineScriptParser timelineScriptParser;

    public NatronPocJobExtractor(TimelineScriptParser timelineScriptParser) {
        this.timelineScriptParser = timelineScriptParser;
    }

    public Optional<NatronPocJob> extract(String script, Collection<String> natronEffectKeys,
                                          String storageRoot, String outputLocalPath) {
        if (natronEffectKeys == null || natronEffectKeys.isEmpty()) {
            return Optional.empty();
        }
        Optional<TimelineSpec> timeline = timelineScriptParser.parse(script);
        if (timeline.isEmpty()) {
            return Optional.empty();
        }

        TimelineClipEffect targetEffect = null;
        TimelineClip targetClip = null;

        for (TimelineTrack track : timeline.get().tracks()) {
            if (track.clips() == null) {
                continue;
            }
            for (TimelineClip clip : track.clips()) {
                if (clip.effects() == null) {
                    continue;
                }
                for (TimelineClipEffect effect : clip.effects()) {
                    if (natronEffectKeys.contains(effect.effectKey())) {
                        targetEffect = effect;
                        targetClip = clip;
                        break;
                    }
                }
                if (targetEffect != null) {
                    break;
                }
            }
            if (targetEffect != null) {
                break;
            }
        }

        if (targetEffect == null || targetClip == null || targetClip.assetRef() == null) {
            return Optional.empty();
        }

        String storageUri = targetClip.assetRef().storageUri();
        if (storageUri == null || storageUri.isBlank()) {
            return Optional.empty();
        }

        String inputPath = timelineScriptParser.resolveLocalPath(storageUri, storageRoot);
        if (!Files.isRegularFile(Path.of(inputPath))) {
            return Optional.empty();
        }

        return Optional.of(new NatronPocJob(
                targetEffect.effectKey(),
                inputPath,
                outputLocalPath,
                targetEffect.parameters() != null ? targetEffect.parameters() : java.util.Map.of()));
    }
}
