package com.example.platform.sandbox;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

/** Exact normalized input and bounded mutable roots. */
@org.springframework.modulith.NamedInterface("API")
public record FilesystemPolicy(
        Set<Path> readOnlyInputs,
        Path workspaceRoot,
        Path temporaryRoot,
        Path outputStagingRoot,
        Path workingDirectory,
        PathEscapePolicy pathEscapePolicy,
        SymlinkPolicy symlinkPolicy) {

    @org.springframework.modulith.NamedInterface("API")
    public enum PathEscapePolicy { REJECT }

    @org.springframework.modulith.NamedInterface("API")
    public enum SymlinkPolicy { REJECT_ESCAPE }

    public FilesystemPolicy {
        readOnlyInputs = Set.copyOf(Objects.requireNonNull(readOnlyInputs, "readOnlyInputs"));
        workspaceRoot = normalized(workspaceRoot, "workspaceRoot");
        temporaryRoot = normalized(temporaryRoot, "temporaryRoot");
        outputStagingRoot = normalized(outputStagingRoot, "outputStagingRoot");
        workingDirectory = normalized(workingDirectory, "workingDirectory");
        Objects.requireNonNull(pathEscapePolicy, "pathEscapePolicy");
        Objects.requireNonNull(symlinkPolicy, "symlinkPolicy");
        if (!temporaryRoot.startsWith(workspaceRoot)
                || !outputStagingRoot.startsWith(workspaceRoot)
                || !workingDirectory.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("mutable roots must be bounded by workspace");
        }
        readOnlyInputs = readOnlyInputs.stream().map(path -> normalized(path, "readOnlyInput"))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public static FilesystemPolicy exact(
            Set<Path> inputs, Path workspace, Path temporary, Path output, Path workingDirectory) {
        return new FilesystemPolicy(inputs, workspace, temporary, output, workingDirectory,
                PathEscapePolicy.REJECT, SymlinkPolicy.REJECT_ESCAPE);
    }

    private static Path normalized(Path path, String label) {
        Objects.requireNonNull(path, label);
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException(label + " must be absolute");
        }
        return path.normalize();
    }
}
