package com.example.platform.events;

import com.example.platform.artifact.api.event.*;
import com.example.platform.artifact.app.*;
import com.example.platform.delivery.api.event.*;
import com.example.platform.delivery.domain.DeliveryProtocol;
import com.example.platform.audit.api.event.*;
import com.example.platform.outbox.app.OutboxEventRouter;
import com.example.platform.outbox.api.event.*;
import com.example.platform.shared.identity.ArtifactId;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OtherDomainEventBoundaryTest {
    private static final List<String> RETIRED=List.of("ArtifactCreatedEvent","AssetEnrichedEvent","AssetMetadataUpdatedEvent",
        "AssetRegisteredEvent","RenderDeliveryCompletedEvent","RenderDeliveryFailedEvent","UsageAnomalyDetectedEvent");
    static Set<String> violations(Path root) throws Exception {
        Set<String> found=new HashSet<>();
        try(var files=Files.walk(root)) {
            for(Path p:files.filter(f->f.toString().endsWith(".java")&&f.toString().contains("/src/main/")).toList()) {
                String path=p.toString().replace('\\','/'),s=Files.readString(p);
                for(String name:RETIRED)if((path.contains("shared-kernel/")&&p.getFileName().toString().equals(name+".java"))
                    ||s.contains("com.example.platform.shared.events."+name))found.add(path);
                if(path.contains("outbox-event-module/")&&s.matches("(?s).*import com.example.platform.(?:artifact|delivery|audit)\\..*"))found.add(path);
                if(p.getFileName().toString().equals("RenderOutboxEvents.java")&&s.matches("(?s).*OutboxEventType<(?:ArtifactCreated|AssetEnriched|AssetRegistered|AssetMetadataUpdated)Event>.*"))found.add(path);
                if(path.contains("render-module/")&&s.contains("new ArtifactCreatedEvent("))found.add(path);
            }
        }
        return found;
    }
    @Test void oldAuthoritiesAndReverseDependenciesAreAbsent() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("AGENTS.md")))root=root.getParent();
        assertEquals(Set.of(),violations(root));
        for(Class<?> type:List.of(ArtifactCreatedEvent.class,AssetEnrichedEvent.class,AssetRegisteredEvent.class,AssetMetadataUpdatedEvent.class))
            assertTrue(type.getPackageName().equals("com.example.platform.artifact.api.event"));
        assertEquals("com.example.platform.delivery.api.event",DeliveryCompletedEvent.class.getPackageName());
        assertEquals("com.example.platform.audit.api.event",UsageAnomalyDetectedEvent.class.getPackageName());
    }
    @Test void guardsDetectRetiredDefinitionsCatalogAndReverseDependency(@TempDir Path root) throws Exception {
        Map<String,String> cases=Map.of(
            "shared-kernel/src/main/java/ArtifactCreatedEvent.java","record ArtifactCreatedEvent(){}",
            "render-module/src/main/java/RenderOutboxEvents.java","OutboxEventType<AssetEnrichedEvent> X;",
            "outbox-event-module/src/main/java/Bad.java","import com.example.platform.artifact.api.event.ArtifactCreatedEvent;",
            "render-module/src/main/java/Producer.java","new ArtifactCreatedEvent(result,now);");
        for(var e:cases.entrySet()){Path p=root.resolve(e.getKey());Files.createDirectories(p.getParent());Files.writeString(p,e.getValue());}
        assertEquals(cases.size(),violations(root).size());
    }
    @Test void ownerCatalogsReconstructAllSevenFactsAndRejectRetiredOrMismatchedContracts() {
        var router=new OutboxEventRouter(List.of(new ArtifactOutboxEvents(),new DeliveryOutboxEvents(),new AuditOutboxEvents()));
        Instant now=Instant.parse("2026-09-13T00:00:00Z");
        var output=new ArtifactOutputReference(new ArtifactScope("tenant","project","job"),new ArtifactId("artifact"));
        var created=new ArtifactCreatedEvent(output,now);
        List<OutboxAppend<?>> appends=List.of(
            ArtifactOutboxEvents.ARTIFACTCREATEDEVENT.append("tenant",created,null),
            ArtifactOutboxEvents.ASSETREGISTEREDEVENT.append("tenant",new AssetRegisteredEvent("asset","v1","VIDEO","project","tenant"),null),
            ArtifactOutboxEvents.ASSETMETADATAUPDATEDEVENT.append("tenant",new AssetMetadataUpdatedEvent("asset","v1","VIDEO","project"),null),
            ArtifactOutboxEvents.ASSETENRICHEDEVENT.append("tenant",new AssetEnrichedEvent("fact",new AssetMetadataReference("tenant","asset","v1","project"),"VIDEO","COMPLETE","ASR",now),null),
            DeliveryOutboxEvents.COMPLETED.append("tenant",new DeliveryCompletedEvent("delivery",output,"destination",1,DeliveryProtocol.SFTP,"sftp://test/output",10,now),null),
            DeliveryOutboxEvents.FAILED.append("tenant",new DeliveryFailedEvent("delivery",output,"destination",2,"SOURCE_UNAVAILABLE","unavailable",now),null),
            AuditOutboxEvents.ANOMALY.append("tenant",new UsageAnomalyDetectedEvent("anomaly","tenant","user","render_burst","LOW","WARN",0.6,Map.of("anomalies",List.of("render_burst")),now),null));
        assertEquals(7,router.size());
        for(var append:appends) {
            var type=append.type();
            var decoded=router.decode(type.name(),type.version(),type.aggregateType(),append.aggregateId(),router.encode(append));
            assertEquals(append.payload(),decoded.payload());assertEquals("tenant",decoded.tenantId());
            assertThrows(OutboxEventRouter.InvalidEvent.class,()->router.decode(type.name(),99,type.aggregateType(),append.aggregateId(),router.encode(append)));
            assertThrows(OutboxEventRouter.InvalidEvent.class,()->router.decode(type.name(),type.version(),type.aggregateType(),append.aggregateId(),"{}"));
        }
        assertThrows(IllegalArgumentException.class,()->ArtifactOutboxEvents.ARTIFACTCREATEDEVENT.append("foreign",created,null));
        for(String name:RETIRED)assertThrows(OutboxEventRouter.InvalidEvent.class,()->router.decode("com.example.platform.shared.events."+name,1,"unused","unused","{}"));
        for(String name:List.of("artifact.created","asset.enriched","render.delivery.completed","render.delivery.failed"))
            assertThrows(OutboxEventRouter.InvalidEvent.class,()->router.decode(name,1,"unused","unused","{}"));
    }
}
