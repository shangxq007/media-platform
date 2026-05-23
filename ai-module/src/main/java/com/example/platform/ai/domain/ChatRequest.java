package com.example.platform.ai.domain;

public record ChatRequest(String capability, String prompt, String model) {

    public ChatRequest(String capability, String prompt) {
        this(capability, prompt, null);
    }
}