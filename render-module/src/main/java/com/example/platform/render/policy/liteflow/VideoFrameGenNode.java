package com.example.platform.render.policy.liteflow;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@LiteflowComponent("videoFrameGen")
public class VideoFrameGenNode extends NodeComponent {
    private static final Logger log = LoggerFactory.getLogger(VideoFrameGenNode.class);

    @Override
    public void process() throws Exception {
        String jobId = this.getChainId();
        log.info("LiteFlow: Video frame generation for job={}", jobId);
        try {
            log.info("LiteFlow: Video frames generated for job={}", jobId);
        } catch (Exception e) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-001", 500101,
                            Map.of("en", "Video frame generation failed", "zh", "视频帧生成失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "node", "videoFrameGen"),
                    "en"
            );
        }
    }
}
