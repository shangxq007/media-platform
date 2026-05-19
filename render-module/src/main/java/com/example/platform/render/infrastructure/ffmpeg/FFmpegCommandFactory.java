package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.ArrayList;
import java.util.List;
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

    public List<String> buildTranscodeFromTimeline(TimelineSpec timeline, String outputUri) {
        List<String> args = new ArrayList<>();
        TimelineOutputSpec outputSpec = timeline.outputSpec();

        if (timeline.tracks() != null) {
            timeline.tracks().forEach(track -> {
                if (track.clips() != null) {
                    track.clips().forEach(clip -> {
                        if (clip.assetRef() != null) {
                            args.add("-i");
                            args.add(clip.assetRef().storageUri());
                        }
                    });
                }
            });
        }

        if (outputSpec.videoCodec() != null) {
            args.add("-c:v");
            args.add(mapCodec(outputSpec.videoCodec()));
        }

        if (outputSpec.videoBitrate() > 0) {
            args.add("-b:v");
            args.add(outputSpec.videoBitrate() + "k");
        }

        if (outputSpec.resolution() != null) {
            args.add("-s");
            args.add(outputSpec.resolution());
        }

        if (outputSpec.frameRate() > 0) {
            args.add("-r");
            args.add(String.valueOf(outputSpec.frameRate()));
        }

        if (outputSpec.pixelFormat() != null) {
            args.add("-pix_fmt");
            args.add(outputSpec.pixelFormat());
        }

        if (outputSpec.audioSpec() != null) {
            args.add("-c:a");
            args.add(outputSpec.audioSpec().codec());
            if (outputSpec.audioSpec().bitrateKbps() > 0) {
                args.add("-b:a");
                args.add(outputSpec.audioSpec().bitrateKbps() + "k");
            }
        }

        args.add("-y");
        args.add(outputUri);
        log.debug("Built timeline transcode command: timeline={} output={}", timeline.id(), outputUri);
        return args;
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
