package com.example.platform.timeline.otio;

import java.util.Objects;

/** Stable source identity attached to an exported OTIO timeline. */
public record OtioExportIdentity(
        String tenantId,
        String projectId,
        String revisionId,
        String schemaVersion) {

    public OtioExportIdentity {
        tenantId = require(tenantId, "tenantId");
        projectId = require(projectId, "projectId");
        revisionId = require(revisionId, "revisionId");
        schemaVersion = require(schemaVersion, "schemaVersion");
    }

    private static String require(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
