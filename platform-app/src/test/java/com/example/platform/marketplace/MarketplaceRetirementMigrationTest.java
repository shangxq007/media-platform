package com.example.platform.marketplace;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MarketplaceRetirementMigrationTest extends PostgresTestContainerSupport {
 record Payload(String bytes,boolean publication) {}
 record Expected(Map<String,Object> job,Map<String,Object> task,boolean retireJob,boolean retireTask) {}
 @Test void exactTopLevelIntentAndSharedEligibilityPreserveAllOtherBytesAndState() {
  String schema=isolatedSchemaName();var admin=new JdbcTemplate(createDataSource());admin.execute("create schema "+schema);
  try {
   Flyway.configure().dataSource(jdbcUrl(),username(),password()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("5").load().migrate();
   var jdbc=new JdbcTemplate(new DriverManagerDataSource(jdbcUrl()+(jdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema,username(),password()));
   var expected=new LinkedHashMap<String,Expected>();
   for(var payload:List.of(
    new Payload("{\"reason\":\"asset.published\"}",true),
    new Payload("{\"reason\":\"asset.archived\"}",true),
    new Payload(" { \"z\": 1, \"reason\" : \"asset.published\", \"assetId\": \"asset\" } ",true),
    new Payload("{\"reason\":\"asset.enriched\",\"history\":{\"reason\":\"asset.published\"}}",false),
    new Payload("{\"reason\":\"asset.enriched\",\"history\":{\"reason\":\"asset.archived\"}}",false),
    new Payload("{\"text\":\"reason asset.published\", \"reason\":\"asset.enriched\"}",false),
    new Payload("{\"text\":\"\\\"reason\\\":\\\"asset.published\\\"\"}",false),
    new Payload("{}",false),
    new Payload("{\"reason\":null}",false),
    new Payload("{\"reason\":123}",false),
    new Payload("{\"reason\":true}",false),
    new Payload("{\"reason\":{\"reason\":\"asset.published\"}}",false),
    new Payload("{\"reason\":[\"asset.published\"]}",false),
    new Payload("null",false),
    new Payload("[]",false),
    new Payload("[{\"reason\":\"asset.published\"}]",false),
    new Payload("\"asset.published\"",false),
    new Payload("not json",false),
    new Payload("{\"reason\":\"asset.published\",",false),
    new Payload("{\"reason\":\"asset.published\",\"reason\":\"asset.enriched\"}",false),
    new Payload("{\"reason\":\"asset.enriched\",\"reason\":\"asset.published\"}",false),
    new Payload("{\"reason\":\"asset.published\",\"reason\":\"asset.published\"}",false),
    new Payload("{\"reason\":\"asset.published\",\"assetId\":\"a\",\"assetId\":\"b\"}",false),
    new Payload("{\"reason\":\"asset.published \",\"x\":1}",false),
    new Payload("{\"reason\":\"asset.\\u0000published\"}",false)
   )) seed(jdbc,expected,payload.bytes(),"SEARCH_REINDEX",null,"project","PENDING","PENDING",payload.publication());
   seed(jdbc,expected,null,"SEARCH_REINDEX",null,"project","PENDING","PENDING",false);
   for(String type:List.of("SEARCH_REINDEX","MEDIA_INGEST"))
    for(String[] scope:List.of(new String[]{null,"project"},new String[]{"tenant",null},new String[]{" "," "},new String[]{"tenant","project"}))
     for(String job:List.of("PENDING","RUNNING","RETRY","COMPLETED","FAILED","CANCELLED"))
      for(String task:List.of("PENDING","RUNNING","RETRY","COMPLETED","FAILED"))
       seed(jdbc,expected,"{\"reason\":\"asset.archived\"}",type,scope[0],scope[1],job,task,
        type.equals("SEARCH_REINDEX")&&!("tenant".equals(scope[0])&&"project".equals(scope[1]))&&List.of("PENDING","RUNNING","RETRY").contains(job));
   Flyway.configure().dataSource(jdbcUrl(),username(),password()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load().migrate();
   for(var e:expected.entrySet()) {
    var afterJob=jdbc.queryForMap("select * from platform_job where id=?",e.getKey());
    var afterTask=jdbc.queryForMap("select * from platform_task where job_id=?",e.getKey());
    var before=e.getValue();
    if(before.retireJob()) {
     assertThat(afterJob.get("status")).isEqualTo("FAILED");assertThat(afterJob.get("updated_at")).isNotNull();
     afterJob.put("status",before.job().get("status"));afterJob.put("updated_at",before.job().get("updated_at"));
    }
    if(before.retireTask()) {
     assertThat(afterTask.get("status")).isEqualTo("FAILED");assertThat(afterTask.get("error_message")).isEqualTo("MARKETPLACE_SCOPE_MISSING: legacy publication reindex needs owner reconciliation");
     assertThat(afterTask.get("updated_at")).isNotNull();
     for(String key:List.of("status","error_message","updated_at"))afterTask.put(key,before.task().get(key));
    }
    assertThat(afterJob).as("job %s must preserve every other field",e.getKey()).isEqualTo(before.job());
    assertThat(afterTask).as("task %s must preserve every other field",e.getKey()).isEqualTo(before.task());
   }
   System.out.println("RETIREMENT_MATRIX rows="+expected.size()+" exactWholeRowPreservation=true schema="+schema);
  } finally {admin.execute("drop schema "+schema+" cascade");}
 }
 private void seed(JdbcTemplate jdbc,Map<String,Expected> rows,String payload,String type,String tenant,String project,String jobState,String taskState,boolean retire) {
  String id="case-"+rows.size();
  jdbc.update("insert into platform_job(id,job_type,aggregate_type,aggregate_id,tenant_id,project_id,status,payload_json,metadata_json,created_at) values (?,?,'ASSET','asset',?,?,?,?,'unchanged metadata',now())",id,type,tenant,project,jobState,payload);
  jdbc.update("insert into platform_task(id,job_id,task_type,capability,status,error_message,result_json,attempt_count,created_at) values (?,?,'REINDEX','REINDEX',?,'original reason','original result',2,now())","task-"+id,id,taskState);
  rows.put(id,new Expected(jdbc.queryForMap("select * from platform_job where id=?",id),jdbc.queryForMap("select * from platform_task where job_id=?",id),retire,retire&&List.of("PENDING","RUNNING","RETRY").contains(taskState)));
 }
}
