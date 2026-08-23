package com.example.platform.execution.domain.provider;

import com.example.platform.execution.planning.CanonicalWriter;
import com.example.platform.extension.domain.CapabilityImplementationId;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Deterministic, structurally framed canonical serialization for immutable provider metadata. */
public final class ProviderCanonicalCodec {

    private ProviderCanonicalCodec() {
    }

    public static byte[] serialize(ProviderDescriptor descriptor) {
        Objects.requireNonNull(descriptor, "descriptor");
        return utf8(new CanonicalWriter()
                .tag("roadmap22.provider-descriptor.v1")
                .field("providerId", providerId(descriptor.providerId()))
                .field("providerImplementationId", providerImplementationId(descriptor.providerImplementationId()))
                .field("providerVersion", providerVersion(descriptor.providerVersion()))
                .field("providerExecutionContractVersion",
                        executionContractVersion(descriptor.providerExecutionContractVersion()))
                .field("providerCapabilityProfileReference",
                        profileReference(descriptor.providerCapabilityProfileReference()))
                .build());
    }

    public static byte[] serialize(ProviderExecutionContract contract) {
        Objects.requireNonNull(contract, "contract");
        List<String> references = contract.capabilityContractReferences().stream()
                .map(ProviderCanonicalCodec::capabilityContractReference)
                .sorted()
                .toList();
        return utf8(new CanonicalWriter()
                .tag("roadmap22.provider-execution-contract.v1")
                .field("schemaVersion", schemaVersion(contract.schemaVersion()))
                .field("contractVersion", executionContractVersion(contract.contractVersion()))
                .field("capabilityContractReferences", new CanonicalWriter().list(references).build())
                .build());
    }

    public static byte[] serialize(ProviderCapabilityProfile profile) {
        Objects.requireNonNull(profile, "profile");
        List<String> supports = profile.supportDeclarations().stream()
                .map(ProviderCanonicalCodec::capabilitySupport)
                .sorted()
                .toList();
        return utf8(new CanonicalWriter()
                .tag("roadmap22.provider-capability-profile.v1")
                .field("reference", profileReference(profile.reference()))
                .field("supportDeclarations", new CanonicalWriter().list(supports).build())
                .build());
    }

    public static byte[] serialize(ProviderBindingPin bindingPin) {
        Objects.requireNonNull(bindingPin, "bindingPin");
        List<String> pins = bindingPin.capabilityImplementationPins().stream()
                .map(ProviderCanonicalCodec::capabilityImplementationId)
                .sorted()
                .toList();
        return utf8(new CanonicalWriter()
                .tag("roadmap22.provider-binding-pin.v1")
                .field("providerId", providerId(bindingPin.providerId()))
                .field("providerImplementationId",
                        providerImplementationId(bindingPin.providerImplementationId()))
                .field("providerVersion", providerVersion(bindingPin.providerVersion()))
                .field("providerExecutionContractVersion",
                        executionContractVersion(bindingPin.providerExecutionContractVersion()))
                .field("providerCapabilityProfileVersionOrDigest",
                        profileReference(bindingPin.providerCapabilityProfileVersionOrDigest()))
                .field("capabilityImplementationPins", new CanonicalWriter().list(pins).build())
                .build());
    }

    private static String providerId(ProviderId value) {
        return new CanonicalWriter().tag("ProviderId").field("value", value.value()).build();
    }

    private static String providerImplementationId(ProviderImplementationId value) {
        return new CanonicalWriter().tag("ProviderImplementationId").field("value", value.value()).build();
    }

    private static String providerVersion(ProviderVersion value) {
        return new CanonicalWriter().tag("ProviderVersion").field("value", value.value()).build();
    }

    private static String schemaVersion(ProviderExecutionContractSchemaVersion value) {
        return new CanonicalWriter().tag("ProviderExecutionContractSchemaVersion")
                .field("value", Integer.toString(value.value())).build();
    }

    private static String executionContractVersion(ProviderExecutionContractVersion value) {
        return new CanonicalWriter().tag("ProviderExecutionContractVersion")
                .field("major", Integer.toString(value.major()))
                .field("minor", Integer.toString(value.minor()))
                .build();
    }

    private static String profileReference(ProviderCapabilityProfileVersionOrDigest reference) {
        if (reference instanceof ProviderCapabilityProfileVersionOrDigest.VersionReference versionReference) {
            ProviderCapabilityProfileVersion version = versionReference.version();
            return new CanonicalWriter().tag("ProviderCapabilityProfileVersionReference")
                    .field("major", Integer.toString(version.major()))
                    .field("minor", Integer.toString(version.minor()))
                    .build();
        }
        if (reference instanceof ProviderCapabilityProfileVersionOrDigest.DigestReference digestReference) {
            return new CanonicalWriter().tag("ProviderCapabilityProfileDigestReference")
                    .field("algorithm", "SHA-256")
                    .field("hex", digestReference.digest().sha256Hex())
                    .build();
        }
        throw new IllegalStateException("unknown provider capability profile reference: " + reference.getClass());
    }

    private static String capabilityContractReference(ProviderCapabilityContractReference reference) {
        return new CanonicalWriter().tag("ProviderCapabilityContractReference")
                .field("capabilityId", reference.capabilityId().value())
                .field("contractVersionRange", contractVersionRange(reference.contractVersionRange()))
                .build();
    }

    private static String capabilitySupport(ProviderCapabilitySupport support) {
        String optionalPin = new CanonicalWriter()
                .tag("OptionalCapabilityImplementationId")
                .optional(support.capabilityImplementationPin().isPresent(),
                        support.capabilityImplementationPin()
                                .map(ProviderCanonicalCodec::capabilityImplementationId).orElse(null))
                .build();
        return new CanonicalWriter().tag("ProviderCapabilitySupport")
                .field("capabilityId", support.capabilityId().value())
                .field("contractVersionRange", contractVersionRange(support.contractVersionRange()))
                .field("capabilityImplementationPin", optionalPin)
                .build();
    }

    private static String capabilityImplementationId(CapabilityImplementationId value) {
        return new CanonicalWriter().tag("CapabilityImplementationId")
                .field("value", value.value()).build();
    }

    private static String contractVersionRange(ContractVersionRange range) {
        return new CanonicalWriter().tag("ContractVersionRange")
                .field("min", contractVersion(range.min()))
                .field("max", contractVersion(range.max()))
                .build();
    }

    private static String contractVersion(ContractVersion version) {
        return new CanonicalWriter().tag("ContractVersion")
                .field("major", Integer.toString(version.major()))
                .field("minor", Integer.toString(version.minor()))
                .build();
    }

    private static byte[] utf8(String canonical) {
        return canonical.getBytes(StandardCharsets.UTF_8);
    }
}
