package com.example.platform.render.infrastructure.providerruntime.capability;

import com.example.platform.render.infrastructure.RenderProvider;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for negotiating capabilities between providers and requirements.
 */
@Service
public class CapabilityNegotiationService {

    private final CapabilityDescriptorRegistry descriptorRegistry;

    public CapabilityNegotiationService(CapabilityDescriptorRegistry descriptorRegistry) {
        this.descriptorRegistry = descriptorRegistry;
    }

    /**
     * Describe a provider's capabilities.
     */
    public CapabilityDescriptor describeProvider(RenderProvider provider) {
        return descriptorRegistry.getOrCreateDescriptor(provider);
    }

    /**
     * Negotiate capabilities between a set of providers and requirements.
     */
    public CapabilityNegotiationResult negotiate(
            List<CapabilityDescriptor> providerDescriptors,
            Set<String> requiredCapabilities
    ) {
        if (requiredCapabilities == null || requiredCapabilities.isEmpty()) {
            // No requirements - all providers are valid
            List<String> allProviders = providerDescriptors.stream()
                    .map(CapabilityDescriptor::providerName)
                    .collect(Collectors.toList());

            return new CapabilityNegotiationResult(
                    true,
                    allProviders,
                    allProviders,
                    Set.of(),
                    "No specific capabilities required"
            );
        }

        // Find providers that support all required capabilities
        List<String> fullyCapable = new ArrayList<>();
        List<String> partiallyCapable = new ArrayList<>();
        Set<String> unsupportedCapabilities = new HashSet<>();

        for (CapabilityDescriptor descriptor : providerDescriptors) {
            Set<String> supported = descriptor.supportedCapabilities();
            Set<String> missing = new HashSet<>(requiredCapabilities);
            missing.removeAll(supported);

            if (missing.isEmpty()) {
                fullyCapable.add(descriptor.providerName());
            } else if (missing.size() < requiredCapabilities.size()) {
                partiallyCapable.add(descriptor.providerName());
                unsupportedCapabilities.addAll(missing);
            } else {
                unsupportedCapabilities.addAll(missing);
            }
        }

        // Prefer fully capable providers
        List<String> supportedProviders = !fullyCapable.isEmpty() ? fullyCapable : partiallyCapable;

        String reason = buildNegotiationReason(
                requiredCapabilities,
                fullyCapable,
                partiallyCapable,
                unsupportedCapabilities
        );

        return new CapabilityNegotiationResult(
                !supportedProviders.isEmpty(),
                supportedProviders,
                fullyCapable,
                unsupportedCapabilities,
                reason
        );
    }

    private String buildNegotiationReason(
            Set<String> required,
            List<String> fullyCapable,
            List<String> partiallyCapable,
            Set<String> unsupported
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Required: ").append(required).append(". ");

        if (!fullyCapable.isEmpty()) {
            sb.append("Fully capable: ").append(fullyCapable).append(". ");
        }
        if (!partiallyCapable.isEmpty()) {
            sb.append("Partially capable: ").append(partiallyCapable).append(". ");
        }
        if (!unsupported.isEmpty()) {
            sb.append("Unsupported: ").append(unsupported).append(". ");
        }

        return sb.toString().trim();
    }

    /**
     * Check if a specific effect can be handled by a provider.
     */
    public boolean canHandleEffect(String providerName, String effectKey) {
        CapabilityDescriptor descriptor = descriptorRegistry.getDescriptor(providerName);
        if (descriptor == null) {
            return false;
        }
        return descriptor.supportedEffects().contains(effectKey)
                || descriptor.supportedCapabilities().contains(effectKey);
    }
}
