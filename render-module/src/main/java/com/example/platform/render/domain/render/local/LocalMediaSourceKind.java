package com.example.platform.render.domain.render.local;

/**
 * Kind of local media source for the local runner.
 *
 * <p>Only controlled local fixture sources are supported.
 * User-uploaded, remote URL, StorageRuntime, and ProductRuntime
 * sources are explicitly rejected.</p>
 */
public enum LocalMediaSourceKind {

    /**
     * Platform-generated local fixture under controlled output root.
     * Deterministic, reproducible, no external dependencies.
     */
    CONTROLLED_LOCAL_FIXTURE
}
