package com.example.platform.media.api;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStream;
import java.util.List;
/** Canonical streams belonging to exactly one Media asset. No mutation surface. */
public interface MediaStreamQueries {
    List<MediaStream> findByMediaAssetId(MediaAssetId mediaAssetId);
}
