package com.example.platform.execution.domain.provider;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityImplementationId;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderImmutableFoundationDeterminismTest {

    private static final ProviderId PROVIDER_ID = ProviderId.of("ffmpeg");
    private static final ProviderImplementationId IMPLEMENTATION_ID =
            ProviderImplementationId.of("org.media-platform.ffmpeg.adapter");
    private static final ProviderVersion PROVIDER_VERSION = ProviderVersion.of("7.1.0");
    private static final ProviderExecutionContractVersion CONTRACT_VERSION =
            ProviderExecutionContractVersion.of(1, 2);
    private static final ProviderCapabilityProfileVersionOrDigest PROFILE_REFERENCE =
            ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(3, 4));
    private static final CapabilityImplementationId DECODE_CPU =
            CapabilityImplementationId.of("org.media-platform.decode.cpu");
    private static final CapabilityImplementationId ENCODE_CPU =
            CapabilityImplementationId.of("org.media-platform.encode.cpu");

    @Test
    void providerBindingPinPermutationHasEqualIdentityHashAndSerialization() {
        ProviderBindingPin first = binding(List.of(ENCODE_CPU, DECODE_CPU));
        ProviderBindingPin permuted = binding(List.of(DECODE_CPU, ENCODE_CPU));

        assertEquals(first, permuted);
        assertEquals(first.hashCode(), permuted.hashCode());
        assertEquals(List.of(DECODE_CPU, ENCODE_CPU), first.capabilityImplementationPins());
        assertArrayEquals(ProviderCanonicalCodec.serialize(first), ProviderCanonicalCodec.serialize(permuted));
    }

    @Test
    void providerBindingPinSerializationIsRepeatableStructurallyFramedUtf8() {
        ProviderBindingPin pin = binding(List.of(ENCODE_CPU, DECODE_CPU));

        byte[] first = ProviderCanonicalCodec.serialize(pin);
        byte[] second = ProviderCanonicalCodec.serialize(pin);

        assertArrayEquals(first, second);
        String canonical = new String(first, StandardCharsets.UTF_8);
        assertTrue(canonical.startsWith("33:roadmap22.provider-binding-pin.v1"));
        assertTrue(canonical.contains("10:providerId"));
        assertTrue(canonical.contains("28:capabilityImplementationPins"));
        assertFalse(canonical.contains(pin.toString()), "record toString must not be serialization authority");
    }

    @Test
    void everyProviderBindingFieldParticipatesInCanonicalSerialization() {
        ProviderBindingPin baseline = binding(List.of(DECODE_CPU));

        assertSerializationDiffers(baseline, new ProviderBindingPin(
                ProviderId.of("blender"), IMPLEMENTATION_ID, PROVIDER_VERSION, CONTRACT_VERSION,
                PROFILE_REFERENCE, List.of(DECODE_CPU)));
        assertSerializationDiffers(baseline, new ProviderBindingPin(
                PROVIDER_ID, ProviderImplementationId.of("org.media-platform.ffmpeg.other"),
                PROVIDER_VERSION, CONTRACT_VERSION, PROFILE_REFERENCE, List.of(DECODE_CPU)));
        assertSerializationDiffers(baseline, new ProviderBindingPin(
                PROVIDER_ID, IMPLEMENTATION_ID, ProviderVersion.of("7.1.1"), CONTRACT_VERSION,
                PROFILE_REFERENCE, List.of(DECODE_CPU)));
        assertSerializationDiffers(baseline, new ProviderBindingPin(
                PROVIDER_ID, IMPLEMENTATION_ID, PROVIDER_VERSION,
                ProviderExecutionContractVersion.of(1, 3), PROFILE_REFERENCE, List.of(DECODE_CPU)));
        assertSerializationDiffers(baseline, new ProviderBindingPin(
                PROVIDER_ID, IMPLEMENTATION_ID, PROVIDER_VERSION, CONTRACT_VERSION,
                ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(3, 5)),
                List.of(DECODE_CPU)));
        assertSerializationDiffers(baseline, binding(List.of(ENCODE_CPU)));
    }

    @Test
    void duplicateCapabilityImplementationPinFailsClosed() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> binding(List.of(DECODE_CPU, DECODE_CPU)));
        assertTrue(failure.getMessage().contains("INVALID_PROVIDER_BINDING"));
    }

    @Test
    void profileAndContractDeclarationOrderIsNonSemantic() {
        ProviderCapabilitySupport decode = ProviderCapabilitySupport.pinned(
                CapabilityId.of("media.decode.video"),
                ContractVersionRange.between(ContractVersion.of(1, 0), ContractVersion.of(1, 3)),
                DECODE_CPU);
        ProviderCapabilitySupport encode = ProviderCapabilitySupport.unpinned(
                CapabilityId.of("video.encode.h264"),
                ContractVersionRange.exactly(ContractVersion.of(2, 0)));
        ProviderCapabilityProfile profileA = new ProviderCapabilityProfile(
                PROFILE_REFERENCE, List.of(encode, decode));
        ProviderCapabilityProfile profileB = new ProviderCapabilityProfile(
                PROFILE_REFERENCE, List.of(decode, encode));

        assertEquals(profileA, profileB);
        assertEquals(profileA.hashCode(), profileB.hashCode());
        assertArrayEquals(ProviderCanonicalCodec.serialize(profileA), ProviderCanonicalCodec.serialize(profileB));

        ProviderCapabilityContractReference decodeReference = new ProviderCapabilityContractReference(
                decode.capabilityId(), decode.contractVersionRange());
        ProviderCapabilityContractReference encodeReference = new ProviderCapabilityContractReference(
                encode.capabilityId(), encode.contractVersionRange());
        ProviderExecutionContract contractA = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1), CONTRACT_VERSION,
                List.of(encodeReference, decodeReference));
        ProviderExecutionContract contractB = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1), CONTRACT_VERSION,
                List.of(decodeReference, encodeReference));

        assertEquals(contractA, contractB);
        assertEquals(contractA.hashCode(), contractB.hashCode());
        assertArrayEquals(ProviderCanonicalCodec.serialize(contractA), ProviderCanonicalCodec.serialize(contractB));
    }

    @Test
    void immutableCollectionsCannotBeMutatedAndDuplicateDeclarationsFailClosed() {
        ProviderCapabilitySupport support = ProviderCapabilitySupport.unpinned(
                CapabilityId.of("media.decode.video"),
                ContractVersionRange.exactly(ContractVersion.of(1, 0)));
        ProviderCapabilityProfile profile = new ProviderCapabilityProfile(PROFILE_REFERENCE, List.of(support));

        assertThrows(UnsupportedOperationException.class, () -> profile.supportDeclarations().add(support));
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderCapabilityProfile(PROFILE_REFERENCE, List.of(support, support)));
        assertThrows(UnsupportedOperationException.class,
                () -> binding(List.of(DECODE_CPU)).capabilityImplementationPins().add(ENCODE_CPU));
    }

    @Test
    void digestAndVersionProfileReferencesAreDistinct() {
        ProviderCapabilityProfileVersionOrDigest version = PROFILE_REFERENCE;
        ProviderCapabilityProfileVersionOrDigest digest =
                ProviderCapabilityProfileVersionOrDigest.digest(
                        ProviderCapabilityProfileDigest.sha256("a".repeat(64)));

        assertNotEquals(version, digest);
        ProviderDescriptor versionDescriptor = new ProviderDescriptor(
                PROVIDER_ID, IMPLEMENTATION_ID, PROVIDER_VERSION, CONTRACT_VERSION, version);
        ProviderDescriptor digestDescriptor = new ProviderDescriptor(
                PROVIDER_ID, IMPLEMENTATION_ID, PROVIDER_VERSION, CONTRACT_VERSION, digest);
        assertFalse(Arrays.equals(
                ProviderCanonicalCodec.serialize(versionDescriptor),
                ProviderCanonicalCodec.serialize(digestDescriptor)));
    }

    @Test
    void providerAndCapabilityAuthoritiesRemainTypedAndDistinct() {
        assertNotEquals(ProviderId.class, CapabilityId.class);
        assertNotEquals(ProviderImplementationId.class, CapabilityImplementationId.class);

        var supportComponents = ProviderCapabilitySupport.class.getRecordComponents();
        assertEquals(CapabilityId.class, supportComponents[0].getType());
        assertEquals(ContractVersionRange.class, supportComponents[1].getType());
        assertEquals(Optional.class, supportComponents[2].getType());

        var bindingComponents = ProviderBindingPin.class.getRecordComponents();
        assertEquals(ProviderId.class, bindingComponents[0].getType());
        assertEquals(ProviderImplementationId.class, bindingComponents[1].getType());
        assertEquals(ProviderVersion.class, bindingComponents[2].getType());
    }

    private static ProviderBindingPin binding(List<CapabilityImplementationId> pins) {
        return new ProviderBindingPin(PROVIDER_ID, IMPLEMENTATION_ID, PROVIDER_VERSION,
                CONTRACT_VERSION, PROFILE_REFERENCE, pins);
    }

    private static void assertSerializationDiffers(ProviderBindingPin first, ProviderBindingPin second) {
        assertFalse(Arrays.equals(
                ProviderCanonicalCodec.serialize(first), ProviderCanonicalCodec.serialize(second)));
    }
}
