package com.example.platform.federation.graphql.context;

import java.util.List;

public record GraphQLRequestContext(
    String tenantId,
    String workspaceId,
    String userId,
    List<String> roles,
    List<String> permissions,
    String requestSource,
    String authType,
    String traceId,
    String requestId,
    String ip,
    String userAgent
) {}
