package com.example.platform.media.app;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStream;
import java.util.List;

/**
 * Canonical source stream persistence port.
 */
public interface MediaStreamRepository extends com.example.platform.media.api.MediaStreamQueries {

    void saveAll(MediaAssetId mediaAssetId, List<MediaStream> streams);

    void deleteByMediaAssetId(MediaAssetId mediaAssetId);
}
