package com.example.platform.ai.app;

import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.ai.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
        String key = router.route(capability);
        ChatProvider provider = providers.get(key);
        if (provider == null) throw new IllegalStateException("No provider: " + key);
        log.info("AiGatewayService: routing capability={} to provider={}", capability, key);
        return provider.chat(new ChatRequest(capability, prompt));
    }

    public AiScriptResult generateScript(String prompt, String profile) {
        log.info("AiGatewayService: generating script for profile={}", profile);
        String key = router.route("script-generation");
        ChatProvider provider = providers.get(key);
        if (provider == null) throw new IllegalStateException("No provider: " + key);
        long start = System.currentTimeMillis();
        ChatResult result = provider.chat(new ChatRequest("script-generation", prompt));
        long elapsed = System.currentTimeMillis() - start;
        int tokensUsed = prompt.length() / 4 + result.content().length() / 4;
        log.info("AiGatewayService: script generated in {}ms, tokensUsed={}", elapsed, tokensUsed);
        return new AiScriptResult(result.content(), result.model(), tokensUsed, Instant.now());
    }
}
