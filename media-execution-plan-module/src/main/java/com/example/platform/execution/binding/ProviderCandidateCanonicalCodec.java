package com.example.platform.execution.binding;

import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityContractReference;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderCapabilitySupport;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.planning.CanonicalWriter;
import com.example.platform.extension.domain.CapabilityImplementationId;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Bounded V1 canonical codec for {@link ProviderCandidate} — one deterministic
 * byte form plus its exact inverse.
 *
 * <p>Format version is carried in the stream header; unknown headers, unknown
 * enum/variant names and malformed frames fail closed with
 * {@link UnsupportedPersistedConstructException}. The declaration's determinism
 * vocabulary is the provider-side one (P2-4b), so no render type is referenced.
 */
public final class ProviderCandidateCanonicalCodec {

    public static final String FORMAT = "p25a.provider-candidate.v1";

    private ProviderCandidateCanonicalCodec() {
    }

    public static byte[] encode(ProviderCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        String canonical = new CanonicalWriter()
                .tag(FORMAT)
                .field("bindingPin", bindingPin(candidate.bindingPin()))
                .field("descriptor", descriptor(candidate.descriptor()))
                .field("executionContract", executionContract(candidate.executionContract()))
                .field("capabilityProfile", capabilityProfile(candidate.capabilityProfile()))
                .field("staticCompatibility", staticCompatibility(candidate.staticCompatibility()))
                .build();
        return canonical.getBytes(StandardCharsets.UTF_8);
    }

    public static ProviderCandidate decode(byte[] payload) {
        Objects.requireNonNull(payload, "payload");
        FrameReader reader = FrameReader.of(payload);
        reader.requireTag(FORMAT);
        ProviderBindingPin bindingPin = bindingPin(reader.fieldNested("bindingPin"));
        ProviderDescriptor descriptor = descriptor(reader.fieldNested("descriptor"));
        ProviderExecutionContract contract = executionContract(reader.fieldNested("executionContract"));
        ProviderCapabilityProfile profile = capabilityProfile(reader.fieldNested("capabilityProfile"));
        ProviderStaticCompatibility compatibility =
                staticCompatibility(reader.fieldNested("staticCompatibility"));
        if (reader.hasRemaining()) {
            throw new UnsupportedPersistedConstructException("trailing bytes after provider candidate");
        }
        return new ProviderCandidate(bindingPin, descriptor, contract, profile, compatibility);
    }

    public static String digestHex(ProviderCandidate candidate) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(encode(candidate)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    // ---------- binding pin ----------

    static String bindingPin(ProviderBindingPin pin) {
        List<String> pins = pin.capabilityImplementationPins().stream()
                .map(value -> value.value())
                .sorted()
                .toList();
        return new CanonicalWriter()
                .tag("ProviderBindingPin")
                .field("providerId", pin.providerId().value())
                .field("implementationId", pin.providerImplementationId().value())
                .field("version", pin.providerVersion().value())
                .field("contractVersion", contractVersion(pin.providerExecutionContractVersion()))
                .field("profileReference", profileReference(pin.providerCapabilityProfileVersionOrDigest()))
                .list(pins)
                .build();
    }

    static ProviderBindingPin bindingPin(FrameReader reader) {
        reader.requireTag("ProviderBindingPin");
        ProviderId providerId = ProviderId.of(reader.field("providerId"));
        ProviderImplementationId implementationId =
                ProviderImplementationId.of(reader.field("implementationId"));
        ProviderVersion version = ProviderVersion.of(reader.field("version"));
        ProviderExecutionContractVersion contractVersion =
                contractVersion(reader.fieldNested("contractVersion"));
        ProviderCapabilityProfileVersionOrDigest profileReference =
                profileReference(reader.fieldNested("profileReference"));
        int count = reader.listCount();
        List<CapabilityImplementationId> pins = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            pins.add(CapabilityImplementationId.of(reader.text()));
        }
        return new ProviderBindingPin(
                providerId, implementationId, version, contractVersion, profileReference, pins);
    }

    // ---------- descriptor / contract / profile ----------

    private static String descriptor(ProviderDescriptor descriptor) {
        return new CanonicalWriter()
                .tag("ProviderDescriptor")
                .field("providerId", descriptor.providerId().value())
                .field("implementationId", descriptor.providerImplementationId().value())
                .field("version", descriptor.providerVersion().value())
                .field("contractVersion", contractVersion(descriptor.providerExecutionContractVersion()))
                .field("profileReference", profileReference(descriptor.providerCapabilityProfileReference()))
                .build();
    }

    private static ProviderDescriptor descriptor(FrameReader reader) {
        reader.requireTag("ProviderDescriptor");
        ProviderId providerId = ProviderId.of(reader.field("providerId"));
        ProviderImplementationId implementationId =
                ProviderImplementationId.of(reader.field("implementationId"));
        ProviderVersion version = ProviderVersion.of(reader.field("version"));
        ProviderExecutionContractVersion contractVersion =
                contractVersion(reader.fieldNested("contractVersion"));
        ProviderCapabilityProfileVersionOrDigest profileReference =
                profileReference(reader.fieldNested("profileReference"));
        return new ProviderDescriptor(
                providerId, implementationId, version, contractVersion, profileReference);
    }

    private static String executionContract(ProviderExecutionContract contract) {
        List<String> references = contract.capabilityContractReferences().stream()
                .map(ProviderCandidateCanonicalCodec::capabilityContractReference)
                .sorted()
                .toList();
        return new CanonicalWriter()
                .tag("ProviderExecutionContract")
                .exactLong(contract.schemaVersion().value())
                .field("contractVersion", contractVersion(contract.contractVersion()))
                .list(references)
                .build();
    }

    private static ProviderExecutionContract executionContract(FrameReader reader) {
        reader.requireTag("ProviderExecutionContract");
        ProviderExecutionContractSchemaVersion schema =
                ProviderExecutionContractSchemaVersion.of(reader.exactInt());
        ProviderExecutionContractVersion contractVersion =
                contractVersion(reader.fieldNested("contractVersion"));
        int count = reader.listCount();
        List<ProviderCapabilityContractReference> references = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FrameReader nested = reader.nested();
            nested.requireTag("ProviderCapabilityContractReference");
            references.add(new ProviderCapabilityContractReference(
                    CapabilityId.of(nested.field("capabilityId")),
                    contractVersionRange(nested.fieldNested("range"))));
        }
        return new ProviderExecutionContract(schema, contractVersion, references);
    }

    private static String capabilityContractReference(ProviderCapabilityContractReference reference) {
        return new CanonicalWriter()
                .tag("ProviderCapabilityContractReference")
                .field("capabilityId", reference.capabilityId().value())
                .field("range", contractVersionRange(reference.contractVersionRange()))
                .build();
    }

    private static String capabilityProfile(ProviderCapabilityProfile profile) {
        List<String> supports = profile.supportDeclarations().stream()
                .map(ProviderCandidateCanonicalCodec::capabilitySupport)
                .sorted()
                .toList();
        return new CanonicalWriter()
                .tag("ProviderCapabilityProfile")
                .field("reference", profileReference(profile.reference()))
                .list(supports)
                .build();
    }

    private static ProviderCapabilityProfile capabilityProfile(FrameReader reader) {
        reader.requireTag("ProviderCapabilityProfile");
        ProviderCapabilityProfileVersionOrDigest reference =
                profileReference(reader.fieldNested("reference"));
        int count = reader.listCount();
        List<ProviderCapabilitySupport> supports = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            FrameReader nested = reader.nested();
            nested.requireTag("ProviderCapabilitySupport");
            CapabilityId capabilityId = CapabilityId.of(nested.field("capabilityId"));
            ContractVersionRange range = contractVersionRange(nested.fieldNested("range"));
            String pin = nested.optional();
            supports.add(pin == null
                    ? ProviderCapabilitySupport.unpinned(capabilityId, range)
                    : ProviderCapabilitySupport.pinned(
                            capabilityId, range, CapabilityImplementationId.of(pin)));
        }
        return new ProviderCapabilityProfile(reference, supports);
    }

    private static String capabilitySupport(ProviderCapabilitySupport support) {
        return new CanonicalWriter()
                .tag("ProviderCapabilitySupport")
                .field("capabilityId", support.capabilityId().value())
                .field("range", contractVersionRange(support.contractVersionRange()))
                .optional(support.capabilityImplementationPin().isPresent(),
                        support.capabilityImplementationPin().map(value -> value.value()).orElse(null))
                .build();
    }

    // ---------- static compatibility ----------

    private static String staticCompatibility(ProviderStaticCompatibility compatibility) {
        return new CanonicalWriter()
                .tag("ProviderStaticCompatibility")
                .field("knowledge", compatibility.knowledge().name())
                .list(names(compatibility.supportedArtifactRequirements()))
                .list(values(compatibility.supportedCodecs(), codec -> codec.value()))
                .list(names(compatibility.supportedDeviceKinds()))
                .list(names(compatibility.supportedRuntimeClasses()))
                .list(names(compatibility.supportedSandboxModes()))
                .list(names(compatibility.supportedDeterminismClasses()))
                .list(values(compatibility.supportedBoundaryContracts(),
                        boundary -> boundary.value()))
                .field("loweringSupport", compatibility.loweringSupport().name())
                .build();
    }

    private static ProviderStaticCompatibility staticCompatibility(FrameReader reader) {
        reader.requireTag("ProviderStaticCompatibility");
        ProviderStaticCompatibility.Knowledge knowledge = enumValue(
                ProviderStaticCompatibility.Knowledge.class, reader.field("knowledge"));
        List<ProviderStaticCompatibility.ArtifactRequirementKind> artifacts =
                enumList(ProviderStaticCompatibility.ArtifactRequirementKind.class, reader);
        List<StaticCompatibilityConstraint.CodecId> codecs = new ArrayList<>();
        int codecCount = reader.listCount();
        for (int i = 0; i < codecCount; i++) {
            codecs.add(StaticCompatibilityConstraint.CodecId.of(reader.text()));
        }
        List<StaticCompatibilityConstraint.ProviderDeviceKind> devices =
                enumList(StaticCompatibilityConstraint.ProviderDeviceKind.class, reader);
        List<StaticCompatibilityConstraint.ProviderRuntimeClass> runtimes =
                enumList(StaticCompatibilityConstraint.ProviderRuntimeClass.class, reader);
        List<ProviderStaticCompatibility.SandboxMode> sandboxes =
                enumList(ProviderStaticCompatibility.SandboxMode.class, reader);
        List<ProviderStaticCompatibility.DeterminismClass> determinism =
                enumList(ProviderStaticCompatibility.DeterminismClass.class, reader);
        List<StaticCompatibilityConstraint.BoundaryContractId> boundaries = new ArrayList<>();
        int boundaryCount = reader.listCount();
        for (int i = 0; i < boundaryCount; i++) {
            boundaries.add(StaticCompatibilityConstraint.BoundaryContractId.of(reader.text()));
        }
        ProviderStaticCompatibility.LoweringSupport lowering = enumValue(
                ProviderStaticCompatibility.LoweringSupport.class, reader.field("loweringSupport"));
        return new ProviderStaticCompatibility(
                knowledge, artifacts, codecs, devices, runtimes, sandboxes, determinism,
                boundaries, lowering);
    }

    // ---------- shared scalars ----------

    private static String profileReference(ProviderCapabilityProfileVersionOrDigest reference) {
        if (reference instanceof ProviderCapabilityProfileVersionOrDigest.VersionReference version) {
            return new CanonicalWriter()
                    .tag("ProfileVersionReference")
                    .exactLong(version.version().major())
                    .exactLong(version.version().minor())
                    .build();
        }
        if (reference instanceof ProviderCapabilityProfileVersionOrDigest.DigestReference digest) {
            return new CanonicalWriter()
                    .tag("ProfileDigestReference")
                    .field("sha256", digest.digest().sha256Hex())
                    .build();
        }
        throw new UnsupportedPersistedConstructException(
                "provider capability profile reference " + reference.getClass().getName());
    }

    private static ProviderCapabilityProfileVersionOrDigest profileReference(FrameReader reader) {
        String tag = reader.text();
        if ("ProfileVersionReference".equals(tag)) {
            return ProviderCapabilityProfileVersionOrDigest.version(
                    ProviderCapabilityProfileVersion.of(reader.exactInt(), reader.exactInt()));
        }
        if ("ProfileDigestReference".equals(tag)) {
            return ProviderCapabilityProfileVersionOrDigest.digest(
                    com.example.platform.execution.domain.provider.ProviderCapabilityProfileDigest
                            .sha256(reader.field("sha256")));
        }
        throw new UnsupportedPersistedConstructException("profile reference variant " + tag);
    }

    private static String contractVersion(ProviderExecutionContractVersion version) {
        return new CanonicalWriter()
                .tag("ProviderExecutionContractVersion")
                .exactLong(version.major())
                .exactLong(version.minor())
                .build();
    }

    private static ProviderExecutionContractVersion contractVersion(FrameReader reader) {
        reader.requireTag("ProviderExecutionContractVersion");
        return ProviderExecutionContractVersion.of(reader.exactInt(), reader.exactInt());
    }

    private static String contractVersionRange(ContractVersionRange range) {
        return new CanonicalWriter()
                .tag("ContractVersionRange")
                .exactLong(range.min().major())
                .exactLong(range.min().minor())
                .exactLong(range.max().major())
                .exactLong(range.max().minor())
                .build();
    }

    private static ContractVersionRange contractVersionRange(FrameReader reader) {
        reader.requireTag("ContractVersionRange");
        ContractVersion min = ContractVersion.of(reader.exactInt(), reader.exactInt());
        ContractVersion max = ContractVersion.of(reader.exactInt(), reader.exactInt());
        return ContractVersionRange.between(min, max);
    }

    private static <E extends Enum<E>> List<String> names(List<E> values) {
        return values.stream().map(Enum::name).toList();
    }

    private static <T> List<String> values(List<T> values, java.util.function.Function<T, String> mapper) {
        return values.stream().map(mapper).toList();
    }

    private static <E extends Enum<E>> List<E> enumList(Class<E> type, FrameReader reader) {
        int count = reader.listCount();
        List<E> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(enumValue(type, reader.text()));
        }
        return values;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String name) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException failure) {
            throw new UnsupportedPersistedConstructException(
                    type.getSimpleName() + " value " + name);
        }
    }
}
