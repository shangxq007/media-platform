package com.example.platform.compatibility.domain;

import java.util.Map;

public record VersionedPayload(
        SchemaFamily schemaFamily,
        SchemaVersion schemaVersion,
        Map<String, Object> payload,
        Map<String, String> metadata
) {}
