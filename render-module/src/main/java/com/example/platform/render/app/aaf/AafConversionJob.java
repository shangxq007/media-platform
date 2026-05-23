package com.example.platform.render.app.aaf;

import java.time.Instant;

public record AafConversionJob(
        String conversionId,
        String aafPath,
        String defaultMediaUri,
        String tenantId,
        Instant enqueuedAt) {}
