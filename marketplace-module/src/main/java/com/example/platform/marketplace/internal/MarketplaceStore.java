package com.example.platform.marketplace.internal;

import com.example.platform.marketplace.api.MarketplaceApi.*;
import com.example.platform.marketplace.api.MarketplacePublicationSubjectRef.MediaAssetSubject;
import com.example.platform.media.domain.identity.MediaAssetId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MarketplaceStore {
    final JdbcTemplate jdbc;
    public MarketplaceStore(JdbcTemplate jdbc) {this.jdbc=jdbc;}
    Optional<Listing> listing(String tenant,String project,String id,boolean lock) {
        return jdbc.query("select * from marketplace_listing where tenant_id=? and project_id=? and id=? and admitted_at is not null"+(lock?" for update":""),this::map,tenant,project,id).stream().findFirst();
    }
    Optional<Listing> admittedAsset(String tenant,String asset) {
        return jdbc.query("select * from marketplace_listing where tenant_id=? and asset_id=? and admitted_at is not null",this::map,tenant,asset).stream().findFirst();
    }
    Optional<Listing> publicListing(String id) {
        return jdbc.query("select * from marketplace_listing where id=? and admitted_at is not null and status='PUBLISHED'",this::map,id).stream().findFirst();
    }
    List<Listing> published(String query,String workspace) {
        return jdbc.query("select * from marketplace_listing where admitted_at is not null and status='PUBLISHED' and (?::text is null or workspace_id=?) and (?::text is null or search_vector @@ plainto_tsquery('english',?)) order by id",this::map,workspace,workspace,query,query);
    }
    List<Listing> project(String tenant,String project,int limit) {
        return jdbc.query("select * from marketplace_listing where tenant_id=? and project_id=? and admitted_at is not null order by updated_at desc,id limit ?",this::map,tenant,project,limit);
    }
    void lockCommand(String tenant,String command) {
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"marketplace-command:"+tenant+":"+command);
    }
    Optional<Map<String,Object>> command(String tenant,String command) {
        return jdbc.queryForList("select * from marketplace_command where tenant_id=? and command_id=?",tenant,command).stream().findFirst();
    }
    void recordCommand(String tenant,String command,String digest,String actor,Object result) {
        jdbc.update("insert into marketplace_command(tenant_id,command_id,request_digest,actor_json,result_json) values (?,?,?,?,?)",tenant,command,digest,actor,MarketplaceJson.write(result));
    }
    String create(String tenant,String workspace,String project,String asset,String version,String actor,String title,String summary,String description) {
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"marketplace-subject:"+asset);
        var prior=jdbc.queryForList("select * from marketplace_listing where asset_id=? for update",asset);
        String id="mlst_"+UUID.randomUUID().toString().replace("-", "");
        if(!prior.isEmpty()) {
            var old=prior.getFirst();
            if(old.get("admitted_at")!=null) throw MarketplaceService.conflict("Subject already has an admitted listing; use its explicit commands");
            if(old.get("tenant_id")!=null&&!tenant.equals(old.get("tenant_id")) || old.get("project_id")!=null&&!project.equals(old.get("project_id")))
                throw MarketplaceService.conflict("Historical listing scope needs explicit owner reconciliation");
            id=(String)old.get("id");
            jdbc.update("update marketplace_listing set tenant_id=?,workspace_id=?,project_id=?,subject_version=?,created_by=?,updated_by=?,title=?,summary=?,description=?,listing_type='MEDIA',status='DRAFT',version=?,aggregate_version=1,review_id=null,preview_url=null,cover_url=null,search_text=?,search_vector=to_tsvector('english',?),admitted_at=now(),updated_at=now() where id=?",tenant,workspace,project,version,actor,actor,title,summary,description,version,title+" "+summary,title+" "+summary,id);
        } else jdbc.update("insert into marketplace_listing(id,asset_id,tenant_id,workspace_id,project_id,subject_version,created_by,updated_by,title,summary,description,listing_type,status,version,aggregate_version,search_text,search_vector,admitted_at,created_at,updated_at) values (?,?,?,?,?,?,?,?,?,?,?,'MEDIA','DRAFT',?,1,?,to_tsvector('english',?),now(),now(),now())",id,asset,tenant,workspace,project,version,actor,actor,title,summary,description,version,title+" "+summary,title+" "+summary);
        return id;
    }
    void edit(Listing row,String title,String summary,String description,String actor) {
        jdbc.update("update marketplace_listing set title=?,summary=?,description=?,review_id=null,status='DRAFT',search_text=?,search_vector=to_tsvector('english',?),aggregate_version=aggregate_version+1,updated_by=?,updated_at=now() where id=?",title,summary,description,title+" "+summary,title+" "+summary,actor,row.id());
    }
    void change(Listing row,Status state,String reviewId,String actor) {
        jdbc.update("update marketplace_listing set status=?,review_id=?,aggregate_version=aggregate_version+1,updated_by=?,updated_at=now(),published_at=case when ?='PUBLISHED' then now() else published_at end where id=?",state.name(),reviewId,actor,state.name(),row.id());
    }
    Map<String,Object> reviewRow(String tenant,String project,String id) {
        return jdbc.queryForList("select * from marketplace_review where tenant_id=? and project_id=? and id=?",tenant,project,id).stream().findFirst().orElseThrow(()->MarketplaceService.missing("Review not found"));
    }
    void insertReview(String id,Listing row,String actor,String title,String description) {
        jdbc.update("insert into marketplace_review(id,listing_id,tenant_id,workspace_id,project_id,subject_version,author_id,title,description,status,aggregate_version) values (?,?,?,?,?,?,?,?,?,'OPEN',?)",id,row.id(),row.tenantId(),row.workspaceId(),row.projectId(),((MediaAssetSubject)row.subject()).version(),actor,title,description,row.version()+1);
    }
    void reviewStatus(String id,ReviewStatus status,long version) {
        jdbc.update("update marketplace_review set status=?,aggregate_version=? where id=?",status.name(),version,id);
    }
    List<ReviewComment> comments(String reviewId) {
        return jdbc.query("select c.*,t.resolved from marketplace_review_comment c join marketplace_review_thread t on t.id=c.thread_id where c.review_id=? order by c.created_at,c.id",(r,i)->new ReviewComment(r.getString("id"),r.getString("thread_id"),r.getString("author_id"),r.getString("content"),r.getBoolean("resolved"),r.getTimestamp("created_at").toInstant()),reviewId);
    }
    boolean unresolved(String review) {return jdbc.queryForObject("select exists(select 1 from marketplace_review_thread where review_id=? and not resolved)",Boolean.class,review);}
    Listing map(ResultSet r,int i)throws SQLException {
        return new Listing(r.getString("id"),new MediaAssetSubject(new MediaAssetId(r.getString("asset_id")),r.getString("subject_version")),r.getString("tenant_id"),r.getString("workspace_id"),r.getString("project_id"),r.getString("title"),r.getString("summary"),r.getString("description"),Status.valueOf(r.getString("status")),r.getLong("aggregate_version"),r.getString("review_id"),r.getString("created_by"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant());
    }
}
