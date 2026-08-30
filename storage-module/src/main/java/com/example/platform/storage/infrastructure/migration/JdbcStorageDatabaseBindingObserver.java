package com.example.platform.storage.infrastructure.migration;

import com.example.platform.storage.app.migration.StorageDatabaseBindingObserver;
import com.example.platform.storage.domain.migration.StorageDatabaseObservation;
import java.time.Clock;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** PostgreSQL observer that reports stable facts and endpoint diagnostics without trust. */
@Component
public final class JdbcStorageDatabaseBindingObserver implements StorageDatabaseBindingObserver {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public JdbcStorageDatabaseBindingObserver(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public StorageDatabaseObservation observe() {
        StorageDatabaseObservation observed = jdbc.queryForObject("""
                select d.oid::bigint, d.datname, current_schema(),
                       coalesce(inet_server_addr()::text, 'local-socket') || ':' ||
                       coalesce(inet_server_port()::text, 'local-socket')
                  from pg_database d
                 where d.datname = current_database()
                """, (rs, rowNumber) -> new StorageDatabaseObservation(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                clock.instant()));
        if (observed == null) {
            throw new IllegalStateException("PostgreSQL identity observation returned no row");
        }
        return observed;
    }
}
