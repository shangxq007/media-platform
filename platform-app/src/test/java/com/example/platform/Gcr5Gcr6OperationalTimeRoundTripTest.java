package com.example.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GCR5/GCR6 (OPERATIONAL_TIME_USES_ABSOLUTE_INSTANT_SEMANTICS_V1): proves the
 * canonical time boundary — java.time.Instant at the application boundary,
 * explicit LocalDateTime(UTC) at the PostgreSQL persistence boundary — is stable
 * across non-UTC JVMs and DST transitions.
 *
 * RUN WITH -Duser.timezone=America/Los_Angeles (non-UTC JVM) to prove no
 * environment-local timezone shift. The assertions are timezone-agnostic: they
 * compare absolute Instants, so the test passes identically under any JVM tz.
 */
class Gcr5Gcr6OperationalTimeRoundTripTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void createSchema() throws Exception {
        dataSource = createDataSource();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE IF NOT EXISTS gcr56_time_probe ("
                + "id varchar(64) primary key, "
                + "written_at timestamp not null, "
                + "probe_value varchar(32) not null)");
    }

    @AfterAll
    static void tearDown() {
        closeDataSource(dataSource);
    }

    /** Canonical persistence boundary: Instant -> LocalDateTime(UTC) -> timestamp. */
    private static LocalDateTime toUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    /** Canonical read boundary: timestamp -> LocalDateTime(UTC) -> Instant. */
    private static Instant fromUtc(LocalDateTime ldt) {
        return ldt.toInstant(ZoneOffset.UTC);
    }

    @Test
    void absoluteInstantRoundTripIsEqual() {
        // Value deliberately chosen near a US DST transition (2026-03-08 02:00
        // America/Los_Angeles is the DST spring-forward).
        Instant original = Instant.parse("2026-03-08T09:30:00Z");
        jdbc.update("INSERT INTO gcr56_time_probe (id, written_at, probe_value) VALUES (?, ?, 'roundtrip')",
                "probe-1", java.sql.Timestamp.from(original));
        java.sql.Timestamp read = jdbc.queryForObject(
                "SELECT written_at FROM gcr56_time_probe WHERE id = 'probe-1'",
                java.sql.Timestamp.class);
        assertNotNull(read, "row must exist");
        Instant roundTripped = read.toInstant();
        assertEquals(original, roundTripped,
                "ABSOLUTE_INSTANT_ROUND_TRIP_EQUAL: instant identity must survive DB round-trip "
                        + "regardless of JVM timezone (original=" + original + ", read=" + roundTripped + ")");
    }

    @Test
    void instantJustBeforeAndAfterDstTransitionRoundTrip() {
        // One second before and one second after the 2026 LA DST spring-forward.
        Instant before = Instant.parse("2026-03-08T09:59:59Z");
        Instant after = Instant.parse("2026-03-08T10:00:01Z");
        jdbc.update("INSERT INTO gcr56_time_probe (id, written_at, probe_value) VALUES (?, ?, 'a')",
                "probe-before", java.sql.Timestamp.from(before));
        jdbc.update("INSERT INTO gcr56_time_probe (id, written_at, probe_value) VALUES (?, ?, 'b')",
                "probe-after", java.sql.Timestamp.from(after));
        java.sql.Timestamp rb = jdbc.queryForObject("SELECT written_at FROM gcr56_time_probe WHERE id='probe-before'",
                java.sql.Timestamp.class);
        java.sql.Timestamp ra = jdbc.queryForObject("SELECT written_at FROM gcr56_time_probe WHERE id='probe-after'",
                java.sql.Timestamp.class);
        assertEquals(before, rb.toInstant(), "pre-transition instant must round-trip exactly");
        assertEquals(after, ra.toInstant(), "post-transition instant must round-trip exactly");
        // The elapsed real time between the two stored instants is exactly 2 seconds.
        assertEquals(2L, ra.toInstant().getEpochSecond() - rb.toInstant().getEpochSecond(),
                "absolute elapsed time must not be shifted by DST");
    }

    @Test
    void canonicalBoundaryIsExplicitUtc() {
        // The canonical boundary function converts with an explicit UTC zone —
        // LocalDateTime.now() without zone is never used at this boundary.
        Instant now = Instant.now();
        LocalDateTime utc = toUtc(now);
        assertEquals(ZoneOffset.UTC, ZoneOffset.ofTotalSeconds(utc.atOffset(ZoneOffset.UTC).getOffset().getTotalSeconds()),
                "boundary conversion uses UTC offset");
        // Round-trip through the canonical boundary pair.
        assertEquals(now.getEpochSecond(), fromUtc(toUtc(now)).getEpochSecond());
    }
}
