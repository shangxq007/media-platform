package com.example.platform.social.api.dto;

import java.util.List;

public record PostHistoryResponse(
        List<PublishPostResponse> posts,
        long total,
        int page,
        int size
) {}
