package com.example.platform.render.infrastructure.gpac;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for building MP4Box command-line argument lists.
 *
 * <p>All commands are built as {@link List<String>} — never as shell strings.</p>
 *
 * <h3>Command Structure</h3>
 * <pre>
 * MP4Box -dash 4000 -out output.mpd input.mp4
 * MP4Box -hls -out master.m3u8 input.mp4
 * </pre>
 */
public class Mp4BoxCommandFactory {

    private static final Logger log = LoggerFactory.getLogger(Mp4BoxCommandFactory.class);

    /**
     * Builds a DASH packaging command.
     *
     * @param inputUri        input media file URI
     * @param outputManifest  output manifest URI (.mpd)
     * @param segmentDuration segment duration in milliseconds
     * @return list of command arguments
     */
    public List<String> buildDashCommand(String inputUri, String outputManifest,
            int segmentDuration) {
        List<String> args = new ArrayList<>();
        args.add("-dash");
        args.add(String.valueOf(segmentDuration));
        args.add("-out");
        args.add(outputManifest);
        args.add("-segment-name");
        args.add("segment_$Number$");
        args.add(inputUri);

        log.debug("Built DASH command: input={} output={} segmentDur={}ms",
                inputUri, outputManifest, segmentDuration);
        return args;
    }

    /**
     * Builds an HLS packaging command.
     *
     * @param inputUri        input media file URI
     * @param outputManifest  output manifest URI (.m3u8)
     * @param segmentDuration segment duration in milliseconds
     * @return list of command arguments
     */
    public List<String> buildHlsCommand(String inputUri, String outputManifest,
            int segmentDuration) {
        List<String> args = new ArrayList<>();
        args.add("-hls");
        args.add(String.valueOf(segmentDuration));
        args.add("-out");
        args.add(outputManifest);
        args.add(inputUri);

        log.debug("Built HLS command: input={} output={} segmentDur={}ms",
                inputUri, outputManifest, segmentDuration);
        return args;
    }

    /**
     * Builds a CMAF packaging command (placeholder).
     *
     * @param inputUri        input media file URI
     * @param outputBase      base URI for output
     * @param segmentDuration segment duration in milliseconds
     * @return list of command arguments
     */
    public List<String> buildCmafCommand(String inputUri, String outputBase,
            int segmentDuration) {
        List<String> args = new ArrayList<>();
        args.add("-dash");
        args.add(String.valueOf(segmentDuration));
        args.add("-segment-name");
        args.add("segment_$Number$");
        args.add("-out");
        args.add(outputBase + "/manifest.mpd");
        args.add(inputUri);

        log.debug("Built CMAF command: input={} output={}", inputUri, outputBase);
        return args;
    }

    /**
     * Builds an MP4 inspection command.
     *
     * @param inputUri input media file URI
     * @return list of command arguments
     */
    public List<String> buildInspectCommand(String inputUri) {
        List<String> args = new ArrayList<>();
        args.add("-info");
        args.add("-std");
        args.add(inputUri);

        log.debug("Built inspect command: input={}", inputUri);
        return args;
    }
}
