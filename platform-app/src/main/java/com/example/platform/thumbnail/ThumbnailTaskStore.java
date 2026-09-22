package com.example.platform.thumbnail;

import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ThumbnailTaskStore {
    private final DSLContext dsl;
    public ThumbnailTaskStore(DSLContext dsl) { this.dsl = dsl; }
    public synchronized String admit(ThumbnailContracts.Request r) {
        var existing = dsl.fetchOne("select id from media_thumbnail_task where tenant_id=? and project_id=? and idempotency_key=?", r.tenantId(), r.projectId(), r.idempotencyKey());
        if (existing != null) return existing.get("id", String.class);
        String id = "thumb_" + java.util.UUID.randomUUID().toString().replace("-", "");
        dsl.execute("insert into media_thumbnail_task (id,tenant_id,project_id,source_asset_id,timestamp_seconds,image_format,width,quality,idempotency_key,status) values (?,?,?,?,?,?,?,?,?,?)",
                id,r.tenantId(),r.projectId(),r.sourceAssetId(),r.timestampSeconds(),r.imageFormat(),r.width(),r.quality(),r.idempotencyKey(),ThumbnailContracts.Status.ADMITTED.name());
        return id;
    }
    public void status(String id, ThumbnailContracts.Status status, String artifactId, String failure) {
        dsl.execute("update media_thumbnail_task set status=?,artifact_id=?,failure_code=?,updated_at=current_timestamp where id=?", status.name(), artifactId, failure, id);
    }
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
