package com.example.platform.timeline;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class TimelineAuthorityBoundaryTest {
 static Set<String> violations(Path root)throws Exception{
  Set<String> result=new HashSet<>();
  try(var files=Files.walk(root)){
   for(Path p:files.filter(x->x.toString().contains("/src/main/")&&x.toString().endsWith(".java")).toList()){
    String path=p.toString().replace('\\','/'),s=Files.readString(p);
    if(path.contains("render-module/")){
     if(s.matches("(?s).*com\\.example\\.platform\\.timeline\\.(?:app|adapter|infrastructure)\\..*"))result.add(path);
     if(s.contains("typedschema.jooq.generated.tables.TimelineReview")||s.contains("typedschema.jooq.generated.tables.TimelineComment")
        ||s.contains("typedschema.jooq.generated.tables.ReviewDecision")||s.contains("typedschema.jooq.generated.tables.ReviewThread"))result.add(path);
     if(s.matches("(?s).*\\b(?:insertInto|update|deleteFrom)\\s*\\(\\s*\"(?:timeline_review|timeline_comment)\".*"))result.add(path);
     if(Set.of("TimelineReviewRepository.java","TimelineReviewService.java","TimelineCommentService.java","ReviewDecisionService.java","TimelineMergeConfiguration.java").contains(p.getFileName().toString()))result.add(path);
    }
    if(path.contains("/timeline/api/")&&s.matches("(?s).*import com\\.example\\.platform\\.timeline\\.(?:app|adapter|infrastructure)\\..*"))result.add(path);
   }
  }return result;
 }
 @Test void foreignPersistenceAndImplementationImportsAreRetired()throws Exception{
  Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("AGENTS.md")))root=root.getParent();assertEquals(Set.of(),violations(root));
  assertTrue(com.example.platform.timeline.api.revision.TimelineRevisionCommands.class.isInterface());
  assertTrue(com.example.platform.timeline.api.revision.TimelineSnapshotQueries.class.isInterface());
 }
 @Test void guardDetectsReintroducedRepositoryForeignWriterAndImplementationImport(@TempDir Path root)throws Exception{
  Map<String,String> cases=Map.of("render-module/src/main/java/TimelineReviewRepository.java","class TimelineReviewRepository {}",
    "render-module/src/main/java/Writer.java","dsl.insertInto(\"timeline_comment\");",
    "render-module/src/main/java/Caller.java","import com.example.platform.timeline.app.TimelineRevisionSaveService;",
    "timeline-module/src/main/java/com/example/platform/timeline/api/Bad.java","import com.example.platform.timeline.adapter.TimelineSnapshotService;");
  for(var e:cases.entrySet()){Path p=root.resolve(e.getKey());Files.createDirectories(p.getParent());Files.writeString(p,e.getValue());}
  assertEquals(cases.size(),violations(root).size());
 }
}
