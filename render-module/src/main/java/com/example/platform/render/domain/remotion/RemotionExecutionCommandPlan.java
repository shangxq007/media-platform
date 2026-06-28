package com.example.platform.render.domain.remotion;

import java.util.List;

/**
 * Internal Remotion execution command plan — structured, non-executing command model.
 *
 * <p>Internal only — not exposed in public APIs.</p>
 *
 * <p>v0: model only. Must not be executed.</p>
 *
 * @param commandKind        command kind (e.g., "render", "preview")
 * @param executableRef      trusted internal executable reference
 * @param arguments          structured argument list (not shell-joined)
 * @param workingDirectoryRef managed working directory reference
 * @param inputPropsRef      managed input props file reference
 * @param outputRef          managed output file reference
 * @param timeoutSeconds     timeout in seconds
 * @param networkPolicy      network policy for this command
 * @param trustedTemplateRef trusted internal template reference
 * @param compositionId      composition ID from allowlist
 * @param safeMetadata       safe metadata only
 */
public record RemotionExecutionCommandPlan(
        String commandKind,
        String executableRef,
        List<String> arguments,
        String workingDirectoryRef,
        String inputPropsRef,
        String outputRef,
        int timeoutSeconds,
        RemotionExecutionNetworkPolicy networkPolicy,
        String trustedTemplateRef,
        String compositionId,
        java.util.Map<String, String> safeMetadata) {

    /**
     * Returns true if this command plan has safe structure.
     * Does NOT validate execution readiness.
     */
    public boolean hasSafeStructure() {
        return commandKind != null && !commandKind.isBlank()
                && executableRef != null && !executableRef.isBlank()
                && arguments != null
                && workingDirectoryRef != null && !workingDirectoryRef.isBlank()
                && inputPropsRef != null && !inputPropsRef.isBlank()
                && outputRef != null && !outputRef.isBlank()
                && timeoutSeconds > 0;
    }

    /**
     * Returns list of structural issues.
     */
    public List<String> structuralIssues() {
        java.util.List<String> issues = new java.util.ArrayList<>();
        if (commandKind == null || commandKind.isBlank()) issues.add("commandKind is missing");
        if (executableRef == null || executableRef.isBlank()) issues.add("executableRef is missing");
        if (arguments == null) issues.add("arguments is null");
        if (workingDirectoryRef == null || workingDirectoryRef.isBlank()) issues.add("workingDirectoryRef is missing");
        if (inputPropsRef == null || inputPropsRef.isBlank()) issues.add("inputPropsRef is missing");
        if (outputRef == null || outputRef.isBlank()) issues.add("outputRef is missing");
        if (timeoutSeconds <= 0) issues.add("timeoutSeconds must be positive");
        return List.copyOf(issues);
    }
}
