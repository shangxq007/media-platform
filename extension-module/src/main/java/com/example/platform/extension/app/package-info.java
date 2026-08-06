@org.springframework.modulith.NamedInterface("app")
package com.example.platform.extension.app;

/**
 * Extension application package (frozen contract PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>P1 adds the plugin capability registry application layer: the one
 * descriptor authority (PluginRegistryImpl), the deterministic validator
 * (PluginDescriptorValidator, PLG-001..PLG-016), the one health authority
 * (PluginHealthRegistry, five states), the deterministic matcher
 * (PluginMatcher, explicit -&gt; policy priority -&gt; stable ID -&gt;
 * AMBIGUOUS_SELECTION_FAILURE) and the default selection policy
 * (PluginDefaultSelectionPolicy). Execution authority remains with the
 * existing ExtensionRegistryService (Compatibility Model B).</p>
 */
