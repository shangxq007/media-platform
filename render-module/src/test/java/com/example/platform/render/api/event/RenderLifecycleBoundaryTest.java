package com.example.platform.render.api.event;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RenderLifecycleBoundaryTest {
    private static final List<String> RETIRED=List.of("RenderJobCreatedEvent","RenderJobCompletedEvent","RenderJobFailedEvent","RenderJobStatusChangedEvent","RenderCacheHashInvalidatedEvent");
    static Set<String> violations(Path root) throws Exception {
        Set<String> found=new HashSet<>();
        try(var files=Files.walk(root)){
            for(Path p:files.filter(f->f.toString().endsWith(".java")&&f.toString().contains("/src/main/")).toList()){
                String path=p.toString().replace('\\','/'),s=Files.readString(p);
                if(path.contains("shared-kernel/")&&RETIRED.contains(p.getFileName().toString().replace(".java","")))found.add(path);
                for(String name:RETIRED)if(s.contains("com.example.platform.shared.events."+name))found.add(path);
                if(path.contains("/render/api/event/")&&s.matches("(?s).*\\b(?:String|ProviderRef)\\s+(?:storageUri|primaryBackend|provider|backend)\\b.*"))found.add(path);
                if(path.contains("render-module/")&&s.contains(".updateArtifactUri("))found.add(path);
                if(path.contains("render-module/")&&(s.contains("ArtifactGraphRepository")
                    ||s.contains("typedschema.jooq.generated.tables.ArtifactGraph")
                    ||s.contains("typedschema.jooq.generated.tables.ArtifactNode")
                    ||s.matches("(?s).*\\b(?:insertInto|update|deleteFrom|table)\\s*\\(\\s*\"artifact_(?:graph|node)\".*")))found.add(path);
                if(path.contains("delivery-module/")&&(s.contains("DELIVERY_JOB.SOURCE_URI")
                    ||s.contains("typedschema.jooq.generated.tables.RenderJob")||s.contains("typedschema.jooq.generated.tables.Artifact")))found.add(path);
            }
        }return found;
    }
    @Test void retiredContractsAndForeignCompletionPathsAreAbsent() throws Exception {
        Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("AGENTS.md")))root=root.getParent();
        assertEquals(Set.of(),violations(root));
        assertEquals(com.example.platform.artifact.app.ArtifactOutputReference.class,RenderJobCompletedEvent.class.getRecordComponents()[0].getType());
        for(Class<?> type:List.of(RenderJobCreatedEvent.class,RenderJobStatusChangedEvent.class,RenderJobFailedEvent.class,RenderJobCompletedEvent.class)){
            assertTrue(type.isRecord());
            assertFalse(Arrays.stream(type.getRecordComponents()).anyMatch(c->Set.of("primaryBackend","storageUri","provider","backend").contains(c.getName())));
        }
    }
    @Test void guardDetectsReintroducedDefinitionsFieldsAndForeignRead(@TempDir Path root) throws Exception {
        Map<String,String> cases=Map.of(
            "shared-kernel/src/main/java/RenderJobCreatedEvent.java","record RenderJobCreatedEvent() {}",
            "render-module/src/main/java/com/example/platform/render/api/event/Bad.java","record Bad(String storageUri) {}",
            "render-module/src/main/java/ForeignGraph.java","import com.example.platform.typedschema.jooq.generated.tables.ArtifactGraph;",
            "render-module/src/main/java/Retired.java","class ArtifactGraphRepository {}",
            "render-module/src/main/java/ForeignSql.java","dsl.deleteFrom(\"artifact_node\");",
            "delivery-module/src/main/java/Bad.java","import com.example.platform.typedschema.jooq.generated.tables.RenderJob;");
        for(var entry:cases.entrySet()){Path file=root.resolve(entry.getKey());Files.createDirectories(file.getParent());Files.writeString(file,entry.getValue());}
        assertEquals(6,violations(root).size());
    }
}
