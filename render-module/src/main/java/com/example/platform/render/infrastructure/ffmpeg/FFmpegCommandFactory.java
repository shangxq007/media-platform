package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.spatial.SpatialCoordinateConverter;
import com.example.platform.render.domain.spatial.SpatialPlan;
import com.example.platform.render.domain.spatial.SpatialSource;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.effects.EffectFilterGraphBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for building FFmpeg command-line argument lists.
 *
 * <p>All commands are built as {@link List<String>} — never as shell strings.
 * This ensures safe execution through the {@link com.example.platform.extension.app.ProcessToolRunner}.</p>
 *
 * <h3>Command Structure</h3>
 * <pre>
 * ffmpeg -i input [filters] -c:v codec -b:v bitrate -c:a codec -b:a bitrate output
 * </pre>
 */
public class FFmpegCommandFactory {

    private static final Logger log = LoggerFactory.getLogger(FFmpegCommandFactory.class);

    private EffectFilterGraphBuilder effectFilterGraphBuilder;

    public void setEffectFilterGraphBuilder(EffectFilterGraphBuilder effectFilterGraphBuilder) {
        this.effectFilterGraphBuilder = effectFilterGraphBuilder;
    }

    public List<String> buildProbeCommand(String inputUri) {
        List<String> args = new ArrayList<>();
        args.add("-i");
        args.add(inputUri);
        args.add("-v");
        args.add("quiet");
        args.add("-print_format");
        args.add("json");
        args.add("-show_format");
        args.add("-show_streams");
        log.debug("Built probe command for input: {}", inputUri);
        return args;
    }

    public List<String> buildThumbnailCommand(String inputUri, String outputUri,
            double timeOffset, int width) {
        List<String> args = new ArrayList<>();
        args.add("-ss");
        args.add(String.valueOf(timeOffset));
        args.add("-i");
        args.add(inputUri);
        args.add("-frames:v");
        args.add("1");
        args.add("-vf");
        args.add("scale=" + width + ":-1");
        args.add("-y");
        args.add(outputUri);
        log.debug("Built thumbnail command: input={} output={}", inputUri, outputUri);
        return args;
    }

    public List<String> buildTranscodeCommand(String inputUri, String outputUri,
            RenderProfile profile) {
        List<String> args = new ArrayList<>();
        args.add("-i");
        args.add(inputUri);

        if (profile.codec() != null && !profile.codec().isBlank()) {
            args.add("-c:v");
            args.add(mapCodec(profile.codec()));
        }

        if (profile.bitrateKbps() > 0) {
            args.add("-b:v");
            args.add(profile.bitrateKbps() + "k");
        }

        if (profile.resolution() != null && !profile.resolution().isBlank()) {
            args.add("-s");
            args.add(profile.resolution());
        }

        if (profile.audioCodec() != null && !profile.audioCodec().isBlank()) {
            args.add("-c:a");
            args.add(profile.audioCodec());
        }

        if (profile.audioRate() > 0) {
            args.add("-ar");
            args.add(String.valueOf(profile.audioRate()));
        }

        if (profile.extraParams() != null) {
            profile.extraParams().forEach((key, value) -> {
                args.add("-" + key);
                args.add(value);
            });
        }

        args.add("-y");
        args.add(outputUri);
        log.debug("Built transcode command: input={} output={} profile={}", inputUri, outputUri, profile.id());
        return args;
    }

    public List<String> buildFaststartCommand(String inputUri, String outputUri) {
        List<String> args = new ArrayList<>();
        args.add("-i");
        args.add(inputUri);
        args.add("-c");
        args.add("copy");
        args.add("-movflags");
        args.add("+faststart");
        args.add("-y");
        args.add(outputUri);
        log.debug("Built faststart command: input={} output={}", inputUri, outputUri);
        return args;
    }

    /**
     * Builds ffmpeg arguments to render resolved timeline clips to a single output file.
     */
    public List<String> buildRenderFromResolvedClips(List<ResolvedClip> clips, String outputUri,
            RenderProfile profile) {
        return buildRenderFromResolvedClips(clips, outputUri, profile, null);
    }

    public List<String> buildRenderFromResolvedClips(List<ResolvedClip> clips, String outputUri,
            RenderProfile profile,
            com.example.platform.render.app.timeline.SegmentRenderSlice segmentWindow) {
        if (clips == null || clips.isEmpty()) {
            throw new IllegalArgumentException("At least one clip is required");
        }
        List<String> args;
        if (clips.size() == 1) {
            ResolvedClip clip = clips.get(0);
            double start = clip.startSeconds();
            double duration = clip.durationSeconds();
            if (segmentWindow != null) {
                start = Math.max(start, segmentWindow.startSeconds());
                duration = Math.min(duration, segmentWindow.durationSeconds());
            }
            args = buildClipTranscodeCommand(clip.localPath(), outputUri, start, duration, profile, clip.effects());
        } else {
            args = buildConcatCommand(clips, outputUri, profile);
        }
        if (segmentWindow != null && clips.size() > 1) {
            args.add("-t");
            args.add(String.valueOf(segmentWindow.durationSeconds()));
        }
        return args;
    }

    public List<String> buildClipTranscodeCommand(String inputPath, String outputUri,
            double startSeconds, double durationSeconds, RenderProfile profile) {
        return buildClipTranscodeCommand(inputPath, outputUri, startSeconds, durationSeconds, profile, List.of());
    }

    public List<String> buildClipTranscodeCommand(String inputPath, String outputUri,
            double startSeconds, double durationSeconds, RenderProfile profile,
            List<TimelineClipEffect> effects) {
        List<String> args = new ArrayList<>();
        if (startSeconds > 0) {
            args.add("-ss");
            args.add(String.valueOf(startSeconds));
        }
        args.add("-i");
        args.add(inputPath);
        if (durationSeconds > 0) {
            args.add("-t");
            args.add(String.valueOf(durationSeconds));
        }
        if (effectFilterGraphBuilder != null) {
            Optional<String> vf = effectFilterGraphBuilder.buildVideoFilterChain(effects);
            vf.ifPresent(chain -> {
                args.add("-vf");
                args.add(chain);
            });
        }
        appendOutputEncoding(args, profile);
        // Force yuv420p for maximum player compatibility (yuv444p causes issues with some players)
        args.add("-pix_fmt");
        args.add("yuv420p");
        args.add("-y");
        args.add(outputUri);
        log.debug("Built clip transcode: input={} start={} duration={}", inputPath, startSeconds, durationSeconds);
        return args;
    }

    public List<String> buildConcatCommand(List<ResolvedClip> clips, String outputUri, RenderProfile profile) {
        List<String> args = new ArrayList<>();
        StringBuilder filter = new StringBuilder();
        for (int i = 0; i < clips.size(); i++) {
            ResolvedClip clip = clips.get(i);
            if (clip.startSeconds() > 0) {
                args.add("-ss");
                args.add(String.valueOf(clip.startSeconds()));
            }
            if (clip.durationSeconds() > 0) {
                args.add("-t");
                args.add(String.valueOf(clip.durationSeconds()));
            }
            args.add("-i");
            args.add(clip.localPath());
            // Only reference audio stream if it exists; use a=0 for video-only inputs
            filter.append("[").append(i).append(":v:0]");
        }
        // Video-only concat (a=0) — works when inputs have no audio streams
        filter.append("concat=n=").append(clips.size()).append(":v=1:a=0[outv]");
        args.add("-filter_complex");
        args.add(filter.toString());
        args.add("-map");
        args.add("[outv]");
        appendOutputEncoding(args, profile);
        // Force yuv420p for maximum player compatibility
        args.add("-pix_fmt");
        args.add("yuv420p");
        args.add("-y");
        args.add(outputUri);
        log.debug("Built concat command: clips={} output={}", clips.size(), outputUri);
        return args;
    }

    public List<String> buildMultiTrackCommand(
            List<ResolvedClip> videoClips,
            List<List<ResolvedClip>> audioTracks,
            String outputUri,
            RenderProfile profile) {
        return buildMultiTrackCommand(videoClips, audioTracks, outputUri, profile, null, null);
    }

    /**
     * Build multi-track ffmpeg command with optional subtitle burn-in and watermark overlay.
     *
     * @param subtitlePath path to SRT subtitle file (null to skip)
     * @param watermarkPath path to PNG watermark image (null to skip)
     */
    public List<String> buildMultiTrackCommand(
            List<ResolvedClip> videoClips,
            List<List<ResolvedClip>> audioTracks,
            String outputUri,
            RenderProfile profile,
            String subtitlePath,
            String watermarkPath) {
        return buildMultiTrackCommand(videoClips, audioTracks, outputUri, profile, subtitlePath, watermarkPath, 0);
    }

    /**
     * Build multi-track ffmpeg command with optional subtitle burn-in, watermark overlay, and fade in/out.
     *
     * @param subtitlePath path to SRT subtitle file (null to skip)
     * @param watermarkPath path to PNG watermark image (null to skip)
     * @param fadeDuration  fade in/out duration in seconds (0 to disable)
     */
    public List<String> buildMultiTrackCommand(
            List<ResolvedClip> videoClips,
            List<List<ResolvedClip>> audioTracks,
            String outputUri,
            RenderProfile profile,
            String subtitlePath,
            String watermarkPath,
            double fadeDuration) {
        return buildMultiTrackCommand(videoClips, audioTracks, outputUri, profile, subtitlePath, watermarkPath, fadeDuration, 0);
    }

    /**
     * Build multi-track ffmpeg command with optional subtitle burn-in, watermark overlay, fade in/out, and cross-dissolve.
     *
     * @param subtitlePath    path to SRT subtitle file (null to skip)
     * @param watermarkPath   path to PNG watermark image (null to skip)
     * @param fadeDuration    fade in/out duration in seconds (0 to disable)
     * @param transitionDuration cross-dissolve duration between adjacent clips in seconds (0 to disable)
     */
    public List<String> buildMultiTrackCommand(
            List<ResolvedClip> videoClips,
            List<List<ResolvedClip>> audioTracks,
            String outputUri,
            RenderProfile profile,
            String subtitlePath,
            String watermarkPath,
            double fadeDuration,
            double transitionDuration) {
        return buildMultiTrackCommand(videoClips, audioTracks, outputUri, profile, subtitlePath, watermarkPath, fadeDuration, transitionDuration, null, null);
    }

    /**
     * Build multi-track ffmpeg command with spatial plan support (no assets base path).
     */
    public List<String> buildMultiTrackCommand(
            List<ResolvedClip> videoClips,
            List<List<ResolvedClip>> audioTracks,
            String outputUri,
            RenderProfile profile,
            String subtitlePath,
            String watermarkPath,
            double fadeDuration,
            double transitionDuration,
            com.example.platform.render.domain.spatial.SpatialPlan spatialPlan) {
        return buildMultiTrackCommand(videoClips, audioTracks, outputUri, profile, subtitlePath, watermarkPath, fadeDuration, transitionDuration, spatialPlan, null);
    }

    /**
     * Build multi-track ffmpeg command with full spatial support.
     *
     * @param spatialPlan    loaded spatial plan with crop/overlay operations (null to skip)
     * @param assetsBasePath base directory for resolving spatial asset paths (null to skip spatial overlay)
     */
    public List<String> buildMultiTrackCommand(
            List<ResolvedClip> videoClips,
            List<List<ResolvedClip>> audioTracks,
            String outputUri,
            RenderProfile profile,
            String subtitlePath,
            String watermarkPath,
            double fadeDuration,
            double transitionDuration,
            com.example.platform.render.domain.spatial.SpatialPlan spatialPlan,
            java.nio.file.Path assetsBasePath) {

        List<String> args = new ArrayList<>();
        List<String> filterParts = new ArrayList<>();
        int inputIndex = 0;

        // Video inputs
        for (ResolvedClip clip : videoClips) {
            if (clip.startSeconds() > 0) {
                args.add("-ss");
                args.add(String.valueOf(clip.startSeconds()));
            }
            if (clip.durationSeconds() > 0) {
                args.add("-t");
                args.add(String.valueOf(clip.durationSeconds()));
            }
            args.add("-i");
            args.add(clip.localPath());
            inputIndex++;
        }

        // Audio inputs
        int audioInputStart = inputIndex;
        for (List<ResolvedClip> audioTrack : audioTracks) {
            for (ResolvedClip clip : audioTrack) {
                if (clip.startSeconds() > 0) {
                    args.add("-ss");
                    args.add(String.valueOf(clip.startSeconds()));
                }
                if (clip.durationSeconds() > 0) {
                    args.add("-t");
                    args.add(String.valueOf(clip.durationSeconds()));
                }
                args.add("-i");
                args.add(clip.localPath());
                inputIndex++;
            }
        }

        log.debug("buildMultiTrackCommand: videoClips={} audioTracks={} subtitlePath={} watermarkPath={}",
                videoClips.size(), audioTracks.size(), subtitlePath, watermarkPath);

        // Build filter graph
        if (videoClips.size() == 1 && audioTracks.isEmpty()) {
            // Single video, no extra audio — direct copy
            args.add("-map");
            args.add("0:v:0");
            args.add("-map");
            args.add("0:a:0?");
        } else if (videoClips.size() == 1 && audioTracks.size() == 1 && audioTracks.get(0).size() == 1) {
            // Single video + single audio track — direct mapping
            args.add("-map");
            args.add("0:v:0");
            args.add("-map");
            args.add(audioInputStart + ":a:0");
        } else {
            // Complex: concat video clips + amix audio + optional subtitle + watermark
            log.debug("buildMultiTrackCommand: COMPLEX branch vc={} at={} sub={} wm={}",
                    videoClips.size(), audioTracks.size(), subtitlePath != null, watermarkPath != null);
            StringBuilder filter = new StringBuilder();

            // Apply crop filters to individual clips before composition
            int canvasWidth = 1920;
            int canvasHeight = 1080;
            if (spatialPlan != null) {
                canvasWidth = spatialPlan.canvasWidth();
                canvasHeight = spatialPlan.canvasHeight();
            }
            // Track which clips have been cropped (output label)
            String[] croppedClipLabels = new String[videoClips.size()];
            for (int i = 0; i < videoClips.size(); i++) {
                croppedClipLabels[i] = String.valueOf(i) + ":v:0";
            }
            // Apply crop operations from spatial plan
            if (spatialPlan != null && spatialPlan.operations() != null) {
                for (var op : spatialPlan.operations()) {
                    if (!op.isCrop() || !op.isSupported() || op.region() == null) continue;
                    // Match crop to clip by assetId (extract filename from clip localPath)
                    int targetIdx = -1;
                    if (op.source() != null && op.source().assetId() != null) {
                        String targetAssetId = op.source().assetId();
                        for (int i = 0; i < videoClips.size(); i++) {
                            String path = videoClips.get(i).localPath();
                            if (path != null && path.contains(targetAssetId)) {
                                targetIdx = i;
                                break;
                            }
                        }
                    }
                    if (targetIdx < 0) targetIdx = 0; // Fallback to first clip
                    var region = op.region();
                    int[] crop = com.example.platform.render.domain.spatial.SpatialCoordinateConverter
                            .ppmRegionToCropFilter(region.x(), region.y(), region.width(), region.height(),
                                    canvasWidth, canvasHeight);
                    filter.append("[").append(targetIdx).append(":v:0]")
                            .append("crop=").append(crop[2]).append(":").append(crop[3])
                            .append(":").append(crop[0]).append(":").append(crop[1])
                            .append("[vc").append(targetIdx).append("];");
                    croppedClipLabels[targetIdx] = "vc" + targetIdx;
                }
            }

            // Video composition: either xfade (cross-dissolve) or concat
            if (videoClips.size() > 1) {
                if (transitionDuration > 0) {
                    // Cross-dissolve chain using xfade filter
                    double clipDuration = videoClips.get(0).durationSeconds();
                    // Trim and reset timestamps for each (possibly cropped) clip
                    for (int i = 0; i < videoClips.size(); i++) {
                        filter.append("[").append(croppedClipLabels[i]).append("]")
                                .append("trim=start=0:end=").append(clipDuration)
                                .append(",setpts=PTS-STARTPTS")
                                .append("[vt").append(i).append("];");
                    }
                    for (int i = 0; i < videoClips.size() - 1; i++) {
                        double offset = (i + 1) * clipDuration - (i + 1) * transitionDuration;
                        String inLabel = (i == 0) ? "[vt" + i + "]" : "[vxf" + i + "]";
                        filter.append(inLabel).append("[vt").append(i + 1).append("]");
                        filter.append("xfade=transition=fade:duration=").append(transitionDuration);
                        filter.append(":offset=").append(offset);
                        filter.append("[vxf").append(i + 1).append("];");
                    }
                    filter.append("[vxf").append(videoClips.size() - 1).append("]null[vconcat];");
                } else {
                    // Simple concat (no transition)
                    for (int i = 0; i < videoClips.size(); i++) {
                        filter.append("[").append(croppedClipLabels[i]).append("]");
                    }
                    filter.append("concat=n=").append(videoClips.size()).append(":v=1:a=0[vconcat];");
                }
            } else {
                filter.append("[").append(croppedClipLabels[0]).append("]null[vconcat];");
            }

            // Subtitle burn-in (if subtitle file provided)
            String videoLabel = "vconcat";
            if (subtitlePath != null && !subtitlePath.isBlank()) {
                // Add subtitle file as an input
                args.add("-i");
                args.add(subtitlePath);
                int subIdx = inputIndex;
                // Use subtitles filter to burn subtitles onto the video
                // subtitles filter may produce YUVA output; force yuv420p for player compatibility
                filter.append("[").append(videoLabel).append("]subtitles=filename='")
                        .append(subtitlePath.replace("'", "'\\''"))
                        .append("':force_style='FontSize=24,PrimaryColour=&HFFFFFF',format=yuv420p[vsub];");
                videoLabel = "vsub";
            }

            // Watermark overlay (if watermark image provided)
            if (watermarkPath != null && !watermarkPath.isBlank()) {
                args.add("-i");
                args.add(watermarkPath);
                int wmIdx = inputIndex + (subtitlePath != null && !subtitlePath.isBlank() ? 1 : 0);
                filter.append("[").append(wmIdx).append(":v:0]scale=192:-1[wm];");
                filter.append("[").append(videoLabel).append("][wm]overlay=W-w-32:32:format=auto,format=yuv420p[vout];");
                videoLabel = "vout";
            } else {
                filter.append("[").append(videoLabel).append("]null[vout];");
                videoLabel = "vout";
            }

            // Spatial overlay/composite operations from spatial plan
            if (spatialPlan != null && spatialPlan.operations() != null) {
                int spatialInputIdx = inputIndex;
                if (subtitlePath != null && !subtitlePath.isBlank()) spatialInputIdx++;
                if (watermarkPath != null && !watermarkPath.isBlank()) spatialInputIdx++;
                for (var op : spatialPlan.operations()) {
                    if (!op.isOverlay() || !op.isSupported() || op.position() == null) continue;
                    // Skip watermark (already handled above) — match by assetId containing "logo"
                    if (op.source() != null && op.source().assetId() != null
                            && op.source().assetId().contains("logo")) continue;
                    // Add the overlay image as input
                    String assetPath = resolveSpatialAssetPath(op.source(), assetsBasePath);
                    if (assetPath == null) continue;
                    args.add("-i");
                    args.add(assetPath);
                    var pos = op.position();
                    int[] overlay = com.example.platform.render.domain.spatial.SpatialCoordinateConverter
                            .ppmPositionToOverlay(pos.x(), pos.y(), pos.width(), pos.height(),
                                    canvasWidth, canvasHeight);
                    // Scale and overlay
                    filter.append("[").append(spatialInputIdx).append(":v:0]")
                            .append("scale=").append(overlay[2]).append(":").append(overlay[3])
                            .append("[so").append(spatialInputIdx).append("];");
                    filter.append("[").append(videoLabel).append("][so").append(spatialInputIdx).append("]")
                            .append("overlay=").append(overlay[0]).append(":").append(overlay[1])
                            .append(":format=auto[vsp").append(spatialInputIdx).append("];");
                    videoLabel = "vsp" + spatialInputIdx;
                    spatialInputIdx++;
                }
            }

            // Calculate total video duration from clips for fade out timing
            // Each ResolvedClip has startSeconds (timeline position) and durationSeconds
            double totalDuration = 0;
            for (ResolvedClip clip : videoClips) {
                double clipEnd = clip.startSeconds() + clip.durationSeconds();
                if (clipEnd > totalDuration) totalDuration = clipEnd;
            }

            // Fade in/out on the final video composite (after subtitles and overlay)
            // This ensures subtitles and watermark also fade together with the video
            System.out.println("[buildMultiTrackCommand] before fade: videoLabel=" + videoLabel + " fadeDuration=" + fadeDuration + " totalDuration=" + totalDuration);
            if (fadeDuration > 0 && totalDuration > fadeDuration * 2) {
                // Apply fade in/out: fade in from black at start, fade out to black at end
                filter.append("[").append(videoLabel).append("]fade=t=in:st=0:d=").append(fadeDuration);
                filter.append(",fade=t=out:st=").append(totalDuration - fadeDuration).append(":d=").append(fadeDuration);
                filter.append("[vfade];");
                videoLabel = "vfade";
            } else {
                // No fade, just rename for consistent output mapping
                filter.append("[").append(videoLabel).append("]null[vfade];");
                videoLabel = "vfade";
            }

            // Audio mix (from dedicated audio tracks only, not from video inputs)
            List<String> audioLabels = new ArrayList<>();
            int audioIdx = audioInputStart;
            for (int t = 0; t < audioTracks.size(); t++) {
                List<ResolvedClip> track = audioTracks.get(t);
                for (int c = 0; c < track.size(); c++) {
                    String label = "a" + t + "_" + c;
                    filter.append("[").append(audioIdx).append(":a:0]aresample=48000[").append(label).append("];");
                    audioLabels.add("[" + label + "]");
                    audioIdx++;
                }
            }

            // Build audio chain: mix -> fade -> output
            // Always output to [afade] for consistent mapping
            if (audioLabels.size() > 1) {
                for (String label : audioLabels) {
                    filter.append(label);
                }
                filter.append("amix=inputs=").append(audioLabels.size()).append(":duration=longest:normalize=0[amixed];");
                // Apply fade to mixed audio
                if (fadeDuration > 0 && totalDuration > fadeDuration * 2) {
                    filter.append("[amixed]afade=t=in:st=0:d=").append(fadeDuration);
                    filter.append(",afade=t=out:st=").append(totalDuration - fadeDuration).append(":d=").append(fadeDuration).append("[afade];");
                } else {
                    filter.append("[amixed]null[afade];");
                }
            } else if (audioLabels.size() == 1) {
                // Single audio track: apply fade directly
                if (fadeDuration > 0 && totalDuration > fadeDuration * 2) {
                    filter.append(audioLabels.get(0)).append("afade=t=in:st=0:d=").append(fadeDuration);
                    filter.append(",afade=t=out:st=").append(totalDuration - fadeDuration).append(":d=").append(fadeDuration).append("[afade];");
                } else {
                    filter.append(audioLabels.get(0)).append("anull[afade];");
                }
            }

            String filterStr = filter.toString();
            System.out.println("[buildMultiTrackCommand] filter=" + filterStr.substring(0, Math.min(300, filterStr.length())));
            args.add("-filter_complex");
            args.add(filterStr);
            args.add("-map");
            args.add("[" + videoLabel + "]");
            if (!audioLabels.isEmpty()) {
                args.add("-map");
                args.add("[afade]");
            }
        }

        // For multi-track with audio, add audio encoding explicitly
        if (!audioTracks.isEmpty()) {
            if (profile.codec() != null && !profile.codec().isBlank()) {
                args.add("-c:v");
                args.add(mapCodec(profile.codec()));
            }
            if (profile.bitrateKbps() > 0) {
                args.add("-b:v");
                args.add(profile.bitrateKbps() + "k");
            }
            if (profile.resolution() != null && !profile.resolution().isBlank()) {
                args.add("-s");
                args.add(profile.resolution());
            }
            // Force yuv420p for maximum player compatibility (yuv444p causes issues with some players)
            args.add("-pix_fmt");
            args.add("yuv420p");
            args.add("-c:a");
            args.add("aac");
            args.add("-ar");
            args.add("48000");
        } else {
            appendOutputEncoding(args, profile);
        }
        args.add("-y");
        args.add(outputUri);
        log.debug("Built multi-track command: videoClips={} audioTracks={} output={}",
                videoClips.size(), audioTracks.size(), outputUri);
        return args;
    }

    public List<String> buildTranscodeFromTimeline(TimelineSpec timeline, String outputUri) {
        RenderProfile profile = timelineProfile(timeline);
        List<ResolvedClip> clips = new ArrayList<>();
        if (timeline.tracks() != null) {
            timeline.tracks().forEach(track -> {
                if (track.clips() != null) {
                    track.clips().forEach(clip -> addClip(clips, clip));
                }
            });
        }
        return buildRenderFromResolvedClips(clips, outputUri, profile);
    }

    private void addClip(List<ResolvedClip> clips, TimelineClip clip) {
        if (clip.assetRef() == null || clip.assetRef().storageUri() == null) {
            return;
        }
        clips.add(new ResolvedClip(
                clip.assetRef().storageUri(),
                clip.assetInPoint(),
                clip.clipDuration(),
                clip.effects() != null ? clip.effects() : List.of()));
    }

    private RenderProfile timelineProfile(TimelineSpec timeline) {
        TimelineOutputSpec outputSpec = timeline.outputSpec();
        if (outputSpec == null) {
            return RenderProfile.of("default_1080p", "1920x1080", "h264");
        }
        // Only set audio codec if the output spec explicitly has audio config
        String audioCodec = outputSpec.audioSpec() != null ? outputSpec.audioSpec().codec() : null;
        int audioRate = outputSpec.audioSpec() != null ? outputSpec.audioSpec().sampleRate() : 0;
        return new RenderProfile(
                timeline.id(),
                timeline.name(),
                null,
                outputSpec.resolution(),
                outputSpec.videoCodec(),
                outputSpec.videoBitrate(),
                audioCodec,
                audioRate,
                Map.of());
    }

    private void appendOutputEncoding(List<String> args, RenderProfile profile) {
        if (profile.codec() != null && !profile.codec().isBlank()) {
            args.add("-c:v");
            args.add(mapCodec(profile.codec()));
        }
        if (profile.bitrateKbps() > 0) {
            args.add("-b:v");
            args.add(profile.bitrateKbps() + "k");
        }
        if (profile.resolution() != null && !profile.resolution().isBlank()) {
            args.add("-s");
            args.add(profile.resolution());
        }
        if (profile.audioCodec() != null && !profile.audioCodec().isBlank()) {
            args.add("-c:a");
            args.add(profile.audioCodec());
            if (profile.audioRate() > 0) {
                args.add("-ar");
                args.add(String.valueOf(profile.audioRate()));
            }
        }
    }

    public record ResolvedClip(
            String localPath,
            double startSeconds,
            double durationSeconds,
            List<TimelineClipEffect> effects) {

        public ResolvedClip(String localPath, double startSeconds, double durationSeconds) {
            this(localPath, startSeconds, durationSeconds, List.of());
        }
    }

    private String mapCodec(String codec) {
        return switch (codec.toLowerCase()) {
            case "h264" -> "libx264";
            case "h265", "hevc" -> "libx265";
            case "vp9" -> "libvpx-vp9";
            case "av1" -> "libaom-av1";
            default -> codec;
        };
    }

    /**
     * Resolve spatial asset file path from operation source.
     * Looks for the asset in the given assets base directory.
     *
     * @param source         spatial source with assetId
     * @param assetsBasePath base directory for assets (e.g. project/assets/)
     * @return absolute path if found, null otherwise
     */
    private static String resolveSpatialAssetPath(
            com.example.platform.render.domain.spatial.SpatialSource source,
            Path assetsBasePath) {
        if (source == null || source.assetId() == null) return null;
        if (assetsBasePath == null || !java.nio.file.Files.isDirectory(assetsBasePath)) return null;
        String assetId = source.assetId();
        String[] exts = {".png", ".jpg", ".jpeg"};
        String[] dirs = {"image", "video", "audio"};
        for (String dir : dirs) {
            for (String ext : exts) {
                Path p = assetsBasePath.resolve(dir + "/" + assetId + ext);
                if (java.nio.file.Files.isRegularFile(p)) return p.toAbsolutePath().toString();
            }
        }
        return null;
    }
}
