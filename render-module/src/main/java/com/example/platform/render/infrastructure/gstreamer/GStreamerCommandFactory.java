package com.example.platform.render.infrastructure.gstreamer;

import com.example.platform.render.infrastructure.RenderPreset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Factory for building GStreamer pipeline argument lists.
 *
 * <p>All pipelines are built as {@link List<String>} — never as shell strings.</p>
 *
 * <h3>Pipeline Structure (test source)</h3>
 * <pre>
 * gst-launch-1.0 videotestsrc num-frames=150 ! videoconvert ! x264enc bitrate=2500 ! mp4mux ! filesink location=output.mp4
 * </pre>
 *
 * <h3>Pipeline Structure (file source)</h3>
 * <pre>
 * gst-launch-1.0 filesrc location=input.mp4 ! decodebin ! videoconvert ! x264enc bitrate=8000 ! mp4mux ! filesink location=output.mp4
 * </pre>
 */
@Component
public class GStreamerCommandFactory {

    private static final Logger log = LoggerFactory.getLogger(GStreamerCommandFactory.class);

    /**
     * Builds a test source pipeline that generates a test video.
     *
     * @param outputPath output file path
     * @param preset     render preset
     * @return list of gst-launch-1.0 arguments
     */
    public List<String> buildTestSourcePipeline(String outputPath, RenderPreset preset) {
        List<String> args = new ArrayList<>();
        int totalFrames = 5 * preset.frameRate();

        args.add("videotestsrc");
        args.add("num-frames=" + totalFrames);
        args.add("!");
        args.add("video/x-raw,width=" + preset.width() + ",height=" + preset.height() +
                ",framerate=" + preset.frameRate() + "/1");
        args.add("!");
        args.add("videoconvert");
        args.add("!");
        args.add("x264enc");
        args.add("bitrate=" + preset.videoBitrateKbps());
        args.add("!");
        args.add("mp4mux");
        args.add("!");
        args.add("filesink");
        args.add("location=" + outputPath);

        log.debug("Built GStreamer test source pipeline: output={} resolution={}x{}",
                outputPath, preset.width(), preset.height());
        return args;
    }

    /**
     * Builds a file source pipeline for transcoding.
     *
     * @param inputPath  input file path
     * @param outputPath output file path
     * @param preset     render preset
     * @return list of gst-launch-1.0 arguments
     */
    public List<String> buildTranscodePipeline(String inputPath, String outputPath, RenderPreset preset) {
        List<String> args = new ArrayList<>();

        args.add("filesrc");
        args.add("location=" + inputPath);
        args.add("!");
        args.add("decodebin");
        args.add("!");
        args.add("videoconvert");
        args.add("!");
        args.add("x264enc");
        args.add("bitrate=" + preset.videoBitrateKbps());
        args.add("!");
        args.add("mp4mux");
        args.add("!");
        args.add("filesink");
        args.add("location=" + outputPath);

        log.debug("Built GStreamer transcode pipeline: input={} output={}", inputPath, outputPath);
        return args;
    }

    /**
     * Builds a pipeline with subtitle overlay.
     *
     * @param inputPath     input file path
     * @param outputPath    output file path
     * @param subtitleText  text to overlay
     * @param preset        render preset
     * @return list of gst-launch-1.0 arguments
     */
    public List<String> buildSubtitleOverlayPipeline(String inputPath, String outputPath,
                                                      String subtitleText, RenderPreset preset) {
        List<String> args = new ArrayList<>();

        args.add("filesrc");
        args.add("location=" + inputPath);
        args.add("!");
        args.add("decodebin");
        args.add("!");
        args.add("videoconvert");
        args.add("!");
        args.add("textoverlay");
        args.add("text=\"" + escapePipelineText(subtitleText) + "\"");
        args.add("valignment=bottom");
        args.add("halignment=center");
        args.add("!");
        args.add("x264enc");
        args.add("bitrate=" + preset.videoBitrateKbps());
        args.add("!");
        args.add("mp4mux");
        args.add("!");
        args.add("filesink");
        args.add("location=" + outputPath);

        log.debug("Built GStreamer subtitle pipeline: input={} text='{}'", inputPath, subtitleText);
        return args;
    }

    private String escapePipelineText(String text) {
        return text.replace("\"", "\\\"").replace("!", "\\!");
    }
}
