package com.example.platform.workerfabric.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Maps a provider's declared {@link ProviderResourceProfile} onto the runtime demand the scheduling
 * chain consumes.
 *
 * <p>Pure function, no state, no policy registry. Bounded V1 maps the declared footprint directly and
 * deliberately does not scale by task parameters (resolution, duration) — that is future work and must
 * not be guessed here.
 *
 * <p><b>Fail closed</b>: an absent profile yields no demand; the caller must treat that as "not
 * schedulable", never as a default.
 */
public final class TaskResourceDemandDeriver {

    private TaskResourceDemandDeriver() {
    }

    /** Direct mapping of a declared profile onto a runtime demand. */
    public static RuntimeResourceDemand derive(ProviderResourceProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return new RuntimeResourceDemand(
                profile.cpuMillicores(),
                profile.memoryBytes(),
                profile.temporaryStorageBytes(),
                profile.deviceDemands());
    }

    /** Fail-closed variant: an undeclared profile produces no demand rather than a default. */
    public static Optional<RuntimeResourceDemand> derive(Optional<ProviderResourceProfile> profile) {
        Objects.requireNonNull(profile, "profile");
        return profile.map(TaskResourceDemandDeriver::derive);
    }
}
