package com.example.platform.extension.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Stable, implementation-independent plugin descriptor (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>Spring-free domain type. Normalization: {@code pluginId} and capability
 * IDs are trimmed; capability and handled-object lists are normalized by
 * stable ID (deterministic enumeration); no case folding (case-sensitive,
 * documented).</p>
 *
 * <p>Identity rules: {@code pluginId} is a stable reverse-dns style string and
 * must never be a Java class name, Spring bean name or PF4J class. The four
 * version layers (plugin version, platform API version, capability contract
 * version, handled-object schema version) remain separate.</p>
 *
 * @param pluginId            stable implementation-independent identifier (e.g. {@code media.render.ffmpeg})
 * @param pluginVersion       semantic version of the plugin
 * @param platformApiVersion  plugin-platform API version, separate from {@code pluginVersion}
 * @param vendor              vendor name
 * @param capabilities        required non-empty capability list
 * @param handledObjects      required non-empty handled-object list
 * @param invocationContract  declarative invocation contract
 * @param permissions         required permission list (may be empty)
 * @param resourceRequirements required resource requirement declaration
 * @param runtimeRequirements runtime/trust requirement declaration
 * @param guarantees          guarantee declaration (false-by-default)
 */
public record PluginDescriptor(
        String pluginId,
        String pluginVersion,
        String platformApiVersion,
        String vendor,
        List<CapabilityDescriptor> capabilities,
        List<HandledObjectDescriptor> handledObjects,
        InvocationContract invocationContract,
        List<PermissionDescriptor> permissions,
        ResourceRequirement resourceRequirements,
        PluginRuntimeRequirement runtimeRequirements,
        PluginGuarantee guarantees) {

    public PluginDescriptor {
        if (pluginId == null) {
            throw new NullPointerException("pluginId must not be null");
        }
        if (pluginVersion == null) {
            throw new NullPointerException("pluginVersion must not be null");
        }
        if (platformApiVersion == null) {
            throw new NullPointerException("platformApiVersion must not be null");
        }
        if (vendor == null) {
            throw new NullPointerException("vendor must not be null");
        }
        if (capabilities == null) {
            throw new NullPointerException("capabilities must not be null");
        }
        if (handledObjects == null) {
            throw new NullPointerException("handledObjects must not be null");
        }
        if (invocationContract == null) {
            throw new NullPointerException("invocationContract must not be null");
        }
        if (permissions == null) {
            throw new NullPointerException("permissions must not be null");
        }
        if (resourceRequirements == null) {
            throw new NullPointerException("resourceRequirements must not be null");
        }
        if (runtimeRequirements == null) {
            throw new NullPointerException("runtimeRequirements must not be null");
        }
        if (guarantees == null) {
            throw new NullPointerException("guarantees must not be null");
        }
        // Normalization: trim pluginId; no case folding (case-sensitive, documented).
        pluginId = pluginId.trim();
        // Normalize capability/handled-object lists by stable ID (deterministic enumeration).
        List<CapabilityDescriptor> caps = new ArrayList<>(capabilities);
        caps.sort(java.util.Comparator.comparing(CapabilityDescriptor::capabilityId));
        capabilities = List.copyOf(caps);
        List<HandledObjectDescriptor> objects = new ArrayList<>(handledObjects);
        objects.sort(java.util.Comparator.comparing(HandledObjectDescriptor::objectTypeId));
        handledObjects = List.copyOf(objects);
        permissions = List.copyOf(permissions);
    }
}
