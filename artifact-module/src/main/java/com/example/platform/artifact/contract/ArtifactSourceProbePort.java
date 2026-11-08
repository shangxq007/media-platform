package com.example.platform.artifact.contract;

import com.example.platform.shared.identity.ArtifactId;

/** Trusted bounded probe and normalization of the exact Storage-verified source bytes. */
@org.springframework.modulith.NamedInterface("app")
public interface ArtifactSourceProbePort {
    ArtifactSourceFacts probe(ArtifactId artifactId, byte[] bytes, String mimeType);
}
