package com.example.platform.ai.infrastructure;

import com.example.platform.ai.domain.ModelRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimpleModelRouter implements ModelRouter {
    private final String defaultProvider;
    public SimpleModelRouter(@Value("${app.ai.default-chat-provider:stubChatProvider}") String defaultProvider) { this.defaultProvider = defaultProvider; }
    @Override public String route(String capability) { return defaultProvider; }
}