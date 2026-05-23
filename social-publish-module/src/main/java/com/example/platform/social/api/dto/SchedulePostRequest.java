package com.example.platform.social.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SchedulePostRequest(
        @NotBlank String scheduledAt
) {}
