package com.example.platform.render.infrastructure.storage;

import static org.jooq.impl.DSL.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class StorageReferenceRepository {

    private final DSLContext dsl;
    public StorageReferenceRepository(DSLContext dsl) { this.dsl = dsl; }

    public StorageReference save(StorageReference r) {
        var id = r.storageReferenceId() != null ? r.storageReferenceId() : Ids.newId("stor");
        var now = OffsetDateTime.now();
        dsl.execute(
                "INSERT INTO storage_reference (storage_reference_id, provider_type, storage_class, root_path, relative_path, checksum, content_hash, file_size, mime_type, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (provider_type, root_path, relative_path) DO UPDATE SET "
                        + "checksum = EXCLUDED.checksum, content_hash = EXCLUDED.content_hash, "
                        + "file_size = EXCLUDED.file_size, mime_type = EXCLUDED.mime_type, updated_at = EXCLUDED.updated_at",
                id, r.providerType(), r.storageClass().name(), r.rootPath(), r.relativePath(),
                r.checksum(), r.contentHash(), r.fileSize(), r.mimeType(), now, now, now);
        return findById(id).orElseThrow();
    }

    public Optional<StorageReference> findById(String id) {
        var row = dsl.select().from(table("storage_reference")).where(field("storage_reference_id").eq(id)).fetchOne();
        return row == null ? Optional.empty() : Optional.of(map(row));
    }

    public Optional<StorageReference> findByContentHash(String hash) {
        var row = dsl.select().from(table("storage_reference")).where(field("content_hash").eq(hash)).limit(1).fetchOne();
        return row == null ? Optional.empty() : Optional.of(map(row));
    }

    public boolean exists(String id) { return dsl.fetchCount(table("storage_reference"), field("storage_reference_id").eq(id)) > 0; }
    public void delete(String id) { dsl.deleteFrom(table("storage_reference")).where(field("storage_reference_id").eq(id)).execute(); }

    private static StorageReference map(Record r) {
        return new StorageReference(
                r.get(field("storage_reference_id", String.class)),
                r.get(field("provider_type", String.class)),
                e(StorageClass.class, r.get(field("storage_class", String.class))),
                r.get(field("root_path", String.class)), r.get(field("relative_path", String.class)),
                r.get(field("checksum", String.class)), r.get(field("content_hash", String.class)),
                r.get(field("file_size", Long.class)), r.get(field("mime_type", String.class)),
                toInst(r.get(field("created_at", OffsetDateTime.class))),
                toInst(r.get(field("updated_at", OffsetDateTime.class))));
    }
    private static Instant toInst(OffsetDateTime o) { return o != null ? o.toInstant() : null; }
    private static <E extends Enum<E>> E e(Class<E> t, String v) { try { return Enum.valueOf(t, v); } catch (Exception ex) { return null; } }
}
