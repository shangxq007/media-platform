package com.example.platform.bmf;

import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.workerfabric.domain.CpuArchitecture;
import com.example.platform.workerfabric.domain.ProviderHardwareRequirement;
import com.example.platform.workerfabric.domain.RuntimeDependencyAbi;
import com.example.platform.workerfabric.domain.RuntimeDependencyCoordinate;
import com.example.platform.workerfabric.domain.RuntimeDependencyRequirement;
import com.example.platform.workerfabric.domain.RuntimeDependencyVersion;
import com.example.platform.workerfabric.domain.RuntimeDependencyVersionConstraint;
import java.util.List;
import java.util.Optional;

/** Immutable identity and fail-closed static declarations for the bounded BMF CPU provider. */
public final class BmfCpuProvider {

    /** Exact, locally executed official release and isolated Debian 11 runtime content. */
    public static final String SOURCE_ARCHIVE_SHA256 =
            "8fb3ec03b06ba56e5276a6844614879cdc0ae3886b42a4bf783782708130e327";
    public static final String RUNTIME_BUNDLE_SHA256 =
            "0d4c2e9220a582f9ce286f7185f513ca71a0272910d0e79da8a226d734a03be1";

    /** The same BMF release with its sndio ABI dependency included in the verified closure. */
    public static final String RUNTIME_BUNDLE_WITH_SNDIO_SHA256 =
            "7b6de78d235e77e14c3de47ed84268b17a071298465d72fb321af0565a0aff4c";

    public static final ProviderId PROVIDER_ID = ProviderId.of("bmf");
    public static final ProviderImplementationId IMPLEMENTATION_ID =
            ProviderImplementationId.of("bmf.cpu.v1");
    public static final ProviderVersion VERSION = ProviderVersion.of("1.2.0");
    public static final ProviderExecutionContractVersion EXECUTION_CONTRACT_VERSION =
            ProviderExecutionContractVersion.of(1, 0);
    private static final ProviderCapabilityProfileVersionOrDigest ORIGINAL_PROFILE =
            com.example.platform.execution.domain.provider.ProviderImplementationContent.profile(
                    BmfCpuProvider.class, "bmf-provider-module", RUNTIME_BUNDLE_SHA256);
    public static final ProviderCapabilityProfileVersionOrDigest CAPABILITY_PROFILE_REFERENCE =
            com.example.platform.execution.domain.provider.ProviderImplementationContent.profile(
                    BmfCpuProvider.class, "bmf-provider-module", RUNTIME_BUNDLE_WITH_SNDIO_SHA256);

    public static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(
            PROVIDER_ID,
            IMPLEMENTATION_ID,
            VERSION,
            EXECUTION_CONTRACT_VERSION,
            CAPABILITY_PROFILE_REFERENCE);

    public static final ProviderBindingPin BINDING = new ProviderBindingPin(
            PROVIDER_ID,
            IMPLEMENTATION_ID,
            VERSION,
            EXECUTION_CONTRACT_VERSION,
            CAPABILITY_PROFILE_REFERENCE,
            List.of());

    private static final ProviderBindingPin ORIGINAL_BINDING = new ProviderBindingPin(
            PROVIDER_ID, IMPLEMENTATION_ID, VERSION, EXECUTION_CONTRACT_VERSION, ORIGINAL_PROFILE, List.of());

    public static ProviderBindingPin bindingFor(String bundleDigest) {
        return switch (bundleDigest) {
            case RUNTIME_BUNDLE_SHA256 -> ORIGINAL_BINDING;
            case RUNTIME_BUNDLE_WITH_SNDIO_SHA256 -> BINDING;
            default -> throw new IllegalArgumentException("Unknown BMF runtime distribution");
        };
    }

    static boolean recognizes(ProviderBindingPin pin) {
        return BINDING.equals(pin) || ORIGINAL_BINDING.equals(pin);
    }

    public static final ProviderStaticCompatibility STATIC_COMPATIBILITY =
            new ProviderStaticCompatibility(
                    ProviderStaticCompatibility.Knowledge.DECLARED,
                    List.of(ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT,
                            ProviderStaticCompatibility.ArtifactRequirementKind.INTERMEDIATE_OUTPUT,
                            ProviderStaticCompatibility.ArtifactRequirementKind.FINAL_OUTPUT),
                    List.of(StaticCompatibilityConstraint.CodecId.of("h264")),
                    List.of(StaticCompatibilityConstraint.ProviderDeviceKind.CPU),
                    List.of(StaticCompatibilityConstraint.ProviderRuntimeClass.NATIVE_PROCESS,
                            StaticCompatibilityConstraint.ProviderRuntimeClass.ISOLATED_PROCESS),
                    List.of(ProviderStaticCompatibility.SandboxMode.SANDBOXED),
                    List.of(ProviderStaticCompatibility.DeterminismClass.DETERMINISTIC),
                    List.of(),
                    ProviderStaticCompatibility.LoweringSupport.SUPPORTED,
                    com.example.platform.execution.compatibility.MediaLoweringEnvelope.TIMED_TRACKS_H264_V1);

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
                    "4.3.7",
                    Optional.of(RuntimeDependencyAbi.of("libavcodec.58")),
                    List.of(),
                    List.of()),
            exactDependency("numpy", "1.19.5", Optional.empty(), List.of(), List.of()),
            exactDependency("python", "3.9.2", Optional.empty(), List.of(), List.of()));

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
