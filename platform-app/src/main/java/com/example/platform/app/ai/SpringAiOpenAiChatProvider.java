package com.example.platform.app.ai;

import com.example.platform.ai.domain.ChatProvider;
import com.example.platform.ai.domain.ChatRequest;
import com.example.platform.ai.domain.ChatResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * ChatProvider backed by Spring AI {@link ChatClient}, targeting any OpenAI-compatible endpoint
 * (OpenAI, NVIDIA NIM, LiteLLM proxy, etc.) via {@code spring.ai.openai.base-url}.
 */
@Component("openAiChatProvider")

@ConditionalOnProperty(prefix = "app.ai.providers.openai", name = "enabled", havingValue = "true")
@ConditionalOnProperty(
        prefix = "app.ai.providers.openai",
        name = "tenant-virtual-keys-enabled",
        havingValue = "false",
        matchIfMissing = true)
public class SpringAiOpenAiChatProvider implements ChatProvider {

    private static final Logger log = LoggerFactory.getLogger(SpringAiOpenAiChatProvider.class);

    private final ChatClient chatClient;
    private final String backendLabel;

    public SpringAiOpenAiChatProvider(
            ChatClient.Builder chatClientBuilder,
            @Value("${app.ai.providers.openai.backend-label:openai}") String backendLabel) {
        this.chatClient = chatClientBuilder.build();
        this.backendLabel = backendLabel;
        log.info("SpringAiOpenAiChatProvider enabled backend={}", backendLabel);
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        var prompt = chatClient.prompt().user(request.prompt());
        if (request.model() != null && !request.model().isBlank()) {
            prompt = prompt.options(OpenAiChatOptions.builder().model(request.model()).build());
        }
        String content = prompt.call().content();
        String model = request.model() != null && !request.model().isBlank() ? request.model() : "default";
        return new ChatResult(backendLabel, model, content != null ? content : "");
    }
}
