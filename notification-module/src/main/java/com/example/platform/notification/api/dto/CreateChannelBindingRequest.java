package com.example.platform.notification.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateChannelBindingRequest(
        @NotBlank String channelType,
        @NotBlank String destination
) {}
