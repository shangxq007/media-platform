package com.example.platform.federation.nlq.api.dto;

import java.util.List;

public record NlqPreviewRequest(
    String question,
    String userId,
    String workspaceId,
    List<String> roles,
    List<String> permissions
) {}
