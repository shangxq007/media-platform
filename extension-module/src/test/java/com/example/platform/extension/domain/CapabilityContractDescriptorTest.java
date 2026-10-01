package com.example.platform.extension.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.capability.MediaFrameExtractParametersV1;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import org.junit.jupiter.api.Test;

class CapabilityContractDescriptorTest {

    private static CapabilityContractDescriptor sample() {
        return new CapabilityContractDescriptor(
                CapabilityId.of("media.frame-extract"),
                ContractVersion.of(1, 0),
                MediaFrameExtractParametersV1.class,
                "MediaFrameSourceRef",
                "RasterFrameImage");
    }

    @Test
    void referencesTypedParameterContract() {
        CapabilityContractDescriptor descriptor = sample();
        assertEquals("media.frame-extract", descriptor.capabilityId().value());
        assertEquals(ContractVersion.of(1, 0), descriptor.contractVersion());
        assertTrue(descriptor.parameterType() == MediaFrameExtractParametersV1.class);
    }

    @Test
    void rejectsMissingFields() {
        assertThrows(NullPointerException.class, () -> new CapabilityContractDescriptor(
                null, ContractVersion.of(1, 0), MediaFrameExtractParametersV1.class, "a", "b"));
        assertThrows(NullPointerException.class, () -> new CapabilityContractDescriptor(
                CapabilityId.of("media.frame-extract"), null, MediaFrameExtractParametersV1.class, "a", "b"));
        assertThrows(NullPointerException.class, () -> new CapabilityContractDescriptor(
                CapabilityId.of("media.frame-extract"), ContractVersion.of(1, 0), null, "a", "b"));
        assertThrows(IllegalArgumentException.class, () -> new CapabilityContractDescriptor(
                CapabilityId.of("media.frame-extract"), ContractVersion.of(1, 0),
                MediaFrameExtractParametersV1.class, "", "b"));
    }
}
