package com.example.platform.extension.domain;

import java.util.List;

public record ToolRunRequest(String executable, List<String> args, long timeoutMillis) {}
