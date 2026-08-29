package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Closed device evidence states for one exact assigned device. */
public sealed interface ProviderHardwareDeviceEvidence extends Serializable
        permits ProviderHardwareAvailableDevice,
                ProviderHardwareUnavailableDevice,
                ProviderHardwareNotExposedDevice {}
