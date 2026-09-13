package com.example.platform.timeline;
import com.example.platform.timeline.api.event.TimelineOutboxEvents;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.shared.authorization.*;
import com.example.platform.identity.api.authorization.*;
import com.example.platform.timeline.api.review.*;
import com.example.platform.timeline.app.review.*;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.infrastructure.review.TimelineReviewRepository;
import com.example.platform.outbox.app.*;
import com.example.platform.web.render.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.jooq.*;
import org.jooq.impl.DSL;
import org.flywaydb.core.Flyway;
import javax.sql.DataSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class TimelineReviewOwnerIntegrationTest extends PostgresTestContainerSupport {
 static final String SCHEMA=isolatedSchemaName();static DataSource admin;static AnnotationConfigApplicationContext context;
 static JdbcTemplate jdbc;static OutboxEventService outbox;static boolean denied;
 @EnableTransactionManagement(proxyTargetClass=true) static class Transactions {}
 @BeforeAll static void setup(){
  admin=createDataSource();new JdbcTemplate(admin).execute("create schema "+SCHEMA);
  Flyway.configure().dataSource(jdbcUrl(),username(),password()).locations("classpath:db/migration").schemas(SCHEMA).defaultSchema(SCHEMA).load().migrate();
  var ds=new DriverManagerDataSource(jdbcUrl()+(jdbcUrl().contains("?")?"&":"?")+"currentSchema="+SCHEMA,username(),password());jdbc=new JdbcTemplate(ds);
  context=new AnnotationConfigApplicationContext();context.register(Transactions.class);
  context.registerBean(JdbcTemplate.class,()->jdbc);
  context.registerBean("transactionManager",DataSourceTransactionManager.class,()->new DataSourceTransactionManager(ds));
  context.registerBean(DSLContext.class,()->DSL.using(new TransactionAwareDataSourceProxy(ds),SQLDialect.POSTGRES,new org.jooq.conf.Settings().withRenderSchema(false)));
  context.registerBean(CanonicalActorResolver.class,()->()->Optional.of(CanonicalActor.user("server-actor","tenant",Set.of("EDITOR"),"fixture")));
  context.registerBean(AuthorizationDecisionPort.class,()->request->{
   if(denied)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"denied by owner");
   assertEquals("tenant",request.resource().tenantId());return AuthorizationDecision.allow("fixture");});
  context.registerBean(TimelineRevisionRepository.class);
  context.registerBean(TimelineRevisionQueryService.class,()->new TimelineRevisionQueryService(context.getBean(TimelineRevisionRepository.class),mock(com.example.platform.timeline.adapter.TimelineSnapshotService.class),mock(com.example.platform.timeline.app.TimelineRevisionDiffService.class)));
  context.registerBean(TimelineReviewRepository.class);context.registerBean(ReviewAuthorization.class);context.registerBean(TimelineReviewQueries.class);
  context.registerBean(TimelineReviewService.class);context.registerBean(TimelineCommentService.class);context.registerBean(ReviewDecisionService.class);context.registerBean(ReviewEventPublisher.class);
  context.registerBean(OutboxEventRouter.class,()->new OutboxEventRouter(List.of(new TimelineOutboxEvents())));
  context.registerBean(PostgresNotificationService.class);
  context.registerBean(OutboxEventService.class,()->{outbox=spy(new OutboxEventService(context.getBean(DSLContext.class),3,context.getBean(PostgresNotificationService.class),context.getBean(OutboxEventRouter.class)));return outbox;});
  context.registerBean(TimelineProjectAuthorizationService.class);context.registerBean(TimelineReviewController.class);
  context.registerBean(com.example.platform.audit.app.AuditService.class,()->new com.example.platform.audit.app.AuditService(context.getBean(DSLContext.class),null));
  context.registerBean(com.example.platform.audit.app.AuditEventHandler.class);
  context.registerBean(com.example.platform.notification.app.NotificationRenderingService.class);
  context.registerBean(com.example.platform.notification.app.NotificationEventHandler.class,()->new com.example.platform.notification.app.NotificationEventHandler(context.getBean(DSLContext.class),List.of(),context.getBean(com.example.platform.notification.app.NotificationRenderingService.class),null));
  context.registerBean(OutboxEventDispatcher.class,()->new OutboxEventDispatcher(context.getBean(OutboxEventService.class),context,context.getBean(OutboxEventRouter.class),3,new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
  context.refresh();
  jdbc.update("insert into tenant(id,name,created_at) values ('tenant','test',now())");
  for(String project:List.of("project","other")){
   jdbc.update("insert into project(id,tenant_id,name,created_at) values (?,'tenant','test',now())",project);
   jdbc.update("insert into timeline_snapshot(id,project_id,tenant_id,payload_json) values (?,?,'tenant','{}')",project+"-snapshot",project);
   jdbc.update("insert into timeline_revision(id,project_id,tenant_id,revision_number,snapshot_id,content_hash,source,created_at) values (?,?,'tenant',1,?,'fixture','TEST',now())",project+"-revision",project,project+"-snapshot");
  }
 }
 @AfterAll static void close(){if(context!=null)context.close();if(admin!=null){new JdbcTemplate(admin).execute("drop schema "+SCHEMA+" cascade");closeDataSource(admin);}}
 @BeforeEach void before(){TenantContext.set("tenant");denied=false;reset(outbox);}
 @AfterEach void after(){TenantContext.clear();}
 long count(String table){return jdbc.queryForObject("select count(*) from "+table,Long.class);}
 String create(){return context.getBean(TimelineReviewController.class).createReview("project",new TimelineReviewController.CreateReviewRequest("project-revision","review","description")).getBody().reviewId();}
 @Test void externalEntryUsesAuthenticatedOwnerAndDurableDispatch(){
  String id=create();assertEquals("server-actor",jdbc.queryForObject("select author_user_id from timeline_review where id=?",String.class,id));
  String event=jdbc.queryForObject("select id from outbox_events where aggregate_id=?",String.class,id);
  assertTrue(context.getBean(OutboxEventDispatcher.class).processOnce(event));assertFalse(context.getBean(OutboxEventDispatcher.class).processOnce(event));
  assertEquals(0L,jdbc.queryForObject("select count(*) from audit_records where resource_id=?",Long.class,id));
  context.getBean(TimelineReviews.class).approve(id,"server-actor");
  String approved=jdbc.queryForObject("select id from outbox_events where aggregate_id=? and event_type='timeline.review.approved'",String.class,id);
  assertTrue(context.getBean(OutboxEventDispatcher.class).processOnce(approved));
  var row=jdbc.queryForMap("select * from outbox_events where id=?",approved);
  var decoded=context.getBean(OutboxEventRouter.class).decode((String)row.get("event_type"),((Number)row.get("event_version")).intValue(),(String)row.get("aggregate_type"),(String)row.get("aggregate_id"),(String)row.get("payload"));
  context.publishEvent(decoded.payload());context.publishEvent(decoded.payload());
  assertEquals(1L,jdbc.queryForObject("select count(*) from notification_event where subject_id=?",Long.class,id));
  assertEquals(1L,jdbc.queryForObject("select count(*) from audit_records where resource_id=?",Long.class,id));
 }
 @Test void deniedWrongRevisionAndForgedAuthorCannotWrite(){
  long before=count("timeline_review");denied=true;assertThrows(RuntimeException.class,this::create);denied=false;
  var reviews=context.getBean(TimelineReviews.class);
  assertThrows(IllegalArgumentException.class,()->reviews.createReview("project","other-revision","server-actor","x","x"));
  assertThrows(IllegalArgumentException.class,()->reviews.createReview("project","project-revision","forged","x","x"));
  TenantContext.set("foreign");assertThrows(RuntimeException.class,this::create);assertEquals(before,count("timeline_review"));
 }
 @Test void appendFailureRollsBackReviewAndDecision(){
  long before=count("timeline_review");doThrow(new IllegalStateException("append failed")).when(outbox).append(any());assertThrows(IllegalStateException.class,this::create);assertEquals(before,count("timeline_review"));reset(outbox);
  String id=create();long decisions=count("review_decision");doThrow(new IllegalStateException("append failed")).when(outbox).append(any());
  assertThrows(IllegalStateException.class,()->context.getBean(TimelineReviews.class).approve(id,"server-actor"));assertEquals("OPEN",jdbc.queryForObject("select status from timeline_review where id=?",String.class,id));assertEquals(decisions,count("review_decision"));
 }
 @Test void scopeThreadsAndClosedTransitionsAreCheckedByOwner(){
  String id=create();var reviews=context.getBean(TimelineReviews.class);assertTrue(reviews.getReview("other","tenant",id).isEmpty());
  var comments=context.getBean(TimelineComments.class);long before=count("timeline_comment");
  assertThrows(IllegalArgumentException.class,()->comments.addComment(id,"other-revision",null,null,"server-actor","bad"));
  assertThrows(IllegalArgumentException.class,()->comments.addComment(id,"project-revision","foreign-thread",null,"server-actor","bad"));assertEquals(before,count("timeline_comment"));
  reviews.reject(id);assertThrows(ReviewConflictException.class,()->reviews.approve(id,"server-actor"));
 }
 @Test void httpContractPreservesDtoAuthorizationAndConflictStatus()throws Exception {
  var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(context.getBean(TimelineReviewController.class))
    .setControllerAdvice(new com.example.platform.web.GlobalExceptionHandler(Optional.empty())).build();
  String base="/api/render/projects/project/timeline/reviews";
  var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base).contentType("application/json")
    .content("{\"revisionId\":\"project-revision\",\"title\":\"HTTP review\",\"description\":\"test\"}"))
    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated()).andReturn();
  String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("reviewId").asText();
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(base+"/"+id)).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
  denied=true;mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/"+id+"/approve")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());denied=false;
  context.getBean(TimelineReviews.class).reject(id);
  mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(base+"/"+id+"/approve")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
 }

 @Test void retiredAndMalformedDurableTimelineInputsAreExplicitlyDeadLettered()throws Exception {
  String review=create();context.getBean(TimelineReviews.class).approve(review,"server-actor");
  long audits=count("audit_records"),notifications=count("notification_event");int n=0;
  List<String> old=new ArrayList<>(List.of("review.created","review.approved","review.rejected","review.changes_requested","review.comment.added","review.thread.resolved","timeline.merged","timeline.restored","timeline.revision.created"));
  for(String name:TimelineEventBoundaryTest.OLD)old.add("com.example.platform.shared.events."+name);
  for(String type:old){
   String id="retired-timeline-"+n++;
   jdbc.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,retry_count,max_retries,created_at) values (?,'old','old',?,1,'{}','PENDING',0,3,now())",id,type);
   assertFalse(context.getBean(OutboxEventDispatcher.class).processOnce(id));
   assertEquals("DEAD_LETTER",jdbc.queryForObject("select status from outbox_events where id=?",String.class,id));
   assertEquals("UNSUPPORTED_EVENT_VERSION",jdbc.queryForObject("select last_error_code from outbox_events where id=?",String.class,id));
  }
  String payload=jdbc.queryForObject("select payload from outbox_events where aggregate_id=? and event_type='timeline.review.approved'",String.class,review);
  var tree=new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
  ((com.fasterxml.jackson.databind.node.ObjectNode)tree.get("payload")).put("targetType","ASSET");
  jdbc.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,retry_count,max_retries,created_at) values ('malformed-timeline','timeline_review',?,'timeline.review.approved',1,?,'PENDING',0,3,now())",review,tree.toString());
  assertFalse(context.getBean(OutboxEventDispatcher.class).processOnce("malformed-timeline"));
  assertEquals("MALFORMED_EVENT_PAYLOAD",jdbc.queryForObject("select last_error_code from outbox_events where id='malformed-timeline'",String.class));
  assertEquals(audits,count("audit_records"));assertEquals(notifications,count("notification_event"));
 }

 @Test void commentAndResolutionFactsPreserveRollbackAndDistinctAcceptedTransitions() {
  String review=create();var comments=context.getBean(TimelineComments.class);
  var anchor=new com.example.platform.timeline.diff.merge.EntityRef(com.example.platform.timeline.diff.merge.EntityKind.CLIP,"review-anchor");
  long beforeComments=count("timeline_comment"),beforeThreads=count("review_thread");
  doThrow(new IllegalStateException("comment append rejected")).when(outbox).append(argThat(a->a.type()==com.example.platform.timeline.api.event.TimelineOutboxEvents.REVIEW_COMMENTADDED));
  assertThrows(IllegalStateException.class,()->comments.addComment(review,"project-revision",null,anchor,"server-actor","text"));
  assertEquals(beforeComments,count("timeline_comment"));assertEquals(beforeThreads,count("review_thread"));reset(outbox);
  var comment=comments.addComment(review,"project-revision",null,anchor,"server-actor","text");
  dispatchSubject(comment.commentId());
  assertEquals(1L,jdbc.queryForObject("select count(*) from audit_records where resource_id=?",Long.class,comment.commentId()));
  assertTrue(comments.resolveThread(review,comment.threadId()));assertTrue(comments.resolveThread(review,comment.threadId()));
  assertEquals(1L,jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?",Long.class,comment.threadId()));
  assertTrue(comments.reopenThread(review,comment.threadId()));assertTrue(comments.resolveThread(review,comment.threadId()));
  assertEquals(2L,jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?",Long.class,comment.threadId()));
  dispatchSubject(comment.threadId());
  assertEquals(2L,jdbc.queryForObject("select count(*) from audit_records where resource_id=?",Long.class,comment.threadId()));
  assertEquals(2L,jdbc.queryForObject("select count(*) from notification_event where subject_id=?",Long.class,comment.threadId()));
 }
 @Test void allDecisionFactsAreBoundToPersistedDecisionAndTenant() {
  String review=create();var reviews=context.getBean(TimelineReviews.class);
  reviews.approve(review,"server-actor");reviews.requestChanges(review,"server-actor");reviews.reject(review);
  for(var row:jdbc.queryForList("select * from outbox_events where aggregate_id=? and event_type<>'timeline.review.created'",review)) {
   var event=context.getBean(OutboxEventRouter.class).decode((String)row.get("event_type"),1,"timeline_review",review,(String)row.get("payload"));
   assertEquals("tenant",event.tenantId());
   String decision=event.payload() instanceof com.example.platform.timeline.api.event.TimelineReviewApprovedEvent x?x.decisionId():
      event.payload() instanceof com.example.platform.timeline.api.event.TimelineReviewRejectedEvent x?x.decisionId():
      ((com.example.platform.timeline.api.event.TimelineReviewChangesRequestedEvent)event.payload()).decisionId();
   assertEquals(review,jdbc.queryForObject("select review_id from review_decision where id=?",String.class,decision));
  }
  dispatchSubject(review);
  assertEquals(3L,jdbc.queryForObject("select count(*) from audit_records where resource_id=?",Long.class,review));
  assertEquals(3L,jdbc.queryForObject("select count(*) from notification_event where subject_id=?",Long.class,review));
 }
 private void dispatchSubject(String subject) {
  for(var row:jdbc.queryForList("select * from outbox_events where aggregate_id=? order by created_at",subject)) {
   var event=context.getBean(OutboxEventRouter.class).decode((String)row.get("event_type"),((Number)row.get("event_version")).intValue(),(String)row.get("aggregate_type"),subject,(String)row.get("payload"));
   assertTrue(context.getBean(OutboxEventDispatcher.class).processOnce((String)row.get("id")));
   context.publishEvent(event.payload());context.publishEvent(event.payload());
  }
 }

}
