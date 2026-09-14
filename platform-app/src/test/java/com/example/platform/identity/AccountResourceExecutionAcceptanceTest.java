package com.example.platform.identity;

import com.example.platform.identity.app.*;
import com.example.platform.identity.api.dto.*;
import com.example.platform.identity.domain.*;
import com.example.platform.identity.infrastructure.RoleRepository;
import com.example.platform.shared.authorization.*;
import com.example.platform.shared.commercial.*;
import com.example.platform.entitlement.app.EntitlementService;
import com.example.platform.entitlement.domain.*;
import com.example.platform.render.api.context.*;
import com.example.platform.render.app.RenderAcceptanceContextService;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.*;
import com.example.platform.shared.web.TenantContext;
import static org.junit.jupiter.api.Assertions.*;

/** Real auth/application/PostgreSQL path. No provider rendering or settlement is claimed. */
class AccountResourceExecutionAcceptanceTest extends WorkspaceAuthorityHttpTest {
    String tenantB,ownerB,membershipB,account,wsA,wsB,projectA,projectB;
    AccountMembershipService accounts;
    @BeforeEach void provisionScope() throws Exception {
        accounts=context.getBean(AccountMembershipService.class);
        account=accounts.resolve("urn:media-platform:local-hmac",member,tenant).accountId();
        tenantB=context.getBean(TenantProjectService.class).createTenant(new CreateTenantRequest("Tenant B")).id();
        ownerB=user(tenantB);
        membershipB=accounts.createMembership(operator(),account,tenantB,"member-b-"+UUID.randomUUID(),UUID.randomUUID()+"@test.invalid","VIEWER").membershipId();
        wsA=create();wsB=readId(callScope(tenantB,ownerB,"POST","/api/workspaces",Map.of("name","B")),"id");
        assertEquals(200,add(wsA,member,"EDITOR",creator).statusCode());
        assertEquals(200,callScope(tenantB,ownerB,"POST","/api/workspaces/"+wsB+"/members",Map.of("userId",membershipB,"role","VIEWER")).statusCode());
        permissions(tenant,wsA,creator,creator,"READ","WRITE","CREATE");permissions(tenant,wsA,creator,member,"READ","WRITE");
        permissions(tenantB,wsB,ownerB,ownerB,"READ","WRITE","CREATE");permissions(tenantB,wsB,ownerB,membershipB,"READ");
        projectA=readId(callScope(tenant,creator,"POST","/api/identity/tenants/"+tenant+"/projects",Map.of("name","A Project","workspaceId",wsA)),"id");
        projectB=readId(callScope(tenantB,ownerB,"POST","/api/identity/tenants/"+tenantB+"/projects",Map.of("name","B Project","workspaceId",wsB)),"id");
        asScope(tenant,creator,()->context.getBean(com.example.platform.media.api.MediaAssets.class).register(tenant,projectA,"assets/a.bin","TEST","a.bin",1L,null));
        asScope(tenantB,ownerB,()->context.getBean(com.example.platform.media.api.MediaAssets.class).register(tenantB,projectB,"assets/b.bin","TEST","b.bin",1L,null));
    }
    CanonicalActor operator(){return CanonicalActor.system("system:identity-provisioning",null);}
    HttpResponse<String> callScope(String scope,String subject,String method,String path,Object body) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Authorization","Bearer "+token(subject,scope))
                .header("Content-Type","application/json").header("X-User-ID",creator);
        return http.send(req.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    String readId(HttpResponse<String> r,String field)throws Exception{assertEquals(200,r.statusCode(),r.body());return json.readTree(r.body()).get(field).asText();}
    void asScope(String scope,String subject,Runnable work){
        var req=new MockHttpServletRequest();req.setAttribute("auth.subject",subject);req.setAttribute("jwt.issuer","urn:media-platform:local-hmac");req.setAttribute("jwt.tenantId",scope);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));TenantContext.set(scope);
        try{work.run();}finally{RequestContextHolder.resetRequestAttributes();TenantContext.clear();}
    }
    void permissions(String scope,String ws,String ownerId,String target,String... keys){
        var roles=context.getBean(RoleRepository.class);var permissionService=context.getBean(PermissionService.class);
        var role=context.getBean(RoleService.class).createRole("scope-"+UUID.randomUUID(),"Explicit content role",null,Role.RoleScope.WORKSPACE);
        for(String key:keys){var permission=roles.findAllPermissions().stream().filter(p->key.equals(p.permissionKey())).findFirst()
                .orElseGet(()->permissionService.createPermission(key,key,null,"PROJECT"));roles.saveRolePermission(new RolePermission("rp-"+UUID.randomUUID(),role.id(),permission.id(),Instant.now()));}
        asScope(scope,ownerId,()->owner.assignRoleToMember(ws,memberId(ws,target),new AssignRoleRequest(role.roleKey(),ownerId)));
    }
    String entitlement(String scope){String id="grant-"+UUID.randomUUID();context.getBean(EntitlementService.class).execute(new EntitlementGrantCommand(
            EntitlementCommandType.GRANT,PrincipalRef.tenantScoped(scope,PrincipalType.ORGANIZATION,scope),id,"render.job.create",null,"ADMIN","scope-test",UUID.randomUUID().toString(),"system:acceptance","test","scope-test",Instant.now(),null,0));return id;}
    String submitPath(String scope,String project){return "/api/tenants/"+scope+"/projects/"+project+"/render-jobs";}
    Map<String,Object> submitBody(String project,String ws){return Map.of("projectId",project,"workspaceId",ws,"timelineSnapshotId","snapshot-acceptance","profile","default_1080p","allocationMode","TENANT_ORGANIZATION");}
    @Test void oneAccountTwoMembershipsAndImmutableAcceptedTaskContext() throws Exception {
        var a=json.readTree(callScope(tenant,member,"GET","/api/identity/session",null).body());
        var b=json.readTree(callScope(tenantB,member,"GET","/api/identity/session",null).body());
        assertEquals(account,a.get("accountId").asText());assertEquals(account,b.get("accountId").asText());
        assertEquals(member,a.get("actorId").asText());assertEquals(membershipB,b.get("actorId").asText());assertNotEquals(member,membershipB);
        assertEquals("MEMBER",a.get("roles").get(0).asText());assertEquals("VIEWER",b.get("roles").get(0).asText());
        assertEquals(200,callScope(tenant,member,"GET","/api/projects/"+projectA+"/assets",null).statusCode());
        assertEquals(200,callScope(tenantB,member,"GET","/api/projects/"+projectB+"/assets",null).statusCode());
        assertEquals(1,json.readTree(callScope(tenant,member,"GET","/api/identity/tenants/"+tenant+"/projects",null).body()).size());
        assertEquals(1,json.readTree(callScope(tenantB,member,"GET","/api/identity/tenants/"+tenantB+"/projects",null).body()).size());
        long jobs=count("select count(*) from render_job where tenant_id in (?,?)",tenant,tenantB);
        // Resource rights without feature entitlement cannot create a task.
        assertEquals(403,callScope(tenant,member,"POST",submitPath(tenant,projectA),submitBody(projectA,wsA)).statusCode());
        assertEquals(jobs,count("select count(*) from render_job where tenant_id in (?,?)",tenant,tenantB));
        String grant=entitlement(tenant);entitlement(tenantB);
        // Tenant B entitlement cannot turn VIEWER's READ into WRITE or charge tenant A.
        assertEquals(403,callScope(tenantB,member,"POST",submitPath(tenantB,projectB),submitBody(projectB,wsB)).statusCode());
        var response=callScope(tenant,member,"POST",submitPath(tenant,projectA),submitBody(projectA,wsA));String job=readId(response,"id");
        var contexts=context.getBean(ExecutionContextQueries.class);var fixed=contexts.get(tenant,projectA,job);
        assertEquals(account,fixed.accountId());assertEquals(member,fixed.tenantMembershipId());assertEquals(wsA,fixed.workspaceId());assertEquals(projectA,fixed.projectId());
        assertEquals(tenant,fixed.tenantId());assertEquals(tenant,fixed.consumptionPrincipalId());assertEquals("ORGANIZATION",fixed.consumptionPrincipalType());assertNull(fixed.allocationSourceId());
        assertTrue(fixed.commercialEvidence().stream().anyMatch(e->e.toString().contains(grant)));
        assertEquals(grant,fixed.entitlementGrantId());assertEquals(0,fixed.entitlementGrantVersion());
        // The next authenticated request selects B; accepted A facts are unchanged.
        assertEquals(200,callScope(tenantB,member,"GET","/api/identity/session",null).statusCode());assertEquals(fixed,contexts.get(tenant,projectA,job));
        assertEquals(403,callScope(tenantB,member,"GET",submitPath(tenant,projectA)+"/"+job+"/execution-context",null).statusCode());
        var reconstructed=new RenderAcceptanceContextService(context.getBean(com.example.platform.identity.api.project.ProjectScopeQueries.class),
                context.getBean(com.example.platform.identity.api.authorization.CanonicalActorResolver.class),context.getBean(com.example.platform.identity.api.authorization.AuthorizationDecisionPort.class),
                context.getBean(com.example.platform.entitlement.api.commercial.CommercialAdmissionPort.class),jdbc,context.getBean(com.fasterxml.jackson.databind.ObjectMapper.class),context.getBean(com.example.platform.entitlement.api.commercial.EntitlementBasisQueries.class));
        try(var worker=Executors.newSingleThreadExecutor()){
            assertEquals(fixed,worker.submit(()->reconstructed.get(tenant,projectA,job)).get(10,TimeUnit.SECONDS));
            assertEquals(fixed,worker.submit(()->reconstructed.get(tenant,projectA,job)).get(10,TimeUnit.SECONDS));
        }
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update render_execution_context set tenant_id=? where job_id=?",tenantB,job));assertEquals(fixed,contexts.get(tenant,projectA,job));
        assertEquals(jobs+1,count("select count(*) from render_job where tenant_id in (?,?)",tenant,tenantB));
        context.getBean(EntitlementService.class).execute(new EntitlementGrantCommand(EntitlementCommandType.REVOKE,
                PrincipalRef.tenantScoped(tenant,PrincipalType.ORGANIZATION,tenant),grant,null,null,"ADMIN","scope-test",UUID.randomUUID().toString(),"system:acceptance","revocation","scope-test",Instant.now(),null,0));
        assertEquals(fixed,contexts.get(tenant,projectA,job));
        assertEquals(403,callScope(tenant,member,"POST",submitPath(tenant,projectA),submitBody(projectA,wsA)).statusCode());
    }
    @Test void forgedScopesRevocationAndIndependentMembershipDisableFailClosed() throws Exception {
        entitlement(tenant);entitlement(tenantB);long before=count("select count(*) from render_job");long quota=count("select count(*) from quota_usage");
        var stale=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+submitPath(tenant,projectA)))
                .header("Authorization","Bearer "+token(member,tenant)).header("X-Tenant-ID",tenantB).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(submitBody(projectA,wsA)))).build();
        assertEquals(403,http.send(stale,HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(403,callScope(tenant,member,"POST",submitPath(tenant,projectA),submitBody(projectA,wsB)).statusCode());
        assertEquals(403,callScope(tenant,member,"POST",submitPath(tenant,projectB),submitBody(projectB,wsB)).statusCode());
        assertEquals(403,callScope(tenant,member,"GET","/api/projects/"+projectB+"/assets",null).statusCode());
        var unsupported=new HashMap<>(submitBody(projectA,wsA));unsupported.put("allocationMode","WORKSPACE_POOL");
        assertEquals(400,callScope(tenant,member,"POST",submitPath(tenant,projectA),unsupported).statusCode());
        accounts.setMembershipActive(operator(),account,tenant,false);
        assertEquals(403,callScope(tenant,member,"GET","/api/identity/session",null).statusCode());
        assertEquals(200,callScope(tenantB,member,"GET","/api/identity/session",null).statusCode());
        assertEquals(1,count("select count(*) from account where id=? and status='ACTIVE'",account));
        assertEquals(1,count("select count(*) from media_asset where project_id=?",projectA));
        accounts.setMembershipActive(operator(),account,tenant,true);assertEquals(204,call("DELETE",path(wsA)+"/members/"+member,null,creator).statusCode());
        assertEquals(403,callScope(tenant,member,"POST",submitPath(tenant,projectA),submitBody(projectA,wsA)).statusCode());
        assertEquals(before,count("select count(*) from render_job"));assertEquals(quota,count("select count(*) from quota_usage"));
    }
    @Test void accountLinkConcurrencyAndTransactionRollbackPreserveIdentity() throws Exception {
        var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)){
            Callable<String> create=()->{start.await();return accounts.provisionVerifiedAccount(operator(),"issuer-test","subject-test");};
            var a=workers.submit(create);var b=workers.submit(create);start.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,count("select count(*) from account where issuer='issuer-test' and subject='subject-test'"));
        assertEquals(membershipB,accounts.linkMembership(operator(),account,tenantB,membershipB).membershipId());
        assertThrows(RuntimeException.class,()->accounts.linkMembership(operator(),account,tenantB,ownerB));
        long before=count("select count(*) from account");
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(t->{accounts.provisionVerifiedAccount(operator(),"issuer-test","rolled-back");throw new IllegalStateException("after account insert");}));
        assertEquals(before,count("select count(*) from account"));assertEquals(member,accounts.resolve("urn:media-platform:local-hmac",member,tenant).membershipId());
        String ownerAccount=accounts.resolve("urn:media-platform:local-hmac",creator,tenant).accountId();
        assertThrows(RuntimeException.class,()->accounts.setMembershipActive(operator(),ownerAccount,tenant,false));
        assertEquals(200,call("GET",path(wsA),null,creator).statusCode());
    }

    @Test void duplicateMembershipCreationAndVerifiedIssuerKeysDoNotMergeByEmail() throws Exception {
        String c=context.getBean(TenantProjectService.class).createTenant(new CreateTenantRequest("C")).id();
        var start=new CountDownLatch(1);
        try(var threads=Executors.newFixedThreadPool(2)){
            Callable<String> create=()->{start.await();return accounts.createMembership(operator(),account,c,"same","same@test.invalid","VIEWER").membershipId();};
            var a=threads.submit(create);var b=threads.submit(create);start.countDown();assertEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
        }
        assertEquals(1,count("select count(*) from \"user\" where account_id=? and tenant_id=?",account,c));
        assertThrows(RuntimeException.class,()->accounts.createMembership(operator(),account,c,"same","same@test.invalid","ADMIN"));
        String one=accounts.provisionVerifiedAccount(operator(),"issuer-one","same-subject"),two=accounts.provisionVerifiedAccount(operator(),"issuer-two","same-subject");
        assertNotEquals(one,two);
        var first=accounts.createMembership(operator(),one,c,"first","same@test.invalid","MEMBER");
        var second=accounts.createMembership(operator(),two,c,"second","same@test.invalid","MEMBER");
        assertNotEquals(first.membershipId(),second.membershipId());
        assertEquals(one,accounts.resolve("issuer-one","same-subject",c).accountId());
        assertEquals(two,accounts.resolve("issuer-two","same-subject",c).accountId());
    }
    @Test void resourceAndTaskFailuresRollBackWithoutIdentityOrQuotaSideEffects() throws Exception {
        long usersBefore=count("select count(*) from \"user\""),accountsBefore=count("select count(*) from account");
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(t->{
            String a=accounts.provisionVerifiedAccount(operator(),"rollback-issuer",UUID.randomUUID().toString());
            accounts.createMembership(operator(),a,tenant,"rollback","same@test.invalid","MEMBER");throw new IllegalStateException("after identity writes");
        }));
        assertEquals(usersBefore,count("select count(*) from \"user\""));assertEquals(accountsBefore,count("select count(*) from account"));
        long projectsBefore=count("select count(*) from project where tenant_id=?",tenant);
        asScope(tenant,creator,()->assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(t->{
            context.getBean(TenantProjectService.class).createProject(tenant,new CreateProjectRequest("rollback",null,wsA));throw new IllegalStateException("after project write");
        })));
        assertEquals(projectsBefore,count("select count(*) from project where tenant_id=?",tenant));
        entitlement(tenant);long jobsBefore=count("select count(*) from render_job where tenant_id=?",tenant),quotaBefore=count("select count(*) from quota_usage");
        String fault="scope_fault_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create function "+fault+"() returns trigger language plpgsql as $$ begin raise exception 'controlled context persistence failure'; return new; end $$");
        try {
            jdbc.execute("create trigger "+fault+" before insert on render_execution_context for each row when (new.tenant_id='"+tenant+"') execute function "+fault+"()");
            assertEquals(500,callScope(tenant,member,"POST",submitPath(tenant,projectA),submitBody(projectA,wsA)).statusCode());
            assertEquals(jobsBefore,count("select count(*) from render_job where tenant_id=?",tenant));
            assertEquals(0,count("select count(*) from render_execution_context where tenant_id=?",tenant));assertEquals(quotaBefore,count("select count(*) from quota_usage"));
        } finally {jdbc.execute("drop trigger if exists "+fault+" on render_execution_context");jdbc.execute("drop function "+fault+"()");}
        String job=readId(callScope(tenant,member,"POST",submitPath(tenant,projectA),submitBody(projectA,wsA)),"id");
        assertEquals(tenant,context.getBean(ExecutionContextQueries.class).get(tenant,projectA,job).consumptionPrincipalId());
    }
    @Test void exhaustedQuotaDoesNotFallbackToAnotherTenant() throws Exception {
        entitlement(tenant);entitlement(tenantB);var now=Instant.now();var month=java.time.YearMonth.from(now.atZone(java.time.ZoneOffset.UTC));
        Instant start=month.atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),end=month.plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        context.getBean(com.example.platform.entitlement.app.QuotaUsageAuthority.class).execute(new QuotaUsageCommand(
                PrincipalRef.tenantScoped(tenant,PrincipalType.ORGANIZATION,tenant),"render.job.create",start,end,10000,10000,
                "fill-"+UUID.randomUUID(),QuotaOperationKind.CONSUMPTION,"scope-test","acceptance capacity control",now));
        long jobsBefore=count("select count(*) from render_job"),operations=count("select count(*) from quota_usage_operation");
        assertEquals(403,callScope(tenant,member,"POST",submitPath(tenant,projectA),submitBody(projectA,wsA)).statusCode());
        assertEquals(jobsBefore,count("select count(*) from render_job"));assertEquals(operations,count("select count(*) from quota_usage_operation"));
        assertEquals(0,count("select count(*) from quota_usage where tenant_id=?",tenantB));
    }
    @Test void migrationPreservesAmbiguousHistoryAndOnlyConvertsKnownScopeEncodings() {
        String schema="scope_migration_"+UUID.randomUUID().toString().replace("-","");
        var flyway=org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("1").load();flyway.migrate();
        jdbc.update("insert into "+schema+".tenant(id,name,status,created_at) values ('tenant-old','Old','ACTIVE',now())");
        jdbc.update("insert into "+schema+".\"user\"(id,tenant_id,username,email,role,status,created_at) values ('member-old','tenant-old','old','same@test.invalid','MEMBER','ACTIVE',now())");
        jdbc.update("insert into "+schema+".project(id,tenant_id,name,status,created_at) values ('project-old','tenant-old','Old','ACTIVE',now())");
        jdbc.update("insert into "+schema+".workspace(id,tenant_id,name,status,created_at,updated_at) values ('workspace-old','tenant-old','Old','ACTIVE',now(),now())");
        jdbc.update("insert into "+schema+".user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,created_at) values ('role-old','tenant-old','project-old','member-old','role',now()),('tenant-role','tenant-old','tenant-old','member-old','role',now())");
        org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
        assertEquals(0,count("select count(*) from "+schema+".account"));
        assertEquals(1,count("select count(*) from "+schema+".\"user\" where id='member-old' and account_id is null"));
        assertEquals(1,count("select count(*) from "+schema+".project where id='project-old' and workspace_id is null"));
        assertEquals(1,count("select count(*) from "+schema+".user_role_assignment where id='role-old' and project_id='project-old' and workspace_id is null"));
        assertEquals(1,count("select count(*) from "+schema+".user_role_assignment where id='tenant-role' and project_id is null and workspace_id is null"));
        assertEquals(0,org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate().migrationsExecuted);
    }

    @Test void subscriptionBeneficiaryIsTenantAndBillingManagementDoesNotGrantContent() throws Exception {
        var billing=context.getBean(com.example.platform.billing.app.SubscriptionBillingService.class);
        String plan="plan-"+UUID.randomUUID();billing.createPlan(plan,"Scoped",null,"MONTHLY",100,"USD",Map.of());
        var request=new HashMap<String,Object>();request.put("tenantId",tenant);request.put("userId",creator);request.put("contractId","sub-"+UUID.randomUUID());
        request.put("planKey",plan);request.put("periodDays",30);request.put("idempotencyKey",UUID.randomUUID().toString());request.put("actor",creator);request.put("reason","test");request.put("traceId","test");request.put("effectiveAt",Instant.now().toString());
        // A Workspace OWNER is not thereby the tenant's billing manager.
        assertEquals(403,callScope(tenant,creator,"POST","/api/billing/subscriptions",request).statusCode());
        String subject="billing-subject-"+UUID.randomUUID();String a=accounts.provisionVerifiedAccount(operator(),"urn:media-platform:local-hmac",subject);
        var admin=accounts.createMembership(operator(),a,tenant,"billing-admin","billing@test.invalid","ADMIN");
        assertEquals(403,callScope(tenant,subject,"GET","/api/identity/admin/tenants",null).statusCode());
        accounts.setPlatformAdministrator(operator(),a,true);
        assertEquals(200,callScope(tenant,subject,"GET","/api/identity/admin/tenants",null).statusCode());
        var response=callScope(tenant,subject,"POST","/api/billing/subscriptions",request);assertEquals(200,response.statusCode(),response.body());
        assertEquals(tenant,json.readTree(response.body()).get("beneficiaryTenantId").asText());
        assertEquals(1,count("select count(*) from subscription_contract where tenant_id=? and subject_type='ORGANIZATION' and subject_id=?",tenant,tenant));
        assertEquals(1,count("select count(*) from subscription_command where tenant_id=? and actor=?",tenant,admin.membershipId()));
        assertEquals(403,callScope(tenant,subject,"GET","/api/projects/"+projectA+"/assets",null).statusCode());
        assertEquals(200,callScope(tenant,member,"GET","/api/billing/subscriptions/current",null).statusCode());
    }
    @Test void projectRoleBindingsStayProjectScopedAndMembershipRevocationRemovesThem() throws Exception {
        assertEquals(200,add(wsA,outsider,"VIEWER",creator).statusCode());
        String second=readId(callScope(tenant,creator,"POST","/api/identity/tenants/"+tenant+"/projects",Map.of("name","Second","workspaceId",wsA)),"id");
        var roles=context.getBean(RoleRepository.class);var role=context.getBean(RoleService.class).createRole("project-"+UUID.randomUUID(),"Project read",null,Role.RoleScope.WORKSPACE);
        var read=roles.findAllPermissions().stream().filter(p->"READ".equals(p.permissionKey())).findFirst().orElseThrow();
        roles.saveRolePermission(new RolePermission("rp-"+UUID.randomUUID(),role.id(),read.id(),Instant.now()));
        jdbc.update("insert into user_role_assignment(id,tenant_id,project_id,user_id,role_id,created_at) values (?,?,?,?,?,now())","pr-"+UUID.randomUUID(),tenant,projectA,outsider,role.id());
        // An independent tenant READ binding must not expand into content access.
        jdbc.update("insert into user_role_assignment(id,tenant_id,user_id,role_id,created_at) values (?,?,?,?,now())","tr-"+UUID.randomUUID(),tenant,outsider,role.id());
        assertEquals(200,callScope(tenant,outsider,"GET","/api/projects/"+projectA+"/assets",null).statusCode());
        assertEquals(403,callScope(tenant,outsider,"GET","/api/projects/"+second+"/assets",null).statusCode());
        assertEquals(204,call("DELETE",path(wsA)+"/members/"+outsider,null,creator).statusCode());
        assertEquals(0,count("select count(*) from user_role_assignment where user_id=? and project_id=?",outsider,projectA));
        assertEquals(1,count("select count(*) from user_role_assignment where user_id=? and workspace_id is null and project_id is null",outsider));
        assertEquals(200,add(wsA,outsider,"VIEWER",creator).statusCode());
        assertEquals(403,callScope(tenant,outsider,"GET","/api/projects/"+projectA+"/assets",null).statusCode());
        assertEquals(1,count("select count(*) from media_asset where project_id=?",projectA));
    }

    @Test void unresolvedProjectRequiresExplicitIdempotentOwnerMapping() throws Exception {
        String old="old-project-"+UUID.randomUUID();
        jdbc.update("insert into project(id,tenant_id,name,status,created_at) values (?,?,?,'ACTIVE',now())",old,tenant,"Unresolved history");
        long jobs=count("select count(*) from render_job");entitlement(tenant);
        assertEquals(409,callScope(tenant,member,"POST",submitPath(tenant,old),submitBody(old,wsA)).statusCode());
        assertEquals(jobs,count("select count(*) from render_job"));assertEquals(1,count("select count(*) from project where id=? and workspace_id is null",old));
        var scopes=context.getBean(ProjectScopeService.class);
        assertThrows(RuntimeException.class,()->scopes.bindUnmappedProject(operator(),tenant,old,wsB));
        assertEquals(wsA,scopes.bindUnmappedProject(operator(),tenant,old,wsA).workspaceId());
        assertEquals(wsA,scopes.bindUnmappedProject(operator(),tenant,old,wsA).workspaceId());
        assertEquals(200,callScope(tenant,member,"GET","/api/identity/projects/"+old,null).statusCode());
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("update project set workspace_id=? where id=?",wsB,old));
        assertEquals(wsA,jdbc.queryForObject("select workspace_id from project where id=?",String.class,old));
    }

    @Test void projectImportCreatesOnlyInExplicitAuthorizedWorkspace() throws Exception {
        Map<String,Object> payload=Map.of("schemaVersion","project-export-v1","exportMode","metadata_only","project",Map.of("projectId","historical-source","name","Imported"));
        var body=new HashMap<String,Object>();body.put("payload",payload);body.put("createNewProject",true);body.put("workspaceId",wsA);body.put("assetImportPolicy","metadata_only");
        var response=callScope(tenant,creator,"POST","/api/identity/tenants/"+tenant+"/project-imports",body);
        String project=readId(response,"projectId");assertEquals(wsA,jdbc.queryForObject("select workspace_id from project where id=?",String.class,project));
        long before=count("select count(*) from project where tenant_id=?",tenant);body.put("workspaceId",wsB);
        assertEquals(403,callScope(tenant,creator,"POST","/api/identity/tenants/"+tenant+"/project-imports",body).statusCode());
        assertEquals(before,count("select count(*) from project where tenant_id=?",tenant));
    }
}
