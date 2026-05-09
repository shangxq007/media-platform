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
public class FfmpegCommandFactory {

    private static final Logger log = LoggerFactory.getLogger(FfmpegCommandFactory.class);

    /**
     * Builds a probe command (ffprobe) for the given input.
     *
     * @param inputUri the input file URI
     * @return list of command arguments (excluding the executable)
     */
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

    /**
     * Builds a thumbnail extraction command.
     *
     * @param inputUri    the input file URI
     * @param outputUri   the output thumbnail URI
     * @param timeOffset  time offset in seconds for the thumbnail
     * @param width       thumbnail width in pixels
     * @return list of command arguments
     */
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

    /**
     * Builds a transcode command from a {@link TimelineOutputSpec} and {@link RenderProfile}.
     *
     * @param inputUri   the input file URI
     * @param outputUri  the output file URI
     * @param profile    the render profile
     * @return list of command arguments
     */
    public List<String> buildTranscodeCommand(String inputUri, String outputUri,
            RenderProfile profile) {
        List<String> args = new ArrayList<>();
        args.add("-i");
        args.add(inputUri);

        // Video codec
        if (profile.codec() != null && !profile.codec().isBlank()) {
            args.add("-c:v");
            args.add(mapCodec(profile.codec()));
        }

        // Video bitrate
        if (profile.bitrateKbps() > 0) {
            args.add("-b:v");
            args.add(profile.bitrateKbps() + "k");
        }

        // Resolution
        if (profile.resolution() != null && !profile.resolution().isBlank()) {
            args.add("-s");
            args.add(profile.resolution());
        }

        // Audio codec
        if (profile.audioCodec() != null && !profile.audioCodec().isBlank()) {
            args.add("-c:a");
            args.add(profile.audioCodec());
        }

        // Audio sample rate
        if (profile.audioRate() > 0) {
            args.add("-ar");
            args.add(String.valueOf(profile.audioRate()));
        }

        // Extra params
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

    /**
     * Builds a faststart command (move moov atom to beginning of file).
     *
     * @param inputUri  the input file URI
     * @param outputUri the output file URI
     * @return list of command arguments
     */
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
     * Builds a transcode command from a full {@link TimelineSpec}.
     *
     * @param timeline the timeline specification
     * @param outputUri the output file URI
     * @return list of command arguments
     */
    public List<String> buildTranscodeFromTimeline(TimelineSpec timeline, String outputUri) {
        List<String> args = new ArrayList<>();
        TimelineOutputSpec outputSpec = timeline.outputSpec();

        // Add inputs from all clips
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

        // Video codec
        if (outputSpec.videoCodec() != null) {
            args.add("-c:v");
            args.add(mapCodec(outputSpec.videoCodec()));
        }

        // Video bitrate
        if (outputSpec.videoBitrate() > 0) {
            args.add("-b:v");
            args.add(outputSpec.videoBitrate() + "k");
        }

        // Resolution
        if (outputSpec.resolution() != null) {
            args.add("-s");
            args.add(outputSpec.resolution());
        }

        // Frame rate
        if (outputSpec.frameRate() > 0) {
            args.add("-r");
            args.add(String.valueOf(outputSpec.frameRate()));
        }

        // Pixel format
        if (outputSpec.pixelFormat() != null) {
            args.add("-pix_fmt");
            args.add(outputSpec.pixelFormat());
        }

        // Audio
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
