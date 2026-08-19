package com.example.platform.timeline.app;

import com.example.platform.timeline.version.TimelineConflictException;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;

@Service
public class ProductCurrentRevisionService {

    private static final Logger log = LoggerFactory.getLogger(ProductCurrentRevisionService.class);
    private final DSLContext dsl;

    public ProductCurrentRevisionService(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Atomically updates the product's current revision pointer with optimistic concurrency.
     *
     * @param productId the product ID
     * @param expectedCurrentRevisionId the expected current revision (null if first revision)
     * @param newRevisionId the new revision to set as current
     * @throws TimelineConflictException if expected != actual current revision
     */
    @Transactional
    public void updateCurrentRevision(String productId, String expectedCurrentRevisionId, String newRevisionId) {
        updateCurrentRevisionTx(dsl, productId, expectedCurrentRevisionId, newRevisionId);
    }

    /** CHECKPOINT_A (Round 3): transaction-scoped head update — the caller's
     *  jOOQ transaction context is used so head mutation joins the same atomic
     *  unit as revision insert + pin registration (rollback-safe).
     *
     *  POST_FINAL_REVIEW_P1: REAL database-enforced compare-and-set. The
     *  expected current revision participates in the UPDATE predicate itself:
     *  expected != null → WHERE current_revision_id = expected;
     *  expected == null → WHERE current_revision_id IS NULL.
     *  Affected-rows == 1 is the ONLY correctness authority — NO
     *  SELECT → Java-compare → unconditional-UPDATE check-then-act. The
     *  post-failure SELECT exists for diagnostics only and never determines
     *  correctness. */
    public void updateCurrentRevisionTx(org.jooq.DSLContext tx,
                                        String productId, String expectedCurrentRevisionId, String newRevisionId) {
        int updated;
        if (expectedCurrentRevisionId == null) {
            updated = tx.update(PRODUCT)
                    .set(PRODUCT.CURRENT_REVISION_ID, newRevisionId)
                    .where(PRODUCT.PRODUCT_ID.eq(productId))
                    .and(PRODUCT.CURRENT_REVISION_ID.isNull())
                    .execute();
        } else {
            updated = tx.update(PRODUCT)
                    .set(PRODUCT.CURRENT_REVISION_ID, newRevisionId)
                    .where(PRODUCT.PRODUCT_ID.eq(productId))
                    .and(PRODUCT.CURRENT_REVISION_ID.eq(expectedCurrentRevisionId))
                    .execute();
        }

        if (updated != 1) {
            // DIAGNOSTIC ONLY (same tx, never the correctness authority):
            // distinguish missing product from stale expected head.
            String actual = tx.select(PRODUCT.CURRENT_REVISION_ID)
                    .from(PRODUCT)
                    .where(PRODUCT.PRODUCT_ID.eq(productId))
                    .fetchAny(PRODUCT.CURRENT_REVISION_ID);
            if (actual == null) {
                throw new IllegalStateException("Product not found: " + productId);
            }
            log.warn("Timeline revision conflict: product={}, expected={}, actual={}",
                    productId, expectedCurrentRevisionId, actual);
            throw new TimelineConflictException(productId, expectedCurrentRevisionId, actual);
        }

        log.debug("Updated product={} current revision to {}", productId, newRevisionId);
    }

    /**
     * Reads the current revision ID for a product.
     */
    @Transactional(readOnly = true)
    public String getCurrentRevisionId(String productId) {
        return dsl.select(PRODUCT.CURRENT_REVISION_ID)
                .from(PRODUCT)
                .where(PRODUCT.PRODUCT_ID.eq(productId))
                .fetchOne(PRODUCT.CURRENT_REVISION_ID);
    }
}
