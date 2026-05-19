package com.example.platform.render.policy.liteflow;

import com.yomahub.liteflow.annotation.LiteflowComponent;
import com.yomahub.liteflow.core.NodeComponent;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * LiteFlow node for AI script generation step in render pipeline.
 */
@LiteflowComponent("aiScriptGen")
public class AIScriptGenNode extends NodeComponent {
    private static final Logger log = LoggerFactory.getLogger(AIScriptGenNode.class);

    @Override
    public void process() throws Exception {
        String jobId = this.getChainId();
        log.info("LiteFlow: AI script generation for job={}", jobId);

        try {
            // Get request context
            Object request = this.getFirstContextBean();
            if (request == null) {
                throw new PlatformException(
                        new ConfigurableErrorCode("RENDER-400-001", 400101,
                                Map.of("en", "Invalid render job request", "zh", "渲染任务请求无效"),
                                "render", 400),
                        "Request context is null",
                        Map.of("jobId", jobId),
                        "en"
                );
            }
            log.info("LiteFlow: AI script generation completed for job={}", jobId);
        } catch (PlatformException e) {
            throw e;
        } catch (Exception e) {
            throw new PlatformException(
                    new ConfigurableErrorCode("RENDER-500-001", 500101,
                            Map.of("en", "Render execution failed", "zh", "渲染执行失败"),
                            "render", 500),
                    e.getMessage(),
                    Map.of("jobId", jobId, "node", "aiScriptGen"),
                    "en"
            );
        }
    }
}
