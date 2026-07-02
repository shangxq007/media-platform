package com.example.platform.render.testsupport.fakes;

import com.example.platform.render.domain.storage.StorageReference;
import com.example.platform.render.infrastructure.storage.StorageReferenceRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory fake for {@link StorageReferenceRepository}.
 *
 * <p>Provides real storage reference semantics without jOOQ or database.</p>
 */
public class FakeStorageReferenceRepository extends StorageReferenceRepository {

    private final Map<String, StorageReference> store = new ConcurrentHashMap<>();

    public FakeStorageReferenceRepository() { super(); }

    @Override
    public StorageReference save(StorageReference r) {
        String id = r.storageReferenceId() != null ? r.storageReferenceId()
                : "stor-" + UUID.randomUUID();
        StorageReference saved = new StorageReference(id, r.providerType(), r.storageClass(),
                r.rootPath(), r.relativePath(), r.checksum(), r.contentHash(),
                r.fileSize(), r.mimeType(), r.createdAt(), r.updatedAt());
        store.put(id, saved);
        return saved;
    }

    @Override
    public Optional<StorageReference> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<StorageReference> findByContentHash(String hash) {
        return store.values().stream()
                .filter(r -> hash.equals(r.contentHash()))
                .findFirst();
    }

    @Override
    public boolean exists(String id) {
        return store.containsKey(id);
    }

    @Override
    public void delete(String id) {
        store.remove(id);
    }
}
