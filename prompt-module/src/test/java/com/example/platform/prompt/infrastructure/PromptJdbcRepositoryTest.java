package com.example.platform.prompt.infrastructure;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.prompt.testsupport.PromptTestSchemaFixture;
import com.example.platform.prompt.domain.PromptTemplate;
import com.example.platform.prompt.domain.PromptTemplateStatus;
import com.example.platform.prompt.domain.PromptTemplateVersion;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptJdbcRepositoryTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private PromptJdbcRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        PromptTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        PromptTestSchemaFixture.truncate(dsl);
        repository = new PromptJdbcRepository(new JdbcTemplate(dataSource));
    }

    @Test
    void shouldPersistAndLoadTemplateAndVersion() {
        OffsetDateTime now = OffsetDateTime.now();
        PromptTemplate template = new PromptTemplate(
                "pt-1", "Demo", "desc", "general", List.of("a"),
                "owner", PromptTemplateStatus.DRAFT, "1.0.0", null, now, now);
        repository.saveTemplate(template);

        PromptTemplateVersion version = new PromptTemplateVersion(
                "pv-1", "pt-1", "1.0.0", "Hello {{name}}", "{}",
                "init", "owner", now, "abc", null, false);
        repository.saveVersion(version);

        assertEquals(1, repository.loadAllTemplates().size());
        assertEquals(1, repository.loadVersionsForTemplate("pt-1").size());
        assertTrue(repository.existsByDerivedCode("demo"));
    }
}
