package com.example.platform.prompt.api.dto;

import java.util.Map;

public record RenderPromptRequest(String templateCode, Map<String, Object> variables) {}
