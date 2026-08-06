package com.example.platform.extension.app;

import com.example.platform.extension.api.port.PluginSelectionPolicy;
import com.example.platform.extension.domain.PluginDescriptor;
import org.springframework.stereotype.Component;

/**
 * Default deterministic selection policy (frozen contract
 * PLUGIN_CAPABILITY_REGISTRY_V1_CONTRACT_V1).
 *
 * <p>Equal priority for all candidates: selection then falls through to the
 * stable identity ordering (plugin ID, then version) and finally the frozen
 * AMBIGUOUS_SELECTION_FAILURE terminal. The policy is deterministic — never
 * registration order, classpath order, filesystem order or randomness.</p>
 */
@Component
public class PluginDefaultSelectionPolicy implements PluginSelectionPolicy {

    @Override
    public int priority(PluginDescriptor descriptor) {
        return 0;
    }
}
