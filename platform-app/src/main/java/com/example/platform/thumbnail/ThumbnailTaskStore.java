package com.example.platform.thumbnail;

import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ThumbnailTaskStore {
    private final DSLContext dsl;
    public ThumbnailTaskStore(DSLContext dsl) { this.dsl = dsl; }
    public Admission admit(ThumbnailContracts.Request r) {
        String id = "thumb_" + java.util.UUID.randomUUID().toString().replace("-", "");
        int inserted = dsl.execute("insert into media_thumbnail_task (id,tenant_id,project_id,source_asset_id,timestamp_seconds,image_format,width,quality,idempotency_key,provider_id,provider_version,status) values (?,?,?,?,?,?,?,?,?,?,?,?) on conflict (tenant_id,project_id,idempotency_key) do nothing",
                id,r.tenantId(),r.projectId(),r.sourceAssetId(),r.timestampSeconds(),r.imageFormat(),r.width(),r.quality(),r.idempotencyKey(),ThumbnailContracts.PROVIDER,"1.0.0",ThumbnailContracts.Status.ADMITTED.name());
        if (inserted == 1) return new Admission(id, true);
        var existing = dsl.fetchOne("select id,source_asset_id,timestamp_seconds,image_format,width,quality from media_thumbnail_task where tenant_id=? and project_id=? and idempotency_key=?", r.tenantId(), r.projectId(), r.idempotencyKey());
        if (existing != null) {
            if (!r.sourceAssetId().equals(existing.get("source_asset_id", String.class))
                    || Double.compare(r.timestampSeconds(), existing.get("timestamp_seconds", Double.class)) != 0
                    || !r.imageFormat().equals(existing.get("image_format", String.class))
                    || !java.util.Objects.equals(r.width(), existing.get("width", Integer.class))
                    || !java.util.Objects.equals(r.quality(), existing.get("quality", Integer.class))) {
                throw new IllegalArgumentException("idempotency key is already bound to a different thumbnail request");
            }
            return new Admission(existing.get("id", String.class), false);
        }
        throw new IllegalStateException("thumbnail admission conflict could not be resolved");
    }
    public boolean statusIfActive(String id, ThumbnailContracts.Status status, String artifactId, String failure) {
        return dsl.execute("update media_thumbnail_task set status=?,artifact_id=?,failure_code=?,updated_at=current_timestamp where id=? and status not in ('CANCELLED','COMPLETED')", status.name(), artifactId, failure, id) == 1;
    }
    public boolean cancel(String tenant, String project, String id) { return dsl.execute("update media_thumbnail_task set status='CANCELLED',failure_code='CANCELLED',updated_at=current_timestamp where tenant_id=? and project_id=? and id=? and status in ('ADMITTED','RUNNING','COMMITTING')", tenant, project, id) == 1; }
    public boolean isCancelled(String tenant, String project, String id) { return dsl.fetchExists(dsl.selectOne().from("media_thumbnail_task").where("tenant_id=? and project_id=? and id=? and status='CANCELLED'", tenant, project, id)); }
    public record Admission(String taskId, boolean created) {}
    public Optional<ThumbnailContracts.Result> find(String tenant, String project, String id) {
        var row=dsl.fetchOne("select status,artifact_id,failure_code from media_thumbnail_task where tenant_id=? and project_id=? and id=?",tenant,project,id);
        return Optional.ofNullable(row).map(r->new ThumbnailContracts.Result(id, ThumbnailContracts.Status.valueOf(r.get("status",String.class)), r.get("artifact_id",String.class), r.get("failure_code",String.class)));
    }
    public Optional<ThumbnailContracts.Request> request(String tenant, String project, String id) {
        var r=dsl.fetchOne("select source_asset_id,timestamp_seconds,image_format,width,quality,idempotency_key from media_thumbnail_task where tenant_id=? and project_id=? and id=?",tenant,project,id);
        return Optional.ofNullable(r).map(x->new ThumbnailContracts.Request(tenant,project,idValue(x,"source_asset_id"),x.get("timestamp_seconds",Double.class),idValue(x,"image_format"),x.get("width",Integer.class),x.get("quality",Integer.class),idValue(x,"idempotency_key")));
    }
    private static String idValue(org.jooq.Record r,String c){return r.get(c,String.class);}
}
