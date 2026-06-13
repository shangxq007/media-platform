package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.platform.shared.test.PostgresTestContainerSupport;

class ApiKeyRepositoryTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private ApiKeyRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("CREATE TABLE IF NOT EXISTS api_key ("
                + "id varchar(64) primary key,"
                + "tenant_id varchar(64),"
                + "fingerprint varchar(32) not null,"
                + "hashed_key varchar(128) not null unique,"
                + "principal varchar(255) not null,"
                + "created_at timestamp not null,"
                + "last_used_at timestamp,"
                + "revoked_at timestamp"
                + ")");

        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE api_key CASCADE");
        repository = new ApiKeyRepository(dsl);
    }

    @Test
    void saveAndFindByHashedKey() {
        ApiKeyRecord record = new ApiKeyRecord("ak_1", "tenant-1", "abc12345", "hash1", "service-a",
                Instant.now(), null, null);
        repository.save(record);

        Optional<ApiKeyRecord> found = repository.findByHashedKey("hash1");
        assertTrue(found.isPresent());
        assertEquals("ak_1", found.get().id());
        assertEquals("service-a", found.get().principal());
    }

    @Test
    void findByHashedKeyReturnsEmptyForUnknown() {
        Optional<ApiKeyRecord> found = repository.findByHashedKey("nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void findAllReturnsAllRecords() {
        repository.save(new ApiKeyRecord("ak_1", "t1", "fp1", "hash1", "p1", Instant.now(), null, null));
        repository.save(new ApiKeyRecord("ak_2", "t1", "fp2", "hash2", "p2", Instant.now(), null, null));

        List<ApiKeyRecord> all = repository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void findByFingerprintReturnsCorrectRecord() {
        repository.save(new ApiKeyRecord("ak_1", "t1", "fp1", "hash1", "p1", Instant.now(), null, null));
        repository.save(new ApiKeyRecord("ak_2", "t1", "fp2", "hash2", "p2", Instant.now(), null, null));

        Optional<ApiKeyRecord> found = repository.findByFingerprint("fp1");
        assertTrue(found.isPresent());
        assertEquals("ak_1", found.get().id());
    }

    @Test
    void updateLastUsedAtPersists() {
        repository.save(new ApiKeyRecord("ak_1", "t1", "fp1", "hash1", "p1", Instant.now(), null, null));

        OffsetDateTime now = OffsetDateTime.now();
        repository.updateLastUsedAt("hash1", now);

        Optional<ApiKeyRecord> found = repository.findByHashedKey("hash1");
        assertTrue(found.isPresent());
        assertNotNull(found.get().lastUsedAt());
    }

    @Test
    void updateRevokedAtMarksAsRevoked() {
        repository.save(new ApiKeyRecord("ak_1", "t1", "fp1", "hash1", "p1", Instant.now(), null, null));

        OffsetDateTime now = OffsetDateTime.now();
        repository.updateRevokedAt("hash1", now);

        Optional<ApiKeyRecord> found = repository.findByHashedKey("hash1");
        assertTrue(found.isPresent());
        assertNotNull(found.get().revokedAt());
    }
}
