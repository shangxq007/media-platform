package com.example.platform.timeline.app;

import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.timeline.version.TimelineConflictException;
import java.util.Objects;
import org.jooq.DSLContext;

/** Error-translating seam; all correctness remains in TimelineRevisionRefMutation. */
public final class TimelineRevisionRefHeadUpdateAdapter implements HeadUpdatePort {

    private final TimelineRevisionRefMutation mutation;

    public TimelineRevisionRefHeadUpdateAdapter(TimelineRevisionRefMutation mutation) {
        this.mutation = Objects.requireNonNull(mutation, "mutation");
    }

    @Override
    public void updateHeadTx(DSLContext tx, RevisionRef ref,
                             String expectedRevisionId, String newRevisionId) {
        boolean advanced = expectedRevisionId == null
                ? mutation.bootstrap(tx, ref, newRevisionId)
                : mutation.advance(tx, ref, expectedRevisionId, newRevisionId);
        if (!advanced) {
            throw new TimelineConflictException(
                    ref.projectId(), expectedRevisionId, mutation.currentHead(tx, ref));
        }
    }
}
