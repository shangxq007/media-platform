@org.springframework.modulith.NamedInterface("domain")
package com.example.platform.extension.domain;

/**
 * Extension domain package (frozen contract PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>P1 adds the plugin capability registry domain model (Spring-free value
 * types): PluginDescriptor, CapabilityDescriptor, HandledObjectDescriptor,
 * InvocationContract, PermissionDescriptor, ResourceRequirement,
 * PluginRuntimeRequirement, PluginGuarantee, PluginHealth, OperationRequest,
 * PluginSelectionResult, PluginDiagnosticCode and
 * PluginDescriptorValidationIssue.</p>
 *
 * <p>Type-reuse decisions preserved: ResourceRequirement EXTENDS
 * ExtensionResourceLimits; PluginRuntimeRequirement ADAPTS ExtensionTrustLevel;
 * PluginInvocationPort remains deferred from P1. The kernel render
 * CapabilityDescriptor remains kernel-internal; no adapter is introduced.</p>
 */
