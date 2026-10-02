package com.example.platform.frameextract;

import com.example.platform.contract.media.CoverImageContracts;
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
import com.example.platform.extension.domain.CapabilityDescriptor;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.extension.domain.ResourceRequirement;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.contract.media.ThumbnailContracts;
import java.util.List;

/**
 * The single capability-neutral platform contribution for the ffmpeg frame-extract capabilities.
 *
 * <p>It replaces {@code CoverImagePlatformProvider} and {@code ThumbnailPlatformProvider}: the
 * provider identity stays capability-independent (family {@link #PROVIDER_ID}
 * {@code platform.ffmpeg} + implementation {@link #IMPLEMENTATION_ID}
 * {@code ffmpeg.cpu.frame-extract.v1}), while the contribution is one {@link #PLUGIN_DESCRIPTOR} whose
 * capability list declares both {@code media.cover-image@1.0} and {@code media.thumbnail@1.0}. The
 * contribution id {@link #PLUGIN_ID} is capability-neutral — it names the frame-extract contribution,
 * never a capability — and every identity value is derived from the slice constants, so there is
 * still exactly one source of truth for the capability ids, contract versions, provider family and
 * implementation id.
 *
 * <p>Boundary: the descriptor declares the platform provider-native boundary this capability is
 * executed through ({@code ExecutableTask} in, {@code ProviderExecutionOutput} out) and the platform
 * Artifact capability contract the composition catalog publishes. Registering the contribution
 * through {@code FrameExtractPlatformRegistration} makes both capabilities discoverable through
 * {@code CapabilityRegistryPort} and {@code PluginRegistryPort}.
 */
public final class FrameExtractPlatformProvider {

    /**
     * Capability-neutral contribution (plugin) identity registered in the platform capability
     * registry. Hyphen-free because {@code PluginDescriptorValidator} requires
     * {@code [a-z0-9]+(\.[a-z0-9]+)+}; it names the frame-extract contribution, not any one capability
     * (the previous {@code media.coverimage.ffmpeg} / {@code media.thumbnail.ffmpeg} ids each fused the
     * contribution with a single capability).
     */
    public static final String PLUGIN_ID = "media.ffmpeg.frameextract";

    /** Contribution version, aligned with the slice's provider version. */
    public static final String PLUGIN_VERSION = FfmpegCpuProvider.PROVIDER_VERSION;

    /** Platform plugin-API version accepted by the frozen registry contract. */
    public static final String PLATFORM_API_VERSION = "1";

    public static final String VENDOR = "media-platform";

    /** Provider family — capability-independent (model A). */
    public static final ProviderId PROVIDER_ID =
            ProviderId.of(FfmpegCpuProvider.PROVIDER_ID);

    /** One runtime/adapter implementation of the family. */
    public static final ProviderImplementationId IMPLEMENTATION_ID =
            ProviderImplementationId.of(FfmpegCpuProvider.PROVIDER_IMPLEMENTATION_ID);

    public static final ProviderVersion VERSION = ProviderVersion.of(PLUGIN_VERSION);

    public static final ProviderExecutionContractVersion EXECUTION_CONTRACT_VERSION =
            ProviderExecutionContractVersion.of(1, 0);

    public static final ProviderCapabilityProfileVersionOrDigest CAPABILITY_PROFILE_REFERENCE =
            ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0));

    /** The capability served by the cover slice (one of this contribution's capabilities). */
    public static final CapabilityId COVER_CAPABILITY_ID =
            CapabilityId.of(CoverImageContracts.CAPABILITY);
    /** The capability served by the thumbnail slice (the other one). */
    public static final CapabilityId THUMBNAIL_CAPABILITY_ID =
            CapabilityId.of(ThumbnailContracts.CAPABILITY);

    public static final ContractVersionRange COVER_CAPABILITY_CONTRACT_RANGE =
            ContractVersionRange.exactly(ContractVersion.parse(CoverImageContracts.CAPABILITY_VERSION));
    public static final ContractVersionRange THUMBNAIL_CAPABILITY_CONTRACT_RANGE =
            ContractVersionRange.exactly(ContractVersion.parse(ThumbnailContracts.CAPABILITY_VERSION));

    /** Provider identity plus the capability list this implementation serves. */
    public static final ProviderDescriptor DESCRIPTOR = new ProviderDescriptor(
            PROVIDER_ID,
            IMPLEMENTATION_ID,
            VERSION,
            EXECUTION_CONTRACT_VERSION,
            CAPABILITY_PROFILE_REFERENCE);

    public static final ProviderExecutionContract EXECUTION_CONTRACT = new ProviderExecutionContract(
            ProviderExecutionContractSchemaVersion.of(1),
            EXECUTION_CONTRACT_VERSION,
            List.of(
                    new ProviderCapabilityContractReference(
                            COVER_CAPABILITY_ID, COVER_CAPABILITY_CONTRACT_RANGE),
                    new ProviderCapabilityContractReference(
                            THUMBNAIL_CAPABILITY_ID, THUMBNAIL_CAPABILITY_CONTRACT_RANGE)));

    public static final ProviderCapabilityProfile CAPABILITY_PROFILE = new ProviderCapabilityProfile(
            CAPABILITY_PROFILE_REFERENCE,
            List.of(
                    ProviderCapabilitySupport.unpinned(
                            COVER_CAPABILITY_ID, COVER_CAPABILITY_CONTRACT_RANGE),
                    ProviderCapabilitySupport.unpinned(
                            THUMBNAIL_CAPABILITY_ID, THUMBNAIL_CAPABILITY_CONTRACT_RANGE)));

    public static final ProviderBindingPin BINDING = new ProviderBindingPin(
            PROVIDER_ID,
            IMPLEMENTATION_ID,
            VERSION,
            EXECUTION_CONTRACT_VERSION,
            CAPABILITY_PROFILE_REFERENCE,
            List.of());

    /**
     * Registry descriptor for this contribution: one plugin identity, two capabilities (cover image
     * and thumbnail), the platform provider-native boundary and the same resource/runtime/trust shape
     * the bounded FFmpeg transcode contribution uses.
     */
    public static final PluginDescriptor PLUGIN_DESCRIPTOR = new PluginDescriptor(
            PLUGIN_ID,
            PLUGIN_VERSION,
            PLATFORM_API_VERSION,
            VENDOR,
            List.of(
                    new CapabilityDescriptor(
                            CoverImageContracts.CAPABILITY,
                            CoverImageContracts.CAPABILITY_VERSION,
                            "cover-image",
                            "Artifact",
                            "Artifact",
                            CapabilityDescriptor.InvocationMode.SYNC_ONLY),
                    new CapabilityDescriptor(
                            ThumbnailContracts.CAPABILITY,
                            ThumbnailContracts.CAPABILITY_VERSION,
                            "thumbnail",
                            "Artifact",
                            "Artifact",
                            CapabilityDescriptor.InvocationMode.SYNC_ONLY)),
            List.of(new HandledObjectDescriptor(
                    "ExecutableTask",
                    "1",
                    "com.example.platform.execution.taskgraph.ExecutableTask",
                    List.of("providerBindingPin"), List.of(),
                    HandledObjectDescriptor.TenantBehavior.TENANT_SCOPED)),
            InvocationContract.syncOnlyDefault(),
            List.of(
                    new PermissionDescriptor("ffmpeg.execute"),
                    new PermissionDescriptor("asset.read"),
                    new PermissionDescriptor("temporary-file.write")),
            resourceRequirements(),
            PluginRuntimeRequirement.trustedInProcess(),
            PluginGuarantee.noneDeclared());

    private static ResourceRequirement resourceRequirements() {
        return new ResourceRequirement(
                1,
                256,
                50,
                0,
                FfmpegCpuProvider.MAXIMUM_INPUT_BYTES,
                64L * 1024 * 1024,
                120_000L,
                false,
                FfmpegCpuProvider.COVER_MAXIMUM_WIDTH,
                false,
                120_000L);
    }

    private FrameExtractPlatformProvider() {}
}
