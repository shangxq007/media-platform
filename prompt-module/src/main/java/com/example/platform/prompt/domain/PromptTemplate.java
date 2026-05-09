package com.example.platform.prompt.domain;

import java.util.List;

public record PromptTemplate(String id, String code, String content, List<String> variables, String status) {}
