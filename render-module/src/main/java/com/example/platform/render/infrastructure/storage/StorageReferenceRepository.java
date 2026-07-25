package com.example.platform.render.infrastructure.storage;

import static org.jooq.impl.DSL.*;
import com.example.platform.render.domain.storage.*;
import com.example.platform.shared.Ids;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.StorageReference.STORAGE_REFERENCE;
import org.jooq.impl.DSL;


@Repository
public class StorageReferenceRepository {

    private final DSLContext dsl;

    protected StorageReferenceRepository() { this.dsl = null; }

    @org.springframework.beans.factory.annotation.Autowired
    public StorageReferenceRepository(DSLContext dsl) { this.dsl = dsl; }

    public StorageReference save(StorageReference r) {
        var id = r.storageReferenceId() != null ? r.storageReferenceId() : Ids.newId("stor");
        var now = LocalDateTime.now();
        dsl.insertInto(STORAGE_REFERENCE)
                .columns(STORAGE_REFERENCE.STORAGE_REFERENCE_ID, STORAGE_REFERENCE.PROVIDER_TYPE, STORAGE_REFERENCE.STORAGE_CLASS,
                        STORAGE_REFERENCE.ROOT_PATH, STORAGE_REFERENCE.RELATIVE_PATH, STORAGE_REFERENCE.CHECKSUM, STORAGE_REFERENCE.CONTENT_HASH,
                        STORAGE_REFERENCE.FILE_SIZE, STORAGE_REFERENCE.MIME_TYPE, STORAGE_REFERENCE.CREATED_AT, STORAGE_REFERENCE.UPDATED_AT)
                .values(id, r.providerType(), r.storageClass().name(), r.rootPath(), r.relativePath(),
                        r.checksum(), r.contentHash(), r.fileSize(), r.mimeType(), now, now)
                .onConflict(STORAGE_REFERENCE.PROVIDER_TYPE, STORAGE_REFERENCE.ROOT_PATH, STORAGE_REFERENCE.RELATIVE_PATH)
                .doUpdate()
                .set(STORAGE_REFERENCE.CHECKSUM, r.checksum())
                .set(STORAGE_REFERENCE.CONTENT_HASH, r.contentHash())
                .set(STORAGE_REFERENCE.FILE_SIZE, r.fileSize())
                .set(STORAGE_REFERENCE.MIME_TYPE, r.mimeType())
                .set(STORAGE_REFERENCE.UPDATED_AT, now)
                .execute();
        return findById(id).orElseThrow();
    }

    public Optional<StorageReference> findById(String id) {
        var row = dsl.select().from(STORAGE_REFERENCE).where(STORAGE_REFERENCE.STORAGE_REFERENCE_ID.eq(id)).fetchOne();
        return row == null ? Optional.empty() : Optional.of(map(row));
    }

    public Optional<StorageReference> findByContentHash(String hash) {
        var row = dsl.select().from(STORAGE_REFERENCE).where(STORAGE_REFERENCE.CONTENT_HASH.eq(hash)).limit(1).fetchOne();
        return row == null ? Optional.empty() : Optional.of(map(row));
    }

    public boolean exists(String id) { return dsl.fetchCount(STORAGE_REFERENCE, STORAGE_REFERENCE.STORAGE_REFERENCE_ID.eq(id)) > 0; }
    public void delete(String id) { dsl.deleteFrom(STORAGE_REFERENCE).where(STORAGE_REFERENCE.STORAGE_REFERENCE_ID.eq(id)).execute(); }

    private static StorageReference map(Record r) {
        return new StorageReference(
                r.get(STORAGE_REFERENCE.STORAGE_REFERENCE_ID),
                r.get(STORAGE_REFERENCE.PROVIDER_TYPE),
                e(StorageClass.class, r.get(STORAGE_REFERENCE.STORAGE_CLASS)),
                r.get(STORAGE_REFERENCE.ROOT_PATH), r.get(STORAGE_REFERENCE.RELATIVE_PATH),
                r.get(STORAGE_REFERENCE.CHECKSUM), r.get(STORAGE_REFERENCE.CONTENT_HASH),
                r.get(STORAGE_REFERENCE.FILE_SIZE), r.get(STORAGE_REFERENCE.MIME_TYPE),
                toInst(r.get(STORAGE_REFERENCE.CREATED_AT)),
                toInst(r.get(STORAGE_REFERENCE.UPDATED_AT)));
    }
    private static Instant toInst(Object o) {
        if (o == null) return null;
        if (o instanceof OffsetDateTime odt) return odt.toInstant();
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof Instant i) return i;
        return null;
    }
    private static <E extends Enum<E>> E e(Class<E> t, String v) { try { return Enum.valueOf(t, v); } catch (Exception ex) { return null; } }
}
