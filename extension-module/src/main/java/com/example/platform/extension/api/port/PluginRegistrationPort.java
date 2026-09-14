package com.example.platform.extension.api.port;

import com.example.platform.extension.domain.PluginDescriptor;
import com.example.platform.extension.domain.PluginDescriptorValidationIssue;
import java.util.List;

/** Extension-owned startup registration; provider execution and capability policy remain separate. */
public interface PluginRegistrationPort extends PluginRegistryPort {
    List<PluginDescriptorValidationIssue> validate(PluginDescriptor descriptor);
    Registration registerRuntime(PluginDescriptor descriptor);

    /** Owns only this exact registration. Closing twice, or after replacement, is harmless. */
    interface Registration extends AutoCloseable {
        @Override void close();
    }
}
