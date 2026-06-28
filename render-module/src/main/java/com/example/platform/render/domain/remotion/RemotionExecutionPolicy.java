package com.example.platform.render.domain.remotion;

import java.util.List;

/**
 * Internal Remotion execution policy — defines when and how Remotion execution would be allowed.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>v0: executionEnabled=false. All execution remains disabled.</p>
 *
 * @param executionEnabled              whether execution is enabled (false in v0)
 * @param productionAllowed             whether production mode execution is allowed (false)
 * @param autoDispatchAllowed           whether auto-dispatch is allowed (false)
 * @param manualModeAllowed             whether MANUAL mode execution is allowed (false in v0)
 * @param experimentModeAllowed          whether EXPERIMENT mode execution is allowed (false in v0)
 * @param publicSelectionAllowed        whether public API can select Remotion (false)
 * @param userSuppliedComponentAllowed  whether user-supplied React components are allowed (false)
 * @param userSuppliedJavaScriptAllowed whether user-supplied JS is allowed (false)
 * @param networkAllowed                whether network access is allowed (false)
 * @param packageInstallAllowed         whether npm install is allowed (false)
 * @param npxPackageDownloadAllowed     whether npx package download is allowed (false)
 * @param auditRequired                 whether audit events are required (true)
 * @param correlationRequired           whether correlation context is required (true)
 * @param timeoutRequired               whether timeout is required (true)
 * @param resourceLimitsRequired        whether resource limits are required (true)
 */
public record RemotionExecutionPolicy(
        boolean executionEnabled,
        boolean productionAllowed,
        boolean autoDispatchAllowed,
        boolean manualModeAllowed,
        boolean experimentModeAllowed,
        boolean publicSelectionAllowed,
        boolean userSuppliedComponentAllowed,
        boolean userSuppliedJavaScriptAllowed,
        boolean networkAllowed,
        boolean packageInstallAllowed,
        boolean npxPackageDownloadAllowed,
        boolean auditRequired,
        boolean correlationRequired,
        boolean timeoutRequired,
        boolean resourceLimitsRequired) {

    /**
     * Default policy: everything disabled.
     */
    public static RemotionExecutionPolicy disabledDefault() {
        return new RemotionExecutionPolicy(
                false, false, false, false, false,
                false, false, false,
                false, false, false,
                true, true, true, true);
    }

    /**
     * Design-only policy for manual/experiment documentation.
     * Still executionEnabled=false.
     */
    public static RemotionExecutionPolicy manualExperimentDesignOnly() {
        return new RemotionExecutionPolicy(
                false, false, false, true, true,
                false, false, false,
                false, false, false,
                true, true, true, true);
    }

    /**
     * Future local POC policy — disabled by default.
     * Would need explicit internal enablement to activate.
     */
    public static RemotionExecutionPolicy futureLocalPocDisabledByDefault() {
        return new RemotionExecutionPolicy(
                false, false, false, true, true,
                false, false, false,
                false, false, false,
                true, true, true, true);
    }

    /**
     * Returns list of reasons why execution is blocked.
     */
    public List<String> blockedReasons() {
        java.util.List<String> reasons = new java.util.ArrayList<>();
        if (!executionEnabled) reasons.add("Execution not enabled");
        if (!manualModeAllowed) reasons.add("Manual mode not allowed");
        if (!experimentModeAllowed) reasons.add("Experiment mode not allowed");
        return List.copyOf(reasons);
    }
}
