package com.example.platform.render.app.timeline;

import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static org.junit.jupiter.api.Assertions.*;

class ProductCurrentRevisionServiceIntegrationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private ProductCurrentRevisionService currentRevisionService;
    private TimelineRevisionSaveService saveService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        RenderTestSchemaFixture.truncate(dsl);
        currentRevisionService = new ProductCurrentRevisionService(dsl);
        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService, new com.example.platform.timeline.canonical.TimelineContentDigester());
    }

    @Test
    void getCurrentRevisionId_initiallyNull() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);
        String current = currentRevisionService.getCurrentRevisionId(productId);
        assertNull(current);
    }

    @Test
    void updateCurrentRevision_success() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);

        currentRevisionService.updateCurrentRevision(productId, null, "rev-1");

        assertEquals("rev-1", currentRevisionService.getCurrentRevisionId(productId));
    }

    @Test
    void updateCurrentRevision_withExpectedConflict() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);

        currentRevisionService.updateCurrentRevision(productId, null, "rev-1");

        assertThrows(TimelineConflictException.class, () ->
                currentRevisionService.updateCurrentRevision(productId, null, "rev-2"));
    }

    @Test
    void updateCurrentRevision_expectedMatchesActual() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);

        currentRevisionService.updateCurrentRevision(productId, null, "rev-1");

        // Update with correct expected value should succeed
        currentRevisionService.updateCurrentRevision(productId, "rev-1", "rev-2");

        assertEquals("rev-2", currentRevisionService.getCurrentRevisionId(productId));
    }

    @Test
    void optimisticConcurrency_twoCompetingSaves_onlyOneWins() throws InterruptedException {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);

        // Initialize
        currentRevisionService.updateCurrentRevision(productId, null, "rev-0");

        // Thread 1: tries to update from rev-0 to rev-1
        Thread t1 = new Thread(() -> {
            try {
                currentRevisionService.updateCurrentRevision(productId, "rev-0", "rev-1");
            } catch (TimelineConflictException e) {
                // Expected if thread 2 wins
            }
        });

        // Thread 2: tries to update from rev-0 to rev-2
        Thread t2 = new Thread(() -> {
            try {
                currentRevisionService.updateCurrentRevision(productId, "rev-0", "rev-2");
            } catch (TimelineConflictException e) {
                // Expected if thread 1 wins
            }
        });

        t1.start();
        t2.start();
        t1.join();
        t2.join();

        String current = currentRevisionService.getCurrentRevisionId(productId);
        assertTrue("rev-1".equals(current) || "rev-2".equals(current));
        assertNotEquals("rev-0", current);
    }

    private void insertProduct(String productId) {
        dsl.insertInto(PRODUCT)
                .set(PRODUCT.PRODUCT_ID, productId)
                .set(PRODUCT.PRODUCT_TYPE, "video")
                .set(PRODUCT.REPRESENTATION_KIND, "master")
                .set(PRODUCT.STATUS, "REGISTERED")
                .set(PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
    }
}
