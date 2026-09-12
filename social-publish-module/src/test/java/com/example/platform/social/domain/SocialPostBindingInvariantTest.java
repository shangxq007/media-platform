package com.example.platform.social.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocialPostBindingInvariantTest {

    @Test
    void legacyMutationRecordMayRemainExplicitlyUnbound() {
        SocialPost post = assertDoesNotThrow(() -> post(null, null, null, null, "content"));

        assertNull(post.projectId());
        assertNull(post.connectedPlatformId());
        assertNull(post.connectedPlatformBindingVersion());
    }

    @Test
    void boundRecordRequiresExactCompleteBindingButNotOptionalContentOrArtifact() {
        assertDoesNotThrow(() -> post("project-1", "account-1", 3L, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> post("project-1", null, null, null, "content"));
        assertThrows(IllegalArgumentException.class,
                () -> post("project-1", "account-1", 0L, null, "content"));
    }

    private static SocialPost post(
            String projectId, String accountId, Long bindingVersion,
            String artifactId, String contentText) {
        return new SocialPost(
                "post-1", "tenant-a", "actor-1", projectId, accountId, bindingVersion, artifactId,
                contentText, List.of(), PlatformType.YOUTUBE, PostStatus.DRAFT,
                null, null, null, null, null, null, null, 0, Instant.EPOCH, Instant.EPOCH);
    }
}
