package com.example.platform.thumbnail;

import java.util.Set;
import java.util.function.BooleanSupplier;

/** Stable typed media.thumbnail invocation contract. */
public interface ThumbnailCapabilityProvider {
    Manifest manifest();
    Result extract(ThumbnailContracts.Request request, byte[] input, BooleanSupplier cancelled);

    /**
     * Provider declaration aligned with {@code CoverImageCapabilityProvider.Manifest}: a
     * capability-independent provider <em>family</em> identity plus the one implementation identity
     * that runs inside it ({@code providerId} and {@code providerImplementationId} are separate
     * slots, both required). The capability id is declared independently of both.
     */
    record Manifest(String capabilityId, String providerId, String providerImplementationId,
            String providerVersion, String executableToolchain, Set<String> inputFormats,
            Set<String> outputFormats, double minimumTimestampSeconds, double maximumTimestampSeconds,
            int minimumWidth, int maximumWidth, long maximumInputBytes,
            int timeoutSeconds, String trustRequirement, String runtimeRequirement) {

        public Manifest {
            ThumbnailContracts.require(capabilityId, "capabilityId");
            ThumbnailContracts.require(providerId, "providerId");
            ThumbnailContracts.require(providerImplementationId, "providerImplementationId");
            ThumbnailContracts.require(providerVersion, "providerVersion");
        }
    }
    record Result(byte[] bytes, String contentType, String failureCode) {
        public static Result success(byte[] bytes, String contentType) { return new Result(bytes, contentType, null); }
        public static Result failure(String code) { return new Result(null, null, code); }
        public boolean succeeded() { return bytes != null && failureCode == null; }
    }
}
