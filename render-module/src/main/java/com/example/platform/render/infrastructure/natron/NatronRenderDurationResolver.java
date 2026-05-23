package com.example.platform.render.infrastructure.natron;

import com.example.platform.render.infrastructure.MediaProbeResult;
import com.example.platform.render.infrastructure.MediaProbeService;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class NatronRenderDurationResolver {

    private final MediaProbeService mediaProbeService;

    public NatronRenderDurationResolver(MediaProbeService mediaProbeService) {
        this.mediaProbeService = mediaProbeService;
    }

    public long resolveDurationSeconds(String jobId, String outputLocalPath) {
        MediaProbeResult probe = mediaProbeService.probeAbsolute(jobId, Path.of(outputLocalPath).toString());
        if (probe.valid() && probe.durationMs() > 0) {
            return Math.max(1L, Math.round(probe.durationMs() / 1000.0));
        }
        return 30L;
    }
}
