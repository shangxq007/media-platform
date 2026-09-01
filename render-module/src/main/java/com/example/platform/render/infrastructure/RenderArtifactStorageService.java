package com.example.platform.render.infrastructure;

import java.io.IOException;
import org.springframework.stereotype.Service;

/**
 * Fail-closed gate for legacy render-output finalization.
 *
 * <p>The former implementation wrapped a provider bucket/key in
 * {@code StorageObjectId} and invented replica/provider facts before calling
 * Artifact. That path is unavailable until a Storage-owned production write
 * boundary returns a canonical logical ID and placement receipt.</p>
 */
@Service
public class RenderArtifactStorageService {

    public void uploadJobOutput(String jobId, String projectId, String artifactId,
            String localRelativePath, String contentType) throws IOException {
        throw new UnsupportedOperationException(
                "Render Artifact finalization requires Storage-owned canonical issuance");
    }
}
