package com.example.platform;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.usage.api.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** Actual public port, Spring transactions, production stores and migrated PostgreSQL schema. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.NONE, properties={
        "app.security.enabled=false", "app.identity.api-key-auth-enabled=false", "app.outbox.dispatcher-enabled=false"})
@ActiveProfiles({"test","preview"})
class UsageIngestionTransactionTest extends PostgresTestContainerSupport {
    @Autowired ObservedRuntimeUsageEmissionPort emission;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private ObservedRuntimeUsage observation(String tenant, String key, long quantity) {
        Instant at=Instant.parse("2026-09-21T00:00:00Z");
        return ObservedRuntimeUsage.observe(tenant,"project",new CanonicalActorRef("actor","USER"),
                OperationRef.of("operation","attempt"),"execution",new ProviderRef("controlled-provider"),
                "test",UsageDimension.DURATION,new UsageQuantity(quantity,UsageUnit.MILLISECONDS),
                RuntimeOutcome.FAILED,at,at,at,UsageProvenance.REPORTED,"ep28a-test","source-ref","trace",key);
    }
    private void counts(String tenant,int expected) {
        assertEquals(expected,jdbc.queryForObject("select count(*) from observed_runtime_usage where tenant_id=?",Integer.class,tenant));
        assertEquals(expected,jdbc.queryForObject("select count(*) from outbox_events where payload::jsonb->>'tenantId'=? and event_type='RUNTIME_USAGE_OBSERVED'",Integer.class,tenant));
        assertEquals(0,jdbc.queryForObject("select count(*) from billable_usage where tenant_id=?",Integer.class,tenant),
                "ingestion must not invent accounting effects");
    }
    @Test void committedObservationReplayAndConflictingContentPreserveOriginalFact() {
        String tenant="ep28a-"+UUID.randomUUID();
        var first=emission.emit(observation(tenant,"replay",0)); // consumed failed/zero observations remain valid
        assertEquals(first,emission.emit(observation(tenant,"replay",0)));
        assertThrows(IllegalStateException.class,()->emission.emit(observation(tenant,"replay",1)));
        counts(tenant,1);
        assertEquals(0L,jdbc.queryForObject("select quantity_base_units from observed_runtime_usage where tenant_id=?",Long.class,tenant));
    }
    @Test void conflictingTenantContextRejectsBeforeAnyDurableEffect() {
        String tenant="ep28a-"+UUID.randomUUID();
        try {
            com.example.platform.shared.web.TenantContext.set("other");
            assertThrows(IllegalArgumentException.class,()->emission.emit(observation(tenant,"scope",1)));
            counts(tenant,0);
        } finally { com.example.platform.shared.web.TenantContext.clear(); }
        emission.emit(observation(tenant,"scope",1));
        counts(tenant,1);
    }
    @Test void outboxFailureRollsBackObservationAndSameIdentityCanRetry() {
        String tenant="ep28a-outbox-failure";
        var observation=observation(tenant,"failure",12);
        jdbc.execute("""
            CREATE FUNCTION ep28a_reject_outbox() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN IF NEW.payload::jsonb->>'tenantId'='ep28a-outbox-failure' THEN RAISE EXCEPTION 'controlled EP28A outbox rejection'; END IF;
            RETURN NEW; END $$
            """);
        jdbc.execute("CREATE TRIGGER ep28a_reject_outbox BEFORE INSERT ON outbox_events FOR EACH ROW EXECUTE FUNCTION ep28a_reject_outbox()");
        try {
            assertThrows(org.jooq.exception.DataAccessException.class,()->emission.emit(observation));
            counts(tenant,0);
        } finally {
            jdbc.execute("DROP TRIGGER ep28a_reject_outbox ON outbox_events");
            jdbc.execute("DROP FUNCTION ep28a_reject_outbox()");
        }
        assertEquals(observation,emission.emit(observation));
        counts(tenant,1);
    }
    @Test void outerAndSavepointRollbackDoNotCommitFactsButRequiresNewSurvives() {
        String outer="ep28a-"+UUID.randomUUID(), nested="ep28a-"+UUID.randomUUID(), independent="ep28a-"+UUID.randomUUID();
        TransactionTemplate tx=new TransactionTemplate(transactions);
        tx.executeWithoutResult(status->{emission.emit(observation(outer,"outer",1));status.setRollbackOnly();});
        counts(outer,0);
        tx.executeWithoutResult(status->{
            TransactionTemplate savepoint=new TransactionTemplate(transactions);
            savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
            savepoint.executeWithoutResult(inner->{emission.emit(observation(nested,"nested",1));inner.setRollbackOnly();});
        });
        counts(nested,0);
        tx.executeWithoutResult(status->{
            emission.emit(observation(outer,"outer",1));
            TransactionTemplate requiresNew=new TransactionTemplate(transactions);
            requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            requiresNew.executeWithoutResult(inner->emission.emit(observation(independent,"new",1)));
            status.setRollbackOnly();
        });
        counts(outer,0);counts(independent,1);
    }
}
