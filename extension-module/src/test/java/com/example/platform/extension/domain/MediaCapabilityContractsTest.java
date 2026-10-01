package com.example.platform.extension.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.capability.CapabilityParameterContract;
import com.example.platform.shared.capability.MediaFrameExtractParametersV1;
import org.junit.jupiter.api.Test;

class MediaCapabilityContractsTest {

    @Test
    void frameExtractIsProviderNeutralTypedCapabilityContract() {
        CapabilityContractDescriptor frameExtract = MediaCapabilityContracts.FRAME_EXTRACT;
        assertEquals("media.frame-extract", frameExtract.capabilityId().value());
        assertTrue(frameExtract.capabilityId().isPlatformReserved());
        assertEquals(ContractVersion.of(1, 0), frameExtract.contractVersion());
        assertTrue(frameExtract.parameterType() == MediaFrameExtractParametersV1.class);
        assertTrue(CapabilityParameterContract.class.isAssignableFrom(frameExtract.parameterType()));
        assertEquals("MediaFrameSourceRef", frameExtract.inputReferenceType());
        assertEquals("RasterFrameImage", frameExtract.outputReferenceType());
    }
}
