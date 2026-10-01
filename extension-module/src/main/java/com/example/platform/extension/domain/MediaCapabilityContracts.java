package com.example.platform.extension.domain;

/**
 * Platform (platform-reserved {@code media.*}) capability model-layer declarations
 * (CAPABILITY_OPERATION_PARAMETER_MODEL / E-2b, D2).
 *
 * <p>Declares the provider-neutral parameter contract of each platform media
 * capability. Catalog convergence (availability projection, implementation
 * registration) is intentionally deferred to E-3; this holds only the model-layer
 * contract identity and its typed parameter contract.</p>
 */
public final class MediaCapabilityContracts {

    /**
     * Shared still-frame extraction capability used by both the cover-image and
     * thumbnail operations. Carries no business intent ({@code COVER_OF} /
     * {@code THUMBNAIL} are operation effects) and no provider identity.
     */
    public static final CapabilityContractDescriptor FRAME_EXTRACT = new CapabilityContractDescriptor(
            CapabilityId.of("media.frame-extract"),
            ContractVersion.of(1, 0),
            com.example.platform.shared.capability.MediaFrameExtractParametersV1.class,
            "MediaFrameSourceRef",
            "RasterFrameImage");

    private MediaCapabilityContracts() {
    }
}
