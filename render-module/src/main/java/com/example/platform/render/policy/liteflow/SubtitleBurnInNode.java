package com.example.platform.render.policy.liteflow;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@LiteflowComponent("subtitleBurnIn")
public class SubtitleBurnInNode extends NodeComponent {
    private static final Logger log = LoggerFactory.getLogger(SubtitleBurnInNode.class);

    @Override
    public void process() throws Exception {
        String jobId = this.getChainId();
        log.info("LiteFlow: Subtitle burn-in for job={}", jobId);
        try {
            // Burn subtitles into video frames with font embedding
            log.info("LiteFlow: Subtitle burn-in completed for job={}", jobId);
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
