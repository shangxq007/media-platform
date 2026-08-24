package com.example.platform.workerfabric.domain;

import java.util.Objects;

/** Current availability evidence for one exact device candidate. */
public record DeviceAvailability(DeviceId deviceId, AvailabilityState state) {

    public DeviceAvailability {
        Objects.requireNonNull(deviceId, "deviceId");
        Objects.requireNonNull(state, "state");
    }

    public boolean isReachable() {
        return state == AvailabilityState.REACHABLE;
    }
}
