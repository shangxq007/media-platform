package com.example.platform.execution.domain.provider;

import java.util.Objects;

/** Typed immutable reference to a provider capability profile by version or content digest. */
public sealed interface ProviderCapabilityProfileVersionOrDigest
        permits ProviderCapabilityProfileVersionOrDigest.VersionReference,
                ProviderCapabilityProfileVersionOrDigest.DigestReference {

    static VersionReference version(ProviderCapabilityProfileVersion version) {
        return new VersionReference(version);
    }

    static DigestReference digest(ProviderCapabilityProfileDigest digest) {
        return new DigestReference(digest);
    }

    record VersionReference(ProviderCapabilityProfileVersion version)
            implements ProviderCapabilityProfileVersionOrDigest {
        public VersionReference {
            Objects.requireNonNull(version, "version");
        }
    }

    record DigestReference(ProviderCapabilityProfileDigest digest)
            implements ProviderCapabilityProfileVersionOrDigest {
        public DigestReference {
            Objects.requireNonNull(digest, "digest");
        }
    }
}
