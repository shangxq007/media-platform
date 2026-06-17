package com.example.platform.app.ai;

import com.example.platform.ai.domain.ChatProvider;
import com.example.platform.ai.domain.ChatRequest;
import com.example.platform.ai.domain.ChatResult;
import com.example.platform.shared.web.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LiteLLM / OpenAI-compatible chat with per-tenant virtual key from {@link TenantLitellmKeyService}.
 */
@Component("openAiChatProvider")

@ConditionalOnProperty(prefix = "app.ai.providers.openai", name = "enabled", havingValue = "true")
@ConditionalOnProperty(
        prefix = "app.ai.providers.openai",
        name = "tenant-virtual-keys-enabled",
        havingValue = "true")
public class TenantAwareLitellmChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(TenantAwareLitellmChatProvider.class);

    private final TenantLitellmKeyService litellmKeyService;
    private final ChatClient platformChatClient;
    private final String baseUrl;
    private final String backendLabel;

    public TenantAwareLitellmChatProvider(
            TenantLitellmKeyService litellmKeyService,
            ChatClient.Builder chatClientBuilder,
            @Value("${spring.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.providers.openai.backend-label:litellm}") String backendLabel) {
        this.litellmKeyService = litellmKeyService;
        this.platformChatClient = chatClientBuilder.build();
        this.baseUrl = baseUrl;
        this.backendLabel = backendLabel;
        log.info("TenantAwareLitellmChatProvider enabled baseUrl={} backend={}", baseUrl, backendLabel);
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        String tenantId = TenantContext.get();
        TenantLitellmKeyService.ResolvedLitellmKey resolved = litellmKeyService.resolveForTenant(tenantId);
        if (resolved.tenantScoped()) {
            log.debug("LiteLLM chat tenant={} keySource={}", tenantId, resolved.source());
            return invokeWithApiKey(resolved.apiKey(), request);
        }
        return invokePlatformClient(request);
    }

    private ChatResult invokePlatformClient(ChatRequest request) {
        var prompt = platformChatClient.prompt().user(request.prompt());
        if (request.model() != null && !request.model().isBlank()) {
            prompt = prompt.options(OpenAiChatOptions.builder().model(request.model()).build());
        }
        String content = prompt.call().content();
        String model = request.model() != null && !request.model().isBlank() ? request.model() : "default";
        return new ChatResult(backendLabel, model, content != null ? content : "");
    }

    private ChatResult invokeWithApiKey(String apiKey, ChatRequest request) {
        OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey).build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder().openAiApi(openAiApi).build();
        ChatClient client = ChatClient.builder(chatModel).build();
        var prompt = client.prompt().user(request.prompt());
        if (request.model() != null && !request.model().isBlank()) {
            prompt = prompt.options(OpenAiChatOptions.builder().model(request.model()).build());
        }
        String content = prompt.call().content();
        String model = request.model() != null && !request.model().isBlank() ? request.model() : "default";
        return new ChatResult(backendLabel + "-tenant", model, content != null ? content : "");
    }
}
