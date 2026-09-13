package com.example.platform.timeline;
import com.example.platform.timeline.api.event.*;
import com.example.platform.outbox.api.event.*;
import com.example.platform.outbox.app.OutboxEventRouter;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class TimelineEventBoundaryTest {
 static final List<String> OLD=List.of("ReviewCreatedEvent","ReviewApprovedEvent","ReviewRejectedEvent","ReviewChangesRequestedEvent","ReviewCommentAddedEvent","ReviewThreadResolvedEvent","TimelineMergedEvent","TimelineRestoredEvent","TimelineRevisionCreatedEvent");
 static Set<String> violations(Path root)throws Exception{
  Set<String> found=new HashSet<>();try(var files=Files.walk(root)){
   for(Path p:files.filter(f->f.toString().contains("/src/main/")&&f.toString().endsWith(".java")).toList()){
    String path=p.toString().replace('\\','/'),s=Files.readString(p);
    for(String name:OLD)if((path.contains("shared-kernel/")&&p.getFileName().toString().equals(name+".java"))||s.contains("com.example.platform.shared.events."+name))found.add(path);
    if(p.getFileName().toString().equals("TimelineReviewEventPublisher.java")||p.getFileName().toString().equals("TimelineReviewOutboxEvents.java"))found.add(path);
    if(path.contains("render-module/")&&s.matches("(?s).*OutboxEventType<Timeline(?:Review\\w+|Merged|Restored|RevisionCreated)Event>.*"))found.add(path);
    if(path.contains("/web/")&&s.matches("(?s).*new\\s+(?:[\\w.]+\\.)?Timeline(?:Review\\w+|Merged|Restored|RevisionCreated)Event\\s*\\(.*"))found.add(path);
    if(path.contains("/timeline/api/event/")&&s.matches("(?s).*\\bString\\s+(?:targetType|targetId)\\b.*"))found.add(path);
    if(path.contains("outbox-event-module/")&&s.contains("import com.example.platform.timeline."))found.add(path);
   }
  }return found;
 }
 @Test void retiredContractsAndPublisherAuthoritiesStayAbsent()throws Exception{
  Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("AGENTS.md")))root=root.getParent();assertEquals(Set.of(),violations(root));
  for(String name:OLD)assertThrows(ClassNotFoundException.class,()->Class.forName("com.example.platform.shared.events."+name));
  assertThrows(ClassNotFoundException.class,()->Class.forName("com.example.platform.render.app.event.TimelineReviewEventPublisher"));
 }
 @Test void guardRejectsReintroducedSharedGenericControllerAndTransportAuthorities(@TempDir Path root)throws Exception{
  Map<String,String> cases=Map.of("shared-kernel/src/main/java/ReviewCreatedEvent.java","record ReviewCreatedEvent(){}",
   "render-module/src/main/java/RenderOutboxEvents.java","OutboxEventType<TimelineRestoredEvent> X;",
   "platform-app/src/main/java/web/Controller.java","new TimelineRestoredEvent(result,source,actor,now);",
   "outbox-event-module/src/main/java/Bad.java","import com.example.platform.timeline.api.event.TimelineMergedEvent;",
   "timeline-module/src/main/java/com/example/platform/timeline/api/event/Bad.java","record Bad(String targetType){}" );
  for(var e:cases.entrySet()){Path p=root.resolve(e.getKey());Files.createDirectories(p.getParent());Files.writeString(p,e.getValue());}assertEquals(cases.size(),violations(root).size());
 }
 @Test void nineOwnerContractsRoundTripAndRejectMalformedOrRetiredInputs(){
  var router=new OutboxEventRouter(List.of(new TimelineOutboxEvents()));assertEquals(9,router.size());
  var revision=new TimelineRevisionIdentity("tenant","project","revision");var review=new TimelineReviewReference("review",revision);var now=Instant.parse("2026-09-13T00:00:00Z");
  List<OutboxAppend<?>> appends=List.of(
   TimelineOutboxEvents.REVIEW_CREATED.append("tenant",new TimelineReviewCreatedEvent(review,"actor","title",now),null),
   TimelineOutboxEvents.REVIEW_APPROVED.append("tenant",new TimelineReviewApprovedEvent(review,"decision-a","actor",now),null),
   TimelineOutboxEvents.REVIEW_REJECTED.append("tenant",new TimelineReviewRejectedEvent(review,"decision-r","actor",now),null),
   TimelineOutboxEvents.REVIEW_CHANGESREQUESTED.append("tenant",new TimelineReviewChangesRequestedEvent(review,"decision-c","actor",now),null),
   TimelineOutboxEvents.REVIEW_COMMENTADDED.append("tenant",new TimelineReviewCommentAddedEvent(review,"comment","actor",null,now),null),
   TimelineOutboxEvents.REVIEW_THREADRESOLVED.append("tenant",new TimelineReviewThreadResolvedEvent(review,"fact","thread",null,now),null),
   TimelineOutboxEvents.MERGED.append("tenant",new TimelineMergedEvent(revision,revision,revision,revision,"actor",now),null),
   TimelineOutboxEvents.RESTORED.append("tenant",new TimelineRestoredEvent(revision,revision,"actor",now),null),
   TimelineOutboxEvents.REVISION_CREATED.append("tenant",new TimelineRevisionCreatedEvent(revision,1,"actor","api",null,false,now),null));
  for(var append:appends)roundTrip(router,append);
  for(String name:OLD)assertThrows(OutboxEventRouter.InvalidEvent.class,()->router.decode("com.example.platform.shared.events."+name,1,"old","old","{}"));
  for(String name:List.of("review.created","review.approved","review.rejected","review.changes_requested","review.comment.added","review.thread.resolved","timeline.merged","timeline.restored","timeline.revision.created"))
   assertThrows(OutboxEventRouter.InvalidEvent.class,()->router.decode(name,1,"old","old","{}"));
  assertThrows(IllegalArgumentException.class,()->new TimelineMergedEvent(revision,new TimelineRevisionIdentity("foreign","project","base"),revision,revision,"actor",now));
 }
 private <T extends Record> void roundTrip(OutboxEventRouter router,OutboxAppend<T> append){
  var type=append.type();assertEquals(append.payload(),router.decode(type.name(),type.version(),type.aggregateType(),append.aggregateId(),router.encode(append)).payload());
  assertThrows(IllegalArgumentException.class,()->type.append("foreign",append.payload(),null));
  assertThrows(OutboxEventRouter.InvalidEvent.class,()->router.decode(type.name(),99,type.aggregateType(),append.aggregateId(),router.encode(append)));
  assertThrows(OutboxEventRouter.InvalidEvent.class,()->router.decode(type.name(),type.version(),type.aggregateType(),append.aggregateId(),"{}"));
 }
}
