package com.example.platform.events;

import com.example.platform.audit.app.UsageAnomalyDetectionService;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles({"test", "preview"})
class UsageAnomalySavepointTest extends PostgresTestContainerSupport {
    @Autowired UsageAnomalyDetectionService detector;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @BeforeEach void productionTransactionManager() {
        var jdbcManager = assertInstanceOf(org.springframework.jdbc.datasource.DataSourceTransactionManager.class, manager);
        assertTrue(jdbcManager.isNestedTransactionAllowed());
        assertSame(jdbc.getDataSource(), jdbcManager.getDataSource());
        System.out.println("Assembled transaction manager: " + manager.getClass().getName()
                + "; Spring " + org.springframework.core.SpringVersion.getVersion());
    }
    @AfterEach void synchronizationIsCleanedUp() {
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }
    private TransactionTemplate nested() {
        var template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        return template;
    }
    private void committed(String user, int expected, double score) {
        assertEquals(expected, count(user));
        assertEquals(expected, history("anomalyHistory", user));
        assertEquals(expected, history("mitigationHistory", user));
        assertEquals(score, detector.getRiskProfile("ep23-review", user).riskScore());
        var histories = (Map<?, ?>) ReflectionTestUtils.getField(detector, "anomalyHistory");
        var observations = (List<?>) histories.get("ep23-review:" + user);
        var ids = observations.stream().map(e -> ((com.example.platform.audit.domain.UsageAnomalyEvent) e).eventId())
                .sorted().toList();
        assertEquals(jdbc.queryForList("select aggregate_id from outbox_events where event_type='audit.usage.anomaly.detected'"
                + " and payload::jsonb->'payload'->>'userId'=? order by aggregate_id", String.class, user), ids);
    }
    private String prepare() {
        String user = "review-" + UUID.randomUUID();
        for(int i=0;i<10;i++) assertTrue(detector.analyzeSubmission("ep23-review", user, "default", "test").clean());
        return user;
    }
    private void observe(String user) {
        assertFalse(detector.analyzeSubmission("ep23-review", user, "default", "test").clean());
    }
    private long count(String user) {
        return jdbc.queryForObject("select count(*) from outbox_events where event_type='audit.usage.anomaly.detected' and payload::jsonb->'payload'->>'userId'=?", Long.class, user);
    }
    private int history(String field, String user) {
        var map=(Map<?,?>)ReflectionTestUtils.getField(detector,field);
        Object entries=map.get("ep23-review:"+user);
        return entries==null ? 0 : ((List<?>)entries).size();
    }
    private void noDerivedObservation(String user, Object before) {
        assertAll(
            ()->assertSame(before,detector.getRiskProfile("ep23-review",user),"risk profile changed without committed fact"),
            ()->assertEquals(0,history("anomalyHistory",user),"anomaly history contains rolled-back observation"),
            ()->assertEquals(0,history("mitigationHistory",user),"mitigation history contains rolled-back observation"));
    }
    @Test void nestedRollbackMustNotPublishViewsWhenOuterTransactionCommits() {
        String user=prepare(); var before=detector.getRiskProfile("ep23-review",user);
        var nested=new TransactionTemplate(manager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        new TransactionTemplate(manager).executeWithoutResult(outer->{
            nested.executeWithoutResult(inner->{
                assertTrue(inner.hasSavepoint(), "actual production transaction manager must create a savepoint");
                observe(user); assertEquals(1,count(user));
                noDerivedObservation(user,before);
                inner.setRollbackOnly();
            });
            assertEquals(0,count(user));
            noDerivedObservation(user,before);
        });
        assertEquals(0,count(user));
        System.out.println("nested rollback: outbox="+count(user)+", anomalies="+history("anomalyHistory",user)+", mitigations="+history("mitigationHistory",user)+", risk="+detector.getRiskProfile("ep23-review",user).riskScore());
        noDerivedObservation(user,before);
    }
    @Test void independentCommitSurvivesOuterRollbackWithExactlyOneDerivedObservation() {
        String user=prepare();
        var independent=new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        new TransactionTemplate(manager).executeWithoutResult(outer->{
            independent.executeWithoutResult(inner->observe(user));
            assertEquals(1,count(user));
            assertEquals(1,history("anomalyHistory",user));
            assertEquals(1,history("mitigationHistory",user));
            outer.setRollbackOnly();
        });
        assertEquals(1,count(user));
        assertEquals(1,history("anomalyHistory",user));
        assertEquals(1,history("mitigationHistory",user));
        assertEquals(0.55,detector.getRiskProfile("ep23-review",user).riskScore());
    }
    @Test void commitPhaseRejectionLeavesNoFactOrDerivedObservation() {
        String user=prepare(); var before=detector.getRiskProfile("ep23-review",user);
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(manager).executeWithoutResult(tx->{
            observe(user); assertEquals(1,count(user)); noDerivedObservation(user,before);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
                @Override public void beforeCommit(boolean readOnly){throw new IllegalStateException("review commit rejection");}
            });
        }));
        assertEquals(0,count(user));noDerivedObservation(user,before);
    }

    @Test void successfulNestedWorkWaitsForItsOuterCommitOrRollback() {
        for (boolean rollback : List.of(false, true)) {
            String user = prepare(); var before = detector.getRiskProfile("ep23-review", user);
            new TransactionTemplate(manager).executeWithoutResult(outer -> {
                nested().executeWithoutResult(inner -> {
                    assertTrue(inner.hasSavepoint()); observe(user);
                });
                assertEquals(1, count(user)); noDerivedObservation(user, before);
                if (rollback) outer.setRollbackOnly();
            });
            if (rollback) { assertEquals(0, count(user)); noDerivedObservation(user, before); }
            else committed(user, 1, 0.55);
        }
    }

    @Test void outerObservationSurvivesOnlyNestedRollback() {
        String user = prepare();
        new TransactionTemplate(manager).executeWithoutResult(outer -> {
            observe(user);
            nested().executeWithoutResult(inner -> {
                assertTrue(inner.hasSavepoint()); observe(user); assertEquals(2, count(user)); inner.setRollbackOnly();
            });
            assertEquals(1, count(user));
        });
        committed(user, 1, 0.55);
    }

    @Test void validWorkAfterRolledBackScopeAndNextTransactionIsNotSuppressed() {
        String user = prepare();
        new TransactionTemplate(manager).executeWithoutResult(outer -> {
            nested().executeWithoutResult(inner -> {
                assertTrue(inner.hasSavepoint()); observe(user); inner.setRollbackOnly();
            });
            assertEquals(0, count(user));
            nested().executeWithoutResult(inner -> { assertTrue(inner.hasSavepoint()); observe(user); });
        });
        committed(user, 1, 0.6);
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        new TransactionTemplate(manager).executeWithoutResult(tx -> observe(user));
        committed(user, 2, 0.65);
    }

    @Test void enclosingRollbackCancelsItsObservationAndReleasedDescendantButNotOuterObservation() {
        String user = prepare();
        new TransactionTemplate(manager).executeWithoutResult(outer -> {
            observe(user);
            nested().executeWithoutResult(enclosing -> {
                assertTrue(enclosing.hasSavepoint()); observe(user);
                nested().executeWithoutResult(descendant -> { assertTrue(descendant.hasSavepoint()); observe(user); });
                assertEquals(3, count(user)); enclosing.setRollbackOnly();
            });
            assertEquals(1, count(user));
        });
        committed(user, 1, 0.55);
    }

    @Test void requiresNewDoesNotShareOuterPendingObservationOrSavepoints() {
        String user = prepare();
        var independent = new TransactionTemplate(manager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        new TransactionTemplate(manager).executeWithoutResult(outer -> {
            observe(user);
            nested().executeWithoutResult(inner -> {
                assertTrue(inner.hasSavepoint());
                independent.executeWithoutResult(tx -> observe(user));
                assertEquals(1, history("anomalyHistory", user));
                inner.setRollbackOnly();
            });
            outer.setRollbackOnly();
        });
        committed(user, 1, 0.6);
    }

    @Test void repeatedRollbackToProgrammaticSavepointOwnsOnlyLaterObservations() {
        String user = prepare();
        new TransactionTemplate(manager).executeWithoutResult(outer -> {
            observe(user);
            Object savepoint = outer.createSavepoint();
            observe(user); outer.rollbackToSavepoint(savepoint);
            observe(user); outer.rollbackToSavepoint(savepoint);
            outer.releaseSavepoint(savepoint);
            observe(user);
            assertEquals(2, count(user));
        });
        committed(user, 2, 0.7);
    }
}
