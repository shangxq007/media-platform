package com.example.platform.policy.domain;

import java.util.List;
import java.util.Map;

public record ExplainResult(String decision, String explanation, List<String> conflicts) {}
