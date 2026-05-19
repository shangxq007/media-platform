package com.example.platform.render.infrastructure.mlt;

import java.util.List;

/**
 * @deprecated Use {@link MLTCommandFactory} instead. This class is retained for
 *             backward compatibility and delegates to MLTCommandFactory.
 */
@Deprecated
public class MeltCommandFactory extends MLTCommandFactory {

    private final MLTCommandFactory delegate = new MLTCommandFactory();

    @Override
    public List<String> buildRenderCommand(String projectXmlPath, String outputUri, String profile) {
        return delegate.buildRenderCommand(projectXmlPath, outputUri, profile);
    }

    @Override
    public List<String> buildRenderCommand(String projectXmlPath, String outputUri,
            int width, int height, double fps, String videoCodec, String audioCodec) {
        return delegate.buildRenderCommand(projectXmlPath, outputUri, width, height, fps, videoCodec, audioCodec);
    }

    @Override
    public List<String> buildPreviewCommand(String projectXmlPath, String outputUri) {
        return delegate.buildPreviewCommand(projectXmlPath, outputUri);
    }
}
