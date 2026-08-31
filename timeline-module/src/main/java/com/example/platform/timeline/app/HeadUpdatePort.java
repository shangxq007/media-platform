package com.example.platform.timeline.app;

/**
 * Narrow failure-injection seam around the sole canonical Timeline ref CAS.
 * Production delegates to {@link TimelineRevisionRefMutation}. Bounded
 * failure injection for TX5: a test may substitute a failing implementation.
 */
public interface HeadUpdatePort {

    /** CAS head update inside the caller's physical transaction. */
    void updateHeadTx(org.jooq.DSLContext tx,
                      com.example.platform.timeline.revisioncommand.RevisionRef ref,
                      String expectedCurrentRevisionId, String newRevisionId);
}
