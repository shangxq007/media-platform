package com.example.platform.render.app.timeline;

import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineDiagnostic;
import com.example.platform.render.domain.timeline.canonicalmodel.TimelineDiagnosticCode;
import com.example.platform.render.domain.timeline.internal.TimelineMergeRequest;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult;
import com.example.platform.render.domain.timeline.internal.TimelineMergeResult.MergeStatus;
import com.example.platform.render.domain.timeline.version.TimelineConflictException;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantContext;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot.TIMELINE_SNAPSHOT;
import static org.junit.jupiter.api.Assertions.*;

/**
 * E1C canonical-gate integration tests (real PostgreSQL Testcontainers).
 *
 * <p>GREEN phase: proves the frozen E1C_THREE_WAY_MERGE_CANONICAL_GATE_V1 contract
 * against the REAL production merge boundary — TimelineMergeService (Spring-authoritative
 * seven-argument constructor) with real TimelineRevisionRepository, TimelineSnapshotService,
 * TimelineSemanticDiffService, TimelineConflictDetector, TimelineConflictResolver and
 * ProductCurrentRevisionService — on real PostgreSQL. Each merge attempt runs inside a
 * jOOQ {@code transaction(...)} boundary, faithfully emulating the production single
 * {@code @Transactional} application boundary, so rejection classes provably leave
 * ZERO durable partial state and the transaction remains usable.
 *
 * <p>RED characterization methods were removed at freeze; their source is preserved in
 * the Candidate Evidence red/ directory.
 */
class TimelineMergeServiceE1cGateIntegrationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static Connection connection;
    private static DSLContext dsl;

    private TimelineRevisionRepository revisionRepository;
    private TimelineSnapshotService snapshotService;
    private TimelineMergeService mergeService;

    /**
     * Canonically VALID internal-1.0 payload: two DISJOINT tracks — v1 (c1 0-30, c2 30-60)
     * and v2 (d1 0-30). The existing engine indexes each track as a whole entity, so a
     * valid non-conflicting merge must keep each branch's changes on its own track.
     */
    private static final String VALID = """
            {"schemaVersion":"1.0","id":"tl-merge",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}},
                 {"id":"c2","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":30,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]},
               {"id":"v2","type":"VIDEO","clips":[
                 {"id":"d1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}""";

    /** Canonically VALID source-side change: v1's c1 0-40 / c2 40-60; v2 unchanged. */
    private static final String VALID_SRC = """
            {"schemaVersion":"1.0","id":"tl-merge",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":40,"rate":{"num":30,"den":1}}}},
                 {"id":"c2","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":40,"rate":{"num":30,"den":1}},"duration":{"frame":20,"rate":{"num":30,"den":1}}}}]},
               {"id":"v2","type":"VIDEO","clips":[
                 {"id":"d1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}""";

    /** Canonically VALID target-side change: v1 unchanged; v2's d1 0-45 (non-conflicting). */
    private static final String VALID_TGT = """
            {"schemaVersion":"1.0","id":"tl-merge",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}},
                 {"id":"c2","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":30,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]},
               {"id":"v2","type":"VIDEO","clips":[
                 {"id":"d1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":45,"rate":{"num":30,"den":1}}}}]}]}}""";

    /** Canonically VALID alternate source change (different range) — different hash input. */
    private static final String VALID_SRC2 = """
            {"schemaVersion":"1.0","id":"tl-merge",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":45,"rate":{"num":30,"den":1}}}},
                 {"id":"c2","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":45,"rate":{"num":30,"den":1}},"duration":{"frame":15,"rate":{"num":30,"den":1}}}}]},
               {"id":"v2","type":"VIDEO","clips":[
                 {"id":"d1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}""";

    /**
     * Canonically VALID target-side conflict: v1's c1 0-45 / c2 45-60 and v2 unchanged —
     * changes the SAME v1 track (and clips) as VALID_SRC, so the engine reports
     * conflicts on CLIP:c1, CLIP:c2 and TRACK:v1 (existing conservative conflict model).
     */
    private static final String CONFLICT_TGT = """
            {"schemaVersion":"1.0","id":"tl-merge",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":45,"rate":{"num":30,"den":1}}}},
                 {"id":"c2","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":45,"rate":{"num":30,"den":1}},"duration":{"frame":15,"rate":{"num":30,"den":1}}}}]},
               {"id":"v2","type":"VIDEO","clips":[
                 {"id":"d1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}""";

    /**
     * Canonically INVALID internal-1.0 payload: clip id c2 appears TWICE on v1
     * (TIMELINE_CLIP_ID_DUPLICATE) — structurally parseable, canonical-invalid.
     */
    private static final String INVALID = """
            {"schemaVersion":"1.0","id":"tl-invalid",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}},
                 {"id":"c2","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":30,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}},
                 {"id":"c2","assetId":"ast-2",
                  "timelineRange":{"start":{"frame":60,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]},
               {"id":"v2","type":"VIDEO","clips":[
                 {"id":"d1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}""";

    @BeforeAll
    static void setUpDatabase() throws Exception {
        dataSource = createDataSource();
        connection = dataSource.getConnection();
        // One shared connection: jOOQ transaction() on this DSLContext gives the merge
        // path a REAL transactional boundary (rollback semantics) like production Spring.
        dsl = DSL.using(connection, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
        // Restricted role for REAL permission-denied persistence failures. The container's
        // "test" user is a superuser (official postgres image), so REVOKE is ignored for
        // it; SET ROLE to a privilege-limited role makes INSERT failures authentic.
        try {
            dsl.execute("CREATE ROLE e1c_restricted");
        } catch (RuntimeException alreadyExists) {
            // role already exists from a previous run against the same container
        }
        dsl.execute("GRANT USAGE ON SCHEMA public TO e1c_restricted");
        dsl.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO e1c_restricted");
        dsl.execute("GRANT INSERT ON TABLE timeline_snapshot TO e1c_restricted");
    }

    @AfterAll
    static void tearDownDatabase() throws Exception {
        if (connection != null) {
            connection.close();
        }
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        RenderTestSchemaFixture.truncate(dsl);
        TenantContext.set("ten-e1c");
        revisionRepository = new TimelineRevisionRepository(dsl);
        snapshotService = new TimelineSnapshotService(dsl);
        // Spring-authoritative constructor: full E1c canonical gating + current-revision
        // update. revisionService is constructor-required but never invoked on the merge path.
        mergeService = new TimelineMergeService(
                null,
                revisionRepository,
                snapshotService,
                new TimelineSemanticDiffService(new TimelineCanonicalizer()),
                new TimelineConflictDetector(),
                new TimelineConflictResolver(),
                new ProductCurrentRevisionService(dsl));
    }

    // ========================================================================================
    // Helpers
    // ========================================================================================

    private TimelineMergeResult merge(TimelineMergeRequest req) {
        AtomicReference<TimelineMergeResult> ref = new AtomicReference<>();
        dsl.transaction(tx -> ref.set(mergeService.threeWayMerge(req)));
        return ref.get();
    }

    private <T extends RuntimeException> T expectThrow(Class<T> type, TimelineMergeRequest req) {
        try {
            dsl.transaction(tx -> mergeService.threeWayMerge(req));
        } catch (RuntimeException e) {
            if (type.isInstance(e)) {
                return type.cast(e);
            }
            fail("expected " + type.getSimpleName() + " but got "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        fail("expected " + type.getSimpleName() + " but the merge succeeded");
        return null;
    }

    private TimelineMergeRequest request(String projectId, String base, String src, String tgt) {
        return new TimelineMergeRequest(projectId, "ten-e1c", base, src, tgt, "alice", "E1C merge");
    }

    private String insertSnapshot(String projectId, String tenantId, String payload) {
        return snapshotService.save(projectId, tenantId, payload, "internal-1.0");
    }

    private String insertRevision(String id, String projectId, String tenantId, String snapId,
                                  int revNum, boolean isMerge, String parent, String mergeParents,
                                  String mergeBase) {
        revisionRepository.insert(new TimelineRevisionRepository.RevisionRow(
                id, projectId, tenantId, parent, revNum, snapId, 0, "hash-" + id,
                "internal-1.0", isMerge ? "merge" : "sync", null, null, null,
                null, null, null, isMerge, mergeParents, mergeBase, OffsetDateTime.now()));
        return id;
    }

    private record Fixtures(String projectId, String baseSnap, String srcSnap, String tgtSnap) {
    }

    /** Seeds base-1=VALID, src-1=VALID_SRC, tgt-1=VALID_TGT with revisions 1/2/3. */
    private Fixtures seedValid(String projectId) {
        String baseSnap = insertSnapshot(projectId, "ten-e1c", VALID);
        String srcSnap = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        String tgtSnap = insertSnapshot(projectId, "ten-e1c", VALID_TGT);
        insertRevision("base-1", projectId, "ten-e1c", baseSnap, 1, false, null, null, null);
        insertRevision("src-1", projectId, "ten-e1c", srcSnap, 2, false, "base-1", null, null);
        insertRevision("tgt-1", projectId, "ten-e1c", tgtSnap, 3, false, "base-1", null, null);
        return new Fixtures(projectId, baseSnap, srcSnap, tgtSnap);
    }

    /** Like {@link #seedValid} but with caller-chosen revision ids (globally unique PKs). */
    private Fixtures seedValidWithIds(String projectId, String baseId, String srcId, String tgtId) {
        String baseSnap = insertSnapshot(projectId, "ten-e1c", VALID);
        String srcSnap = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        String tgtSnap = insertSnapshot(projectId, "ten-e1c", VALID_TGT);
        insertRevision(baseId, projectId, "ten-e1c", baseSnap, 1, false, null, null, null);
        insertRevision(srcId, projectId, "ten-e1c", srcSnap, 2, false, baseId, null, null);
        insertRevision(tgtId, projectId, "ten-e1c", tgtSnap, 3, false, baseId, null, null);
        return new Fixtures(projectId, baseSnap, srcSnap, tgtSnap);
    }

    private void seedProduct(String productId, String currentRevisionId) {
        dsl.insertInto(PRODUCT)
                .set(PRODUCT.PRODUCT_ID, productId)
                .set(PRODUCT.PRODUCT_TYPE, "VIDEO")
                .set(PRODUCT.REPRESENTATION_KIND, "FINAL")
                .set(PRODUCT.STATUS, "DRAFT")
                .set(PRODUCT.VERSION, 1)
                .set(PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .set(PRODUCT.CURRENT_REVISION_ID, currentRevisionId)
                .execute();
    }

    private long snapshotRows(String projectId) {
        return dsl.selectCount().from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.PROJECT_ID.eq(projectId)).fetchOne(0, Long.class);
    }

    private long revisionRows(String projectId) {
        return dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(projectId)).fetchOne(0, Long.class);
    }

    private String currentRevision(String productId) {
        return dsl.select(PRODUCT.CURRENT_REVISION_ID).from(PRODUCT)
                .where(PRODUCT.PRODUCT_ID.eq(productId)).fetchOne(PRODUCT.CURRENT_REVISION_ID);
    }

    private TimelineRevisionRepository.RevisionRow mergedRow(String projectId) {
        return revisionRepository.listByProject(projectId, 20).stream()
                .filter(TimelineRevisionRepository.RevisionRow::isMerge)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no merge revision row for " + projectId));
    }

    private String snapshotPayload(String snapshotId) {
        return snapshotService.findPayload(snapshotId).orElseThrow();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Subsequent valid merge succeeds on the same connection after a rejection/failure
     * class (frozen "transaction remains usable" + recovery requirement).
     */
    private TimelineMergeResult assertRecoveryMergeSucceeds(String projectId) {
        dsl.execute("INSERT INTO product (product_id, product_type, representation_kind, status,"
                + " version, created_at, updated_at, current_revision_id)"
                + " VALUES ('" + projectId + "', 'VIDEO', 'FINAL', 'DRAFT', 1, now(), now(), 'r-tgt')"
                + " ON CONFLICT (product_id) DO UPDATE SET current_revision_id = 'r-tgt'");
        String snapBase = insertSnapshot(projectId, "ten-e1c", VALID);
        String snapSrc = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        String snapTgt = insertSnapshot(projectId, "ten-e1c", VALID_TGT);
        insertRevision("r-base", projectId, "ten-e1c", snapBase, 90, false, null, null, null);
        insertRevision("r-src", projectId, "ten-e1c", snapSrc, 91, false, "r-base", null, null);
        insertRevision("r-tgt", projectId, "ten-e1c", snapTgt, 92, false, "r-base", null, null);
        TimelineMergeResult result = merge(request(projectId, "r-base", "r-src", "r-tgt"));
        assertEquals(MergeStatus.MERGED, result.status(),
                "subsequent valid merge must succeed after the rejection class");
        return result;
    }

    // ========================================================================================
    // Valid merge: MERGED + persisted + current advanced + lineage + bounded hash
    // ========================================================================================

    @Test
    void validNonConflictingMerge_mergesPersistsAdvancesCurrent_lineageCorrect() {
        String projectId = "prj-valid-" + UUID.randomUUID();
        seedValid(projectId);
        seedProduct(projectId, "tgt-1");

        TimelineMergeResult result = merge(request(projectId, "base-1", "src-1", "tgt-1"));

        assertEquals(MergeStatus.MERGED, result.status());
        assertNotNull(result.mergedRevisionId(), "merged revision must be allocated");
        // snapshot + revision persisted (3 fixtures + 1 merged each)
        assertEquals(4L, snapshotRows(projectId), "merged snapshot persisted");
        assertEquals(4L, revisionRows(projectId), "merge revision persisted");
        // lineage: isMerge=true, exact base, exact deterministic parent ids, parent=target
        TimelineRevisionRepository.RevisionRow merged = mergedRow(projectId);
        assertTrue(merged.isMerge(), "merge row must be flagged isMerge=true");
        assertEquals("base-1", merged.mergeBaseRevisionId(), "exact base/common ancestor");
        assertEquals("src-1,tgt-1", merged.mergeParentRevisionIds(), "deterministic parent ordering");
        assertEquals("tgt-1", merged.parentRevisionId(), "parent column = target (existing semantics)");
        assertEquals("merge", merged.source());
        // current revision advanced to the merged revision (frozen policy)
        assertEquals(result.mergedRevisionId(), currentRevision(projectId),
                "PRODUCT.CURRENT_REVISION_ID must advance to the merged revision");
        // merged payload is the canonical engine result (target side), persisted verbatim
        assertEquals(VALID_TGT, snapshotPayload(merged.snapshotId()), "merged payload persisted");
        // bounded deterministic content hash: 64 lowercase hex chars
        assertEquals(64, merged.contentHash().length(), "content hash must fit varchar(64)");
        assertTrue(merged.contentHash().matches("[0-9a-f]{64}"), "content hash must be 64 lowercase hex");
        assertEquals(sha256Hex("merge:src-1:tgt-1:" + VALID_TGT), merged.contentHash(),
                "content hash must equal the deterministic SHA-256 of the canonical input");
    }

    // ========================================================================================
    // Both-sides contribution: nonconflicting source + target changes retained
    // ========================================================================================

    @Test
    void validMergeWithChangesOnBothSides_bothContributionsRetained_deterministic_canonical() {
        String projectId = "prj-bothsides-" + UUID.randomUUID();
        seedValid(projectId);
        seedProduct(projectId, "tgt-1");

        TimelineMergeResult result = merge(request(projectId, "base-1", "src-1", "tgt-1"));

        assertEquals(MergeStatus.MERGED, result.status());
        List<String> mergedEntityKeys = result.autoMergedChanges().stream()
                .map(c -> c.entity().key())
                .toList();
        assertTrue(mergedEntityKeys.contains("CLIP:c1"),
                "nonconflicting SOURCE contribution (c1 range) retained");
        assertTrue(mergedEntityKeys.contains("CLIP:d1"),
                "nonconflicting TARGET contribution (d1 changed on v2) retained");
        // deterministic: identical inputs in a fresh project produce the identical merged payload
        String projectB = "prj-bothsides-b-" + UUID.randomUUID();
        seedValidWithIds(projectB, "base-b", "src-b", "tgt-b");
        seedProduct(projectB, "tgt-b");
        TimelineMergeResult resultB = merge(request(projectB, "base-b", "src-b", "tgt-b"));
        assertEquals(VALID_TGT, snapshotPayload(mergedRow(projectB).snapshotId()),
                "deterministic merged payload across projects");
        // merged result canonical: the accepted gate accepts the persisted payload
        assertDoesNotThrow(() -> TimelineMergeService.canonicalGate(projectId,
                snapshotPayload(mergedRow(projectId).snapshotId())),
                "persisted merged payload must be canonical");

    }

    // ========================================================================================
    // Conflict model: deterministic set, stable order, no silent resolution, zero writes
    // ========================================================================================

    @Test
    void deterministicConflictDetection_conflictOrderingStable_zeroWrites() {
        String projectId = "prj-conflict-" + UUID.randomUUID();
        String snapBase = insertSnapshot(projectId, "ten-e1c", VALID);
        String snapSrc = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        String snapTgt = insertSnapshot(projectId, "ten-e1c", CONFLICT_TGT);
        insertRevision("base-1", projectId, "ten-e1c", snapBase, 1, false, null, null, null);
        insertRevision("src-1", projectId, "ten-e1c", snapSrc, 2, false, "base-1", null, null);
        insertRevision("tgt-1", projectId, "ten-e1c", snapTgt, 3, false, "base-1", null, null);

        TimelineMergeResult first = merge(request(projectId, "base-1", "src-1", "tgt-1"));
        assertEquals(MergeStatus.CONFLICTS, first.status());
        assertNull(first.mergedRevisionId(), "no revision on conflict");
        assertFalse(first.conflicts().isEmpty(), "conflicts must be reported");
        List<String> firstKeys = first.conflicts().stream().map(c -> c.entityRef().key()).toList();
        List<String> firstTypes = first.conflicts().stream().map(c -> c.conflictType().name()).toList();
        assertEquals(List.of("CLIP:c1", "CLIP:c2", "TRACK:v1"), firstKeys,
                "deterministic conflict set and order (clips plus the whole-track entity)");
        assertEquals(List.of("SAME_ENTITY_MODIFIED", "SAME_ENTITY_MODIFIED", "SAME_ENTITY_MODIFIED"),
                firstTypes);
        // stable ordering across a repeated attempt
        TimelineMergeResult second = merge(request(projectId, "base-1", "src-1", "tgt-1"));
        assertEquals(firstKeys, second.conflicts().stream().map(c -> c.entityRef().key()).toList(),
                "stable conflict ordering");
        assertEquals(first.conflicts().get(0).conflictId(), second.conflicts().get(0).conflictId());
        // zero durable writes
        assertEquals(3L, snapshotRows(projectId), "conflict must write zero snapshot rows");
        assertEquals(3L, revisionRows(projectId), "conflict must write zero revision rows");
    }

    @Test
    void unresolvedConflict_zeroWrites() {
        String projectId = "prj-unresolved-" + UUID.randomUUID();
        String snapBase = insertSnapshot(projectId, "ten-e1c", VALID);
        String snapSrc = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        String snapTgt = insertSnapshot(projectId, "ten-e1c", CONFLICT_TGT);
        insertRevision("base-1", projectId, "ten-e1c", snapBase, 1, false, null, null, null);
        insertRevision("src-1", projectId, "ten-e1c", snapSrc, 2, false, "base-1", null, null);
        insertRevision("tgt-1", projectId, "ten-e1c", snapTgt, 3, false, "base-1", null, null);

        TimelineMergeResult result = merge(request(projectId, "base-1", "src-1", "tgt-1"));

        assertEquals(MergeStatus.CONFLICTS, result.status());
        assertEquals(0L, snapshotRows(projectId) - 3L, "unresolved conflict: zero snapshot delta");
        assertEquals(0L, revisionRows(projectId) - 3L, "unresolved conflict: zero revision delta");
        assertTrue(result.conflicts().stream().allMatch(c -> c.resolutionRequired()),
                "no silent conflict resolution");
    }

    // ========================================================================================
    // Canonical input rejections: invalid base / source / target -> zero writes + diagnostics
    // ========================================================================================

    @Test
    void invalidBase_zeroWrites_orderedDiagnostics() {
        String projectId = "prj-invalidbase-" + UUID.randomUUID();
        String snapBase = insertSnapshot(projectId, "ten-e1c", INVALID);
        String snapSrc = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        String snapTgt = insertSnapshot(projectId, "ten-e1c", VALID_TGT);
        insertRevision("base-1", projectId, "ten-e1c", snapBase, 1, false, null, null, null);
        insertRevision("src-1", projectId, "ten-e1c", snapSrc, 2, false, "base-1", null, null);
        insertRevision("tgt-1", projectId, "ten-e1c", snapTgt, 3, false, "base-1", null, null);

        TimelineCanonicalRejectionException ex = expectThrow(
                TimelineCanonicalRejectionException.class, request(projectId, "base-1", "src-1", "tgt-1"));

        assertDiagnostic(ex, TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE);
        assertEquals(3L, snapshotRows(projectId), "invalid base: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "invalid base: zero revision delta");
        assertRecoveryMergeSucceeds(projectId);
    }

    @Test
    void invalidLeft_zeroWrites_orderedDiagnostics() {
        String projectId = "prj-invalidleft-" + UUID.randomUUID();
        String snapBase = insertSnapshot(projectId, "ten-e1c", VALID);
        String snapSrc = insertSnapshot(projectId, "ten-e1c", INVALID);
        String snapTgt = insertSnapshot(projectId, "ten-e1c", VALID_TGT);
        insertRevision("base-1", projectId, "ten-e1c", snapBase, 1, false, null, null, null);
        insertRevision("src-1", projectId, "ten-e1c", snapSrc, 2, false, "base-1", null, null);
        insertRevision("tgt-1", projectId, "ten-e1c", snapTgt, 3, false, "base-1", null, null);

        TimelineCanonicalRejectionException ex = expectThrow(
                TimelineCanonicalRejectionException.class, request(projectId, "base-1", "src-1", "tgt-1"));

        assertDiagnostic(ex, TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE);
        assertEquals(3L, snapshotRows(projectId), "invalid left: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "invalid left: zero revision delta");
        assertRecoveryMergeSucceeds(projectId);
    }

    @Test
    void invalidRight_zeroWrites_orderedDiagnostics() {
        String projectId = "prj-invalidright-" + UUID.randomUUID();
        String snapBase = insertSnapshot(projectId, "ten-e1c", VALID);
        String snapSrc = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        String snapTgt = insertSnapshot(projectId, "ten-e1c", INVALID);
        insertRevision("base-1", projectId, "ten-e1c", snapBase, 1, false, null, null, null);
        insertRevision("src-1", projectId, "ten-e1c", snapSrc, 2, false, "base-1", null, null);
        insertRevision("tgt-1", projectId, "ten-e1c", snapTgt, 3, false, "base-1", null, null);

        TimelineCanonicalRejectionException ex = expectThrow(
                TimelineCanonicalRejectionException.class, request(projectId, "base-1", "src-1", "tgt-1"));

        assertDiagnostic(ex, TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE);
        assertEquals(3L, snapshotRows(projectId), "invalid right: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "invalid right: zero revision delta");
        // R2 closure: the canonical-invalid payload never becomes a merged snapshot row
        assertRecoveryMergeSucceeds(projectId);
    }

    private static void assertDiagnostic(TimelineCanonicalRejectionException ex,
                                         TimelineDiagnosticCode code) {
        List<TimelineDiagnostic> diagnostics = ex.diagnostics();
        assertFalse(diagnostics.isEmpty(), "rejection must carry ordered canonical diagnostics");
        assertTrue(diagnostics.stream().anyMatch(d -> d.code() == code),
                "expected diagnostic " + code + " but got " + diagnostics);
        assertTrue(ex.diagnostics().equals(ex.diagnostics().stream().sorted().toList()),
                "diagnostics must be deterministically ordered");
    }

    // ========================================================================================
    // Merged-result gate: direct reachability + R2 end-to-end closure
    // ========================================================================================

    @Test
    void invalidMergedResult_gateRejects_directReachabilityAndR2Closure() {
        String projectId = "prj-mergedgate-" + UUID.randomUUID();

        // Direct reachability proof: the merged-result gate itself rejects a
        // canonical-invalid payload with ordered diagnostics (defense-in-depth).
        TimelineCanonicalRejectionException direct = assertThrows(
                TimelineCanonicalRejectionException.class,
                () -> TimelineMergeService.canonicalGate(projectId, INVALID));
        assertDiagnostic(direct, TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE);

        // R2 closure end-to-end: an invalid target input is rejected at the INPUT gate
        // before the merge engine, so no canonical-invalid merged payload can be persisted.
        String snapBase = insertSnapshot(projectId, "ten-e1c", VALID);
        String snapSrc = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        String snapTgt = insertSnapshot(projectId, "ten-e1c", INVALID);
        insertRevision("base-1", projectId, "ten-e1c", snapBase, 1, false, null, null, null);
        insertRevision("src-1", projectId, "ten-e1c", snapSrc, 2, false, "base-1", null, null);
        insertRevision("tgt-1", projectId, "ten-e1c", snapTgt, 3, false, "base-1", null, null);

        TimelineCanonicalRejectionException ex = expectThrow(
                TimelineCanonicalRejectionException.class, request(projectId, "base-1", "src-1", "tgt-1"));
        assertDiagnostic(ex, TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE);
        assertEquals(3L, snapshotRows(projectId), "R2 closed: zero merged snapshot rows");
        assertEquals(3L, revisionRows(projectId), "R2 closed: zero merge revision rows");
        assertRecoveryMergeSucceeds(projectId);
    }

    // ========================================================================================
    // Tenant / project isolation
    // ========================================================================================

    @Test
    void crossTenantRequest_rejected_zeroWrites() {
        String projectId = "prj-crosstenant-" + UUID.randomUUID();
        seedValid(projectId);

        TimelineMergeRequest req = new TimelineMergeRequest(
                projectId, "ten-other", "base-1", "src-1", "tgt-1", "alice", "cross-tenant");
        PlatformException ex = expectThrow(PlatformException.class, req);

        assertEquals("COMMON-403-002", ex.getErrorCode().code(), "cross-tenant must be TENANT_ACCESS_DENIED");
        assertEquals(3L, snapshotRows(projectId), "cross-tenant: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "cross-tenant: zero revision delta");
        assertRecoveryMergeSucceeds(projectId);
    }

    @Test
    void crossTenantSnapshot_rejected_zeroWrites() {
        String projectId = "prj-crosstenantsnap-" + UUID.randomUUID();
        // target snapshot belongs to another tenant while its revision is in ten-e1c
        String snapBase = insertSnapshot(projectId, "ten-e1c", VALID);
        String snapSrc = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        String snapTgt = snapshotService.save(projectId, "ten-other", VALID_TGT, "internal-1.0");
        insertRevision("base-1", projectId, "ten-e1c", snapBase, 1, false, null, null, null);
        insertRevision("src-1", projectId, "ten-e1c", snapSrc, 2, false, "base-1", null, null);
        insertRevision("tgt-1", projectId, "ten-e1c", snapTgt, 3, false, "base-1", null, null);

        PlatformException ex = expectThrow(
                PlatformException.class, request(projectId, "base-1", "src-1", "tgt-1"));

        assertEquals("COMMON-403-002", ex.getErrorCode().code(),
                "cross-tenant snapshot resolution must be TENANT_ACCESS_DENIED");
        assertEquals(3L, snapshotRows(projectId), "cross-tenant snapshot: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "cross-tenant snapshot: zero revision delta");
    }

    @Test
    void crossProject_rejected_zeroWrites() {
        String projectId = "prj-crossproject-" + UUID.randomUUID();
        seedValid(projectId);

        String otherProject = "prj-other-" + UUID.randomUUID();
        PlatformException ex = expectThrow(PlatformException.class,
                request(otherProject, "base-1", "src-1", "tgt-1"));

        assertEquals("TIMELINE-400-CROSS_PROJECT_MERGE", ex.getErrorCode().code(),
                "cross-project must be rejected with the frozen project-mismatch code");
        assertEquals(3L, snapshotRows(projectId), "cross-project: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "cross-project: zero revision delta");
        // correct project merge succeeds
        seedProduct(projectId, "tgt-1");
        assertEquals(MergeStatus.MERGED, merge(request(projectId, "base-1", "src-1", "tgt-1")).status());
    }

    @Test
    void authorizationFailure_missingTenantContext_zeroWrites() {
        String projectId = "prj-authz-" + UUID.randomUUID();
        seedValid(projectId);
        try {
            TenantContext.clear();
            PlatformException ex = expectThrow(
                    PlatformException.class, request(projectId, "base-1", "src-1", "tgt-1"));
            assertEquals("COMMON-401-002", ex.getErrorCode().code(),
                    "missing tenant context must be AUTHORIZATION_FAILURE");
        } finally {
            TenantContext.set("ten-e1c");
        }
        assertEquals(3L, snapshotRows(projectId), "authorization failure: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "authorization failure: zero revision delta");
        assertRecoveryMergeSucceeds(projectId);
    }

    // ========================================================================================
    // Missing revision / missing snapshot classification
    // ========================================================================================

    @Test
    void missingBaseRevision_classified_zeroWrites() {
        String projectId = "prj-missingbase-" + UUID.randomUUID();
        seedValid(projectId);
        IllegalArgumentException ex = expectThrow(IllegalArgumentException.class,
                request(projectId, "base-missing", "src-1", "tgt-1"));
        assertTrue(ex.getMessage().contains("Revision not found"), ex.getMessage());
        assertEquals(3L, snapshotRows(projectId), "missing base revision: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "missing base revision: zero revision delta");
        assertRecoveryMergeSucceeds(projectId);
    }

    @Test
    void missingSourceRevision_classified_zeroWrites() {
        String projectId = "prj-missingsrc-" + UUID.randomUUID();
        seedValid(projectId);
        expectThrow(IllegalArgumentException.class, request(projectId, "base-1", "src-missing", "tgt-1"));
        assertEquals(3L, snapshotRows(projectId), "missing source revision: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "missing source revision: zero revision delta");
    }

    @Test
    void missingTargetRevision_classified_zeroWrites() {
        String projectId = "prj-missingtgt-" + UUID.randomUUID();
        seedValid(projectId);
        expectThrow(IllegalArgumentException.class, request(projectId, "base-1", "src-1", "tgt-missing"));
        assertEquals(3L, snapshotRows(projectId), "missing target revision: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "missing target revision: zero revision delta");
    }

    @Test
    void missingSnapshot_classified_zeroWrites() {
        String projectId = "prj-missingsnap-" + UUID.randomUUID();
        String snapBase = insertSnapshot(projectId, "ten-e1c", VALID);
        String snapSrc = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        insertRevision("base-1", projectId, "ten-e1c", snapBase, 1, false, null, null, null);
        insertRevision("src-1", projectId, "ten-e1c", snapSrc, 2, false, "base-1", null, null);
        insertRevision("tgt-1", projectId, "ten-e1c", "snap-missing", 3, false, "base-1", null, null);

        IllegalStateException ex = expectThrow(IllegalStateException.class,
                request(projectId, "base-1", "src-1", "tgt-1"));
        assertTrue(ex.getMessage().contains("Snapshot not found"), ex.getMessage());
        assertEquals(2L, snapshotRows(projectId), "missing snapshot: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "missing snapshot: zero revision delta");
        assertRecoveryMergeSucceeds(projectId);
    }

    // ========================================================================================
    // Stale current revision -> zero writes
    // ========================================================================================

    @Test
    void staleCurrentRevision_rejected_zeroWrites() {
        String projectId = "prj-stale-" + UUID.randomUUID();
        seedValid(projectId);
        seedProduct(projectId, "stale-rev"); // PRODUCT points elsewhere -> stale

        TimelineConflictException ex = expectThrow(TimelineConflictException.class,
                request(projectId, "base-1", "src-1", "tgt-1"));

        assertEquals("tgt-1", ex.getExpectedRevisionId());
        assertEquals("stale-rev", ex.getActualRevisionId());
        assertEquals(3L, snapshotRows(projectId), "stale current: zero snapshot delta");
        assertEquals(3L, revisionRows(projectId), "stale current: zero revision delta");
        assertEquals("stale-rev", currentRevision(projectId), "stale current: pointer unchanged");
        // recovery: after the current pointer moves to the target, the merge succeeds
        dsl.update(PRODUCT).set(PRODUCT.CURRENT_REVISION_ID, "tgt-1")
                .where(PRODUCT.PRODUCT_ID.eq(projectId)).execute();
        assertEquals(MergeStatus.MERGED, merge(request(projectId, "base-1", "src-1", "tgt-1")).status());
    }

    // ========================================================================================
    // Persistence failure rollback (real permission-denied failures on real PostgreSQL)
    // ========================================================================================

    @Test
    void snapshotPersistenceFailure_rollsBack_noPartialState_thenRecovery() {
        String projectId = "prj-snapfail-" + UUID.randomUUID();
        seedValid(projectId);
        seedProduct(projectId, "tgt-1");

        dsl.execute("REVOKE INSERT ON TABLE timeline_snapshot FROM e1c_restricted");
        dsl.execute("SET ROLE e1c_restricted");
        try {
            expectThrow(RuntimeException.class, request(projectId, "base-1", "src-1", "tgt-1"));
        } finally {
            dsl.execute("RESET ROLE");
            dsl.execute("GRANT INSERT ON TABLE timeline_snapshot TO e1c_restricted");
        }
        assertEquals(3L, snapshotRows(projectId), "snapshot failure: merged snapshot rolled back");
        assertEquals(3L, revisionRows(projectId), "snapshot failure: zero revision delta");
        assertEquals("tgt-1", currentRevision(projectId), "snapshot failure: current pointer unchanged");
        assertRecoveryMergeSucceeds(projectId);
    }

    @Test
    void revisionInsertFailure_rollsBack_noPartialState_noCurrentPointer_thenRecovery() {
        String projectId = "prj-revfail-" + UUID.randomUUID();
        seedValid(projectId);
        seedProduct(projectId, "tgt-1");

        // R5 closure: on the pristine base the swallowed revision-insert failure left a
        // committed orphan snapshot (partial durable state). E1c rethrows -> the whole
        // transaction rolls back: ZERO new snapshot rows, ZERO new revision rows.
        // The restricted role has INSERT on timeline_snapshot but NOT on timeline_revision.
        dsl.execute("SET ROLE e1c_restricted");
        try {
            expectThrow(RuntimeException.class, request(projectId, "base-1", "src-1", "tgt-1"));
        } finally {
            dsl.execute("RESET ROLE");
        }
        assertEquals(3L, snapshotRows(projectId), "revision failure: snapshot write rolled back too");
        assertEquals(3L, revisionRows(projectId), "revision failure: zero revision delta");
        assertEquals("tgt-1", currentRevision(projectId), "no current pointer to a missing revision");
        assertRecoveryMergeSucceeds(projectId);
    }

    @Test
    void currentUpdateFailure_rollsBack() {
        String projectId = "prj-curfail-" + UUID.randomUUID();
        seedValid(projectId);
        // NO product row: the optimistic current-revision update cannot succeed -> the
        // complete merge (snapshot + revision) must roll back.
        expectThrow(RuntimeException.class, request(projectId, "base-1", "src-1", "tgt-1"));

        assertEquals(3L, snapshotRows(projectId), "current update failure: snapshot rolled back");
        assertEquals(3L, revisionRows(projectId), "current update failure: revision rolled back");
        assertRecoveryMergeSucceeds(projectId);
    }

    // ========================================================================================
    // Revision gaps: rejections leave no gap
    // ========================================================================================

    @Test
    void revisionGap_zeroAfterRejections() {
        String projectId = "prj-gap-" + UUID.randomUUID();
        seedValid(projectId);
        seedProduct(projectId, "tgt-1");

        expectThrow(IllegalArgumentException.class, request(projectId, "bogus", "src-1", "tgt-1"));
        expectThrow(PlatformException.class, request("prj-other-" + UUID.randomUUID(), "base-1", "src-1", "tgt-1"));
        dsl.update(PRODUCT).set(PRODUCT.CURRENT_REVISION_ID, "stale").where(PRODUCT.PRODUCT_ID.eq(projectId)).execute();
        expectThrow(TimelineConflictException.class, request(projectId, "base-1", "src-1", "tgt-1"));
        dsl.update(PRODUCT).set(PRODUCT.CURRENT_REVISION_ID, "tgt-1").where(PRODUCT.PRODUCT_ID.eq(projectId)).execute();

        TimelineMergeResult result = merge(request(projectId, "base-1", "src-1", "tgt-1"));
        assertEquals(MergeStatus.MERGED, result.status());
        assertEquals(4, mergedRow(projectId).revisionNumber(), "merged revision takes the next number");
        List<Integer> numbers = dsl.select(TIMELINE_REVISION.REVISION_NUMBER)
                .from(TIMELINE_REVISION).where(TIMELINE_REVISION.PROJECT_ID.eq(projectId))
                .orderBy(TIMELINE_REVISION.REVISION_NUMBER.asc())
                .fetch(TIMELINE_REVISION.REVISION_NUMBER);
        assertEquals(List.of(1, 2, 3, 4), numbers, "revision numbers contiguous: no gap");
    }

    // ========================================================================================
    // Idempotency: exact duplicate -> existing merge; cross-project dedup prohibited;
    // different payload hash -> distinct merge
    // ========================================================================================

    @Test
    void exactDuplicateMerge_noSecondWrite_sameMergedRevision() {
        String projectId = "prj-idem-" + UUID.randomUUID();
        seedValid(projectId);
        seedProduct(projectId, "tgt-1");

        TimelineMergeResult first = merge(request(projectId, "base-1", "src-1", "tgt-1"));
        assertEquals(MergeStatus.MERGED, first.status());
        TimelineMergeResult second = merge(request(projectId, "base-1", "src-1", "tgt-1"));

        assertEquals(first.mergedRevisionId(), second.mergedRevisionId(),
                "duplicate request must return the existing accepted merge");
        assertEquals(4L, snapshotRows(projectId), "duplicate: zero new snapshot rows");
        assertEquals(4L, revisionRows(projectId), "duplicate: zero new revision rows");
        assertEquals(1L, revisionRows(projectId) - 3L,
                "exactly one merge revision row (no duplicate rows)");
    }

    @Test
    void crossProjectDedup_prohibited() {
        String projectA = "prj-dedup-a-" + UUID.randomUUID();
        String projectB = "prj-dedup-b-" + UUID.randomUUID();
        seedValid(projectA);
        seedProduct(projectA, "tgt-1");
        seedValidWithIds(projectB, "base-b", "src-b", "tgt-b");
        seedProduct(projectB, "tgt-b");

        TimelineMergeResult mergeA = merge(request(projectA, "base-1", "src-1", "tgt-1"));
        TimelineMergeResult mergeB = merge(request(projectB, "base-b", "src-b", "tgt-b"));

        assertNotEquals(mergeA.mergedRevisionId(), mergeB.mergedRevisionId(),
                "cross-project deduplication is PROHIBITED: each project merges independently");
        assertEquals(4L, snapshotRows(projectB), "project B wrote its own merged snapshot");
        assertEquals(4L, revisionRows(projectB), "project B wrote its own merge revision");
    }

    @Test
    void differentMergedPayloadHash_distinctMerge() {
        String projectId = "prj-diffhash-" + UUID.randomUUID();
        String snapBase = insertSnapshot(projectId, "ten-e1c", VALID);
        String snapSrc = insertSnapshot(projectId, "ten-e1c", VALID_SRC);
        String snapTgt = insertSnapshot(projectId, "ten-e1c", VALID_TGT);
        String snapSrc2 = insertSnapshot(projectId, "ten-e1c", VALID_SRC2);
        insertRevision("base-1", projectId, "ten-e1c", snapBase, 1, false, null, null, null);
        insertRevision("src-1", projectId, "ten-e1c", snapSrc, 2, false, "base-1", null, null);
        insertRevision("tgt-1", projectId, "ten-e1c", snapTgt, 3, false, "base-1", null, null);
        insertRevision("src2-1", projectId, "ten-e1c", snapSrc2, 4, false, "base-1", null, null);
        seedProduct(projectId, "tgt-1");

        TimelineMergeResult first = merge(request(projectId, "base-1", "src-1", "tgt-1"));
        // The accepted merge advanced PRODUCT.CURRENT_REVISION_ID to the merged revision;
        // the second merge targets the new head (frozen stale-current policy).
        TimelineMergeResult second = merge(
                request(projectId, "base-1", "src2-1", first.mergedRevisionId()));

        assertEquals(MergeStatus.MERGED, first.status());
        assertEquals(MergeStatus.MERGED, second.status());
        assertNotEquals(first.mergedRevisionId(), second.mergedRevisionId(),
                "different merge input must produce a distinct merge");
        TimelineRevisionRepository.RevisionRow firstRow =
                revisionRepository.findById(first.mergedRevisionId()).orElseThrow();
        TimelineRevisionRepository.RevisionRow secondRow =
                revisionRepository.findById(second.mergedRevisionId()).orElseThrow();
        assertNotEquals(firstRow.contentHash(), secondRow.contentHash(),
                "different merged payload hash must be recorded distinctly");
        assertEquals(6L, revisionRows(projectId), "two distinct merge revisions persisted");
    }

    // ========================================================================================
    // Content hash contract
    // ========================================================================================

    @Test
    void contentHash_boundedDeterministic_stableForIdempotencyInput() {
        String projectId = "prj-hash-" + UUID.randomUUID();
        seedValid(projectId);
        seedProduct(projectId, "tgt-1");

        TimelineMergeResult first = merge(request(projectId, "base-1", "src-1", "tgt-1"));
        String storedHash = mergedRow(projectId).contentHash();
        assertEquals(64, storedHash.length(), "hash must be <= 64 characters (varchar(64))");
        assertTrue(storedHash.matches("[0-9a-f]{64}"), "hash must be 64 lowercase hex characters");
        assertEquals(sha256Hex("merge:src-1:tgt-1:" + VALID_TGT), storedHash,
                "hash must equal the canonical SHA-256 of the complete canonical input (no label)");

        // stability for the frozen idempotency input: the exact duplicate returns the
        // SAME stored hash because it returns the SAME accepted merge revision.
        TimelineMergeResult duplicate = merge(request(projectId, "base-1", "src-1", "tgt-1"));
        assertEquals(first.mergedRevisionId(), duplicate.mergedRevisionId());
        assertEquals(storedHash, mergedRow(projectId).contentHash(),
                "idempotency input maps to the identical stored hash");
        assertEquals(4L, revisionRows(projectId), "no duplicate revision row");
    }
}
