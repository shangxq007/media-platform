package com.example.platform.notification.api.dto;

import java.util.List;

public record UpdateSubscriptionRequest(
        boolean enabled,
        List<String> channels
) {}
