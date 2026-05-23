package com.example.platform.social.api.dto;

import java.util.List;

public record UpdatePostRequest(
        String contentText,
        List<String> mediaUrls,
        String platformType
) {}
