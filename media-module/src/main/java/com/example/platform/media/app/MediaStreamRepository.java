package com.example.platform.media.app;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStream;
import java.util.List;

/**
 * Canonical source stream persistence port.
 */
public interface MediaStreamRepository {

    void saveAll(MediaAssetId mediaAssetId, List<MediaStream> streams);

    List<MediaStream> findByMediaAssetId(MediaAssetId mediaAssetId);

    void deleteByMediaAssetId(MediaAssetId mediaAssetId);
}
