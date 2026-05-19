package com.example.platform.identity.domain;

import java.time.Instant;

public record WorkspaceGroupMember(
        String id,
        String workspaceId,
        String groupId,
        String memberId,
        Instant createdAt) {}
