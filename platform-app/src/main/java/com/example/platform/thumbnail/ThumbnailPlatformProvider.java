package com.example.platform.thumbnail;

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
import java.util.List;

/**
 * Platform registration metadata for the {@code media.thumbnail} capability
 * (COVER-THUMBNAIL-REBUILD-001, action 1).
 *
 * <p>Mirror of {@code CoverImagePlatformProvider}: the provider identity is
 * capability-independent (family {@code platform.ffmpeg} + implementation
 * {@code ffmpeg.cpu.frame-extract.v1}) and the contribution declares the capability list it serves.
 * Every identity value is taken from {@link ThumbnailContracts}, so the slice has exactly one
 * source of truth for the capability id, contract version, provider family and implementation id.
 *
 * <p>The contribution's capability descriptor declares the platform <em>capability contract</em>
 * (subject Artifact in, thumbnail Artifact out) — the same Artifact contract the composition
 * capability catalog publishes — so availability is derived from a real, healthy registration
 * rather than a pinned value. The provider-native execution boundary
 * ({@code ExecutableTask}/{@code ProviderExecutionOutput}) is declared by the separate
 * {@link ProviderDescriptor}/{@code ProviderExecutionContract}, exactly as the cover provider does.
 *
 * <p>Registered by {@link ThumbnailPlatformRegistration} through
 * {@code PluginRegistrationPort.registerRuntime}; the plugin/contribution identity
 * {@link #PLUGIN_ID} is a deployment identity (hyphen-free contribution naming, mirroring
 * {@code media.transcode.ffmpeg} / {@code media.coverimage.ffmpeg}), never a provider identity.
 */
public final class ThumbnailPlatformProvider {

    /** Contribution (plugin) identity registered in the platform capability registry. */
    public static final String PLUGIN_ID = "media.thumbnail.ffmpeg";

    /** Contribution version, aligned with the slice's provider version. */
    public static final String PLUGIN_VERSION = ThumbnailContracts.PROVIDER_VERSION;

    /** Platform plugin-API version accepted by the frozen registry contract. */
    public static final String PLATFORM_API_VERSION = "1";

    public static final String VENDOR = "media-platform";

    /** Provider family — capability-independent (model A). */
    public static final ProviderId PROVIDER_ID = ProviderId.of(ThumbnailContracts.PROVIDER);

    /** One runtime/adapter implementation of the family. */
    public static final ProviderImplementationId IMPLEMENTATION_ID =
            ProviderImplementationId.of(ThumbnailContracts.PROVIDER_IMPLEMENTATION);

    public static final ProviderVersion VERSION = ProviderVersion.of(ThumbnailContracts.PROVIDER_VERSION);

    public static final ProviderExecutionContractVersion EXECUTION_CONTRACT_VERSION =
            ProviderExecutionContractVersion.of(1, 0);

    public static final ProviderCapabilityProfileVersionOrDigest CAPABILITY_PROFILE_REFERENCE =
            ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0));

    public static final CapabilityId CAPABILITY_ID = CapabilityId.of(ThumbnailContracts.CAPABILITY);

    public static final ContractVersionRange CAPABILITY_CONTRACT_RANGE = ContractVersionRange.exactly(
            ContractVersion.parse(ThumbnailContracts.CAPABILITY_VERSION));

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
            List.of(new ProviderCapabilityContractReference(CAPABILITY_ID, CAPABILITY_CONTRACT_RANGE)));

    public static final ProviderCapabilityProfile CAPABILITY_PROFILE = new ProviderCapabilityProfile(
            CAPABILITY_PROFILE_REFERENCE,
            List.of(ProviderCapabilitySupport.unpinned(CAPABILITY_ID, CAPABILITY_CONTRACT_RANGE)));

    public static final ProviderBindingPin BINDING = new ProviderBindingPin(
            PROVIDER_ID,
            IMPLEMENTATION_ID,
            VERSION,
            EXECUTION_CONTRACT_VERSION,
            CAPABILITY_PROFILE_REFERENCE,
            List.of());

    /**
     * Registry descriptor for this contribution. The capability descriptor declares the platform
     * Artifact capability contract (subject Artifact in, thumbnail Artifact out); the
     * handled-object/provider-native execution boundary stays {@code ExecutableTask} ->
     * {@code ProviderExecutionOutput}, mirroring the cover contribution.
     */
    public static final PluginDescriptor PLUGIN_DESCRIPTOR = new PluginDescriptor(
            PLUGIN_ID,
            PLUGIN_VERSION,
            PLATFORM_API_VERSION,
            VENDOR,
            List.of(new CapabilityDescriptor(
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
                512L * 1024L * 1024L,
                64L * 1024 * 1024,
                60_000L,
                false,
                4096,
                false,
                60_000L);
    }

    private ThumbnailPlatformProvider() {}
}
