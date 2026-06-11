package com.example.platform.render.infrastructure.font;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryFontAssetRepository implements FontAssetRepository {

    private final Map<String, FontAsset> store = new ConcurrentHashMap<>();

    @Override
    public Optional<FontAsset> findById(String fontAssetId) {
        return Optional.ofNullable(store.get(fontAssetId));
    }

    @Override
    public List<FontAsset> findByStatus(FontAssetStatus status) {
        return store.values().stream()
                .filter(a -> a.status() == status)
                .toList();
    }

    @Override
    public List<FontAsset> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void save(FontAsset asset) {
        store.put(asset.id(), asset);
    }

    @Override
    public void updateStatus(String fontAssetId, FontAssetStatus newStatus) {
        FontAsset existing = store.get(fontAssetId);
        if (existing != null) {
            store.put(fontAssetId, new FontAsset(
                    existing.id(), existing.fileName(), existing.fontFamily(),
                    existing.fontSubfamily(), existing.format(), existing.fileSize(),
                    existing.sha256(), existing.storageUri(), newStatus,
                    existing.securityResult(), existing.validationResult(),
                    existing.subsetResult()
            ));
        }
    }

    @Override
    public void delete(String fontAssetId) {
        store.remove(fontAssetId);
    }

    @Override
    public boolean exists(String fontAssetId) {
        return store.containsKey(fontAssetId);
    }
}
