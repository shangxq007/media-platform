package com.example.platform.artifact.contract;

import com.example.platform.shared.identity.ArtifactId;
import java.nio.file.Path;

/** Trusted bounded probe and normalization of the exact Storage-verified source file. */
@org.springframework.modulith.NamedInterface("app")
public interface ArtifactSourceProbePort {
    ArtifactSourceFacts probe(ArtifactId artifactId, Path verifiedSource, String mimeType);
}
