package com.example.platform.prompt.infrastructure;

import com.example.platform.prompt.domain.PromptTemplate;
import com.example.platform.prompt.domain.PromptTemplateStatus;
import com.example.platform.prompt.domain.PromptTemplateVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptJdbcRepositoryTest {

    private PromptJdbcRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema-prompt-h2.sql")
                .build();
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
