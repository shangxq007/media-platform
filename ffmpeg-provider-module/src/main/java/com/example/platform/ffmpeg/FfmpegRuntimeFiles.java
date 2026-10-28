package com.example.platform.ffmpeg;

import java.nio.file.*;
import java.util.*;

/** Read-only assets of the configured runtime distribution; never selected from request data. */
final class FfmpegRuntimeFiles {
    private FfmpegRuntimeFiles() {}
    static Set<Path> inputs(Path executable, Path... mediaInputs) {
        Set<Path> inputs = new HashSet<>(List.of(mediaInputs));
        Path root = executable.getParent();
        // The pinned distribution uses $ORIGIN DSOs. Keep that dedicated directory together;
        // binding just its executable under another name incorrectly loads host codec libraries.
        if (root != null && Files.isRegularFile(root.resolve("libavcodec.so.61"), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(root.resolve("libavformat.so.61"), LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(root.resolve("libavutil.so.59"), LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(root)) throw new IllegalArgumentException("runtime directory symlink refused");
            inputs.add(root);
        } else {
            inputs.add(executable);
        }
        return Set.copyOf(inputs);
    }
}
