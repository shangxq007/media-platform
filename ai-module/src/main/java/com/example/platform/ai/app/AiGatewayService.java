package com.example.platform.ai.app;

import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.ai.domain.ChatProvider;
import com.example.platform.ai.domain.ChatRequest;
import com.example.platform.ai.domain.ChatResult;
import com.example.platform.ai.domain.ModelRouter;
import com.example.platform.ai.domain.RoutePlan;
import com.example.platform.ai.domain.RouteTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class AiGatewayService implements AiGatewayPort {
    private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);

    private final ModelRouter router;
    private final Map<String, ChatProvider> providers;

    public AiGatewayService(ModelRouter router, Map<String, ChatProvider> providers) {
        this.router = router;
        this.providers = providers;
    }

    @Override
    public ChatResult chat(String capability, String prompt) {
        return invokeRouted(capability, prompt);
    }

    public AiScriptResult generateScript(String prompt, String profile) {
        log.info("AiGatewayService: generating script for profile={}", profile);
        long start = System.currentTimeMillis();
        ChatResult result = invokeRouted("script-generation", prompt);
        long elapsed = System.currentTimeMillis() - start;
        int tokensUsed = prompt.length() / 4 + result.content().length() / 4;
        log.info("AiGatewayService: script generated in {}ms, tokensUsed={}", elapsed, tokensUsed);
        return new AiScriptResult(result.content(), result.model(), tokensUsed, Instant.now());
    }

    private ChatResult invokeRouted(String capability, String prompt) {
        RoutePlan plan = router.routePlan(capability);
        List<String> attempted = new ArrayList<>();
        RuntimeException lastFailure = null;

        for (RouteTarget target : plan.targets()) {
            ChatProvider provider = providers.get(target.providerId());
            if (provider == null) {
                lastFailure = new IllegalStateException("No ChatProvider bean: " + target.providerId());
                attempted.add(target.providerId() + "(missing)");
                continue;
            }
            attempted.add(target.providerId());
            log.info("AiGatewayService: capability={} provider={} model={}",
                    capability, target.providerId(), target.model());
            try {
                return provider.chat(new ChatRequest(capability, prompt, target.model()));
            } catch (RuntimeException ex) {
                lastFailure = ex;
                log.warn("AiGatewayService: provider {} failed for capability={}: {}",
                        target.providerId(), capability, ex.getMessage());
            }
        }
        throw new IllegalStateException(
                "All providers failed for capability=" + capability + " attempted=" + attempted,
                lastFailure);
    }
}
