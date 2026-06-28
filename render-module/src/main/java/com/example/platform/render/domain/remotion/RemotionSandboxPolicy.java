package com.example.platform.render.domain.remotion;

import java.util.List;

/**
 * Internal Remotion sandbox policy — defines workspace and IO constraints.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>v0: model and contract only. No actual sandbox implementation.</p>
 */
public record RemotionSandboxPolicy(
        boolean managedWorkingDirectoryRequired,
        boolean managedOutputDirectoryRequired,
        boolean storageMaterializedInputsRequired,
        boolean prohibitRawStorageInternals,
        boolean prohibitSignedUrls,
        boolean prohibitArbitraryUserPaths,
        boolean prohibitEnvironmentLeakage,
        boolean prohibitInheritedSecrets,
        boolean prohibitNetwork,
        boolean prohibitPackageInstall,
        boolean prohibitUserUploadedProject,
        boolean prohibitDynamicImportsFromUserContent,
        boolean cleanupRequired,
        boolean quarantineOnFailure,
        boolean auditBeforeExecutionRequired,
        boolean auditAfterExecutionRequired) {

    /**
     * Default locked-down sandbox policy.
     */
    public static RemotionSandboxPolicy lockedDown() {
        return new RemotionSandboxPolicy(
                true, true, true,
                true, true, true, true, true,
                true, true, true, true,
                true, true, true, true);
    }

    /**
     * Returns list of sandbox constraints.
     */
    public List<String> constraints() {
        java.util.List<String> c = new java.util.ArrayList<>();
        if (managedWorkingDirectoryRequired) c.add("Managed working directory required");
        if (managedOutputDirectoryRequired) c.add("Managed output directory required");
        if (storageMaterializedInputsRequired) c.add("StorageRuntime materialized inputs required");
        if (prohibitRawStorageInternals) c.add("Raw storage internals prohibited");
        if (prohibitSignedUrls) c.add("Signed URLs prohibited");
        if (prohibitArbitraryUserPaths) c.add("Arbitrary user paths prohibited");
        if (prohibitEnvironmentLeakage) c.add("Environment leakage prohibited");
        if (prohibitInheritedSecrets) c.add("Inherited secrets prohibited");
        if (prohibitNetwork) c.add("Network access prohibited");
        if (prohibitPackageInstall) c.add("Package install prohibited");
        if (prohibitUserUploadedProject) c.add("User-uploaded project prohibited");
        if (prohibitDynamicImportsFromUserContent) c.add("Dynamic imports from user content prohibited");
        return List.copyOf(c);
    }
}
