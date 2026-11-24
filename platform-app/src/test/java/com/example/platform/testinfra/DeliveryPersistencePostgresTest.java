package com.example.platform.testinfra;

import com.example.platform.delivery.app.DeliveryRecord;
import com.example.platform.delivery.infrastructure.DeliveryRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeliveryPersistencePostgresTest {
    DeliveryRecord record(String id,String tenant,String project,String job,String artifact) {
        return new DeliveryRecord(id,tenant,project,job,artifact,"key","a".repeat(64),"requester",OffsetDateTime.now());
    }
    @Test void uniqueRequestIsScopedAndAcceptedIdentityCannotBeChangedOrDeleted() throws Exception {
        try(var w=new GraphRenderLifecycleIT.World()) {
            w.artifact("artifact-a","tenant-a","project-a","job-a","RENDER_MASTER");var repo=new DeliveryRepository(w.db);
            assertTrue(repo.insert(record("delivery-a","tenant-a","project-a","job-a","artifact-a")));
            assertFalse(repo.insert(record("delivery-repeat","tenant-a","project-a","job-a","artifact-a")));
            assertEquals("delivery-a",repo.byKey("tenant-a","project-a","key").orElseThrow().id());
            assertTrue(repo.find("tenant-b","project-b","delivery-a").isEmpty());
            assertThrows(RuntimeException.class,()->w.db.execute("update publication.delivery set artifact_id='other' where id='delivery-a'"));
            assertThrows(RuntimeException.class,()->w.db.execute("delete from publication.delivery where id='delivery-a'"));
            assertEquals("artifact-a",repo.find("tenant-a","project-a","delivery-a").orElseThrow().artifactId());
        }
    }
    @Test void crossScopeArtifactAndJobReferencesAreRejectedAndFailedTransactionRollsBack() throws Exception {
        try(var w=new GraphRenderLifecycleIT.World()) {
            w.artifact("artifact-a","tenant-a","project-a","job-a","RENDER_MASTER");var repo=new DeliveryRepository(w.db);
            assertThrows(RuntimeException.class,()->repo.insert(record("cross","tenant-b","project-b","job-b","artifact-a")));
            assertThrows(RuntimeException.class,()->w.tx(()->{repo.insert(record("rolled-back","tenant-a","project-a","job-a","artifact-a"));throw new IllegalStateException("bounded transaction interruption");}));
            assertEquals(0,w.count("publication.delivery"));
        }
    }
}
