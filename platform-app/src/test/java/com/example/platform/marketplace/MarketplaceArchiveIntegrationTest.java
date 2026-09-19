package com.example.platform.marketplace;

import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.example.platform.media.api.MediaAssets;

/** Real HTTP/Identity/Media/Outbox regression for the independently reproduced archive race. */
class MarketplaceArchiveIntegrationTest extends MarketplaceTestSupport {
 @Test void reviewArchiveCannotMutateVersionChangedBetweenReadAndWrite() throws Exception {
  String asset=asset();var publication=publish(approve(submit(create(asset))));String listing=publication.path("id").asText();
  var body=Map.of("commandId","archive-race","expectedVersion",publication.path("version").asLong(),"transition","ARCHIVE");
  int status;
  try(var conn=context.getBean(javax.sql.DataSource.class).getConnection();var pool=Executors.newSingleThreadExecutor()) {
   conn.setAutoCommit(false);
   try(var q=conn.prepareStatement("select id from media_asset where id=? for update")){q.setString(1,asset);q.executeQuery().close();}
   var archive=pool.submit(()->http(user,"POST",root()+"/listings/"+listing+"/transitions",body));
   try {
    org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(()->jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and lower(query) like '%update%media_asset%'",Integer.class)>0);
    assertThat(archive.isDone()).isFalse();
    try(var q=conn.prepareStatement("update media_asset set media_version='v2' where id=?")){q.setString(1,asset);assertThat(q.executeUpdate()).isEqualTo(1);}
   } finally {conn.commit();}
   status=archive.get(15,TimeUnit.SECONDS).statusCode();
  }
  var media=jdbc.queryForMap("select media_version,publish_status from media_asset where id=?",asset);
  System.out.println("REVIEW_ARCHIVE_RACE listing="+listing+" asset="+asset+" http="+status+" media="+media+" listingState="+jdbc.queryForMap("select status,aggregate_version from marketplace_listing where id=?",listing)+" receipts="+commands()+" events="+events());
  assertThat(status).isEqualTo(200);
  assertThat(jdbc.queryForMap("select status,aggregate_version from marketplace_listing where id=?",listing))
    .containsEntry("status","ARCHIVED").containsEntry("aggregate_version",5L);
  assertThat(commands()).isEqualTo(5);assertThat(events()).isEqualTo(5);
  assertThat(jdbc.queryForObject("select count(*) from outbox_events where event_type='marketplace.listing.archived' and aggregate_id=?",Integer.class,listing)).isEqualTo(1);
  assertThat(media.get("media_version")).isEqualTo("v2");
  assertThat(media.get("publish_status")).as("Archive of pinned v1 must never mutate changed v2").isEqualTo("PUBLISHED");
 }
 @Test void reviewAlreadyChangedVersionArchivePositiveControl() throws Exception {
  String asset=asset();var published=publish(approve(submit(create(asset))));String id=published.path("id").asText();
  jdbc.update("update media_asset set media_version='v2' where id=?",asset);
  var body=Map.of("commandId","stale-archive","expectedVersion",published.path("version").asLong(),"transition","ARCHIVE");
  response(http(user,"POST",root()+"/listings/"+id+"/transitions",body),200);
  assertThat(jdbc.queryForObject("select publish_status from media_asset where id=?",String.class,asset)).isEqualTo("PUBLISHED");
  assertThat(jdbc.queryForObject("select status from marketplace_listing where id=?",String.class,id)).isEqualTo("ARCHIVED");
  var after=state();response(http(user,"POST",root()+"/listings/"+id+"/transitions",body),200);assertThat(state()).isEqualTo(after);
  System.out.println("REVIEW_STALE_ARCHIVE_CONTROL listing="+id+" currentMedia=v2/PUBLISHED listing=ARCHIVED retryNoMutation=true");
 }

 @Test void currentArchiveIsAtomicAndRetryDoesNotRepeatEffects() throws Exception {
  String asset=asset();var published=publish(approve(submit(create(asset))));String id=published.path("id").asText();
  var body=Map.of("commandId","archive-fault","expectedVersion",4,"transition","ARCHIVE");
  String path=root()+"/listings/"+id+"/transitions";var before=state();
  jdbc.execute("create function archive_receipt_fault() returns trigger language plpgsql as $$ begin if NEW.command_id='archive-fault' then raise exception 'injected archive receipt failure'; end if; return NEW; end $$");
  jdbc.execute("create trigger archive_receipt_fault before insert on marketplace_command for each row execute function archive_receipt_fault()");
  try {assertThat(http(user,"POST",path,body).statusCode()).isEqualTo(500);assertThat(state()).isEqualTo(before);
   assertThat(jdbc.queryForMap("select media_version,publish_status from media_asset where id=?",asset)).containsEntry("media_version","v1").containsEntry("publish_status","PUBLISHED");
  } finally {jdbc.execute("drop trigger archive_receipt_fault on marketplace_command");jdbc.execute("drop function archive_receipt_fault()");}
  response(http(user,"POST",path,body),200);
  assertThat(jdbc.queryForMap("select media_version,publish_status from media_asset where id=?",asset)).containsEntry("media_version","v1").containsEntry("publish_status","ARCHIVED");
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
  as(user,()->{
   var media=context.getBean(MediaAssets.class);
   assertThat(media.archivePublicationIfCurrent(tenant,project,"missing", "v1")).isFalse();
   assertThat(media.archivePublicationIfCurrent(tenant,project,asset, "v2")).isFalse();
   assertThatThrownBy(()->media.archivePublicationIfCurrent(tenant,"foreign-project",asset,"v1")).isInstanceOf(RuntimeException.class);
  });
  assertThat(state()).isEqualTo(before);
  // Current canonical FK disallows a dangling admitted listing. Missing Media is
  // exercised at its owner boundary without disabling referential integrity.
  response(http(user,"POST",root()+"/listings/"+id+"/transitions",body),200);
  assertThat(commands()).isEqualTo(5);assertThat(events()).isEqualTo(5);
 }
 @Test void archiveHoldingMediaLockCommitsBeforeFollowingVersionWriter() throws Exception {
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
    writer=pool.submit(()->jdbc.update("update media_asset set media_version='v2',publish_status='PUBLISHED' where id=?",asset));
    org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(()->jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%update media_asset%'",Integer.class)>0);
    assertThat(archive.isDone()).isFalse();assertThat(writer.isDone()).isFalse();
   } finally {latch.createStatement().execute("select pg_advisory_unlock(715290020)");}
   response(archive.get(15,TimeUnit.SECONDS),200);assertThat(writer.get(15,TimeUnit.SECONDS)).isEqualTo(1);
  } finally {jdbc.execute("drop trigger archive_receipt_latch on marketplace_command");jdbc.execute("drop function archive_receipt_latch()");}
  assertThat(jdbc.queryForMap("select media_version,publish_status from media_asset where id=?",asset)).containsEntry("media_version","v2").containsEntry("publish_status","PUBLISHED");
  assertThat(jdbc.queryForMap("select status,aggregate_version from marketplace_listing where id=?",id)).containsEntry("status","ARCHIVED").containsEntry("aggregate_version",5L);
  assertThat(commands()).isEqualTo(5);assertThat(events()).isEqualTo(5);
 }

}
