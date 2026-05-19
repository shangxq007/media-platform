package com.example.platform.federation.graphql.dto;

import java.util.List;

public record ExportValidation(
        boolean allowed,
        List<String> violations,
        List<String> recommendations
) {}
