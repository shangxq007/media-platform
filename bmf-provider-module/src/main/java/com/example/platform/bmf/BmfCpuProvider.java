package com.example.platform.bmf;

import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.workerfabric.domain.CpuArchitecture;
import com.example.platform.workerfabric.domain.ProviderHardwareRequirement;
import com.example.platform.workerfabric.domain.RuntimeDependencyAbi;
import com.example.platform.workerfabric.domain.RuntimeDependencyCoordinate;
import com.example.platform.workerfabric.domain.RuntimeDependencyFingerprint;
import com.example.platform.workerfabric.domain.RuntimeDependencyRequirement;
import com.example.platform.workerfabric.domain.RuntimeDependencyVersion;
import com.example.platform.workerfabric.domain.RuntimeDependencyVersionConstraint;
import java.util.List;
import java.util.Optional;

/** Immutable identity and fail-closed static declarations for the bounded BMF CPU provider. */
public final class BmfCpuProvider {

    public static final ProviderId PROVIDER_ID = ProviderId.of("bmf");
    public static final ProviderImplementationId IMPLEMENTATION_ID =
            ProviderImplementationId.of("bmf.cpu.v1");
    public static final ProviderVersion VERSION = ProviderVersion.of("1.0.0");
    public static final ProviderExecutionContractVersion EXECUTION_CONTRACT_VERSION =
            ProviderExecutionContractVersion.of(1, 0);
    public static final ProviderCapabilityProfileVersionOrDigest CAPABILITY_PROFILE_REFERENCE =
            ProviderCapabilityProfileVersionOrDigest.version(
                    ProviderCapabilityProfileVersion.of(1, 0));

    public static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(
            PROVIDER_ID,
            IMPLEMENTATION_ID,
            VERSION,
            EXECUTION_CONTRACT_VERSION,
            CAPABILITY_PROFILE_REFERENCE);

    public static final ProviderExecutionContract EXECUTION_CONTRACT =
            new ProviderExecutionContract(
                    ProviderExecutionContractSchemaVersion.of(1),
                    EXECUTION_CONTRACT_VERSION,
                    List.of());

    public static final ProviderCapabilityProfile CAPABILITY_PROFILE =
            new ProviderCapabilityProfile(CAPABILITY_PROFILE_REFERENCE, List.of());

    public static final ProviderBindingPin BINDING = new ProviderBindingPin(
            PROVIDER_ID,
            IMPLEMENTATION_ID,
            VERSION,
            EXECUTION_CONTRACT_VERSION,
            CAPABILITY_PROFILE_REFERENCE,
            List.of());

    public static final ProviderStaticCompatibility STATIC_COMPATIBILITY =
            new ProviderStaticCompatibility(
                    ProviderStaticCompatibility.Knowledge.DECLARED,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    ProviderStaticCompatibility.LoweringSupport.UNSUPPORTED);

    /** Exact top-level B0 closure inputs; H1 remains the dependency authority. */
    public static final List<RuntimeDependencyRequirement> RUNTIME_DEPENDENCY_REQUIREMENTS = List.of(
            exactDependency(
                    "bmf",
                    "0.2.0",
                    Optional.empty(),
                    List.of("ffmpeg.enabled", "python.enabled"),
                    List.of("ambient-module-authority.disabled", "cuda.disabled")),
            exactDependency(
                    "ffmpeg",
                    "4.4.8",
                    Optional.of(RuntimeDependencyAbi.of("libavcodec.58")),
                    List.of(),
                    List.of()),
            exactDependency("numpy", "1.26.4", Optional.empty(), List.of(), List.of()),
            exactDependency("python", "3.12.8", Optional.empty(), List.of(), List.of()));

    /** Authoritative B0 dependency evidence identity, distinct from provider identity. */
    public static final RuntimeDependencyFingerprint EXPECTED_RUNTIME_DEPENDENCY_FINGERPRINT =
            RuntimeDependencyFingerprint.parseSha256(
                    "sha256:5ad7e1e40dd3cfa453960b829a6f61de7216c956638d06e7ad2cefe4be96dfd5");

    /** CPU-only B0 runtime shape; no device or semantic capability is claimed. */
    public static final ProviderHardwareRequirement HARDWARE_REQUIREMENT =
            new ProviderHardwareRequirement(
                    IMPLEMENTATION_ID,
                    CpuArchitecture.X86_64,
                    Optional.empty(),
                    List.of("bmf.cpu.enabled", "bmf.ffmpeg.enabled", "bmf.python.enabled"),
                    List.of(),
                    List.of("ambient-module-authority.disabled"));

    private BmfCpuProvider() {}

    private static RuntimeDependencyRequirement exactDependency(
            String coordinate,
            String version,
            Optional<RuntimeDependencyAbi> abi,
            List<String> features,
            List<String> flags) {
        return new RuntimeDependencyRequirement(
                IMPLEMENTATION_ID,
                RuntimeDependencyCoordinate.of(coordinate),
                RuntimeDependencyVersionConstraint.exact(RuntimeDependencyVersion.of(version)),
                abi,
                features,
                flags);
    }
}
