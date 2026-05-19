package com.example.platform.federation.nlq.api.dto;

public record NlqExecuteRequest(
    String sql,
    String question,
    String userId,
    String workspaceId,
    Integer maxRows,
    Boolean confirmed
) {}
