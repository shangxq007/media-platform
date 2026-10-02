package com.example.platform.frameextract;

import com.example.platform.contract.media.FrameExtractProvider;
import com.example.platform.contract.media.FrameExtractManifest;
import com.example.platform.contract.media.FrameExtractResult;

import com.example.platform.contract.media.CoverImageCapabilityProvider;
import com.example.platform.contract.media.CoverImageContracts;
import com.example.platform.contract.media.ThumbnailCapabilityProvider;
import com.example.platform.contract.media.ThumbnailContracts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Unified worker-side execution adapter for the ffmpeg frame-extract capabilities.
 *
 * <p>It replaces {@code CoverImageCapabilityRegistry} and {@code ThumbnailProviderInvoker}. Both
 * worker activities keep their capability-scoped worker-facing signatures, but the selection,
 * execution and scratch-space lifecycle now live in this one adapter: it resolves the provider that
 * declares the requested {@code capabilityId} from the registered {@link FrameExtractProvider} beans
 * and delegates. There is no second discovery path and no fallback provider — discovery and
 * registration remain the platform capability registry's job
 * ({@code FrameExtractPlatformRegistration}).
 *
 * <p>Fail-closed composition: empty registration, a duplicate provider family, a provider whose
 * family and implementation identity collapse into one value, a missing pinned family, a capability
 * no registered provider declares, and a pinned provider that does not declare one of the platform
 * frame-extract capabilities all fail at construction. One capability may be declared by several
 * provider families (N:M): the pinned family wins deterministically and the others stay registered.
 */
@Component
@ConditionalOnProperty(name = "platform.runtime.role", havingValue = "WORKER")
public final class FrameExtractExecutionAdapter
        implements CoverImageCapabilityProvider, ThumbnailCapabilityProvider {

    /** The platform frame-extract capabilities this adapter routes; one provider may serve both. */
    public static final List<String> CAPABILITIES = List.of(
            CoverImageContracts.CAPABILITY, ThumbnailContracts.CAPABILITY);

    /**
     * Pinned provider family — the capability-neutral platform ffmpeg frame-extract family. This is a
     * provider family identity, never a capability id, so the pin stays capability-neutral: the
     * capability-scoped facts (width bound, timeout, sandbox task capability) are carried by the
     * executing {@code capabilityId} the caller passes in, not by the pin.
     */
    public static final String PINNED_PROVIDER_ID = FfmpegCpuProvider.PROVIDER_ID;

    private final Map<String, FrameExtractProvider> byProviderId;
    private final Map<String, String> capabilityFamily;
    private final Path workRoot;

    public FrameExtractExecutionAdapter(
            List<FrameExtractProvider> registered,
            @Value("${app.cover-image.work-root:./.data/cover-image-work}") String workRoot) {
        if (registered == null || registered.isEmpty()) {
            throw new IllegalStateException("no frame-extract provider registered");
        }
        Map<String, FrameExtractProvider> accepted = new HashMap<>();
        for (FrameExtractProvider provider : registered) {
            Objects.requireNonNull(provider, "provider");
            var declared = provider.manifest();
            if (declared.providerId().equals(declared.providerImplementationId())) {
                throw new IllegalStateException("frame-extract provider family and implementation"
                        + " identity must differ: " + declared.providerId());
            }
            if (accepted.putIfAbsent(declared.providerId(), provider) != null) {
                throw new IllegalStateException("duplicate frame-extract provider: " + declared.providerId());
            }
        }
        if (!accepted.containsKey(PINNED_PROVIDER_ID)) {
            throw new IllegalStateException(
                    "pinned frame-extract provider is not registered: " + PINNED_PROVIDER_ID);
        }
        Map<String, String> families = new HashMap<>();
        for (String capabilityId : CAPABILITIES) {
            boolean declared = false;
            for (FrameExtractProvider provider : accepted.values()) {
                declared |= provider.manifest().supports(capabilityId);
            }
            if (!declared) {
                throw new IllegalStateException(
                        "no registered provider declares capability " + capabilityId);
            }
            // One capability may be declared by several providers (N:M); the pinned family wins
            // deterministically, so the pinned provider must itself declare every routed capability.
            if (!accepted.get(PINNED_PROVIDER_ID).manifest().supports(capabilityId)) {
                throw new IllegalStateException("pinned frame-extract provider " + PINNED_PROVIDER_ID
                        + " does not declare capability " + capabilityId);
            }
            families.put(capabilityId, PINNED_PROVIDER_ID);
        }
        this.byProviderId = Map.copyOf(accepted);
        this.capabilityFamily = Map.copyOf(families);
        this.workRoot = Path.of(workRoot).toAbsolutePath().normalize();
    }

    @Override
    public FrameExtractManifest manifest() {
        return provider().manifest();
    }

    /** Explicit override resolving the two identical capability-contract defaults. */
    @Override
    public boolean supports(String capabilityId) {
        return manifest().supports(capabilityId);
    }

    @Override
    public FrameExtractResult render(
            String capabilityId,
            CoverImageContracts.Request request,
            Path inputPath,
            BooleanSupplier cancelled) {
        return execute(capabilityId, inputPath, workDirectory(request.idempotencyKey()),
                request.imageFormat(), request.width(), request.quality(),
                request.timestampSeconds(), cancelled);
    }

    @Override
    public FrameExtractResult extract(
            String capabilityId,
            ThumbnailContracts.Request request,
            byte[] input,
            BooleanSupplier cancelled) {
        Objects.requireNonNull(input, "input");
        Path work = workDirectory(request.idempotencyKey());
        try {
            Files.createDirectories(work);
            Path source = work.resolve("input");
            Files.write(source, input);
            return execute(capabilityId, source, work, request.imageFormat(), request.width(),
                    request.quality(), request.timestampSeconds(), cancelled);
        } catch (IOException failure) {
            return FrameExtractResult.failure("PROVIDER_IO_FAILED");
        } finally {
            deleteRecursively(work);
        }
    }

    /** Invocation entry point for the cover-side worker activity (capability-scoped, no fallback). */
    public FrameExtractResult invoke(
            String capabilityId,
            CoverImageContracts.Request request,
            Path inputPath,
            BooleanSupplier cancelled) {
        return render(capabilityId, request, inputPath, cancelled);
    }

    /** Invocation entry point for the thumbnail-side worker activity. */
    public FrameExtractResult invoke(
            ThumbnailContracts.Request request, byte[] input, BooleanSupplier cancelled) {
        return extract(ThumbnailContracts.CAPABILITY, request, input, cancelled);
    }

    /** Static factory for non-Spring callers that hold exactly one provider. */
    public static FrameExtractExecutionAdapter of(FrameExtractProvider provider, Path workRoot) {
        return new FrameExtractExecutionAdapter(
                List.of(provider), Objects.requireNonNull(workRoot, "workRoot").toString());
    }

    /** The provider family registered for the manifest's own (pinned) family identity. */
    public FrameExtractProvider provider() {
        return provider(PINNED_PROVIDER_ID);
    }

    /** Lookup by provider family identity, independent of any capability. */
    public FrameExtractProvider provider(String providerFamilyId) {
        FrameExtractProvider provider = byProviderId.get(providerFamilyId);
        if (provider == null) {
            throw new IllegalArgumentException("unregistered frame-extract provider: " + providerFamilyId);
        }
        return provider;
    }

    /** Deterministic capability → provider-family resolution; fail closed, no fallback provider. */
    public FrameExtractProvider providerForCapability(String capabilityId) {
        String family = capabilityFamily.get(capabilityId);
        if (family == null) {
            throw new IllegalStateException("no registered provider serves capability " + capabilityId);
        }
        return provider(family);
    }

    /** Provider family identity registered for a capability. */
    public String providerFamilyOf(String capabilityId) {
        return providerForCapability(capabilityId).manifest().providerId();
    }

    private FrameExtractResult execute(
            String capabilityId,
            Path input,
            Path work,
            String imageFormat,
            Integer width,
            Integer quality,
            double timestampSeconds,
            BooleanSupplier cancelled) {
        if (!manifest().supports(capabilityId)) {
            return FrameExtractResult.failure("UNSUPPORTED_CAPABILITY");
        }
        if (cancelled.getAsBoolean()) {
            return FrameExtractResult.failure("CANCELLED");
        }
        try {
            Files.createDirectories(work);
        } catch (IOException failure) {
            return FrameExtractResult.failure("PROVIDER_IO_FAILED");
        }
        FrameExtractResult result = providerForCapability(capabilityId).render(capabilityId, input, work,
                imageFormat, width, quality, timestampSeconds, cancelled);
        return result.succeeded()
                ? result
                : FrameExtractResult.failure(
                        result.failureCode() == null ? "PROVIDER_FAILED" : result.failureCode());
    }

    private Path workDirectory(String idempotencyKey) {
        Path work = workRoot.resolve(idempotencyKey).normalize();
        if (!work.startsWith(workRoot)) {
            throw new IllegalArgumentException("invalid frame-extract scratch directory");
        }
        return work;
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort scratch cleanup; the provider result is already computed
                }
            });
        } catch (IOException ignored) {
            // best-effort scratch cleanup
        }
    }
}
