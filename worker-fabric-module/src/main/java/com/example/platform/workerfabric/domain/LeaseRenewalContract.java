package com.example.platform.workerfabric.domain;

import java.time.Duration;
import java.util.Objects;

/** Immutable timing contract; Task E owns heartbeat/expiry runtime handling. */
public record LeaseRenewalContract(Duration heartbeatInterval, Duration leaseDuration) {

    public static final LeaseRenewalContract NATIVE_PULL_V1 =
            new LeaseRenewalContract(Duration.ofSeconds(15), Duration.ofSeconds(60));

    public LeaseRenewalContract {
        Objects.requireNonNull(heartbeatInterval, "heartbeatInterval");
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (heartbeatInterval.isZero() || heartbeatInterval.isNegative()) {
            throw new IllegalArgumentException("heartbeat interval must be positive");
        }
        if (leaseDuration.isZero() || leaseDuration.isNegative()
                || leaseDuration.compareTo(heartbeatInterval) <= 0) {
            throw new IllegalArgumentException(
                    "lease duration must be greater than the heartbeat interval");
        }
    }
}
