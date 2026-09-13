package com.example.platform.storage.api;
import com.example.platform.storage.contract.StorageReference;
import java.util.Optional;
/** Internal materialization and reference contract. Physical paths stay inside runtime callers. */
public interface StorageRuntime {
 StorageReference register(StorageReference reference);
 Optional<StorageReference> find(String id);
 Optional<String> materialize(String id);
 boolean verifyChecksum(String id);
}
