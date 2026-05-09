package com.example.platform.notification.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateNotificationEventRequest(@NotBlank String eventType, @NotBlank String subjectId, Map<String, Object> payload) {}