package com.example.platform.notification.api.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

public record BatchUpdateSubscriptionRequest(
        @NotEmpty List<Map<String, Object>> updates
) {}
