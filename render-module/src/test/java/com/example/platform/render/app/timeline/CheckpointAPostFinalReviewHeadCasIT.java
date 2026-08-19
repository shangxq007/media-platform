package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.app.ProductCurrentRevisionService;
import com.example.platform.timeline.version.TimelineConflictException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * POST_FINAL_REVIEW_P1: REAL database-enforced head CAS proof.
 *
 * ProductCurrentRevisionService.updateCurrentRevisionTx must put the expected
 * revision in the UPDATE predicate (null → IS NULL) and let affected-rows == 1
 * decide — NOT a SELECT → Java-compare → unconditional UPDATE check-then-act.
 *
 * Classifications:
 * - casSingleWriterSucceeds          REAL_DB_CAS_SINGLE_WRITER
 * - casStaleExpectationFailsClosed   REAL_DB_CAS_STALE_EXPECTATION
 * - concurrentWritersSingleWinner    REAL_DB_CAS_CONCURRENT_WRITERS
 */
class CheckpointAPostFinalReviewHeadCasIT extends PostgresTestContainerSupport {

    private javax.sql.DataSource dataSource;
    private DSLContext dsl;
    private ProductCurrentRevisionService service;

    @BeforeEach
    void setUp() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        service = new ProductCurrentRevisionService(dsl);
        com.example.platform.render.testsupport.RenderTestSchemaFixture.createSchema(dsl);
        com.example.platform.render.testsupport.RenderTestSchemaFixture.truncate(dsl);
    }

    @AfterEach
    void tearDown() {
        closeDataSource(dataSource);
    }

    private void insertProduct(String productId) {
        dsl.insertInto(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT)
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.PRODUCT_ID, productId)
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.PRODUCT_TYPE, "video")
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.REPRESENTATION_KIND, "master")
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.STATUS, "REGISTERED")
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    private String currentHead(String productId) {
        return dsl.select(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.CURRENT_REVISION_ID)
                .from(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT)
                .where(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.PRODUCT_ID.eq(productId))
                .fetchAny(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.CURRENT_REVISION_ID);
    }

    @Test
    void casSingleWriterSucceeds() {
        String productId = "prod-cas-ok-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        // seed head = R100 via the CAS API itself (expected null → IS NULL branch)
        service.updateCurrentRevision(productId, null, "R100");
        assertEquals("R100", currentHead(productId), "null expectation uses IS NULL and advances head");

        // single writer with exact expectation advances again
        service.updateCurrentRevision(productId, "R100", "RA");
        assertEquals("RA", currentHead(productId), "expected==actual advances head");
    }

    @Test
    void casStaleExpectationFailsClosed() {
        String productId = "prod-cas-stale-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        service.updateCurrentRevision(productId, null, "R100");

        TimelineConflictException ex = assertThrows(TimelineConflictException.class,
                () -> service.updateCurrentRevision(productId, "R101", "RA"),
                "stale expectation must fail closed (0 affected rows)");
        assertEquals("R100", currentHead(productId), "head unchanged after stale CAS");
        assertTrue(ex.getMessage() != null && !ex.getMessage().isBlank());
    }

    @Test
    void concurrentWritersSingleWinner() throws Exception {
        String productId = "prod-cas-conc-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        service.updateCurrentRevision(productId, null, "R100");

        // TWO concurrent writers race the SAME persisted head R100:
        //   writer A: expected=R100 → new=RA
        //   writer B: expected=R100 → new=RB
        // Exactly one conditional UPDATE may match; the loser gets 0 rows →
        // TimelineConflictException. The final head is the winner's revision.
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        Future<?> fA = pool.submit(() -> {
            startGate.await();
            try {
                service.updateCurrentRevision(productId, "R100", "RA");
                success.incrementAndGet();
            } catch (TimelineConflictException e) {
                conflicts.incrementAndGet();
            }
            return null;
        });
        Future<?> fB = pool.submit(() -> {
            startGate.await();
            try {
                service.updateCurrentRevision(productId, "R100", "RB");
                success.incrementAndGet();
            } catch (TimelineConflictException e) {
                conflicts.incrementAndGet();
            }
            return null;
        });
        startGate.countDown();
        fA.get(30, TimeUnit.SECONDS);
        fB.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(1, success.get(), "exactly ONE concurrent writer may win");
        assertEquals(1, conflicts.get(), "the other writer must observe a conflict");
        String finalHead = currentHead(productId);
        assertTrue("RA".equals(finalHead) || "RB".equals(finalHead),
                "final head must be the winner's revision (was " + finalHead + ")");
    }
}
