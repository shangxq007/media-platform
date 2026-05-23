package com.example.platform.delivery.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateDeliveryDestinationRequest(
        @NotBlank String name,
        @NotBlank String protocol,
        Map<String, Object> config,
        /** Optional explicit ref, e.g. {@code vault:media-platform/delivery/...} */
        String credentialRef,
        Map<String, String> credentials,
        Boolean enabled) {}
