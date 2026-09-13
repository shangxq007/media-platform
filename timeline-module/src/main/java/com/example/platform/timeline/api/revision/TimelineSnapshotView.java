package com.example.platform.timeline.api.revision;
public record TimelineSnapshotView(
            String id,
            String projectId,
            String tenantId,
            String payloadJson,
            String schemaVersion) {}
