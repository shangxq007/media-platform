package com.example.platform.storage.infrastructure.migration;

import com.example.platform.storage.app.migration.StorageDatabaseBindingExpectation;
import com.example.platform.storage.app.migration.StorageDatabaseBindingMismatchException;
import com.example.platform.storage.app.migration.StorageDatabaseBindingObserver;
import com.example.platform.storage.domain.migration.StableStorageMigrationFingerprint;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding;
import com.example.platform.storage.domain.migration.StorageDatabaseBinding.DatabaseKind;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL observer that derives database identity only from the live JDBC connection. */
@Component
public final class JdbcStorageDatabaseBindingObserver implements StorageDatabaseBindingObserver {

    private static final String IDENTITY_VERSION = "storage-postgresql-binding-v1";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcStorageDatabaseBindingObserver(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public StorageDatabaseBinding observe(StorageDatabaseBindingExpectation expectation) {
        Objects.requireNonNull(expectation, "expectation");
        ObservedPostgresIdentity observed = jdbc.queryForObject("""
                select current_database(), current_schema(),
                       coalesce(inet_server_addr()::text, 'local-socket'),
                       coalesce(inet_server_port()::text, 'local-socket')
                """, (rs, rowNumber) -> new ObservedPostgresIdentity(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
        if (observed == null) {
            throw new IllegalStateException("PostgreSQL identity observation returned no row");
        }
        requireExactMatch("database", expectation.expectedDatabaseName(), observed.databaseName());
        requireExactMatch("schema", expectation.expectedSchema(), observed.schemaName());

        String fingerprint = StableStorageMigrationFingerprint.sha256(List.of(
                IDENTITY_VERSION,
                observed.databaseName(),
                observed.schemaName(),
                observed.serverAddress(),
                observed.serverPort()));
        boolean canonical = expectation.databaseKind() == DatabaseKind.EXPLICIT
                && expectation.canonicalRequested();
        return new StorageDatabaseBinding(
                expectation.bindingId(),
                expectation.databaseKind(),
                "postgresql:sha256:" + fingerprint,
                expectation.deploymentIdentity(),
                expectation.environmentIdentity(),
                observed.schemaName(),
                expectation.schemaVersion(),
                expectation.queryEvidenceVersion(),
                expectation.bindingEvidenceRef(),
                canonical,
                clock.instant());
    }

    private static void requireExactMatch(String fact, String expected, String observed) {
        if (!expected.equals(observed)) {
            throw new StorageDatabaseBindingMismatchException(
                    "connected PostgreSQL " + fact + " does not match the declared expectation");
        }
    }

    private record ObservedPostgresIdentity(
            String databaseName,
            String schemaName,
            String serverAddress,
            String serverPort) {}
}
