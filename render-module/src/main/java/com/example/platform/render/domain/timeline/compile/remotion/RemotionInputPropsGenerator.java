package com.example.platform.render.domain.timeline.compile.remotion;

import com.example.platform.render.domain.timeline.compile.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates RemotionInputProps from NormalizedTimeline.
 *
 * <p>Internal only — document generation POC. Does not execute Remotion.</p>
 *
 * <p>v0: Supports single primary video input + caption layers.
 * Unsupported features fail closed.</p>
 */
public class RemotionInputPropsGenerator {

    /**
     * Generate RemotionInputProps from a NormalizedTimeline.
     *
     * @param timeline the normalized timeline
     * @return the generated props
     * @throws IllegalArgumentException if timeline is null or unsupported
     */
    public RemotionInputProps generate(NormalizedTimeline timeline) {
        if (timeline == null) {
            throw new IllegalArgumentException("NormalizedTimeline must not be null");
        }

        // Validate single primary input
        List<NormalizedAssetRef> assets = timeline.allAssetRefs();
        if (assets.isEmpty()) {
            throw new IllegalArgumentException("Timeline has no source assets");
        }

        // Build composition
        NormalizedOutputProfile profile = timeline.outputProfile();
        int fps = (int) Math.round(profile.frameRate());
        int durationInFrames = (int) Math.round(timeline.totalDuration() * fps);

        RemotionCompositionSpec composition = new RemotionCompositionSpec(
                timeline.timelineId(),
                profile.width(), profile.height(),
                profile.frameRate(),
                durationInFrames,
                timeline.totalDuration());

        // Build timeline tracks
        List<RemotionTrackSpec> tracks = new ArrayList<>();
        for (NormalizedTrack track : timeline.tracks()) {
            List<RemotionClipSpec> clips = new ArrayList<>();
            for (NormalizedClip clip : track.clips()) {
                clips.add(new RemotionClipSpec(
                        clip.clipId(),
                        clip.assetRef().assetId(),
                        clip.timelineStart(),
                        clip.clipDuration(),
                        clip.assetInPoint(),
                        clip.assetOutPoint()));
            }
            tracks.add(new RemotionTrackSpec(
                    track.trackId(), track.name(),
                    track.type().name(), track.layer(),
                    track.muted(), clips));
        }

        RemotionTimelineSpec timelineSpec = new RemotionTimelineSpec(
                tracks, timeline.totalDuration());

        // Build media assets
        List<RemotionMediaAssetSpec> mediaAssets = new ArrayList<>();
        for (NormalizedAssetRef asset : assets) {
            mediaAssets.add(new RemotionMediaAssetSpec(
                    asset.assetId(),
                    "video",
                    asset.format(),
                    asset.duration(),
                    asset.width(), asset.height()));
        }

        // Build captions
        List<RemotionCaptionSpec> captions = new ArrayList<>();
        if (timeline.hasCaptions()) {
            for (NormalizedCaptionLayer layer : timeline.captionLayers()) {
                captions.add(new RemotionCaptionSpec(
                        layer.layerId(), layer.text(),
                        layer.startTime(), layer.startTime() + layer.duration(),
                        layer.fontFamily(), layer.fontSize(),
                        layer.color(), layer.positionX(), layer.positionY(),
                        layer.backgroundColor()));
            }
        }

        // Fonts — extract from captions
        List<RemotionFontSpec> fonts = new ArrayList<>();
        if (timeline.hasCaptions()) {
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (NormalizedCaptionLayer layer : timeline.captionLayers()) {
                String family = layer.fontFamily() != null ? layer.fontFamily() : "sans-serif";
                if (seen.add(family)) {
                    fonts.add(new RemotionFontSpec(family, 400, "normal", null));
                }
            }
        }

        // Output
        RemotionOutputSpec output = new RemotionOutputSpec(
                "default", profile.width(), profile.height(),
                profile.frameRate(), profile.format(), profile.videoCodec());

        // Metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("generationReady", "false");
        metadata.put("provider", "remotion");

        return new RemotionInputProps(
                RemotionInputProps.SCHEMA_VERSION,
                composition, timelineSpec, mediaAssets, captions, fonts, output, metadata);
    }
}
