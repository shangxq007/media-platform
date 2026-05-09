package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.shared.Ids;
import java.time.OffsetDateTime;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class RenderJobStatusHistoryRepository {

    private final DSLContext dsl;

    public RenderJobStatusHistoryRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    public void record(String jobId, String fromStatus, String toStatus, String reason, String errorCode) {
        String id = Ids.newId("rsh");
        dsl.insertInto(table("render_job_status_history"))
                .columns(field("id"), field("job_id"), field("from_status"), field("to_status"),
                        field("reason"), field("error_code"), field("occurred_at"))
                .values(id, jobId, fromStatus, toStatus, reason, errorCode, OffsetDateTime.now())
                .execute();
    }

    public List<StatusHistoryResponse> findByJobId(String jobId) {
        return dsl.select()
                .from(table("render_job_status_history"))
                .where(field("job_id").eq(jobId))
                .orderBy(field("occurred_at").asc())
                .fetch(this::mapRecord);
    }

    private StatusHistoryResponse mapRecord(Record record) {
        return new StatusHistoryResponse(
                record.get(field("id"), String.class),
                record.get(field("job_id"), String.class),
                record.get(field("from_status"), String.class),
                record.get(field("to_status"), String.class),
                record.get(field("reason"), String.class),
                record.get(field("error_code"), String.class),
                record.get(field("occurred_at"), OffsetDateTime.class)
        );
    }
}
