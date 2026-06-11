package com.example.platform.render.infrastructure.font;

import java.util.List;
import java.util.Optional;

public interface FontAssetRepository {

    Optional<FontAsset> findById(String fontAssetId);

    List<FontAsset> findByStatus(FontAssetStatus status);

    List<FontAsset> findAll();

    void save(FontAsset asset);

    void updateStatus(String fontAssetId, FontAssetStatus newStatus);

    void delete(String fontAssetId);

    boolean exists(String fontAssetId);
}
