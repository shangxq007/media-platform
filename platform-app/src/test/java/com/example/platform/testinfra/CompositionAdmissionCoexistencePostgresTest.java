package com.example.platform.testinfra;

import com.example.platform.composition.app.JdbcCompositionAdmissionRepository;
import com.example.platform.execution.admission.ProviderBoundExecutionPlan;
import com.example.platform.execution.admission.PlatformExecutionState;
import com.example.platform.shared.web.TenantContext;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** Existing Composition consumer and Artifact capabilities share one Execution owner schema. */
class CompositionAdmissionCoexistencePostgresTest {
    @AfterEach void clear() { TenantContext.clear(); }
    JdbcCompositionAdmissionRepository repository(PlatformCapabilityExecutionPostgresTest.World w) {
        return new JdbcCompositionAdmissionRepository(new JdbcTemplate(
                new SingleConnectionDataSource(w.fixture.schema.connection(), true)), JsonMapper.builder().build());
    }
    ProviderBoundExecutionPlan plan(String key) {
        return new ProviderBoundExecutionPlan(
                new ProviderBoundExecutionPlan.PublishedRevision("composition", "composition-a", "1.0", 3),
                new ProviderBoundExecutionPlan.Scope("tenant-a", "workspace-a", "user-a"),
                List.of(new ProviderBoundExecutionPlan.CapabilityProviderBinding("image", "media.cover-image", "1.0",
                        new ProviderBoundExecutionPlan.ProviderIdentity("catalog-reference"), "1.0", "Artifact", "1.0", "Artifact", "1.0")),
                List.of(new ProviderBoundExecutionPlan.TypedReference("source", "Artifact", "1.0", "source-a")),
                List.of(), List.of(new ProviderBoundExecutionPlan.TypedOutputContract("image", "Artifact", "1.0", "IMAGE")),
                ProviderBoundExecutionPlan.ExecutionMode.ASYNCHRONOUS,
                new ProviderBoundExecutionPlan.EntitlementQuotaSnapshot("quota", Map.of(), BigDecimal.ONE),
                new ProviderBoundExecutionPlan.PlanFingerprint("d".repeat(64)),
                new ProviderBoundExecutionPlan.IdempotencyIdentity(key, "e".repeat(64)),
                new ProviderBoundExecutionPlan.PolicyReferences("cancel", "retry"));
    }
    @Test void compositionRoundTripAndTransitionsUseCanonicalSourceColumns() throws Exception {
        try (var w = new PlatformCapabilityExecutionPostgresTest.World()) {
            var repo = repository(w); var plan = plan("composition-replay");
            var accepted = w.tx(() -> repo.admit(plan));
            assertEquals(plan, accepted.plan()); assertEquals("composition-a", accepted.compositionId());
            assertEquals(3, accepted.compositionRevision()); assertEquals("ADMITTED", accepted.state());
            assertEquals(accepted, w.tx(() -> repository(w).admit(plan)));
            assertTrue(w.tx(() -> repo.transition(accepted.executionId(), 0, "ADMITTED", "RUNNING")));
            assertFalse(w.tx(() -> repo.transition(accepted.executionId(), 1, "RUNNING", "COMPLETED")));
            assertTrue(w.tx(() -> repo.transition(accepted.executionId(), 0, "RUNNING", "COMPLETED")));
            assertTrue(w.tx(() -> repo.claimQuotaCharge(accepted.executionId())));
            assertFalse(w.tx(() -> repo.claimQuotaCharge(accepted.executionId())));
            w.tx(() -> { repo.markQuotaCharged(accepted.executionId(), BigDecimal.ONE); return null; });
            assertEquals(0, repo.findByExecutionId(accepted.executionId()).orElseThrow().quotaChargedUnits().compareTo(BigDecimal.ONE));
            w.tx(() -> { repo.deleteUncharged(accepted.executionId()); return null; });
            assertTrue(repo.findByExecutionId(accepted.executionId()).isPresent());
            assertEquals(1, w.count("platform_execution_admission"));
            assertEquals(0, w.count("platform_execution_result")); assertEquals(0, w.count("outbox_events"));
        }
    }
    @Test void compositionAdapterCannotReadMutateChargeOrDeleteArtifactExecution() throws Exception {
        try (var w = new PlatformCapabilityExecutionPostgresTest.World()) {
            var capability = new PlatformCapabilityExecutionPostgresTest().plan("other-owner", "source-a", "0.25", "media.thumbnail");
            var owner = w.owner(w.db); var accepted = w.tx(() -> owner.admit(capability)); var repo = repository(w);
            String internalKey = w.db.fetchOne("select idempotency_key from execution.platform_execution_admission where execution_id=?", accepted.executionId()).get(0, String.class);
            assertTrue(repo.findByExecutionId(accepted.executionId()).isEmpty());
            assertTrue(w.tx(() -> repo.find("tenant-a", "workspace-a", internalKey)).isEmpty());
            assertFalse(w.tx(() -> repo.transition(accepted.executionId(), 1, "ADMITTED", "RUNNING")));
            assertFalse(w.tx(() -> repo.claimQuotaCharge(accepted.executionId())));
            w.tx(() -> { repo.markQuotaCharged(accepted.executionId(), BigDecimal.ONE); repo.deleteUncharged(accepted.executionId()); return null; });
            assertEquals(accepted.handle(), owner.find("tenant-a", "project-a", accepted.executionId()).orElseThrow().handle());
            var stored = w.db.fetchOne("select state,quota_charged,quota_claimed from execution.platform_execution_admission where execution_id=?", accepted.executionId());
            assertEquals("ADMITTED", stored.get(0)); assertEquals(false, stored.get(1)); assertEquals(false, stored.get(2));
            assertEquals(1, w.count("platform_execution_admission")); assertEquals(1, w.count("outbox_events"));
            assertEquals(0, w.count("artifact")); assertEquals(0, w.count("quota_usage_operation"));
        }
    }
    @Test void compositionResultsRoundTripWithExactReplayAndRejectConflictingCompletion() throws Exception {
        try (var w = new PlatformCapabilityExecutionPostgresTest.World()) {
            var plan = plan("composition-result"); var accepted = w.tx(() -> repository(w).admit(plan));
            var results = new com.example.platform.composition.app.JdbcCompositionResultRepository(new JdbcTemplate(
                    new SingleConnectionDataSource(w.fixture.schema.connection(), true)));
            var output = new com.example.platform.composition.app.CompositionResultRepository.MaterializedResult(
                    accepted.executionId(), "composition-attempt", "composition-object", "a".repeat(64), 3, "composition-image");
            w.tx(() -> { results.recordMaterialized("tenant-a", "composition-result", "composition-result", output, 0); return null; });
            assertEquals(output, results.findMaterialized("tenant-a", "composition-result", "composition-result").orElseThrow());
            var completion = results.find(accepted.executionId(), "composition-attempt").orElseThrow();
            assertEquals(completion, results.findByIdempotency("tenant-a", "composition-result", plan.idempotency().requestHash()).orElseThrow());
            assertTrue(results.findByIdempotency("tenant-a", "composition-result", "wrong-hash").isEmpty());
            assertTrue(results.findByIdempotency("other-tenant", "composition-result", plan.idempotency().requestHash()).isEmpty());
            assertEquals(completion, w.tx(() -> results.record(completion, 0)));
            assertThrows(IllegalArgumentException.class, () -> w.tx(() -> results.record(completion, 1)));
            var conflict = new com.example.platform.execution.result.PlatformCompletionReference(accepted.executionId(),
                    "composition-attempt", completion.resultId(), "wrong-receipt", completion.artifactCommitId(), completion.status());
            assertThrows(IllegalArgumentException.class, () -> w.tx(() -> results.record(conflict, 0)));
            assertEquals(completion, results.find(accepted.executionId(), "composition-attempt").orElseThrow());
            assertEquals(1,w.count("platform_execution_result")); assertEquals(0,w.count("artifact"));
            assertEquals(0,w.count("quota_usage_operation"));
        }
    }
    @Test void compositionResultAdapterCannotReadOrOverwriteARealCapabilityOwnersCommittedResult() throws Exception {
        try (var w = new PlatformCapabilityExecutionPostgresTest.World()) {
            var fixture = new PlatformCapabilityTerminalRacePostgresTest(); var f = fixture.ready(w); var calls = new java.util.concurrent.atomic.AtomicInteger();
            w.tx(() -> { try { return w.owner(w.db).publish("tenant-a","project-a",f.plan().executionId(),1,
                    f.task(),f.execution(),() -> fixture.nativeCallbackFixture(w.db,f,calls)); }
                    catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); } });
            assertEquals(1,calls.get());
            var before = w.db.fetchOne("select * from execution.platform_execution_result where execution_id=?", f.plan().executionId()).intoMap();
            var results = new com.example.platform.composition.app.JdbcCompositionResultRepository(new JdbcTemplate(
                    new SingleConnectionDataSource(w.fixture.schema.connection(), true)));
            String key = f.plan().scopedIdempotencyKey();
            assertTrue(results.find(f.plan().executionId(),"race-attempt").isEmpty());
            assertTrue(results.findByIdempotency("tenant-a",key,f.plan().requestHash()).isEmpty());
            assertTrue(results.findMaterialized("tenant-a",key,f.plan().requestHash()).isEmpty());
            var completion = new com.example.platform.execution.result.PlatformCompletionReference(f.plan().executionId(),
                    "race-attempt","foreign-result","foreign-receipt","foreign-artifact",
                    com.example.platform.execution.result.PlatformCompletionReference.Status.COMPLETED);
            assertThrows(IllegalArgumentException.class, () -> w.tx(() -> results.record(completion,1)));
            var output = new com.example.platform.composition.app.CompositionResultRepository.MaterializedResult(
                    f.plan().executionId(),"race-attempt","foreign-object","f".repeat(64),99,"foreign-artifact");
            assertThrows(IllegalArgumentException.class, () -> w.tx(() -> {results.recordMaterialized("tenant-a",key,f.plan().requestHash(),output,1);return null;}));
            assertEquals(before,w.db.fetchOne("select * from execution.platform_execution_result where execution_id=?",f.plan().executionId()).intoMap());
            assertEquals(1,w.count("platform_execution_result")); assertEquals(1,w.count("artifact")); assertEquals(1,w.count("quota_usage_operation"));
            assertEquals(PlatformExecutionState.COMPLETED,w.owner(w.db).find("tenant-a","project-a",f.plan().executionId()).orElseThrow().state());
        }
    }

}
