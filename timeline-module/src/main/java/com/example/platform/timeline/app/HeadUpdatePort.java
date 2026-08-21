package com.example.platform.timeline.app;

/**
 * ROADMAP20 B2: narrow port for the canonical HEAD update (product current
 * revision CAS). Production default delegates to
 * {@link ProductCurrentRevisionService#updateCurrentRevisionTx}. Bounded
 * failure injection for TX5: a test may substitute a failing implementation.
 */
public interface HeadUpdatePort {

    /** CAS head update inside the caller's physical transaction. */
    void updateHeadTx(org.jooq.DSLContext tx, String productId,
                      String expectedCurrentRevisionId, String newRevisionId);
}
