package com.example.platform.media.app;

import com.example.platform.media.domain.stream.MediaStream;
import com.example.platform.shared.identity.ArtifactId;
import java.util.List;

/**
 * Canonical source stream persistence port.
 */
public interface MediaStreamRepository extends com.example.platform.media.api.MediaStreamQueries {

    void saveAll(ArtifactId artifactId, List<MediaStream> streams);

    void deleteByArtifactId(ArtifactId artifactId);
}
