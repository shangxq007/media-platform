package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IdentityAccessServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private IdentityProperties properties;
    private IdentityAccessService service;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "idacctest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        DSLContext dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

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

        ApiKeyRepository apiKeyRepository = new ApiKeyRepository(dsl);

        properties = new IdentityProperties();
        properties.setApiKeyAuthEnabled(true);
        LinkedHashMap<String, String> keys = new LinkedHashMap<>();
        keys.put("test-key-alpha", "principal-a");
        keys.put("test-key-beta", "principal-b");
        properties.setApiKeys(keys);
        service = new IdentityAccessService(properties, apiKeyRepository);
    }

    @Test
    void hashApiKeyProducesConsistentHash() {
        String hash1 = service.hashApiKey("test-key-alpha");
        String hash2 = service.hashApiKey("test-key-alpha");
        assertNotNull(hash1);
        assertEquals(hash1, hash2);
    }

    @Test
    void hashApiKeyProducesDifferentHashesForDifferentKeys() {
        String hash1 = service.hashApiKey("test-key-alpha");
        String hash2 = service.hashApiKey("test-key-beta");
        assertNotNull(hash1);
        assertNotNull(hash2);
        assertFalse(hash1.equals(hash2));
    }

    @Test
    void hashApiKeyReturnsNullForNullInput() {
        assertNull(service.hashApiKey(null));
    }

    @Test
    void hashApiKeyProduces64CharHex() {
        String hash = service.hashApiKey("any-key");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void fingerprintReturnsFirst8CharsOfHash() {
        String hash = service.hashApiKey("test-key-alpha");
        String fp = service.fingerprint("test-key-alpha");
        assertNotNull(fp);
        assertEquals(8, fp.length());
        assertEquals(hash.substring(0, 8), fp);
    }

    @Test
    void fingerprintReturnsNullForNullInput() {
        assertNull(service.fingerprint(null));
    }

    @Test
    void validateApiKeyAcceptsValidKey() {
        assertTrue(service.validateApiKey("test-key-alpha"));
        assertTrue(service.validateApiKey("test-key-beta"));
    }

    @Test
    void validateApiKeyRejectsInvalidKey() {
        assertFalse(service.validateApiKey("nonexistent-key"));
    }

    @Test
    void validateApiKeyRejectsNullKey() {
        assertFalse(service.validateApiKey(null));
    }

    @Test
    void validateApiKeyRejectsRevokedKey() {
        assertTrue(service.validateApiKey("test-key-alpha"));
        service.revoke("test-key-alpha");
        assertFalse(service.validateApiKey("test-key-alpha"));
    }

    @Test
    void principalOfReturnsCorrectPrincipal() {
        assertEquals("principal-a", service.principalOf("test-key-alpha"));
        assertEquals("principal-b", service.principalOf("test-key-beta"));
    }

    @Test
    void principalOfReturnsNullForInvalidKey() {
        assertNull(service.principalOf("nonexistent-key"));
    }

    @Test
    void principalOfReturnsNullForRevokedKey() {
        service.revoke("test-key-alpha");
        assertNull(service.principalOf("test-key-alpha"));
    }

    @Test
    void revokeMarksKeyAsRevoked() {
        boolean result = service.revoke("test-key-alpha");
        assertTrue(result);
        assertFalse(service.validateApiKey("test-key-alpha"));
    }

    @Test
    void revokeReturnsFalseForNonexistentKey() {
        assertFalse(service.revoke("nonexistent-key"));
    }

    @Test
    void revokeReturnsFalseForNullKey() {
        assertFalse(service.revoke(null));
    }

    @Test
    void revokeReturnsFalseForAlreadyRevokedKey() {
        assertTrue(service.revoke("test-key-alpha"));
        assertFalse(service.revoke("test-key-alpha"));
    }

    @Test
    void recordUsageUpdatesLastUsedAt() {
        ApiKeyRecord recordBefore = service.findRecordByFingerprint(
                service.fingerprint("test-key-alpha"));
        assertNotNull(recordBefore);
        assertNull(recordBefore.lastUsedAt());

        service.recordUsage("test-key-alpha");

        ApiKeyRecord recordAfter = service.findRecordByFingerprint(
                service.fingerprint("test-key-alpha"));
        assertNotNull(recordAfter);
        assertNotNull(recordAfter.lastUsedAt());
    }

    @Test
    void recordUsageDoesNothingForInvalidKey() {
        service.recordUsage("nonexistent-key");
    }

    @Test
    void recordUsageDoesNothingForNullKey() {
        service.recordUsage(null);
    }

    @Test
    void overviewReturnsCorrectCounts() {
        Map<String, Object> overview = service.overview();
        assertEquals("identity-access-module", overview.get("module"));
        assertEquals("active", overview.get("status"));
        assertEquals(2, overview.get("apiKeyCount"));
        assertEquals(2L, overview.get("activeKeyCount"));
        assertEquals(0L, overview.get("revokedKeyCount"));
    }

    @Test
    void overviewReflectsRevokedCount() {
        service.revoke("test-key-alpha");
        Map<String, Object> overview = service.overview();
        assertEquals(2, overview.get("apiKeyCount"));
        assertEquals(1L, overview.get("activeKeyCount"));
        assertEquals(1L, overview.get("revokedKeyCount"));
    }

    @Test
    void serviceAccountsExcludesRevoked() {
        List<Map<String, String>> accounts = service.serviceAccounts();
        assertEquals(2, accounts.size());
        service.revoke("test-key-alpha");
        accounts = service.serviceAccounts();
        assertEquals(1, accounts.size());
        assertEquals("principal-b", accounts.get(0).get("principal"));
    }

    @Test
    void listRecordsReturnsAllRecords() {
        List<ApiKeyRecord> records = service.listRecords();
        assertEquals(2, records.size());
    }

    @Test
    void findRecordByFingerprintReturnsCorrectRecord() {
        String fp = service.fingerprint("test-key-alpha");
        ApiKeyRecord record = service.findRecordByFingerprint(fp);
        assertNotNull(record);
        assertEquals("principal-a", record.principal());
        assertEquals(fp, record.fingerprint());
    }

    @Test
    void findRecordByFingerprintReturnsNullForUnknown() {
        assertNull(service.findRecordByFingerprint("00000000"));
    }

    @Test
    void apiKeyRecordIsRevoked() {
        ApiKeyRecord record = new ApiKeyRecord(
                "ak_123456789012", "tenant-test", "abc12345", "hash", "principal-x",
                Instant.now(), null, null);
        assertFalse(record.isRevoked());

        ApiKeyRecord revoked = record.withRevokedAt(Instant.now());
        assertTrue(revoked.isRevoked());
    }

    @Test
    void apiKeyRecordWithLastUsedAt() {
        ApiKeyRecord record = new ApiKeyRecord(
                "ak_123456789012", "tenant-test", "abc12345", "hash", "principal-x",
                Instant.now(), null, null);
        assertNull(record.lastUsedAt());

        Instant now = Instant.now();
        ApiKeyRecord updated = record.withLastUsedAt(now);
        assertEquals(now, updated.lastUsedAt());
    }
}
