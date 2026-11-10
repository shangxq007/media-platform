package com.example.platform.testinfra;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real PG18 owner FK and immutable reference pin; no alternate Job state machine. */
class IncrementalReferencePostgresTest {
    @Test void databaseRejectsCrossTenantReferenceEvenForSameAdministratorConnection() throws Exception {
        try(var w=new GraphRenderLifecycleIT.World()) {
            assertThrows(org.jooq.exception.DataAccessException.class,()->w.tx(()->{
                w.db.execute("update render_job set incremental_reference_job_id='job-b' where id='job-a'");return null;
            }));
            assertNull(w.db.fetchOne("select incremental_reference_job_id from render_job where id='job-a'").get(0));
        }
    }
    @Test void referenceCannotBeReboundAfterPreparationCommit() throws Exception {
        try(var w=new GraphRenderLifecycleIT.World()) {
            w.db.execute("insert into render_job(id,project_id,tenant_id,timeline_snapshot_id,profile,status,initiator_type,initiator_id,initiator_tenant_id,created_at) values('reference-a','project-a','tenant-a','snapshot','test','COMPLETED','USER','user-a','tenant-a',now())");
            w.tx(()->{w.jobs.markGraphPreparation("tenant-a","job-a");w.jobs.graphPreparationStage("tenant-a","job-a","DISPATCH");w.jobs.pinIncrementalReference("tenant-a","project-a","job-a","reference-a");return null;});
            assertThrows(IllegalStateException.class,()->w.tx(()->{w.jobs.pinIncrementalReference("tenant-a","project-a","job-a","job-b");return null;}));
            assertEquals("reference-a",w.db.fetchOne("select incremental_reference_job_id from render_job where id='job-a'").get(0));
        }
    }
}
