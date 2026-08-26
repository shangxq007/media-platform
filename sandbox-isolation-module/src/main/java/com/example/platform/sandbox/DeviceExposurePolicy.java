package com.example.platform.sandbox;

import java.util.Set;

/** Opaque pre-authorized device references only; no device discovery or identity authority. */
@org.springframework.modulith.NamedInterface("API")
public record DeviceExposurePolicy(Set<String> grantedDeviceReferences) {
    public DeviceExposurePolicy {
        grantedDeviceReferences = Set.copyOf(grantedDeviceReferences);
        if (grantedDeviceReferences.stream().anyMatch(reference -> reference == null || reference.isBlank())) {
            throw new IllegalArgumentException("device references must be non-blank");
        }
    }
    public static DeviceExposurePolicy none() { return new DeviceExposurePolicy(Set.of()); }
    public static DeviceExposurePolicy granted(Set<String> references) {
        return new DeviceExposurePolicy(references);
    }
}
