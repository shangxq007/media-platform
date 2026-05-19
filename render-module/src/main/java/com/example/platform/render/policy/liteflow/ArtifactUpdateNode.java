package com.example.platform.render.policy.liteflow;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

@LiteflowComponent("artifactUpdate")
public class ArtifactUpdateNode extends NodeComponent {
    private static final Logger log = LoggerFactory.getLogger(ArtifactUpdateNode.class);

    @Override
    public void process() throws Exception {
        String jobId = this.getChainId();
        log.info("LiteFlow: Artifact update for job={}", jobId);
        try {
            log.info("LiteFlow: Artifact updated for job={}", jobId);
        } catch (Exception e) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-001", 500101,
                            Map.of("en", "Artifact update failed", "zh", "产物更新失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "node", "artifactUpdate"),
                    "en"
            );
        }
    }
}
