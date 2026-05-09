package com.example.platform.ai.infrastructure;

import com.example.platform.ai.domain.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component("openAiChatProvider")
@ConditionalOnProperty(prefix = "app.ai", name = "default-chat-provider", havingValue = "openAiChatProvider")
public class OpenAiChatProvider implements ChatProvider {
    @Override
    public ChatResult chat(ChatRequest request) {
        return new ChatResult("openai", "configured-later", "Replace with Spring AI ChatClient integration");
    }
}