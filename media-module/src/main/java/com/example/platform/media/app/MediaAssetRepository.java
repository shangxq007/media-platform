package com.example.platform.media.app;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.media.MediaAsset;
import java.util.Optional;

/**
 * MediaAsset persistence port (media domain owner).
 */
public interface MediaAssetRepository {

    MediaAsset save(MediaAsset asset);

    Optional<MediaAsset> findById(MediaAssetId id);

    boolean exists(MediaAssetId id);
}
