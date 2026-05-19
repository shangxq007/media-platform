package com.example.platform.render.infrastructure.mlt;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for building melt command-line argument lists.
 *
 * <p>All commands are built as {@link List<String>} — never as shell strings.</p>
 */
public class MLTCommandFactory {

    private static final Logger log = LoggerFactory.getLogger(MLTCommandFactory.class);

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
