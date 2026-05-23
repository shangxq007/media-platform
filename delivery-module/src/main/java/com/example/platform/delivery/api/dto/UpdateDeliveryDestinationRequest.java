package com.example.platform.delivery.api.dto;

import java.util.Map;

public record UpdateDeliveryDestinationRequest(
        String name,
        Boolean enabled,
        Map<String, Object> config,
        String credentialRef,
        Map<String, String> credentials) {}
