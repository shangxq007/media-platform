package com.example.platform;

import com.example.platform.auditcontract.api.*;
import com.example.platform.audit.app.*;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.contract.StorageUriReferenceContributor;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE, properties={
        "app.outbox.dispatcher-enabled=false", "storage.s3.enabled=true"})
@ActiveProfiles({"test","preview"})
class AuditAssetOwnershipTest extends PostgresTestContainerSupport {
    @Autowired AuditPort audit;
    @Autowired AdminAuditPublisher admin;
    @Autowired ApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @AfterEach void clear(){MDC.clear();TenantContext.clear();}

    @Test void assembledOwnerPortsAndStorageReferencesHaveNoRetiredPathEvenWithS3Enabled() {
        assertEquals(1,context.getBeansOfType(AuditPort.class).size());
        assertEquals(1,context.getBeansOfType(AdminAuditPublisher.class).size());
        assertInstanceOf(AuditPortAdapter.class,audit);
        assertInstanceOf(AdminAuditPublisherImpl.class,admin);
        assertTrue(context.getBeansOfType(StorageUriReferenceContributor.class).values().stream()
                .anyMatch(c->c.contributorId().equals("delivery")));
        for(String retired:new String[]{"com.example.platform.shared.audit.AuditPort",
                "com.example.platform.shared.audit.AdminAuditPublisher",
                "com.example.platform.shared.asset.StorageUriReferenceContributor",
                "com.example.platform.shared.asset.StorageUriReferenceHit",
                "com.example.platform.shared.asset.AssetDownloadUrlPort",
                "com.example.platform.storage.infrastructure.S3AssetDownloadUrlPort"})
            assertThrows(ClassNotFoundException.class,()->Class.forName(retired),retired);
        assertNotNull(context.getBean(com.example.platform.storage.domain.BlobStorage.class));
    }
    @Test void serverPrincipalWinsOverPayloadAndAdminActorHintsAndTenantNeverBecomesActor() {
        String resource="ep27b-"+UUID.randomUUID();
        MDC.put("principal","server-actor");TenantContext.set("tenant-not-actor");
        audit.record("USER","EP27B_USER","CONFIG","test",resource,Map.of("actorId","forged"));
        admin.publish("forged","ADMIN","EP27B_ADMIN","test",resource,"tenant-not-actor","SUCCESS");
        assertEquals(java.util.List.of("server-actor","server-actor"),jdbc.queryForList(
                "select actor_id from audit_records where resource_id=? order by action",String.class,resource));
        MDC.clear();audit.record("SYSTEM","EP27B_SYSTEM","CONFIG","test",resource,Map.of());
        assertEquals("system",jdbc.queryForObject("select actor_id from audit_records where resource_id=? and action='EP27B_SYSTEM'",String.class,resource));
    }
    @Test void requiredAuditFailureAndOuterRollbackLeaveNoBusinessOrAuditEffectThenRetrySucceeds() {
        String resource="ep27b-"+UUID.randomUUID();
        jdbc.execute("create table if not exists ep27b_business_probe (id text primary key)");
        jdbc.execute("CREATE FUNCTION ep27b_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='EP27B_REQUIRED' THEN RAISE EXCEPTION 'controlled audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER ep27b_reject_audit BEFORE INSERT ON audit_records FOR EACH ROW EXECUTE FUNCTION ep27b_reject_audit()");
        TransactionTemplate tx=new TransactionTemplate(transactions);
        Runnable operation=()->tx.executeWithoutResult(status->{
            jdbc.update("insert into ep27b_business_probe values (?)",resource);
            audit.record("SYSTEM","EP27B_REQUIRED","CONFIG","test",resource,Map.of());
        });
        try {
            assertThrows(org.jooq.exception.DataAccessException.class,operation::run);
            assertEquals(0,jdbc.queryForObject("select count(*) from ep27b_business_probe where id=?",Integer.class,resource));
            assertEquals(0,jdbc.queryForObject("select count(*) from audit_records where resource_id=?",Integer.class,resource));
        } finally {
            jdbc.execute("DROP TRIGGER ep27b_reject_audit ON audit_records");
            jdbc.execute("DROP FUNCTION ep27b_reject_audit()");
        }
        tx.executeWithoutResult(status->{audit.record("SYSTEM","EP27B_ROLLBACK","CONFIG","test",resource,Map.of());status.setRollbackOnly();});
        assertEquals(0,jdbc.queryForObject("select count(*) from audit_records where resource_id=?",Integer.class,resource));
        operation.run();
        assertEquals(1,jdbc.queryForObject("select count(*) from ep27b_business_probe where id=?",Integer.class,resource));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_records where resource_id=?",Integer.class,resource));
    }
    @Test void missingRequiredAuditInfrastructureCannotSelectAFallback() {
        new ApplicationContextRunner().withBean(AuditPortAdapter.class).run(c->assertNotNull(c.getStartupFailure()));
    }
}
