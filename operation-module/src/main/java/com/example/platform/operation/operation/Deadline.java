package com.example.platform.operation.operation;

import java.time.Duration;
import java.util.Objects;

/**
 * Provider-neutral execution deadline (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b).
 *
 * <p>A bounded maximum wall-clock duration. It carries no provider/backend
 * identity; provider-native timeout classes live in the implementation support
 * envelope.</p>
 */
public record Deadline(Duration maxWallClock) {

    public Deadline {
        Objects.requireNonNull(maxWallClock, "maxWallClock");
        if (maxWallClock.isZero() || maxWallClock.isNegative()) {
            throw new IllegalArgumentException("deadline must be positive: " + maxWallClock);
        }
    }
}
