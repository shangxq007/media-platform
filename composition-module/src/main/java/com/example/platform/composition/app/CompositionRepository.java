package com.example.platform.composition.app;

import com.example.platform.composition.domain.CompositionModels.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

/** All predicates include Identity-verified tenant/workspace ownership. */
@Repository
public class CompositionRepository {
    public enum Kind {
        WORKFLOW("composition_template_workflow_draft", "workflow_id", TemplateWorkflow.class),
        APPLICATION("composition_application_draft", "application_id", Application.class);
        final String table, idColumn;
        final Class<?> type;
        Kind(String table, String idColumn, Class<?> type) { this.table=table; this.idColumn=idColumn; this.type=type; }
    }
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final CompositionAccess access;
    private final ObjectMapper json = new ObjectMapper();

    public CompositionRepository(JdbcTemplate db, PlatformTransactionManager manager, CompositionAccess access) {
        this.db=db; this.transactions=new TransactionTemplate(manager); this.access=access;
    }

    public <T> Optional<T> draft(Kind kind, String tenant, String workspace, String id, Class<T> type) {
        access.require(tenant, workspace);
        return db.query("SELECT definition::text FROM "+kind.table+" WHERE tenant_id=? AND workspace_id=? AND "+kind.idColumn+"=?",
                (r,n)->read(r.getString(1),type),tenant,workspace,id).stream().findFirst();
    }

    public <T> Optional<T> version(Kind kind,String tenant,String workspace,String id,String version,Class<T> type) {
        access.require(tenant,workspace);
        return db.query("SELECT definition::text FROM composition_version WHERE tenant_id=? AND workspace_id=? AND kind=? AND composition_id=? AND version=?",
                (r,n)->read(r.getString(1),type),tenant,workspace,kind.name(),id,version).stream().findFirst();
    }

    public void save(Kind kind,String tenant,String workspace,String id,String version,long expected,Object value) {
        access.require(tenant,workspace);
        transactions.executeWithoutResult(tx->{
            if (expected < 0) throw new CompositionService.OptimisticConcurrencyException();
            int changed;
            if(expected==0) changed=db.update("INSERT INTO "+kind.table+" (tenant_id,workspace_id,"+kind.idColumn+",version,revision,lifecycle,definition) VALUES (?,?,?,?,1,'DRAFT',?::jsonb) ON CONFLICT DO NOTHING",tenant,workspace,id,version,write(value));
            else changed=db.update("UPDATE "+kind.table+" SET definition=?::jsonb,revision=revision+1,updated_at=now() WHERE tenant_id=? AND workspace_id=? AND "+kind.idColumn+"=? AND version=? AND revision=? AND lifecycle='DRAFT'",write(value),tenant,workspace,id,version,expected);
            if(changed!=1) throw new CompositionService.OptimisticConcurrencyException();
        });
    }

    public void publish(Kind kind,String tenant,String workspace,String id,String version,long expected,Object value) {
        access.require(tenant,workspace);
        transactions.executeWithoutResult(tx->{
            int changed=db.update("UPDATE "+kind.table+" SET lifecycle='PUBLISHED',definition=?::jsonb,revision=revision+1,updated_at=now() WHERE tenant_id=? AND workspace_id=? AND "+kind.idColumn+"=? AND version=? AND revision=? AND lifecycle='DRAFT'",write(value),tenant,workspace,id,version,expected);
            if(changed!=1) throw new CompositionService.OptimisticConcurrencyException();
            db.update("INSERT INTO composition_version (tenant_id,workspace_id,kind,composition_id,version,definition) VALUES (?,?,?,?,?,?::jsonb)",tenant,workspace,kind.name(),id,version,write(value));
        });
    }

    public void snapshot(String tenant,String workspace,String id,ValidationResult result) {
        access.require(tenant,workspace);
        db.update("INSERT INTO composition_validation_snapshot (snapshot_id,tenant_id,workspace_id,subject_id,result) VALUES (?,?,?,?,?::jsonb) ON CONFLICT DO NOTHING",result.snapshotId(),tenant,workspace,id,write(result));
    }

    public void newVersion(Kind kind,String tenant,String workspace,String id,String version,long expected,Object value) {
        access.require(tenant,workspace);
        transactions.executeWithoutResult(tx->{
            if(version(kind,tenant,workspace,id,version,Map.class).isPresent()) throw new CompositionService.OptimisticConcurrencyException();
            int changed=db.update("UPDATE "+kind.table+" SET version=?,definition=?::jsonb,lifecycle='DRAFT',revision=revision+1,updated_at=now() WHERE tenant_id=? AND workspace_id=? AND "+kind.idColumn+"=? AND revision=? AND lifecycle='PUBLISHED' AND version<>?",version,write(value),tenant,workspace,id,expected,version);
            if(changed!=1) throw new CompositionService.OptimisticConcurrencyException();
        });
    }

    private String write(Object value) { try {return json.writeValueAsString(value);} catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid composition document",e);} }
    private <T> T read(String value,Class<T> type) {try{return json.readValue(value,type);}catch(JsonProcessingException e){throw new IllegalStateException("Invalid persisted composition document",e);} }
}
