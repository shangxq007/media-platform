package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiKeyRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private ApiKeyRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "apikeytest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table api_key ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64),"
                    + "fingerprint varchar(32) not null,"
                    + "hashed_key varchar(128) not null unique,"
                    + "principal varchar(255) not null,"
                    + "created_at timestamp not null,"
                    + "last_used_at timestamp,"
                    + "revoked_at timestamp"
                    + ")");
        }

        repository = new ApiKeyRepository(dsl);
    }

    @Test
    void saveAndFindByHashedKey() {
        ApiKeyRecord record = new ApiKeyRecord("ak_1", "tenant-1", "abc12345", "hash1", "service-a",
                Instant.now(), null, null);
        repository.save(record);

        Optional<ApiKeyRecord> found = repository.findByHashedKey("hash1");
        assertTrue(found.isPresent());
        assertEquals("service-a", found.get().principal());
        assertEquals("abc12345", found.get().fingerprint());
        assertEquals("tenant-1", found.get().tenantId());
        assertFalse(found.get().isRevoked());
    }

    @Test
    void findByHashedKeyReturnsEmptyForUnknown() {
        Optional<ApiKeyRecord> found = repository.findByHashedKey("nonexistent_hash");
        assertTrue(found.isEmpty());
    }

    @Test
    void findByFingerprintReturnsCorrectRecord() {
        repository.save(new ApiKeyRecord("ak_1", "tenant-1", "fp_alpha", "hash_alpha", "svc-a", Instant.now(), null, null));
        repository.save(new ApiKeyRecord("ak_2", "tenant-2", "fp_beta", "hash_beta", "svc-b", Instant.now(), null, null));

        Optional<ApiKeyRecord> found = repository.findByFingerprint("fp_alpha");
        assertTrue(found.isPresent());
        assertEquals("svc-a", found.get().principal());
    }

    @Test
    void findAllReturnsAllRecords() {
        repository.save(new ApiKeyRecord("ak_1", "tenant-1", "fp_1", "hash_1", "svc-1", Instant.now(), null, null));
        repository.save(new ApiKeyRecord("ak_2", "tenant-2", "fp_2", "hash_2", "svc-2", Instant.now(), null, null));

        List<ApiKeyRecord> all = repository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void updateLastUsedAtPersists() {
        repository.save(new ApiKeyRecord("ak_1", "tenant-1", "fp_1", "hash_1", "svc-1", Instant.now(), null, null));

        OffsetDateTime now = OffsetDateTime.now();
        repository.updateLastUsedAt("hash_1", now);

        Optional<ApiKeyRecord> found = repository.findByHashedKey("hash_1");
        assertTrue(found.isPresent());
        assertNotNull(found.get().lastUsedAt());
    }

    @Test
    void updateRevokedAtMarksAsRevoked() {
        repository.save(new ApiKeyRecord("ak_1", "tenant-1", "fp_1", "hash_1", "svc-1", Instant.now(), null, null));

        OffsetDateTime now = OffsetDateTime.now();
        repository.updateRevokedAt("hash_1", now);

        Optional<ApiKeyRecord> found = repository.findByHashedKey("hash_1");
        assertTrue(found.isPresent());
        assertTrue(found.get().isRevoked());
        assertNotNull(found.get().revokedAt());
    }
}
