package com.example.platform.storage.api;
import com.example.platform.storage.contract.StorageReference;
import java.util.Optional;
/** Storage-owned reference commands and queries for internal runtime consumers. */
public interface StorageReferenceStore {
 StorageReference save(StorageReference reference);
 Optional<StorageReference> findById(String id);
 Optional<StorageReference> findByContentHash(String hash);
 boolean exists(String id);
 void delete(String id);
}
