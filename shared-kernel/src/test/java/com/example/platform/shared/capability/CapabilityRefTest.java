package com.example.platform.shared.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CapabilityRefTest {

    @Test
    void carriesCapabilityIdentityAndContractRange() {
        CapabilityRef ref = new CapabilityRef(
                CapabilityId.of("media.frame-extract"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)));
        assertEquals("media.frame-extract", ref.capabilityId().value());
        assertTrue(ref.capabilityId().isPlatformReserved());
        assertEquals(ContractVersion.of(1, 0), ref.versionRange().min());
        assertEquals(ContractVersion.of(1, 0), ref.versionRange().max());
    }

    @Test
    void rejectsMissingComponents() {
        assertThrows(NullPointerException.class,
                () -> new CapabilityRef(null, ContractVersionRange.exactly(ContractVersion.of(1, 0))));
        assertThrows(NullPointerException.class,
                () -> new CapabilityRef(CapabilityId.of("media.frame-extract"), null));
    }

    @Test
    void rangeAndIdentityParticipateInEquality() {
        CapabilityRef a = new CapabilityRef(CapabilityId.of("media.frame-extract"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)));
        CapabilityRef b = new CapabilityRef(CapabilityId.of("media.frame-extract"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)));
        CapabilityRef c = new CapabilityRef(CapabilityId.of("media.thumbnail"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)));
        assertEquals(a, b);
        assertNotEquals(a, c);
    }
}
