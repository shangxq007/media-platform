package com.example.platform.render.api.rawmedia;

import java.time.Instant;

/** Boundary-safe result for an uploaded raw-media Product registration. */
public record RawMediaProductRegistrationResult(
        String productId,
        Instant createdAt
) {
}
