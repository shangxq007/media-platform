package com.example.platform.ai.infrastructure;

import com.example.platform.ai.api.AiProviderUnavailableException;
import com.example.platform.ai.domain.ChatProvider;
import com.example.platform.ai.domain.ChatRequest;
import com.example.platform.ai.domain.ChatResult;
import org.springframework.stereotype.Component;

/** Fail-closed default used until a real chat provider is composed. */
@Component("unavailableChatProvider")
public final class UnavailableChatProvider implements ChatProvider {

    @Override
    public ChatResult chat(ChatRequest request) {
        throw new AiProviderUnavailableException(request.capability());
    }
}
