package com.example.platform.render.app.planner;

import com.example.platform.render.domain.timeline.ExternalRenderNode;
import com.example.platform.render.domain.timeline.FinalComposerHint;
import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.domain.timeline.TimelineExtensions;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTrack;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Chooses MLT vs FFmpeg for final timeline composition.
 */
@Component
public class FinalComposerSelector {

    @Value("${render.timeline.mlt-multitrack-min-tracks:2}")
    private int mltMultitrackMinTracks = 2;

    public FinalComposerHint resolve(TimelineSpec timeline, TimelineExtensions extensions) {
        if (extensions != null && extensions.finalComposer() != FinalComposerHint.AUTO) {
            return extensions.finalComposer();
        }
        if (countVideoTracks(timeline) >= mltMultitrackMinTracks) {
            return FinalComposerHint.MLT;
        }
        if (extensions != null && !extensions.externalRenderNodes().isEmpty()) {
            return FinalComposerHint.MLT;
        }
        if (hasAlphaExternalLayers(extensions)) {
            return FinalComposerHint.MLT;
        }
        if (hasCrossDissolveOrComplexTransition(timeline)) {
            return FinalComposerHint.MLT;
        }
        if (hasMultitrackAudioMix(timeline)) {
            return FinalComposerHint.MLT;
        }
        return FinalComposerHint.FFMPEG;
    }

    public String backendKey(FinalComposerHint hint) {
        return hint == FinalComposerHint.MLT ? "mlt" : "ffmpeg";
    }

    private int countVideoTracks(TimelineSpec timeline) {
        if (timeline.tracks() == null) {
            return 0;
        }
        return (int) timeline.tracks().stream()
                .filter(t -> t.type() == TimelineTrack.TrackType.VIDEO)
                .count();
    }

    private boolean hasAlphaExternalLayers(TimelineExtensions extensions) {
        if (extensions == null) {
            return false;
        }
        return extensions.externalRenderNodes().stream()
                .anyMatch(n -> n.intermediateFormat() != null
                        && (n.intermediateFormat().contains("4444")
                        || n.intermediateFormat().contains("png")
                        || n.intermediateFormat().contains("alpha")));
    }

    private boolean hasCrossDissolveOrComplexTransition(TimelineSpec timeline) {
        return timeline.tracks().stream()
                .flatMap(t -> t.clips().stream())
                .flatMap(c -> c.effects() != null ? c.effects().stream() : java.util.stream.Stream.empty())
                .map(TimelineClipEffect::effectKey)
                .anyMatch(k -> k != null && (k.contains("cross_dissolve") || k.contains("wipe")
                        || k.contains("slide") || k.contains("dissolve")));
    }

    private boolean hasMultitrackAudioMix(TimelineSpec timeline) {
        return timeline.tracks().stream()
                .filter(t -> t.type() == TimelineTrack.TrackType.AUDIO)
                .mapToLong(t -> t.clips() != null ? t.clips().size() : 0)
                .sum() > 1;
    }
}
