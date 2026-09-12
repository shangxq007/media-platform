package com.example.platform.social.app;

import java.time.Instant;

/** Internal row projection containing only fields approved for the read contract. */
public record SocialPostReadModel(
        String postId,
        String projectId,
        String connectedAccountId,
        long bindingVersion,
        String contentText,
        String artifactId,
        String platformType,
        Instant scheduledAt) {}
