package com.example.platform.coverimage;

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
 * Registers the {@code media.cover-image} provider contribution with the platform capability
 * registry (COVER-PROVIDER-PLATFORM-REGISTER-001).
 *
 * <p>The registration uses the platform capability authority's own seam:
 * {@link PluginRegistrationPort#registerRuntime(PluginDescriptor)} is the canonical startup
 * registration the PF4J provider host also uses, and the registry derives one capability
 * implementation per declared capability — which is what makes {@code media.cover-image}
 * discoverable through {@code CapabilityRegistryPort} and {@code PluginRegistryPort}.
 * Validation is the platform's: an invalid descriptor or a duplicate contribution identity fails
 * closed and propagates, never silently degrades.
 *
 * <p><b>Role:</b> the platform capability registry exists in the platform (API) process. The cover
 * worker scans this package but deliberately does not scan the extension registry, so the injectable
 * port is optional here: absent, the component registers nothing, records {@link #registered()} as
 * {@code false} and logs, instead of fabricating a registry or failing the worker context. The
 * worker is not a capability-registration authority.
 *
 * <p>The registration owns exactly its own lease; {@link #close()} retires only this entry.
 */
@Component
public final class CoverImagePlatformRegistration implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CoverImagePlatformRegistration.class);

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
    public CoverImagePlatformRegistration(ObjectProvider<PluginRegistrationPort> providerRegistry) {
        this(providerRegistry.getIfAvailable(), CoverImagePlatformProvider.PLUGIN_DESCRIPTOR,
                CoverImagePlatformProvider.DESCRIPTOR);
        if (registry == null) {
            log.info("cover-image platform capability registration skipped: this role has no platform "
                    + "capability registry (worker); {} is registered by the platform process",
                    CoverImagePlatformProvider.PLUGIN_ID);
        }
    }

    /** Direct registration against a given registry (verification and focused tests). */
    public CoverImagePlatformRegistration(PluginRegistrationPort providerRegistry) {
        this(providerRegistry, CoverImagePlatformProvider.PLUGIN_DESCRIPTOR,
                CoverImagePlatformProvider.DESCRIPTOR);
    }

    /** Registration of an explicit descriptor pair (verification and focused tests). */
    public CoverImagePlatformRegistration(
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
                    "cover-image platform contribution is not registrable: " + issues);
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
