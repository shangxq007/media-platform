package com.example.platform.runtime;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * P1-1A.7: a controlled start probe over the shared sandbox boundary.
 *
 * <p>It runs a caller-supplied executable with a bounded argv inside the production sandbox shape
 * (same launcher, mount policy, environment clearing and no-network policy as real execution) and
 * reports whether the process started. It proves <b>process start</b> only — it is never a complete
 * runtime dependency manifest, a media-capability proof or an admission authority.
 */
public interface SandboxedExecutableStartProbe {

    Result probe(Path executable, List<String> arguments);

    enum Status {
        /** The sandbox launched the exact executable and it exited zero. */
        STARTED,
        /** The sandbox refused the request or the process exited non-zero. */
        FAILED,
        /** No trustworthy conclusion (sandbox unavailable, or an unexpected failure). */
        UNKNOWN
    }

    record Result(Status status, String stdout, String detail) {
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(stdout, "stdout");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
