package com.example.platform.media.api;
import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.media.MediaAsset;
import java.util.Optional;
/** Canonical source identity/scope query. Trusted consumers must validate the returned
 * owner against their authorized operation scope; this is not an HTTP authorization grant. */
public interface MediaAssetQueries {
    Optional<MediaAsset> findById(MediaAssetId id);
}
