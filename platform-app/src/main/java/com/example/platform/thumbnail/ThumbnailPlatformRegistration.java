package com.example.platform.thumbnail;

import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.extension.api.port.PluginRegistrationPort;
import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginDescriptorValidationIssue;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Registers the {@code media.thumbnail} provider contribution with the platform capability registry
 * (COVER-THUMBNAIL-REBUILD-001, action 1; mirror of {@code CoverImagePlatformRegistration}).
 *
 * <p>{@link PluginRegistrationPort#registerRuntime(PluginDescriptor)} is the platform capability
 * authority's own seam, so {@code media.thumbnail} becomes discoverable through
 * {@code CapabilityRegistryPort} and {@code PluginRegistryPort}. Validation is the platform's: an
 * invalid descriptor or a duplicate contribution identity fails closed and propagates.
 *
 * <p><b>Role:</b> the platform capability registry exists in the platform (API) process. The
 * thumbnail worker scans this package but not the extension registry, so the injectable port is
 * optional here: absent, the component registers nothing, records {@link #registered()} as
 * {@code false} and logs — the worker is not a capability-registration authority. The registration
 * owns exactly its own lease and retires only it on {@link #close()}.
 */
@Component
public final class ThumbnailPlatformRegistration implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailPlatformRegistration.class);

    private final PluginRegistrationPort registry;
    private final ProviderDescriptor providerDescriptor;
    private final PluginDescriptor pluginDescriptor;
    private final String registrationId;
    private final PluginRegistrationPort.Registration lease;

    /**
     * Platform-role composition: registers when the capability registry is present in this process
     * (API/platform role) and is inert when it is not (worker role).
     */
    @Autowired
    public ThumbnailPlatformRegistration(ObjectProvider<PluginRegistrationPort> providerRegistry) {
        this(providerRegistry.getIfAvailable(), ThumbnailPlatformProvider.PLUGIN_DESCRIPTOR,
                ThumbnailPlatformProvider.DESCRIPTOR);
        if (registry == null) {
            log.info("thumbnail platform capability registration skipped: this role has no platform "
                    + "capability registry (worker); {} is registered by the platform process",
                    ThumbnailPlatformProvider.PLUGIN_ID);
        }
    }

    /** Direct registration against a given registry (verification and focused tests). */
    public ThumbnailPlatformRegistration(PluginRegistrationPort providerRegistry) {
        this(providerRegistry, ThumbnailPlatformProvider.PLUGIN_DESCRIPTOR,
                ThumbnailPlatformProvider.DESCRIPTOR);
    }

    /** Registration of an explicit descriptor pair (verification and focused tests). */
    public ThumbnailPlatformRegistration(
            PluginRegistrationPort providerRegistry,
            PluginDescriptor pluginDescriptor,
            ProviderDescriptor providerDescriptor) {
        this.registry = providerRegistry;
        this.pluginDescriptor = Objects.requireNonNull(pluginDescriptor, "pluginDescriptor");
        this.providerDescriptor = Objects.requireNonNull(providerDescriptor, "providerDescriptor");
        if (providerRegistry == null) {
            this.lease = null;
            this.registrationId = null;
            return;
        }
        List<PluginDescriptorValidationIssue> issues = providerRegistry.validate(pluginDescriptor);
        if (!issues.isEmpty()) {
            throw new IllegalStateException(
                    "thumbnail platform contribution is not registrable: " + issues);
        }
        this.lease = providerRegistry.registerRuntime(pluginDescriptor);
        this.registrationId = pluginDescriptor.pluginId() + "@" + pluginDescriptor.pluginVersion();
    }

    /** True when this process registered the contribution with the platform capability registry. */
    public boolean registered() {
        return lease != null;
    }

    /** Contribution identity registered with the platform registry; {@code null} when not registered. */
    public String registrationId() {
        return registrationId;
    }

    /** The registry descriptor registered (its capability list is the authority). */
    public PluginDescriptor pluginDescriptor() {
        return pluginDescriptor;
    }

    /** Execution-provider descriptor declared for the same implementation (model A identity). */
    public ProviderDescriptor providerDescriptor() {
        return providerDescriptor;
    }

    /** Registry port this registration was made through; {@code null} in a non-platform role. */
    public PluginRegistrationPort registry() {
        return registry;
    }

    @Override
    public void close() {
        if (lease != null) {
            lease.close();
        }
    }
}
