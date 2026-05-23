package com.example.platform.notification.api.dto;

public record UpdateChannelBindingRequest(
        String destination,
        Boolean enabled
) {}
