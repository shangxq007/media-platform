package com.example.platform.timeline.app;

import java.util.Objects;
import org.jooq.DSLContext;

/**
 * F2/F3: production {@link HeadUpdatePort} — delegates to the REAL CAS head
 * update ({@link ProductCurrentRevisionService#updateCurrentRevisionTx}) which
 * keeps the expected-head inside the database UPDATE predicate (Checkpoint A
 * concurrency protection preserved — no check-then-act replacement).
 */
public final class ProductCurrentRevisionHeadUpdateAdapter implements HeadUpdatePort {

    private final ProductCurrentRevisionService currentRevisionService;

    public ProductCurrentRevisionHeadUpdateAdapter(ProductCurrentRevisionService currentRevisionService) {
        this.currentRevisionService = Objects.requireNonNull(currentRevisionService, "currentRevisionService");
    }

    @Override
    public void updateHeadTx(DSLContext tx, String productId,
                             String expectedCurrentRevisionId, String newRevisionId) {
        currentRevisionService.updateCurrentRevisionTx(
                tx, productId, expectedCurrentRevisionId, newRevisionId);
    }
}
