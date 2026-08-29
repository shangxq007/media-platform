package com.example.platform.workerfabric.domain;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Exact typed device constraints declared by one provider implementation. */
public record ProviderHardwareDeviceRequirement(
        DeviceKind kind,
        Optional<DeviceVendor> vendorConstraint,
        Optional<DeviceModel> modelConstraint,
        DriverRuntimeRequirement driverRuntimeRequirement,
        List<String> requiredDeviceFeatures)
        implements Serializable {

    public ProviderHardwareDeviceRequirement {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(vendorConstraint, "vendorConstraint");
        Objects.requireNonNull(modelConstraint, "modelConstraint");
        Objects.requireNonNull(driverRuntimeRequirement, "driverRuntimeRequirement");
        requiredDeviceFeatures = RuntimeDependencyNames.canonicalize(
                requiredDeviceFeatures, "requiredDeviceFeatures");
    }
}
