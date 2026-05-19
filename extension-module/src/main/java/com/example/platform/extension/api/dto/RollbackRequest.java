package com.example.platform.extension.api.dto;

public record RollbackRequest(
        String targetVersion,
        String rolledBackBy
) {}
