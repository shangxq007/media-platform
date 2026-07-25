package com.example.platform.render.app;

import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.shared.Ids;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;
import static com.example.platform.typedschema.jooq.generated.tables.RenderJobStatusHistory.RENDER_JOB_STATUS_HISTORY;


@Repository
public class RenderJobStatusHistoryRepository {

    private final DSLContext dsl;

    public RenderJobStatusHistoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void record(String jobId, String fromStatus, String toStatus, String reason, String errorCode) {
        String id = Ids.newId("rsh");
        dsl.insertInto(RENDER_JOB_STATUS_HISTORY)
                .columns(RENDER_JOB_STATUS_HISTORY.ID, RENDER_JOB_STATUS_HISTORY.JOB_ID, RENDER_JOB_STATUS_HISTORY.FROM_STATUS, RENDER_JOB_STATUS_HISTORY.TO_STATUS,
                        RENDER_JOB_STATUS_HISTORY.REASON, RENDER_JOB_STATUS_HISTORY.ERROR_CODE, RENDER_JOB_STATUS_HISTORY.OCCURRED_AT)
                .values(id, jobId, fromStatus, toStatus, reason, errorCode, LocalDateTime.now())
                .execute();
    }

    public List<StatusHistoryResponse> findByJobId(String jobId) {
        return dsl.select()
                .from(RENDER_JOB_STATUS_HISTORY)
                .where(RENDER_JOB_STATUS_HISTORY.JOB_ID.eq(jobId))
                .orderBy(RENDER_JOB_STATUS_HISTORY.OCCURRED_AT.asc())
                .fetch(this::mapRecord);
    }

    private StatusHistoryResponse mapRecord(Record record) {
        return new StatusHistoryResponse(
                record.get(RENDER_JOB_STATUS_HISTORY.ID, String.class),
                record.get(RENDER_JOB_STATUS_HISTORY.JOB_ID, String.class),
                record.get(RENDER_JOB_STATUS_HISTORY.FROM_STATUS, String.class),
                record.get(RENDER_JOB_STATUS_HISTORY.TO_STATUS, String.class),
                record.get(RENDER_JOB_STATUS_HISTORY.REASON, String.class),
                record.get(RENDER_JOB_STATUS_HISTORY.ERROR_CODE, String.class),
                record.get(RENDER_JOB_STATUS_HISTORY.OCCURRED_AT, OffsetDateTime.class)
        );
    }
}
