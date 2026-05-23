package com.example.platform.shared.runtime;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cross-cutting runtime switches for production safety and persistence authority.
 */
@ConfigurationProperties(prefix = "platform.runtime")
public class PlatformRuntimeProperties {

    /**
     * When true (default in prod profile), startup fails if dev-oriented defaults are active
     * (H2, disabled security, stub AI, noop payment, in-memory commerce authority).
     */
    private boolean productionChecksEnabled = false;

    /**
     * Maximum allowed Spring Modulith violations before CI fails (debt budget; reduce over time).
     */
    private int modulithViolationBudget = 13;

    public boolean isProductionChecksEnabled() {
        return productionChecksEnabled;
    }

    public void setProductionChecksEnabled(boolean productionChecksEnabled) {
        this.productionChecksEnabled = productionChecksEnabled;
    }

    public int getModulithViolationBudget() {
        return modulithViolationBudget;
    }

    public void setModulithViolationBudget(int modulithViolationBudget) {
        this.modulithViolationBudget = modulithViolationBudget;
    }
}
