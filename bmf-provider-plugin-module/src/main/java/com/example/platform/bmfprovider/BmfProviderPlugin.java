package com.example.platform.bmfprovider;

import com.example.platform.bmf.BmfExecutionBackend;
import com.example.platform.providerplugin.ProviderPluginContribution;
import com.example.platform.sandbox.execution.ExecutionBackend;

/**
 * The one place where the composition root connects BMF to the provider-plugin runtime.
 *
 * <p>The generic provider-plugin host and the BMF engine module never reference each other; the
 * composition root asks this bridge for the BMF contribution and the BMF execution backend.
 */
public final class BmfProviderPlugin {

    private BmfProviderPlugin() {
    }

    /** The in-process provider contribution (identity and runtime bundle verification stay in BMF). */
    public static ProviderPluginContribution contribution() {
        return new BmfProviderExtension();
    }

    /** The BMF execution backend, contributed only when the BMF provider flag is enabled. */
    public static ExecutionBackend executionBackend() {
        return new BmfExecutionBackend();
    }
}
