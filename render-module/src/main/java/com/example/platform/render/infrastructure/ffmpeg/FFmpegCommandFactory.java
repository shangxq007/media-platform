package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.infrastructure.effects.EffectFilterGraphBuilder;
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
            filter.append("[").append(i).append(":v:0][").append(i).append(":a:0]");
        }
        filter.append("concat=n=").append(clips.size()).append(":v=1:a=1[outv][outa]");
        args.add("-filter_complex");
        args.add(filter.toString());
        args.add("-map");
        args.add("[outv]");
        args.add("-map");
        args.add("[outa]");
        appendOutputEncoding(args, profile);
        args.add("-y");
        args.add(outputUri);
        log.debug("Built concat command: clips={} output={}", clips.size(), outputUri);
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
        return new RenderProfile(
                timeline.id(),
                timeline.name(),
                null,
                outputSpec.resolution(),
                outputSpec.videoCodec(),
                outputSpec.videoBitrate(),
                outputSpec.audioSpec() != null ? outputSpec.audioSpec().codec() : "aac",
                outputSpec.audioSpec() != null ? outputSpec.audioSpec().sampleRate() : 48000,
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
        }
        if (profile.audioRate() > 0) {
            args.add("-ar");
            args.add(String.valueOf(profile.audioRate()));
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
}
