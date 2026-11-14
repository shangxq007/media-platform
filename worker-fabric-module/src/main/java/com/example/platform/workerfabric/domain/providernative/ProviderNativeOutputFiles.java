package com.example.platform.workerfabric.domain.providernative;

import java.nio.file.Path;

/**
 * Well-known provider output file name inside the sandbox working directory.
 *
 * <p>Bounded CPU providers write their primary artifact to this file instead of stdout so a
 * large render is never buffered in memory or truncated by the sandbox stdout capture bound.
 * Small auxiliary outputs (e.g. a single extracted frame) may still use stdout.
 */
public final class ProviderNativeOutputFiles {

    /** Primary provider artifact file name, relative to the sandbox working directory. */
    public static final String RENDER_OUTPUT = "platform-provider-output.bin";

    private ProviderNativeOutputFiles() {}

    /** Resolves the primary provider output file inside the given working directory. */
    public static Path renderOutput(Path workingDirectory) {
        return workingDirectory.resolve(RENDER_OUTPUT);
    }
}
