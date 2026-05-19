package com.example.platform.compatibility.domain;

public record CompatibilityTarget(
        SchemaFamily schemaFamily,
        SchemaVersion targetVersion,
        boolean force
) {}
