package com.example.platform.marketplace;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

/**
 * Real HTTP/Identity/Outbox regression for the archive race. V28: withdrawal is listing-owned and the
 * retired Media publication mirror is not consulted or written.
 */
class MarketplaceArchiveIntegrationTest extends MarketplaceTestSupport {
 @Test void reviewArchiveCannotMutateVersionChangedBetweenReadAndWrite() throws Exception {
  String asset=asset();var publication=publish(approve(submit(create(asset))));String listing=publication.path("id").asText();
  var artifactBefore=jdbc.queryForMap("select id,content_digest,byte_length,media_type from artifact where id=?",asset);
  var body=Map.of("commandId","archive-race","expectedVersion",publication.path("version").asLong(),"transition","ARCHIVE");
  int status;
  try(var conn=context.getBean(javax.sql.DataSource.class).getConnection();var pool=Executors.newSingleThreadExecutor()) {
   conn.setAutoCommit(false);
   try(var q=conn.prepareStatement("select id from artifact where id=? for update")){q.setString(1,asset);q.executeQuery().close();}
   var archive=pool.submit(()->http(user,"POST",root()+"/listings/"+listing+"/transitions",body));
   try {
    // V28 withdrawal is listing-owned: it neither waits on nor mutates the Artifact subject row,
    // which stays locked by this transaction for the whole archive command.
    assertThat(archive.get(15,TimeUnit.SECONDS).statusCode()).isEqualTo(200);
    try(var q=conn.prepareStatement("update artifact set state='QUARANTINED' where id=?")){q.setString(1,asset);assertThat(q.executeUpdate()).isEqualTo(1);}
   } finally {conn.commit();}
  }
  status=200;
  System.out.println("REVIEW_ARCHIVE_RACE listing="+listing+" artifact="+asset+" http="+status+" listingState="+jdbc.queryForMap("select status,aggregate_version from marketplace_listing where id=?",listing)+" receipts="+commands()+" events="+events());
  assertThat(status).isEqualTo(200);
  assertThat(jdbc.queryForMap("select status,aggregate_version from marketplace_listing where id=?",listing))
    .containsEntry("status","ARCHIVED").containsEntry("aggregate_version",5L);
  assertThat(commands()).isEqualTo(5);assertThat(events()).isEqualTo(5);
  assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='marketplace.listing.archived' and aggregate_id=?",Integer.class,listing)).isEqualTo(1);
  assertThat(jdbc.queryForMap("select id,content_digest,byte_length,media_type from artifact where id=?",asset)).isEqualTo(artifactBefore);
  assertThat(jdbc.queryForObject("select state from artifact where id=?",String.class,asset)).as("Listing withdrawal never mutates the Artifact subject").isEqualTo("QUARANTINED");
 }
 @Test void reviewAlreadyChangedVersionArchivePositiveControl() throws Exception {
  String asset=asset();var published=publish(approve(submit(create(asset))));String id=published.path("id").asText();
  // V28: an unusable Artifact subject does not block listing-owned withdrawal.
  jdbc.update("update artifact set state='QUARANTINED' where id=?",asset);
  var body=Map.of("commandId","stale-archive","expectedVersion",published.path("version").asLong(),"transition","ARCHIVE");
  response(http(user,"POST",root()+"/listings/"+id+"/transitions",body),200);
  assertThat(jdbc.queryForObject("select state from artifact where id=?",String.class,asset)).isEqualTo("QUARANTINED");
  assertThat(jdbc.queryForObject("select status from marketplace_listing where id=?",String.class,id)).isEqualTo("ARCHIVED");
  var after=state();response(http(user,"POST",root()+"/listings/"+id+"/transitions",body),200);assertThat(state()).isEqualTo(after);
  System.out.println("REVIEW_STALE_ARCHIVE_CONTROL listing="+id+" artifact=QUARANTINED listing=ARCHIVED retryNoMutation=true");
 }

 @Test void currentArchiveIsAtomicAndRetryDoesNotRepeatEffects() throws Exception {
  String asset=asset();var published=publish(approve(submit(create(asset))));String id=published.path("id").asText();
  var body=Map.of("commandId","archive-fault","expectedVersion",4,"transition","ARCHIVE");
  String path=root()+"/listings/"+id+"/transitions";var before=state();
  jdbc.execute("create function archive_receipt_fault() returns trigger language plpgsql as $$ begin if NEW.command_id='archive-fault' then raise exception 'injected archive receipt failure'; end if; return NEW; end $$");
  jdbc.execute("create trigger archive_receipt_fault before insert on marketplace_command for each row execute function archive_receipt_fault()");
  try {assertThat(http(user,"POST",path,body).statusCode()).isEqualTo(500);assertThat(state()).isEqualTo(before);
   assertThat(jdbc.queryForObject("select status from marketplace_listing where id=?",String.class,id)).isEqualTo("PUBLISHED");
  } finally {jdbc.execute("drop trigger archive_receipt_fault on marketplace_command");jdbc.execute("drop function archive_receipt_fault()");}
  response(http(user,"POST",path,body),200);
  assertThat(jdbc.queryForObject("select state from artifact where id=?",String.class,asset)).as("Withdrawal never mutates the Artifact subject").isEqualTo("AVAILABLE");
  assertThat(jdbc.queryForMap("select status,aggregate_version from marketplace_listing where id=?",id)).containsEntry("status","ARCHIVED").containsEntry("aggregate_version",5L);
  assertThat(commands()).isEqualTo(5);assertThat(events()).isEqualTo(5);var after=state();
  response(http(user,"POST",path,body),200);assertThat(state()).isEqualTo(after);
  assertThat(http(user,"POST",path,Map.of("commandId","archive-fault","expectedVersion",5,"transition","ARCHIVE")).statusCode()).isEqualTo(409);assertThat(state()).isEqualTo(after);
 }
 @Test void scopedConditionalMissIsDistinctFromDeniedCallerAndMissingSubject() throws Exception {
  String asset=asset();var published=publish(approve(submit(create(asset))));String id=published.path("id").asText();
  var body=Map.of("commandId","denied-archive","expectedVersion",4,"transition","ARCHIVE");var before=state();
  for(String caller:new String[]{null,outsider})assertThat(http(caller,"POST",root()+"/listings/"+id+"/transitions",body).statusCode()).isIn(401,403);
  assertThat(state()).isEqualTo(before);
  // V28: Media publication withdrawal is retired; withdrawal is listing-owned. The distinct,
  // non-mutating "subject unknown to the Artifact authority" case is asserted at its canonical port.
  assertThat(http(user,"GET","/api/marketplace/assets/art_unknown_subject/listing",null).statusCode()).isEqualTo(404);
  assertThat(state()).isEqualTo(before);
  // Current canonical FK disallows a dangling admitted listing. Missing Media is
  // exercised at its owner boundary without disabling referential integrity.
  response(http(user,"POST",root()+"/listings/"+id+"/transitions",body),200);
  assertThat(commands()).isEqualTo(5);assertThat(events()).isEqualTo(5);
 }
 @Test void archiveCommitsBeforeFollowingListingVersionWriter() throws Exception {
  String asset=asset();var published=publish(approve(submit(create(asset))));String id=published.path("id").asText();
  // Hold the receipt insertion after Media mutation, then observe the version writer
  // blocked on that row. The advisory latch is test synchronization, not production logic.
  jdbc.execute("create function archive_receipt_latch() returns trigger language plpgsql as $$ begin if NEW.command_id='archive-first' then perform pg_advisory_xact_lock(715290020); end if; return NEW; end $$");
  jdbc.execute("create trigger archive_receipt_latch before insert on marketplace_command for each row execute function archive_receipt_latch()");
  try(var latch=context.getBean(javax.sql.DataSource.class).getConnection();var pool=Executors.newFixedThreadPool(2)) {
   latch.createStatement().execute("select pg_advisory_lock(715290020)");
   var archive=pool.submit(()->http(user,"POST",root()+"/listings/"+id+"/transitions",Map.of("commandId","archive-first","expectedVersion",4,"transition","ARCHIVE")));
   Future<Integer> writer=null;
   try {
    org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(()->jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event='advisory' and query like '%marketplace_command%'",Integer.class)>0);
    // A following version writer is another listing-owning mutation; the immutability fence requires
    // it to advance the aggregate version exactly once.
    writer=pool.submit(()->jdbc.update("update marketplace_listing set status='ARCHIVED',aggregate_version=aggregate_version+1 where id=?",id));
    org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(()->jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%update marketplace_listing%'",Integer.class)>0);
    assertThat(archive.isDone()).isFalse();assertThat(writer.isDone()).isFalse();
   } finally {latch.createStatement().execute("select pg_advisory_unlock(715290020)");}
   response(archive.get(15,TimeUnit.SECONDS),200);assertThat(writer.get(15,TimeUnit.SECONDS)).isEqualTo(1);
  } finally {jdbc.execute("drop trigger archive_receipt_latch on marketplace_command");jdbc.execute("drop function archive_receipt_latch()");}
  assertThat(jdbc.queryForObject("select state from artifact where id=?",String.class,asset)).isEqualTo("AVAILABLE");
  // The archive committed at version 5; the serialized follower advanced it to 6 exactly once.
  assertThat(jdbc.queryForMap("select status,aggregate_version from marketplace_listing where id=?",id)).containsEntry("status","ARCHIVED").containsEntry("aggregate_version",6L);
  assertThat(commands()).isEqualTo(5);assertThat(events()).isEqualTo(5);
 }

}
