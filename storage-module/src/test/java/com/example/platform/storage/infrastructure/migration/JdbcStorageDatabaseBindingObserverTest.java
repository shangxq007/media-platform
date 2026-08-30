package com.example.platform.storage.infrastructure.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.storage.app.migration.StorageDatabaseBindingExpectation;
import com.example.platform.storage.app.migration.StorageDatabaseBindingMismatchException;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.CountStatus;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DeploymentEnvironment;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class JdbcStorageDatabaseBindingObserverTest {

    private static final Instant NOW = Instant.parse("2026-08-30T00:00:00Z");

    @Test
    void derivesDeterministicIdentityAndForcesTestcontainersNoncanonical() {
        JdbcStorageDatabaseBindingObserver observer = observer(
                "media", "storage_schema", "10.20.30.40", "5432");
        StorageDatabaseBindingExpectation expectation = expectation(
                "media", "storage_schema", true);

        StorageDatabaseBinding first = observer.observe(expectation);
        StorageDatabaseBinding second = observer.observe(expectation);

        assertEquals(first.databaseIdentity(), second.databaseIdentity());
        assertEquals("postgresql:sha256:", first.databaseIdentity().substring(0, 18));
        assertFalse(first.canonical());
        assertEquals(CountStatus.UNKNOWN, first.observeCounts(7, 2, 1).status());
    }

    @Test
    void failsClosedBeforeBuildingBindingWhenDatabaseOrSchemaDiffers() {
        JdbcStorageDatabaseBindingObserver observer = observer(
                "actual_media", "actual_schema", "10.20.30.40", "5432");

        assertThrows(StorageDatabaseBindingMismatchException.class,
                () -> observer.observe(expectation("unrelated_media", "actual_schema", false)));
        assertThrows(StorageDatabaseBindingMismatchException.class,
                () -> observer.observe(expectation("actual_media", "unrelated_schema", false)));
    }

    @Test
    void exactExplicitProductionExpectationCanProduceCanonicalBinding() {
        JdbcStorageDatabaseBindingObserver observer = observer(
                "media", "storage_schema", "10.20.30.40", "5432");
        StorageDatabaseBindingExpectation expectation = new StorageDatabaseBindingExpectation(
                "binding-explicit", DatabaseKind.EXPLICIT, "deployment-prod",
                DeploymentEnvironment.PROD, "media", "storage_schema",
                "V1", "query-v1", "evidence:prod-binding", true);

        StorageDatabaseBinding binding = observer.observe(expectation);

        assertTrue(binding.canonical());
        assertEquals(CountStatus.KNOWN, binding.observeCounts(7, 2, 1).status());
    }

    private static JdbcStorageDatabaseBindingObserver observer(
            String database, String schema, String address, String port) {
        String[] values = {database, schema, address, port};
        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[] {ResultSet.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("getString") && arguments[0] instanceof Integer index) {
                        return values[index - 1];
                    }
                    if (method.getName().equals("wasNull")) {
                        return false;
                    }
                    return null;
                });
        JdbcTemplate jdbc = new JdbcTemplate() {
            @Override
            public <T> T queryForObject(String sql, RowMapper<T> rowMapper) {
                try {
                    return rowMapper.mapRow(resultSet, 0);
                } catch (SQLException exception) {
                    throw new IllegalStateException("test ResultSet failed", exception);
                }
            }
        };
        return new JdbcStorageDatabaseBindingObserver(
                jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static StorageDatabaseBindingExpectation expectation(
            String database, String schema, boolean canonicalRequested) {
        return new StorageDatabaseBindingExpectation(
                "binding-test", DatabaseKind.TESTCONTAINERS, "local-test-run",
                DeploymentEnvironment.TESTCONTAINERS, database, schema,
                "V1", "query-v1", "evidence:test-run", canonicalRequested);
    }
}
