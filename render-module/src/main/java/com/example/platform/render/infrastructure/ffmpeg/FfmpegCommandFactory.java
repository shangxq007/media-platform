package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.render.domain.RenderProfile;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.util.List;

/**
 * @deprecated Use {@link FFmpegCommandFactory} instead. This class is retained for
 *             backward compatibility and delegates to FFmpegCommandFactory.
 */
@Deprecated
public class FfmpegCommandFactory extends FFmpegCommandFactory {

    private final FFmpegCommandFactory delegate = new FFmpegCommandFactory();

    @Override
    public List<String> buildProbeCommand(String inputUri) {
        return delegate.buildProbeCommand(inputUri);
    }

    @Override
    public List<String> buildThumbnailCommand(String inputUri, String outputUri,
            double timeOffset, int width) {
        return delegate.buildThumbnailCommand(inputUri, outputUri, timeOffset, width);
    }

    @Override
    public List<String> buildTranscodeCommand(String inputUri, String outputUri,
            RenderProfile profile) {
        return delegate.buildTranscodeCommand(inputUri, outputUri, profile);
    }

    @Override
    public List<String> buildFaststartCommand(String inputUri, String outputUri) {
        return delegate.buildFaststartCommand(inputUri, outputUri);
    }

    @Override
    public List<String> buildTranscodeFromTimeline(TimelineSpec timeline, String outputUri) {
        return delegate.buildTranscodeFromTimeline(timeline, outputUri);
    }
}
