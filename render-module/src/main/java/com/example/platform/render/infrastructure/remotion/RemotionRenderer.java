package com.example.platform.render.infrastructure.remotion;

import java.nio.file.Path;
import java.util.List;

public interface RemotionRenderer {

    RemotionRenderResult render(RemotionRenderRequest request);

    record RemotionRenderRequest(
            String compositionId,
            Path workingDir,
            Path outputPath,
            RemotionInputProps inputProps,
            String format,
            int width,
            int height,
            int fps,
            int concurrency,
            boolean overwrite
    ) {}
}
