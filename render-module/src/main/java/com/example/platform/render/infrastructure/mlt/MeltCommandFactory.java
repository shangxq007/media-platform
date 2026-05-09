package com.example.platform.render.infrastructure.mlt;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for building melt command-line argument lists.
 *
 * <p>All commands are built as {@link List<String>} — never as shell strings.</p>
 *
 * <h3>Command Structure</h3>
 * <pre>
 * melt project.xml -consumer avformat:output.mp4
 * </pre>
 */
public class MeltCommandFactory {

    private static final Logger log = LoggerFactory.getLogger(MeltCommandFactory.class);

    /**
     * Builds a melt render command from an MLT project XML path.
     *
     * @param projectXmlPath path to the MLT project XML file
     * @param outputUri      output file URI
     * @param profile        render profile string (e.g., "atsc_1080p_30")
     * @return list of command arguments
     */
    public List<String> buildRenderCommand(String projectXmlPath, String outputUri, String profile) {
        List<String> args = new ArrayList<>();
        args.add(projectXmlPath);
        args.add("-consumer");
        args.add("avformat:" + outputUri);

        if (profile != null && !profile.isBlank()) {
            args.add("profile=" + profile);
        }

        log.debug("Built melt render command: project={} output={}", projectXmlPath, outputUri);
        return args;
    }

    /**
     * Builds a melt render command with explicit video settings.
     *
     * @param projectXmlPath path to the MLT project XML file
     * @param outputUri      output file URI
     * @param width          output width
     * @param height         output height
     * @param fps            frame rate
     * @param videoCodec     video codec (e.g., "libx264")
     * @param audioCodec     audio codec (e.g., "aac")
     * @return list of command arguments
     */
    public List<String> buildRenderCommand(String projectXmlPath, String outputUri,
            int width, int height, double fps, String videoCodec, String audioCodec) {
        List<String> args = new ArrayList<>();
        args.add(projectXmlPath);
        args.add("-consumer");
        args.add("avformat:" + outputUri);
        args.add("width=" + width);
        args.add("height=" + height);
        args.add("frame_rate_num=" + (int) fps);
        if (videoCodec != null) {
            args.add("vcodec=" + videoCodec);
        }
        if (audioCodec != null) {
            args.add("acodec=" + audioCodec);
        }

        log.debug("Built melt render command with settings: project={} output={} {}x{}",
                projectXmlPath, outputUri, width, height);
        return args;
    }

    /**
     * Builds a melt preview command (lower quality, faster).
     *
     * @param projectXmlPath path to the MLT project XML file
     * @param outputUri      output file URI
     * @return list of command arguments
     */
    public List<String> buildPreviewCommand(String projectXmlPath, String outputUri) {
        List<String> args = new ArrayList<>();
        args.add(projectXmlPath);
        args.add("-consumer");
        args.add("avformat:" + outputUri);
        args.add("width=854");
        args.add("height=480");
        args.add("vcodec=libx264");
        args.add("preset=ultrafast");
        args.add("crf=28");

        log.debug("Built melt preview command: project={} output={}", projectXmlPath, outputUri);
        return args;
    }
}
