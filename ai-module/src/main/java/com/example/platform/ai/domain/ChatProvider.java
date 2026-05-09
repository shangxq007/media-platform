package com.example.platform.ai.domain;

public interface ChatProvider {
    ChatResult chat(ChatRequest request);
}