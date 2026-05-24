package com.example.platform.render.infrastructure.remote;

import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProvider.RenderResult;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("remote-ffmpeg")
public class RemoteRenderProvider implements RenderProvider {

    private static final Logger log = LoggerFactory.getLogger(RemoteRenderProvider.class);

    private final RemoteRenderDispatcher dispatcher;

    @Value("${app.remote-worker.callback-url:http://localhost:8088}")
    private String callbackBaseUrl;

    public RemoteRenderProvider(RemoteRenderDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public RenderResult render(String jobId, String timelineJson, String profile) {
        log.info("RemoteRenderProvider: dispatching job={} profile={}", jobId, profile);

        RemoteRenderDispatcher.RemoteJobTracker tracker = dispatcher.dispatch(
                jobId, profile, timelineJson, callbackBaseUrl);

        log.info("RemoteRenderProvider: job {} dispatched to worker {}", jobId, tracker.workerId());

        return new RenderResult(
                "remote-" + jobId,
                "remote://worker/" + tracker.workerId() + "/artifacts/" + jobId,
                0, "mp4", "pending");
    }

    @Override
    public List<String> getSupportedProfiles() {
        return List.of("remote_720p", "remote_1080p", "remote_4k", "remote_social_1080p");
    }

    @Override
    public boolean supports(String profile) {
        return profile != null && (profile.startsWith("remote_") || profile.contains("remote"));
    }
}
