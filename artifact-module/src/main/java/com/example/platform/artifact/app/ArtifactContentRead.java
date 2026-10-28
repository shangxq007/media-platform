package com.example.platform.artifact.app;

import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.digest.ContentDigest;

/** Authorized callers supply logical scope/identity only. No physical coordinates leave Artifact. */
public interface ArtifactContentRead {
    record Content(byte[] bytes,String contentType,String fileName,ContentDigest digest) {
        public Content { bytes=bytes.clone(); }
        @Override public byte[] bytes(){return bytes.clone();}
    }
    Content readProjectArtifact(ArtifactProjectScope scope, ArtifactId artifactId);
    Content read(ArtifactScope scope,ArtifactId artifactId);
}
