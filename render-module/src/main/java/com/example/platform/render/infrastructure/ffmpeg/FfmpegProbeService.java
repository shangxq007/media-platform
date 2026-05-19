package com.example.platform.render.infrastructure.ffmpeg;

import com.example.platform.extension.app.ProcessToolRunner;
import java.util.Map;

/**
 * @deprecated Use {@link FFmpegProbeService} instead. This class is retained for
 *             backward compatibility and delegates to FFmpegProbeService.
 */
@Deprecated
public class FfmpegProbeService {

    private final FFmpegProbeService delegate;

    public FfmpegProbeService(ProcessToolRunner processToolRunner,
            FFmpegCommandFactory commandFactory) {
        this.delegate = new FFmpegProbeService(processToolRunner, commandFactory);
    }

    public Map<String, Object> probe(String inputUri) {
        return delegate.probe(inputUri);
    }

    public boolean extractThumbnail(String inputUri, String outputUri,
            double timeOffset, int width) {
        return delegate.extractThumbnail(inputUri, outputUri, timeOffset, width);
    }
}
