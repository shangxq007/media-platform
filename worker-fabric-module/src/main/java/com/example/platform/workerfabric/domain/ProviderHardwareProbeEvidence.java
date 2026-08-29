package com.example.platform.workerfabric.domain;

import java.io.Serializable;

/** Closed probe outcomes prevent incomplete successful observations. */
public sealed interface ProviderHardwareProbeEvidence extends Serializable
        permits ProviderHardwareAvailableEvidence,
                ProviderHardwareRuntimeUnavailableEvidence,
                ProviderHardwareProbeUnknownEvidence,
                ProviderHardwareProbeFailedEvidence {}
