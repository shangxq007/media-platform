package com.example.platform.prompt.domain;

public record PromptVersion(String id, String templateId, int version, String content, String changelog) {}
