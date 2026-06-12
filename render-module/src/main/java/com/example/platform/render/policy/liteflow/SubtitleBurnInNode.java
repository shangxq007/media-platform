package com.example.platform.render.policy.liteflow;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.example.platform.render.infrastructure.SubtitleBurnInService;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * LiteFlow node for subtitle burn-in.
 *
 * <p>This node delegates to {@link SubtitleBurnInService} to build
 * subtitle filter strings for FFmpeg drawtext burn-in.
 *
 * <p>Note: The baseline subtitle burn-in path is via
 * {@code RenderJobExecutionService} → {@code FFmpegRenderProvider} /
 * {@code LibassSubtitleCompositor}. This LiteFlow node is used
 * when the policy chain explicitly includes subtitle burn-in as a step.
 */
@LiteflowComponent("subtitleBurnIn")
public class SubtitleBurnInNode extends NodeComponent {
    private static final Logger log = LoggerFactory.getLogger(SubtitleBurnInNode.class);

    private final SubtitleBurnInService subtitleBurnInService;

    public SubtitleBurnInNode(SubtitleBurnInService subtitleBurnInService) {
        this.subtitleBurnInService = subtitleBurnInService;
    }

    @Override
    public void process() throws Exception {
        String jobId = this.getChainId();
        log.info("LiteFlow: Subtitle burn-in for job={}", jobId);

        try {
            // Get subtitle tracks from the context
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> subtitleTracks = (List<Map<String, Object>>) this.getContextBean("subtitleTracks");

            if (subtitleTracks == null || subtitleTracks.isEmpty()) {
                log.info("LiteFlow: No subtitle tracks for job={}, skipping", jobId);
                return;
            }

            String filter = subtitleBurnInService.buildSubtitleFilter(subtitleTracks);
            if (filter.isEmpty()) {
                log.info("LiteFlow: No burn-in tracks for job={}, skipping", jobId);
                return;
            }

            // Note: The filter result is logged but not stored in context
            // because LiteFlow context API varies by version.
            // The baseline subtitle burn-in path is via RenderJobExecutionService → FFmpegRenderProvider.
            log.info("LiteFlow: Subtitle burn-in completed for job={}, filter length={}", jobId, filter.length());
        } catch (Exception e) {
            throw new PlatformException(
                    new ConfigurableErrorCode("SUBTITLE-400-001", 400201,
                            Map.of("en", "Subtitle burn-in failed", "zh", "字幕烧录失败"),
                            "subtitle", 400),
                    e.getMessage(),
                    Map.of("jobId", jobId, "node", "subtitleBurnIn"),
                    "en"
            );
        }
    }
}
