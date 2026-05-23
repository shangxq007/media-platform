package com.example.platform.policy.featureflag;

import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.OpenFeatureAPI;
import org.springframework.beans.factory.DisposableBean;

/**
 * Registers the global OpenFeature provider at startup and shuts down on context close.
 */
public class OpenFeatureLifecycle implements DisposableBean {

    public OpenFeatureLifecycle(FeatureProvider provider) throws Exception {
        OpenFeatureAPI.getInstance().setProviderAndWait(provider);
    }

    @Override
    public void destroy() {
        OpenFeatureAPI.getInstance().shutdown();
    }
}
