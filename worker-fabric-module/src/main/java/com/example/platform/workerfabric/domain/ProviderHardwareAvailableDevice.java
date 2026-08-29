package com.example.platform.workerfabric.domain;

import java.util.List;
import java.util.Objects;

/** Complete successful evidence for one exact exposed and available device. */
public record ProviderHardwareAvailableDevice(
        DeviceKind kind,
        DeviceVendor vendor,
        DeviceModel model,
        DriverRuntimeObservation driverRuntime,
        List<String> availableFeatures)
        implements ProviderHardwareDeviceEvidence {

    public ProviderHardwareAvailableDevice {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(vendor, "vendor");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(driverRuntime, "driverRuntime");
        availableFeatures = RuntimeDependencyNames.canonicalize(
                availableFeatures, "availableFeatures");
    }
}
