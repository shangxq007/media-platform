package com.example.platform.social;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class SocialOwnershipMigrationPostgresTest extends PostgresTestContainerSupport {
    @Test void deliveredSchemaUpgradesWithoutLosingHistoricalRowsOrRetryEvidence() {
        String schema=isolatedSchemaName();
        var config=Flyway.configure().dataSource(jdbcUrl(),username(),password()).schemas(schema).defaultSchema(schema).locations("classpath:db/migration");
        config.target("7").load().migrate();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(jdbcUrl()+(jdbcUrl().contains("?")?"&":"?")+"currentSchema="+schema,username(),password()));
        for(String status:new String[]{"DRAFT","SCHEDULED","PUBLISHED","PUBLISHING","FAILED"})
            jdbc.update("INSERT INTO social_post(id,tenant_id,user_id,platform_type,status,retry_count,platform_post_id) VALUES (?,'tenant','actor','TWITTER',?,3,'legacy-result')",status,status);
        assertThat(config.target("latest").load().migrate().migrationsExecuted).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM social_post",Integer.class)).isEqualTo(5);
        for(String status:new String[]{"DRAFT","SCHEDULED","PUBLISHED","PUBLISHING","FAILED"}) {
            var row=jdbc.queryForMap("SELECT * FROM social_post WHERE id=?",status);
            assertThat(row).containsEntry("retry_count",3).containsEntry("platform_post_id","legacy-result").containsEntry("publication_attempt_id",null);
            assertThat(row.get("status")).isEqualTo(status.equals("FAILED")||status.equals("PUBLISHING")?"UNRESOLVED":status);
        }
        assertThat(config.load().validateWithResult().validationSuccessful).isTrue();
    }
}
