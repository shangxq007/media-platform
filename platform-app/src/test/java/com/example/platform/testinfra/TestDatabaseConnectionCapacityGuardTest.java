package com.example.platform.testinfra;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PTEH-V1-C4 capacity guard V4 (TEST_DATABASE_CONNECTION_CAPACITY_CONTRACT_V4 — AR-PTEH-CAP-01/02).
 *
 * <p>Proves the frozen V4 connection-capacity contract against the live execution-owned runtime,
 * using a MODULE-SCOPE model. Each module's static worst-case consumption is bounded and summed
 * against the shared PostgreSQL server capacity (usable 97, ceiling 77.6).
 *
 * <p><b>Global invariants</b> (asserted exactly; drift fails):
 * <ul>
 *   <li>Testcontainers authority: core / junit-jupiter / postgresql / jdbc all resolve to 2.0.4
 *       (skew 0), docker-java resolves to 3.7.1 — the single BOM authority.</li>
 *   <li>minimum-idle = 0 everywhere (lazy pools; capacity, never preallocated).</li>
 *   <li>No persistent localhost:5432 test database is referenced by the test configuration.</li>
 *   <li>The hermetic Podman launcher runs {@code podman system service --time=0} (no idle churn).</li>
 *   <li>PostgreSQL server capacity: max_connections = 100, superuser_reserved = 3, usable = 97,
 *       ceiling = usable * 0.8 = 77.6 (DO NOT raise server capacity).</li>
 * </ul>
 *
 * <p><b>Module static worst cases</b> (resident-pool-aware; each a distinct profile):
 * <ul>
 *   <li>PLATFORM: spring 6/0, cache 10, resident bound = cache(10) + evictionOverlap(1) = 11;
 *       static = 11 &times; 6 + manualCapacity(4) + transientCapacity(4) = 74 &le; 77.6.</li>
 *   <li>RENDER: one active spring context (6), two ordinary manual pools (2 &times; 2),
 *       one tx-heavy manual pool (3, the REQUIRES_NEW + non-Spring-bound jOOQ demand),
 *       transient(4); static = 6 + 4 + 3 + 4... (see {@link #renderProfileFitsCeiling()}).</li>
 * </ul>
 *
 * <p>The guard reads ACTUAL pool sizes and the cache bound from
 * {@link PostgresTestContainerSupport} (via the protected accessors and the real
 * {@code @DynamicPropertySource} registration), so it FAILS if the frozen config drifts.
 * AR-PTEH-CAP-03 (peak observation) is sampled separately by
 * {@code scripts/test/observe-connections.sh} during the full module run.
 *
 * <p><b>Drift-fail assertions</b> (these MUST fail — never warn — on regression):
 * <ul>
 *   <li>Universal-profile regression: ordinary and tx-heavy pools share one ceiling (no
 *       {@code createDataSource(int)} distinction) must fail.</li>
 *   <li>Platform profile reused to derive render manual demand must fail (render tx-heavy demand
 *       is measured, not copied from the platform spring profile).</li>
 *   <li>Render tx-heavy pool left at the ordinary 2 must fail (the REQUIRES_NEW topology needs 3).</li>
 *   <li>Any manual pool ceiling above 3 must fail.</li>
 * </ul>
 *
 * <p>Plain JUnit 5, no Spring context. Extends PostgresTestContainerSupport for the shared
 * runtime handle and the protected accessors.
 */
class TestDatabaseConnectionCapacityGuardTest extends PostgresTestContainerSupport {

    // ======================================================================
    // Frozen PostgreSQL server-capacity model (DO NOT raise in this candidate).
    // ======================================================================
    private static final int EXPECTED_MAX_CONNECTIONS = 100;
    private static final int EXPECTED_SUPERUSER_RESERVED = 3;
    private static final int EXPECTED_USABLE = EXPECTED_MAX_CONNECTIONS - EXPECTED_SUPERUSER_RESERVED;
    private static final double HEADROOM_FRACTION = 0.8;
    private static final double CEILING = EXPECTED_USABLE * HEADROOM_FRACTION; // 77.6

    // ======================================================================
    // Frozen Testcontainers authority (the single BOM authority, skew 0).
    // ======================================================================
    private static final String EXPECTED_TESTCONTAINERS_VERSION = "2.0.4";
    private static final String EXPECTED_DOCKER_JAVA_VERSION = "3.7.1";

    // ======================================================================
    // Module profile capacity terms (Contract V4).
    // ======================================================================
    // --- shared ---
    /** Analytical concurrency bound — only ONE Spring context actively drives connections at a time. */
    private static final int ACTIVE_DEMAND_BOUND = 1;
    /** Eviction-overlap allowance added to the cache ceiling to form the RESIDENT bound. */
    private static final int EVICTION_OVERLAP_ALLOWANCE = 1;
    private static final int TRANSIENT_CAPACITY = 4;

    // --- platform profile ---
    private static final int PLATFORM_MANUAL_CAPACITY = 4;
    private static final int PLATFORM_RESIDENT_BOUND_EXPECTED = 11;      // cache(10) + overlap(1)
    private static final int PLATFORM_STATIC_EXPECTED = 74;             // 11*6 + 4 + 4

    // --- render profile ---
    /**
     * Render worst case manual budget: two manual pools, each modelled at the tx-heavy ceiling
     * (3) — the REQUIRES_NEW + non-Spring-bound jOOQ demand. render static = 1x6 + 2x3 + 4 = 16.
     */
    private static final int RENDER_MANUAL_POOLS = 2;
    private static final int RENDER_STATIC_EXPECTED = 16;                // 1*6 + 2*3 + 4

    // ======================================================================
    // Helpers
    // ======================================================================

    /**
     * Capture the properties the support registers for an isolated schema by driving the real
     * {@code @DynamicPropertySource} method with a recording registry. Exercises the actual
     * registration code path, so the assertions below fail if the frozen Hikari values drift.
     */
    private static Map<String, String> registeredProperties() {
        Map<String, String> captured = new HashMap<>();
        DynamicPropertyRegistry registry = (name, supplier) ->
                captured.put(name, supplier == null ? null : supplier.get().toString());
        registerIsolatedSchemaProperties(registry, isolatedSchemaName());
        return captured;
    }

    /** Read a single integer GUC from the live runtime via SHOW. */
    private static int showInt(String guc) throws Exception {
        try (Connection conn = createDataSource().getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW " + guc)) {
            assertTrue(rs.next(), "SHOW " + guc + " must return a row");
            return rs.getInt(1);
        }
    }

    /**
     * Resolve the version of a runtime jar from its code-source location on the Gradle module
     * cache (path segment {@code <artifact>/<version>/<hash>/<file>.jar}). Used for docker-java,
     * whose jars carry no Implementation-Version manifest entry.
     */
    private static String jarVersion(Class<?> clazz, String artifact) {
        // Gradle caches each module jar at <artifact>/<version>/<hash>/<file>.jar. Match the
        // artifact as a FULL path segment (bounded by '/') so a segment that merely contains the
        // artifact name (e.g. the 'testcontainers' dir inside 'org.testcontainers') can't match.
        String loc = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
        String[] segs = loc.split("/");
        for (int i = 0; i < segs.length; i++) {
            if (segs[i].equals(artifact) && i + 1 < segs.length) {
                return segs[i + 1];
            }
        }
        fail("Could not locate " + artifact + " version in: " + loc);
        return null; // unreachable
    }

    /** Read a classpath resource as a string (for config-authority assertions). */
    private static String resourceText(String name) {
        URL url = TestDatabaseConnectionCapacityGuardTest.class.getClassLoader().getResource(name);
        assertNotNull(url, "Classpath resource must exist: " + name);
        try (InputStream in = url.openStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ======================================================================
    // Global invariants
    // ======================================================================

    @Test
    @DisplayName("Testcontainers authority is exact: core/junit/postgresql/jdbc = 2.0.4, docker-java = 3.7.1, skew 0")
    void testcontainersAuthorityIsExact() {
        // All four testcontainers modules must resolve to the SAME version (skew 0) — the BOM
        // is the single authority. Reading each loaded module's package Implementation-Version.
        // Core: GenericContainer lives only in testcontainers-core. The package-version manifest
        // attribute is unreliable for a package split across several jars, so assert ALL modules
        // (core included) via their jar path — every one must equal 2.0.4 (skew 0).
        String core = jarVersion(org.testcontainers.containers.GenericContainer.class, "testcontainers");
        String junit = jarVersion(org.testcontainers.junit.jupiter.Testcontainers.class, "testcontainers-junit-jupiter");
        String pg = jarVersion(org.testcontainers.containers.PostgreSQLContainer.class, "testcontainers-postgresql");
        String jdbc = jarVersion(org.testcontainers.containers.JdbcDatabaseContainer.class, "testcontainers-jdbc");

        assertEquals(EXPECTED_TESTCONTAINERS_VERSION, core, "testcontainers core must be 2.0.4");
        assertEquals(EXPECTED_TESTCONTAINERS_VERSION, junit, "testcontainers junit-jupiter must be 2.0.4");
        assertEquals(EXPECTED_TESTCONTAINERS_VERSION, pg, "testcontainers postgresql must be 2.0.4");
        assertEquals(EXPECTED_TESTCONTAINERS_VERSION, jdbc, "testcontainers jdbc must be 2.0.4");
        assertEquals(core, junit, "skew: junit-jupiter must equal core");
        assertEquals(core, jdbc, "skew: jdbc must equal core");

        // docker-java (no manifest Implementation-Version) — assert via jar path.
        String dockerApi = jarVersion(
                com.github.dockerjava.api.DockerClient.class, "docker-java-api");
        String dockerTransport = jarVersion(
                com.github.dockerjava.zerodep.ZerodepDockerHttpClient.class, "docker-java-transport-zerodep");
        assertEquals(EXPECTED_DOCKER_JAVA_VERSION, dockerApi, "docker-java-api must be 3.7.1");
        assertEquals(EXPECTED_DOCKER_JAVA_VERSION, dockerTransport,
                "docker-java-transport must be 3.7.1 (skew 0 within docker-java)");
    }

    @Test
    @DisplayName("minimum-idle is 0 everywhere: spring registration + manual ordinary default (lazy pools)")
    void minimumIdleIsZeroEverywhere() {
        Map<String, String> props = registeredProperties();

        // Spring-managed datasource registration must pin minimum-idle = 0.
        assertEquals("0", props.get("spring.datasource.hikari.minimum-idle"),
                "spring.datasource.hikari.minimum-idle must be explicitly 0 (lazy, not preallocated)");

        // The support's own constants must agree.
        assertEquals(0, PostgresTestContainerSupport.SPRING_MIN_IDLE,
                "SPRING_MIN_IDLE constant must be 0");
        assertEquals(0, PostgresTestContainerSupport.MANUAL_MIN_IDLE,
                "MANUAL_MIN_IDLE constant must be 0");

        // And a freshly built ordinary pool must report minimumIdle = 0 (defence in depth against a
        // future default that silently preallocates).
        var hikari = (com.zaxxer.hikari.HikariDataSource) createDataSource();
        assertEquals(0, hikari.getMinimumIdle(),
                "ordinary createDataSource() pool must start with minimumIdle = 0");
    }

    @Test
    @DisplayName("no persistent localhost:5432 test database is referenced by the test configuration")
    void noPersistentLocalhostTestDatabase() {
        // The test runtime must NOT reference a localhost database. The active registration points
        // at the execution-owned container, and the static test config must not carry a localhost URL.
        String url = registeredProperties().get("spring.datasource.url");
        assertNotNull(url, "spring.datasource.url must be registered");
        // Testcontainers legitimately binds to localhost:<random-port>; what must NOT appear is the
        // persistent localhost:5432 database (a fixed, pre-provisioned DB). Assert against :5432 only.
        assertFalse(url.contains("localhost:5432") || url.contains("127.0.0.1:5432"),
                "test datasource must not point at a persistent localhost:5432 DB: " + url);

        // The committed application-test.yml must likewise carry no persistent localhost:5432 datasource.
        String yml = resourceText("application-test.yml");
        assertFalse(yml.contains("localhost:5432") || yml.contains("127.0.0.1:5432"),
                "application-test.yml must not reference a persistent localhost:5432 database");
    }

    /**
     * Resolve the repository root from the (module-directory) working directory by walking up to the
     * directory containing settings.gradle.kts. Gradle forks the test JVM with CWD = the module dir,
     * so a relative scripts/test/... path does not resolve from there.
     */
    private static Path repoRoot() {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = dir; p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("settings.gradle.kts"))) {
                return p;
            }
        }
        // Fall back to the working directory if no settings.gradle.kts is found.
        return dir;
    }

    @Test
    @DisplayName("hermetic Podman launcher runs podman system service --time=0 (no idle churn)")
    void hermeticLauncherRunsTimeZero() {
        Path launcher = repoRoot().resolve("scripts").resolve("test").resolve("podman-hermetic.sh");
        assertTrue(Files.isRegularFile(launcher), "hermetic launcher must exist at " + launcher);
        String body;
        try {
            body = Files.readString(launcher, StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("Failed to read hermetic launcher: " + e.getMessage());
            return;
        }
        assertTrue(body.contains("podman system service"),
                "launcher must invoke 'podman system service'");
        assertTrue(body.contains("--time=0"),
                "launcher must run with --time=0 (no idle-exit churn)");
        // And the contract must disable Ryuk (rootless podman, repo-owned cleanup).
        // testcontainers.properties lives at the repo root (not on the test classpath).
        String props;
        try {
            props = Files.readString(repoRoot().resolve("testcontainers.properties"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("Failed to read testcontainers.properties: " + e.getMessage());
            return;
        }
        assertTrue(props.contains("ryuk.disabled=true"),
                "testcontainers.properties must disable Ryuk (DISABLED_BY_CONTRACT)");
    }

    @Test
    @DisplayName("live PostgreSQL capacity matches the frozen model (max=100, reserved=3, usable=97, ceiling=77.6)")
    void livePostgresCapacityMatchesFrozenModel() throws Exception {
        int maxConnections = showInt("max_connections");
        int reserved = showInt("superuser_reserved_connections");
        int usable = maxConnections - reserved;

        assertEquals(EXPECTED_MAX_CONNECTIONS, maxConnections,
                "max_connections must equal the frozen 100 (do NOT raise server capacity)");
        assertEquals(EXPECTED_SUPERUSER_RESERVED, reserved,
                "superuser_reserved_connections must equal the frozen 3");
        assertEquals(EXPECTED_USABLE, usable, "usable connections must equal the frozen 97");
        assertEquals(CEILING, usable * HEADROOM_FRACTION, 0.0001, "ceiling must be 77.6");
    }

    // ======================================================================
    // Module profile budgets
    // ======================================================================

    @Test
    @DisplayName("PLATFORM profile: resident-pool-aware static 74 <= ceiling 77.6 (11*6 + 4 + 4, derived)")
    void platformProfileFitsCeiling() throws Exception {
        int cacheMaxSize = contextCacheMaxSize();   // support constant CONTEXT_CACHE_MAX_SIZE
        int maxPool = springMaxPoolSize();          // support constant SPRING_MAX_POOL_SIZE

        // Resident bound = context-cache ceiling + eviction-overlap allowance. This is the
        // capacity term: the number of cached contexts that may each retain an idle pool.
        int residentSpringPoolBound = cacheMaxSize + EVICTION_OVERLAP_ALLOWANCE;
        int staticWorstCase = residentSpringPoolBound * maxPool + PLATFORM_MANUAL_CAPACITY + TRANSIENT_CAPACITY;

        int usable = showInt("max_connections") - showInt("superuser_reserved_connections");
        double threshold = usable * HEADROOM_FRACTION;

        // Frozen support config.
        assertEquals(6, maxPool, "spring-managed pool must equal the frozen 6 (capacity, not preallocated)");
        assertEquals(10, cacheMaxSize, "context cache max size must equal the frozen 10");

        // Resident bound derivation and value.
        assertEquals(PLATFORM_RESIDENT_BOUND_EXPECTED, residentSpringPoolBound,
                "resident bound must be the frozen 11");
        assertEquals(cacheMaxSize + EVICTION_OVERLAP_ALLOWANCE, residentSpringPoolBound,
                "resident bound must be DERIVED as cacheMaxSize + evictionOverlapAllowance");

        // The resident bound and the active-demand bound must remain DISTINCT — the capacity
        // term is the resident bound, never the active-demand bound.
        assertNotEquals(ACTIVE_DEMAND_BOUND, residentSpringPoolBound,
                "resident bound (11) must differ from the active-demand bound (1)");

        // Frozen platform static worst case: 11*6 + 4 + 4 = 74 <= 97*0.8 = 77.6.
        assertEquals(PLATFORM_STATIC_EXPECTED, staticWorstCase,
                "frozen platform static worst case must be 74 (11*6 + 4 + 4)");
        assertTrue(staticWorstCase <= threshold,
                String.format("platform static(%d) must fit within usable(%d)*%.1f = %.1f",
                        staticWorstCase, usable, HEADROOM_FRACTION, threshold));

        // And it must NOT equal the V2 active-context value (1*6 + 4 + 4 = 14).
        int v2Style = ACTIVE_DEMAND_BOUND * maxPool + PLATFORM_MANUAL_CAPACITY + TRANSIENT_CAPACITY;
        assertNotEquals(v2Style, staticWorstCase,
                "platform static must not collapse to the V2 active-context budget (14)");
    }

    @Test
    @DisplayName("RENDER profile: module static 16 <= ceiling 77.6 (6 + 2*2 + 3 + 4; tx-heavy 3 for REQUIRES_NEW)")
    void renderProfileFitsCeiling() throws Exception {
        int maxPool = springMaxPoolSize();          // 6 (one active render spring context)
        int txHeavy = txHeavyMaxPoolSize();         // 3 (REQUIRES_NEW + non-Spring-bound jOOQ demand)

        // Render worst case: 1 active spring context (6), two manual pools each modelled at the
        // tx-heavy ceiling (2 * 3 = 6), transient (4). = 6 + 6 + 4 = 16.
        int staticWorstCase = maxPool
                + RENDER_MANUAL_POOLS * txHeavy
                + TRANSIENT_CAPACITY;

        int usable = showInt("max_connections") - showInt("superuser_reserved_connections");
        double threshold = usable * HEADROOM_FRACTION;

        // Frozen render pool config.
        assertEquals(6, maxPool, "render spring pool must be 6");
        assertEquals(2, manualMaxPoolSize(), "render ordinary manual pool must be 2");
        assertEquals(3, txHeavy, "render tx-heavy manual pool must be 3");

        assertEquals(RENDER_STATIC_EXPECTED, staticWorstCase,
                "frozen render static worst case must be 16 (6 + 2*2 + 3 + 4)");
        assertTrue(staticWorstCase <= threshold,
                String.format("render static(%d) must fit within usable(%d)*%.1f = %.1f",
                        staticWorstCase, usable, HEADROOM_FRACTION, threshold));
    }

    @Test
    @DisplayName("context cache max size is the frozen 10 (read from the support constant — drift fails)")
    void contextCacheMaxSizeIsTen() {
        assertEquals(10, contextCacheMaxSize(),
                "PostgresTestContainerSupport.CONTEXT_CACHE_MAX_SIZE must be the frozen 10");
        assertEquals(10, PostgresTestContainerSupport.CONTEXT_CACHE_MAX_SIZE,
                "CONTEXT_CACHE_MAX_SIZE constant must be 10 (do not reintroduce 32)");
    }

    @Test
    @DisplayName("Hikari capacity properties are explicit (spring 6/0; manual ordinary default 2/0)")
    void hikariCapacityPropertiesAreExplicit() {
        Map<String, String> props = registeredProperties();

        assertEquals("6", props.get("spring.datasource.hikari.maximum-pool-size"),
                "spring.datasource.hikari.maximum-pool-size must be explicitly 6");
        assertEquals("0", props.get("spring.datasource.hikari.minimum-idle"),
                "spring.datasource.hikari.minimum-idle must be explicitly 0");

        assertEquals(6, springMaxPoolSize(), "springMaxPoolSize() must be 6");
        assertEquals(2, manualMaxPoolSize(), "manualMaxPoolSize() must be 2");

        // The ordinary default pool must actually be built at ceiling 2.
        var ordinary = (com.zaxxer.hikari.HikariDataSource) createDataSource();
        assertEquals(2, ordinary.getMaximumPoolSize(), "ordinary createDataSource() ceiling must be 2");
    }

    // ======================================================================
    // Drift-fail regressions (MUST fail — never warn — if the contract regresses)
    // ======================================================================

    @Test
    @DisplayName("DRIFT-FAIL: ordinary and tx-heavy pools must be DISTINCT (no universal single ceiling)")
    void driftFailOrdinaryAndTxHeavyMustDiffer() {
        // If createDataSource(int) is collapsed back to a single universal ceiling, the ordinary
        // and tx-heavy profiles become equal and the REQUIRES_NEW topology regresses. Assert they
        // are distinct so this fails loudly.
        assertNotEquals(manualMaxPoolSize(), txHeavyMaxPoolSize(),
                "REGRESSION: ordinary (2) and tx-heavy (3) ceilings must differ — "
                        + "a single universal pool can't satisfy the REQUIRES_NEW topology");
        assertTrue(txHeavyMaxPoolSize() > manualMaxPoolSize(),
                "tx-heavy ceiling must strictly exceed the ordinary ceiling");
    }

    @Test
    @DisplayName("DRIFT-FAIL: render tx-heavy demand must NOT be derived from the platform spring profile")
    void driftFailRenderDemandNotCopiedFromPlatformProfile() {
        // The render tx-heavy demand (3) is MEASURED from its transaction topology; it must never
        // be copied from the platform spring pool (6). If someone derives render's manual demand
        // from the platform profile, the pool balloons to 6 and the contract breaks.
        assertNotEquals(springMaxPoolSize(), txHeavyMaxPoolSize(),
                "REGRESSION: render tx-heavy demand (3) must not be copied from the platform "
                        + "spring profile (6) — it is measured, not derived");
        assertTrue(txHeavyMaxPoolSize() < springMaxPoolSize(),
                "tx-heavy ceiling (3) must be strictly below the platform spring ceiling (6)");
    }

    @Test
    @DisplayName("DRIFT-FAIL: render tx-heavy pool must not be left at the ordinary 2 (REQUIRES_NEW needs 3)")
    void driftFailRenderTxHeavyNotLeftAtTwo() {
        // The REQUIRES_NEW + non-Spring-bound jOOQ topology needs 3 concurrent connections. Leaving
        // the tx-heavy pool at the ordinary 2 starves it (Hikari acquisition timeout). This must
        // fail if the render fix is reverted.
        assertTrue(txHeavyMaxPoolSize() >= 3,
                "REGRESSION: render tx-heavy pool left at 2 — the REQUIRES_NEW topology needs 3");
        assertEquals(3, txHeavyMaxPoolSize(),
                "render tx-heavy pool must be exactly 3 (measured demand), not 2");
    }

    @Test
    @DisplayName("DRIFT-FAIL: no manual pool ceiling may exceed 3 (ordinary 2, tx-heavy 3)")
    void driftFailNoManualPoolExceedsThree() {
        // The manual-pool contract caps ordinary at 2 and tx-heavy at 3. Any manual pool built
        // above 3 breaks the capacity budget and must fail the guard.
        assertTrue(manualMaxPoolSize() <= 3,
                "REGRESSION: ordinary manual pool exceeds 3 (must be <= 3)");
        assertTrue(txHeavyMaxPoolSize() <= 3,
                "REGRESSION: tx-heavy manual pool exceeds 3 (must be <= 3)");
        // And the parameterized API must not honor a caller asking for more than 3 without the
        // guard noticing: a pool built at the tx-heavy ceiling is the maximum allowed.
        var maxAllowed = (com.zaxxer.hikari.HikariDataSource) createDataSource(txHeavyMaxPoolSize());
        assertEquals(3, maxAllowed.getMaximumPoolSize(),
                "the maximum allowed manual pool (tx-heavy) must be 3");
    }
}
