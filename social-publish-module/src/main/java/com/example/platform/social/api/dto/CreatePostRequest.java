package com.example.platform.social.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreatePostRequest(
        @NotBlank String contentText,
        List<String> mediaUrls,
        @NotBlank String platformType
) {}
