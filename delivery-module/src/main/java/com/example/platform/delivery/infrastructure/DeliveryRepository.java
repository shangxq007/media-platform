package com.example.platform.delivery.infrastructure;

import com.example.platform.delivery.app.DeliveryRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

/** Owns only Delivery records; never queries Render/Artifact owner tables. */
@Repository
public class DeliveryRepository {
    private final DSLContext dsl;
    public DeliveryRepository(DSLContext dsl) { this.dsl=dsl; }
    public boolean insert(DeliveryRecord r) {
        return dsl.execute("""
                insert into delivery(id,tenant_id,project_id,source_render_job_id,artifact_id,
                    idempotency_key,request_hash,requester_id,status,created_at)
                values(?,?,?,?,?,?,?,?,'READY',cast(? as timestamptz))
                on conflict(tenant_id,project_id,idempotency_key) do nothing
                """,r.id(),r.tenantId(),r.projectId(),r.sourceRenderJobId(),r.artifactId(),
                r.idempotencyKey(),r.requestHash(),r.requesterId(),r.createdAt())==1;
    }
    public Optional<DeliveryRecord> byKey(String tenant,String project,String key) {
        return Optional.ofNullable(dsl.fetchOne("select * from delivery where tenant_id=? and project_id=? and idempotency_key=?",tenant,project,key)).map(DeliveryRepository::record);
    }
    public Optional<DeliveryRecord> find(String tenant,String project,String id) {
        return Optional.ofNullable(dsl.fetchOne("select * from delivery where tenant_id=? and project_id=? and id=?",tenant,project,id)).map(DeliveryRepository::record);
    }
    public List<DeliveryRecord> list(String tenant,String project,int limit) {
        return dsl.fetch("select * from delivery where tenant_id=? and project_id=? order by created_at desc,id desc limit ?",tenant,project,limit).map(DeliveryRepository::record);
    }
    private static DeliveryRecord record(Record r) {
        return new DeliveryRecord(r.get("id",String.class),r.get("tenant_id",String.class),r.get("project_id",String.class),
                r.get("source_render_job_id",String.class),r.get("artifact_id",String.class),r.get("idempotency_key",String.class),
                r.get("request_hash",String.class),r.get("requester_id",String.class),r.get("created_at",OffsetDateTime.class));
    }
}
