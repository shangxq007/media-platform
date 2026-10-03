package com.example.platform.providerplugin;

import java.util.List;
import java.util.Objects;

/**
 * BMF-PROVIDER-INTEGRATION-001: in-process registration for infrastructure providers.
 *
 * <p>BMF is an embedded media framework rather than a separately packaged PF4J plugin, so it is
 * never discovered from the worker's plugins directory. This helper lives beside
 * {@link ProviderPluginCatalog} on purpose: it uses the catalog's own package-private
 * {@link ProviderPluginCatalog#register} entry point instead of widening the registry API, and it
 * returns the host's catalog so the worker keeps exactly one catalog.
 */
public final class InProcessProviderPlugins {

    private InProcessProviderPlugins() {
    }

    /** Registers the extra in-process contributions on the given catalog and returns it. */
    public static ProviderPluginCatalog catalogWith(
            ProviderPluginCatalog hostCatalog,
            List<ProviderPluginContribution> extraContributions) {
        Objects.requireNonNull(hostCatalog, "hostCatalog");
        Objects.requireNonNull(extraContributions, "extraContributions");
        for (ProviderPluginContribution contribution : extraContributions) {
            hostCatalog.register(Objects.requireNonNull(contribution, "extraContributions element"));
        }
        return hostCatalog;
    }
}
