package com.example.platform.media.api;
import com.example.platform.media.domain.stream.MediaStream;
import com.example.platform.shared.identity.ArtifactId;
import java.util.List;
/** Canonical streams belonging to exactly one Artifact. No mutation surface. */
public interface MediaStreamQueries {
    List<MediaStream> findByArtifactId(ArtifactId artifactId);
}
