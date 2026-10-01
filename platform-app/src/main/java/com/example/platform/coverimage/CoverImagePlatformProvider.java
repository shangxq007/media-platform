package com.example.platform.coverimage;

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
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.extension.domain.HandledObjectDescriptor;
import com.example.platform.extension.domain.InvocationContract;
import com.example.platform.extension.domain.PermissionDescriptor;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginGuarantee;
import com.example.platform.extension.domain.PluginRuntimeRequirement;
import com.example.platform.extension.domain.ResourceRequirement;
import java.util.List;

/**
 * Platform registration metadata for the {@code media.cover-image} capability
 * (COVER-PROVIDER-PLATFORM-REGISTER-001).
 *
 * <p>Model A: the <em>provider</em> identity is capability-independent
 * ({@link ProviderId} {@code platform.ffmpeg} + {@link ProviderImplementationId}
 * {@code ffmpeg.cpu.frame-extract.v1}) and the capabilities it serves are declared as a
 * list. Every identity value is taken from {@link CoverImageContracts}, so the slice has
 * exactly one source of truth for the capability id, the contract version, the provider
 * family and the implementation id — there is no second identity.
 *
 * <h2>What is registered, and what is not (yet)</h2>
 *
 * <p>Two complementary declarations travel together:
 *
 * <ul>
     *   <li>the <b>execution provider contract</b> ({@link #DESCRIPTOR} /
     *       {@link #EXECUTION_CONTRACT} / {@link #CAPABILITY_PROFILE} / {@link #BINDING}) — the
     *       platform's model-A provider identity plus the capability list it serves, in the same
     *       shape the platform's bounded FFmpeg transcode provider declares. The worker runtime
     *       support requirement is deliberately NOT declared here: it belongs to the PF4J
     *       contribution path used when the platform itself dispatches the provider, which is
     *       backlog C2, not this registration;</li>
 *   <li>the <b>plugin/capability contribution</b> ({@link #PLUGIN_DESCRIPTOR}) — the
 *       descriptor the platform capability registry derives capability implementations
 *       from, so {@code media.cover-image} becomes discoverable through
 *       {@code CapabilityRegistryPort.findCapabilityImplementations} and
 *       {@code PluginRegistryPort.findCapabilityCandidates}.</li>
 * </ul>
 *
 * <p>The contribution is registered by
 * {@code com.example.platform.config.CoverImagePlatformRegistration} (platform process
 * only; the capability registry is not part of the worker role). The plugin id
 * {@link #PLUGIN_ID} follows the platform's existing <em>contribution</em> naming used by
 * {@code media.transcode.ffmpeg} (the hyphen-free form of the capability namespace, because
 * the registry's plugin-id grammar forbids hyphens); it is a deployment/contribution
 * identity, never a provider identity — the provider identity is {@link #PROVIDER_ID}.
 *
 * <p><b>Boundary (explicit, not hidden):</b> the descriptor declares the platform
 * provider-native boundary this capability will be executed through ({@code ExecutableTask}
 * in, {@code ProviderExecutionOutput} out; the platform's P1 invocation vocabulary is
 * synchronous with a bounded timeout, exactly as the transcode contribution declares). The
 * <em>runtime</em> that executes {@code media.cover-image} today is the cover worker
 * (slice-local activity, bubblewrap + FFmpeg sandbox, {@code ArtifactCommitService} commit).
 * Routing the capability through the platform operation-invocation seam is backlog item C2
 * and is deliberately NOT claimed here, so the composition capability catalog lists the
 * capability but projects it {@code UNAVAILABLE} until that seam exists.
 */
public final class CoverImagePlatformProvider {

    /**
     * Contribution (plugin) identity registered in the platform capability registry.
     *
     * <p>Hyphen-free because {@code PluginDescriptorValidator} requires
     * {@code [a-z0-9]+(\.[a-z0-9]+)+}; it mirrors the existing
     * {@code media.transcode.ffmpeg} contribution naming for the cover capability.
     */
    public static final String PLUGIN_ID = "media.coverimage.ffmpeg";

    /** Contribution version, aligned with the slice's provider version. */
    public static final String PLUGIN_VERSION = CoverImageContracts.PROVIDER_VERSION;

    /** Platform plugin-API version accepted by the frozen registry contract. */
    public static final String PLATFORM_API_VERSION = "1";

    public static final String VENDOR = "media-platform";

    /** Provider family — capability-independent (model A). */
    public static final ProviderId PROVIDER_ID = ProviderId.of(CoverImageContracts.PROVIDER);

    /** One runtime/adapter implementation of the family. */
    public static final ProviderImplementationId IMPLEMENTATION_ID =
            ProviderImplementationId.of(CoverImageContracts.PROVIDER_IMPLEMENTATION);

    public static final ProviderVersion VERSION =
            ProviderVersion.of(CoverImageContracts.PROVIDER_VERSION);

    public static final ProviderExecutionContractVersion EXECUTION_CONTRACT_VERSION =
            ProviderExecutionContractVersion.of(1, 0);

    public static final ProviderCapabilityProfileVersionOrDigest CAPABILITY_PROFILE_REFERENCE =
            ProviderCapabilityProfileVersionOrDigest.version(
                    ProviderCapabilityProfileVersion.of(1, 0));

    /** The capability served by this contribution. */
    public static final CapabilityId CAPABILITY_ID = CapabilityId.of(CoverImageContracts.CAPABILITY);

    /** Exact capability contract version range, derived from the slice constant. */
    public static final ContractVersionRange CAPABILITY_CONTRACT_RANGE = ContractVersionRange.exactly(
            ContractVersion.parse(CoverImageContracts.CAPABILITY_VERSION));

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
     * Registry descriptor for this contribution: one capability, the platform
     * provider-native boundary ({@code ExecutableTask} in, {@code ProviderExecutionOutput}
     * out) and the same resource/runtime/trust declaration shape the bounded FFmpeg
     * transcode contribution uses. Bounds mirror the slice's own limits (512 MiB maximum
     * input, 120 s provider timeout, no network inside the sandbox).
     */
    public static final PluginDescriptor PLUGIN_DESCRIPTOR = new PluginDescriptor(
            PLUGIN_ID,
            PLUGIN_VERSION,
            PLATFORM_API_VERSION,
            VENDOR,
            List.of(new CapabilityDescriptor(
                    CoverImageContracts.CAPABILITY,
                    CoverImageContracts.CAPABILITY_VERSION,
                    "cover",
                    "ExecutableTask",
                    "ProviderExecutionOutput",
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
                CpuFrameExtractCoverImageProvider.MAXIMUM_INPUT_BYTES,
                64L * 1024 * 1024,
                120_000L,
                false,
                4096,
                false,
                120_000L);
    }

    private CoverImagePlatformProvider() {}
}
