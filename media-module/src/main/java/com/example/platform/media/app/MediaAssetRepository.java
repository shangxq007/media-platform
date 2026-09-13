package com.example.platform.media.app;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.media.MediaAsset;
import java.util.Optional;

/**
 * MediaAsset persistence port (media domain owner).
 */
public interface MediaAssetRepository extends com.example.platform.media.api.MediaAssetQueries {

    MediaAsset save(MediaAsset asset);

    boolean exists(MediaAssetId id);
}
