package com.example.platform.bmf;

import java.nio.file.Path;
import java.util.Objects;

/** Worker-owned mutable roots for one BMF process. */
public record BmfSandboxWorkspace(Path root, Path temporaryRoot, Path outputStagingRoot) {
    public BmfSandboxWorkspace {
        Objects.requireNonNull(root, "root");
        if (!root.isAbsolute() || !root.equals(root.normalize())) {
            throw new IllegalArgumentException("workspace root must be absolute and normalized");
        }
        Objects.requireNonNull(temporaryRoot, "temporaryRoot");
        Objects.requireNonNull(outputStagingRoot, "outputStagingRoot");
    }

    public static BmfSandboxWorkspace under(Path root) {
        Objects.requireNonNull(root, "root");
        return new BmfSandboxWorkspace(root, root.resolve(".sandbox-tmp"),
                root.resolve(".sandbox-output"));
    }
}
