package com.example.platform.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.domain.*;
import com.example.platform.shared.commercial.*;
import com.example.platform.render.api.context.*;

/** Normal regressions promoted from the saved independent PostgreSQL/HTTP probes. */
class IdentityScopeCorrectionsTest extends AccountResourceExecutionAcceptanceTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void correctionGrantRace(boolean revoke,boolean incremental) throws Exception { grantRace(revoke,incremental); }
    void grantRace(boolean revoke,boolean incremental) throws Exception {
        String grant="grant-"+UUID.randomUUID();
        context.getBean(EntitlementService.class).execute(new EntitlementGrantCommand(EntitlementCommandType.GRANT,
            PrincipalRef.tenantScoped(tenant,PrincipalType.ORGANIZATION,tenant),grant,"render.job.create",null,"ADMIN","scope-test",UUID.randomUUID().toString(),"system:acceptance","test","ir",Instant.now(),Instant.now().plusSeconds(3600),0));
        long jobs=count("select count(*) from render_job where tenant_id=?",tenant);
        long usage=count("select count(*) from quota_usage_operation");
        long lock=ThreadLocalRandom.current().nextLong(1000000,9999999);
        String hook="ir_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create function "+hook+"() returns trigger language plpgsql as $$ begin perform pg_advisory_xact_lock("+lock+"); return new; end $$");
        jdbc.execute("create trigger "+hook+" before insert on render_job for each row when (new.tenant_id='"+tenant+"') execute function "+hook+"()");
        try(var blocker=jdbc.getDataSource().getConnection();var pool=Executors.newSingleThreadExecutor()) {
            blocker.createStatement().execute("select pg_advisory_lock("+lock+")");
            var body=new HashMap<String,Object>(submitBody(projectA,wsA));body.put("tenantId",tenant);
            var pending=pool.submit(()->callScope(tenant,member,"POST",submitPath(tenant,projectA)+(incremental?"/incremental/submit":""),body));
            try {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
                while(count("select count(*) from pg_locks where locktype='advisory' and objid=? and not granted",lock)==0 && System.nanoTime()<deadline) Thread.sleep(20);
                assertEquals(1,count("select count(*) from pg_locks where locktype='advisory' and objid=? and not granted",lock),"HTTP task must reach insert AFTER admission");
                Instant changedAt=Instant.now();
                context.getBean(EntitlementService.class).execute(new EntitlementGrantCommand(
                    revoke?EntitlementCommandType.REVOKE:EntitlementCommandType.EXTEND,
                    PrincipalRef.tenantScoped(tenant,PrincipalType.ORGANIZATION,tenant),grant,null,null,"ADMIN","scope-test",UUID.randomUUID().toString(),"system:acceptance","independent concurrent change","ir",changedAt,revoke?null:changedAt.plusSeconds(7200),0));
                assertEquals(1,count("select count(*) from entitlement_grant where id=? and version=1",grant));
                blocker.createStatement().execute("select pg_advisory_unlock("+lock+")");
                var result=pending.get(20,TimeUnit.SECONDS);
                if(revoke) {
                    assertEquals(403,result.statusCode(),result.body());
                    assertEquals(jobs,count("select count(*) from render_job where tenant_id=?",tenant));
                    assertEquals(0,count("select count(*) from render_execution_context where tenant_id=?",tenant));
                } else {
                    assertEquals(200,result.statusCode(),result.body());
                    String job=jdbc.queryForObject("select id from render_job where tenant_id=?",String.class,tenant);var fixed=context.getBean(ExecutionContextQueries.class).get(tenant,projectA,job);
                    assertEquals(1,fixed.entitlementGrantVersion(),"Final owner admission uses extended version");
                    assertTrue(fixed.acceptedAt().isAfter(changedAt),"Repeated admission time must follow the extension");
                    assertEquals(grant,fixed.entitlementGrantId());
                    assertTrue(fixed.commercialEvidence().stream().anyMatch(e -> "Entitlement".equals(e.authority()) && "GRANT".equals(e.evidenceType()) && grant.equals(e.evidenceId())));
                    assertEquals(jobs+1,count("select count(*) from render_job where tenant_id=?",tenant));
                    assertEquals(1,count("select count(*) from render_execution_context where job_id=?",job));
                    System.out.println("IR_GRANT_RACE persisted version="+fixed.entitlementGrantVersion()+" accepted="+fixed.acceptedAt()+" mutation="+changedAt);
                }
                assertEquals(usage,count("select count(*) from quota_usage_operation"));
            } finally { blocker.createStatement().execute("select pg_advisory_unlock("+lock+")"); }
        } finally {jdbc.execute("drop trigger "+hook+" on render_job");jdbc.execute("drop function "+hook+"()");}
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "false,valid", "true,valid", "false,mode", "true,mode", "false,workspace", "true,workspace",
        "false,both", "true,both", "false,canonical", "true,canonical", "false,omitted", "true,omitted",
        "false,malformedMode", "true,malformedMode", "false,malformedWorkspace", "true,malformedWorkspace",
        "false,blankMode", "true,blankMode", "false,blankWorkspace", "true,blankWorkspace"})
    void correctionHttpContract(boolean incremental,String scenario) throws Exception {
        String grant=entitlement(tenant);
        var body=new HashMap<String,Object>(submitBody(projectA,wsA));body.put("tenantId",tenant);
        if(scenario.equals("valid"))body.remove("workspaceId");
        if(scenario.equals("omitted")){body.remove("workspaceId");body.remove("allocationMode");}
        if(scenario.equals("mode")||scenario.equals("both"))body.put("allocationMode","WORKSPACE_POOL");
        if(scenario.equals("workspace")||scenario.equals("both"))body.put("workspaceId",wsB);
        if(scenario.equals("malformedMode"))body.put("allocationMode",Map.of("value","TENANT_ORGANIZATION"));
        if(scenario.equals("malformedWorkspace"))body.put("workspaceId",List.of(wsA));
        if(scenario.equals("blankMode"))body.put("allocationMode","");
        if(scenario.equals("blankWorkspace"))body.put("workspaceId","");
        long jobs=count("select count(*) from render_job where tenant_id=?",tenant);
        long contexts=count("select count(*) from render_execution_context where tenant_id=?",tenant);
        long usage=count("select count(*) from quota_usage_operation"),quota=count("select count(*) from quota_usage");
        long commands=count("select count(*) from entitlement_command_audit");
        var result=callScope(tenant,member,"POST",submitPath(tenant,projectA)+(incremental?"/incremental/submit":""),body);
        boolean valid=Set.of("valid","canonical","omitted").contains(scenario);
        if(valid) {
            assertEquals(200,result.statusCode(),result.body());
            String job=jdbc.queryForObject("select id from render_job where tenant_id=?",String.class,tenant);
            var fixed=context.getBean(ExecutionContextQueries.class).get(tenant,projectA,job);
            assertEquals(wsA,fixed.workspaceId());assertEquals("TENANT_ORGANIZATION",fixed.allocationMode());
            assertEquals(grant,fixed.entitlementGrantId());assertEquals(0,fixed.entitlementGrantVersion());
            assertEquals(jobs+1,count("select count(*) from render_job where tenant_id=?",tenant));
            assertEquals(contexts+1,count("select count(*) from render_execution_context where tenant_id=?",tenant));
        } else {
            assertEquals(Set.of("workspace","blankWorkspace").contains(scenario)?403:400,result.statusCode(),result.body());
            assertEquals(jobs,count("select count(*) from render_job where tenant_id=?",tenant));
            assertEquals(contexts,count("select count(*) from render_execution_context where tenant_id=?",tenant));
        }
        assertEquals(usage,count("select count(*) from quota_usage_operation"));
        assertEquals(quota,count("select count(*) from quota_usage"));
        assertEquals(commands,count("select count(*) from entitlement_command_audit"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"collision,false", "collision,true", "workspace,false", "workspace,true", "project,false", "project,true", "conflict,false", "conflict,true"})
    void correctionHistoricalMigration(String kind,boolean missingTenant) throws Exception {
        String schema="correction_"+UUID.randomUUID().toString().replace("-","");
        var config=org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        config.target("1").load().migrate();
        String role=jdbc.queryForObject("select role_id from user_role_assignment where user_id=? and workspace_id=? limit 1",String.class,member,wsA);
        jdbc.update("insert into "+schema+".tenant(id,name,status,created_at) values (?, 'Legacy','ACTIVE',now()), (?, 'Foreign','ACTIVE',now())",tenant,tenantB);
        jdbc.update("insert into "+schema+".\"user\"(id,tenant_id,username,email,role,status,created_at) values (?,?,'legacy','legacy@test.invalid','MEMBER','ACTIVE',now())",member,tenant);
        String id=kind.equals("project")?projectA:wsA;
        if(!kind.equals("project"))jdbc.update("insert into "+schema+".workspace(id,tenant_id,name,status,created_at,updated_at) values (?,?,'Legacy','ACTIVE',now(),now())",wsA,kind.equals("conflict")?tenantB:tenant);
        if(!kind.equals("workspace"))jdbc.update("insert into "+schema+".project(id,tenant_id,name,status,created_at) values (?,?,'Legacy','ACTIVE',now())",id,tenant);
        jdbc.update("insert into "+schema+".role select * from public.role where id=?",role);
        jdbc.update("insert into "+schema+".permission select p.* from public.permission p join public.role_permission rp on p.id=rp.permission_id where rp.role_id=?",role);
        jdbc.update("insert into "+schema+".role_permission select * from public.role_permission where role_id=?",role);
        String assignment="correction-"+UUID.randomUUID();
        jdbc.update("insert into "+schema+".user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values (?,?,?,?,?,now())",assignment,missingTenant?null:tenant,id,member,role);
        config.target("2").load().migrate();
        String before=jdbc.queryForObject("select row_to_json(a)::text from "+schema+".user_role_assignment a where id=?",String.class,assignment);
        config.target(org.flywaydb.core.api.MigrationVersion.LATEST).load().migrate();
        String after=jdbc.queryForObject("select (to_jsonb(a)-'scope_unresolved')::text from "+schema+".user_role_assignment a where id=?",String.class,assignment);
        assertEquals(json.readTree(before),json.readTree(after),"V3 must preserve every V2 field");
        boolean unresolved=Set.of("collision","conflict").contains(kind);
        assertEquals(unresolved,jdbc.queryForObject("select scope_unresolved from "+schema+".user_role_assignment where id=?",Boolean.class,assignment));
        jdbc.update("delete from user_role_assignment where user_id=? and workspace_id=?",member,wsA);
        assertEquals(403,callScope(tenant,member,"GET","/api/identity/projects/"+projectA,null).statusCode());
        jdbc.update("insert into user_role_assignment select * from "+schema+".user_role_assignment where id=?",assignment);
        assertEquals(unresolved?403:200,callScope(tenant,member,"GET","/api/identity/projects/"+projectA,null).statusCode());
        String other=readId(callScope(tenant,creator,"POST","/api/identity/tenants/"+tenant+"/projects",Map.of("name","Other","workspaceId",wsA)),"id");
        assertEquals(kind.equals("workspace")?200:403,callScope(tenant,member,"GET","/api/identity/projects/"+other,null).statusCode());
        if(unresolved) {
            context.getBean(com.example.platform.identity.infrastructure.RoleRepository.class).deleteMemberAssignments(wsA,member);
            assertEquals(1,count("select count(*) from user_role_assignment where id=? and scope_unresolved",assignment));
        }
        assertEquals(0,config.load().migrate().migrationsExecuted);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void correctionContextFailureRollsBackBothPaths(boolean incremental) throws Exception {
        entitlement(tenant);
        long jobs=count("select count(*) from render_job where tenant_id=?",tenant);
        long quota=count("select count(*) from quota_usage_operation");
        String hook="correction_fail_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create function "+hook+"() returns trigger language plpgsql as $$ begin raise exception 'controlled context failure'; end $$");
        jdbc.execute("create trigger "+hook+" before insert on render_execution_context for each row when (new.tenant_id='"+tenant+"') execute function "+hook+"()");
        var body=new HashMap<String,Object>(submitBody(projectA,wsA));body.put("tenantId",tenant);
        String path=submitPath(tenant,projectA)+(incremental?"/incremental/submit":"");
        try {
            assertEquals(500,callScope(tenant,member,"POST",path,body).statusCode());
            assertEquals(jobs,count("select count(*) from render_job where tenant_id=?",tenant));
            assertEquals(0,count("select count(*) from render_execution_context where tenant_id=?",tenant));
            assertEquals(quota,count("select count(*) from quota_usage_operation"));
        } finally {jdbc.execute("drop trigger "+hook+" on render_execution_context");jdbc.execute("drop function "+hook+"()");}
        assertEquals(200,callScope(tenant,member,"POST",path,body).statusCode());
        assertEquals(jobs+1,count("select count(*) from render_job where tenant_id=?",tenant));
        assertEquals(1,count("select count(*) from render_execution_context where tenant_id=?",tenant));
    }

    @Test void correctionFinalAdmissionLocksSerializeLaterRevoke() throws Exception {
        String grant=entitlement(tenant);
        long lock=ThreadLocalRandom.current().nextLong(1000000,9999999);
        String hook="correction_lock_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create function "+hook+"() returns trigger language plpgsql as $$ begin perform pg_advisory_xact_lock("+lock+"); return new; end $$");
        jdbc.execute("create trigger "+hook+" before insert on render_execution_context for each row when (new.tenant_id='"+tenant+"') execute function "+hook+"()");
        try(var blocker=jdbc.getDataSource().getConnection();var pool=Executors.newFixedThreadPool(2)) {
            blocker.createStatement().execute("select pg_advisory_lock("+lock+")");
            var pending=pool.submit(()->callScope(tenant,member,"POST",submitPath(tenant,projectA),submitBody(projectA,wsA)));
            try {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
                while(count("select count(*) from pg_locks where locktype='advisory' and objid=? and not granted",lock)==0 && System.nanoTime()<deadline) Thread.sleep(20);
                assertEquals(1,count("select count(*) from pg_locks where locktype='advisory' and objid=? and not granted",lock),"Context insert is after final admission");
                var mutation=pool.submit(()->context.getBean(EntitlementService.class).execute(new EntitlementGrantCommand(
                    EntitlementCommandType.REVOKE,PrincipalRef.tenantScoped(tenant,PrincipalType.ORGANIZATION,tenant),grant,
                    null,null,"ADMIN","scope-test",UUID.randomUUID().toString(),"system:acceptance","later revoke","correction",Instant.now(),null,0)));
                deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
                String waiting="select count(*) from pg_stat_activity where wait_event_type='Lock' and query like 'UPDATE entitlement_grant%'";
                while(count(waiting)==0 && System.nanoTime()<deadline) Thread.sleep(20);
                assertEquals(1,count(waiting),"Real grant UPDATE must wait for acceptance commit");
                blocker.createStatement().execute("select pg_advisory_unlock("+lock+")");
                String job=readId(pending.get(20,TimeUnit.SECONDS),"id");
                mutation.get(20,TimeUnit.SECONDS);
                var fixed=context.getBean(ExecutionContextQueries.class).get(tenant,projectA,job);
                assertEquals(0,fixed.entitlementGrantVersion());
                assertEquals(1,count("select count(*) from entitlement_grant where id=? and version=1 and grant_status='REVOKED'",grant));
                assertEquals(403,callScope(tenant,member,"POST",submitPath(tenant,projectA),submitBody(projectA,wsA)).statusCode());
                assertEquals(fixed,context.getBean(ExecutionContextQueries.class).get(tenant,projectA,job));
                assertEquals(1,count("select count(*) from render_job where tenant_id=?",tenant));
                assertEquals(1,count("select count(*) from render_execution_context where tenant_id=?",tenant));
            } finally {blocker.createStatement().execute("select pg_advisory_unlock("+lock+")");}
        } finally {jdbc.execute("drop trigger "+hook+" on render_execution_context");jdbc.execute("drop function "+hook+"()");}
    }
}
