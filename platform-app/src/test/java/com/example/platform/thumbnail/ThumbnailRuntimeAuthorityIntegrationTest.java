package com.example.platform.thumbnail;

import com.example.platform.contract.media.ThumbnailContracts;

import static org.assertj.core.api.Assertions.*;

import com.example.platform.contract.media.CoverImageContracts;
import com.example.platform.frameextract.FfmpegCpuProvider;
import com.example.platform.frameextract.FrameExtractExecutionAdapter;
import com.example.platform.sandbox.execution.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThumbnailRuntimeAuthorityIntegrationTest {

    @TempDir Path temp;

    @Test void typedBackendPathCarriesPinnedProviderIdentity() throws Exception {
        var backend = new ExecutionBackend() {
            @Override public String backendId() { return "test-worker-runtime"; }
            @Override public boolean supports(TaskCapability c) { return c == TaskCapability.THUMBNAIL; }
            @Override public ExecutionResult execute(ExecutionRequest r) {
                assertThat(r.taskCapability()).isEqualTo(TaskCapability.THUMBNAIL);
                assertThat(r.payload()).containsEntry("providerId", ThumbnailContracts.PROVIDER);
                // The merged provider runs its ffprobe duration probe through the same backend.
                if (r.arguments().contains("-show_entries")) {
                    return ExecutionResult.success(0, "2.0", "", 1);
                }
                try {
                    Files.write(Path.of(r.arguments().getLast()), new byte[] {(byte) 0xff, (byte) 0xd8});
                } catch (java.io.IOException failure) {
                    throw new AssertionError("test backend could not write the provider output", failure);
                }
                return ExecutionResult.success(0, "", "", 1);
            }
        };
        ExecutionBackendRegistry registry = new ExecutionBackendRegistry() {
            public Optional<ExecutionBackend> resolve(TaskCapability c) { return c == TaskCapability.THUMBNAIL ? Optional.of(backend) : Optional.empty(); }
            public int size() { return 1; }
        };
        var provider = new FfmpegCpuProvider(registry, "/usr/bin/ffmpeg", "/usr/bin/ffprobe");
        Path input = temp.resolve("input");
        Files.write(input, new byte[] {1, 2, 3});

        var result = provider.render(ThumbnailContracts.CAPABILITY, input, temp.resolve("work"),
                "jpeg", null, null, 0.5d, () -> false);
        assertThat(result.succeeded()).isTrue();

        // No fallback backend: the cover capability has no registered COVER_IMAGE backend here, so the
        // same provider fails closed rather than silently reusing the thumbnail backend.
        assertThatThrownBy(() -> provider.render(CoverImageContracts.CAPABILITY, input,
                temp.resolve("cover-work"), "jpeg", null, null, 0.0d, () -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cover-image execution backend is not registered");

        // The unified adapter still refuses an unregistered provider family (no implicit default).
        var adapter = FrameExtractExecutionAdapter.of(provider, temp);
        assertThatThrownBy(() -> adapter.provider("unregistered.provider"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unregistered frame-extract provider");
    }
}
