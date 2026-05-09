package com.example.platform.prompt.app;

import com.example.platform.prompt.domain.PromptTemplate;
import com.example.platform.prompt.domain.PromptVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PromptRenderServiceTest {

    private PromptRenderService service;

    @BeforeEach
    void setUp() {
        service = new PromptRenderService();
    }

    @Test
    void renderThrowsForUnknownTemplateCode() {
        assertThrows(IllegalArgumentException.class,
                () -> service.render("nonexistent", Map.of()));
    }

    @Test
    void renderSubstitutesSingleVariable() {
        service.createTemplate("greeting", "Hello, {{name}}!", List.of("name"));
        String result = service.render("greeting", Map.of("name", "World"));
        assertEquals("Hello, World!", result);
    }

    @Test
    void renderSubstitutesMultipleVariables() {
        service.createTemplate("intro", "{{greeting}}, {{name}}! Welcome to {{place}}.",
                List.of("greeting", "name", "place"));
        String result = service.render("intro", Map.of(
                "greeting", "Hello", "name", "Alice", "place", "Wonderland"));
        assertEquals("Hello, Alice! Welcome to Wonderland.", result);
    }

    @Test
    void renderWithNullVariablesReturnsRawContent() {
        service.createTemplate("static", "No variables here.", List.of());
        String result = service.render("static", null);
        assertEquals("No variables here.", result);
    }

    @Test
    void renderWithEmptyVariablesReturnsRawContent() {
        service.createTemplate("static2", "No variables here.", List.of());
        String result = service.render("static2", Map.of());
        assertEquals("No variables here.", result);
    }

    @Test
    void renderWithMissingVariableReplacesWithEmpty() {
        service.createTemplate("partial", "Hello, {{name}}! Age: {{age}}.",
                List.of("name", "age"));
        String result = service.render("partial", Map.of("name", "Bob"));
        assertEquals("Hello, Bob! Age: .", result);
    }

    @Test
    void renderWithNoVariablesInTemplate() {
        service.createTemplate("novars", "Plain text without placeholders.", List.of());
        String result = service.render("novars", Map.of("unused", "value"));
        assertEquals("Plain text without placeholders.", result);
    }

    @Test
    void createTemplateReturnsTemplateWithGeneratedId() {
        PromptTemplate template = service.createTemplate("my-tpl", "Content {{x}}", List.of("x"));
        assertNotNull(template.id());
        assertTrue(template.id().startsWith("pt-"));
        assertEquals("my-tpl", template.code());
        assertEquals("Content {{x}}", template.content());
        assertEquals(List.of("x"), template.variables());
        assertEquals("ACTIVE", template.status());
    }

    @Test
    void createTemplateThrowsForDuplicateCode() {
        service.createTemplate("dup", "Content", List.of());
        assertThrows(IllegalArgumentException.class,
                () -> service.createTemplate("dup", "Other", List.of()));
    }

    @Test
    void createTemplateWithNullVariablesUsesEmptyList() {
        PromptTemplate template = service.createTemplate("nullvars", "Content", null);
        assertTrue(template.variables().isEmpty());
    }

    @Test
    void findTemplateByCodeReturnsTemplate() {
        service.createTemplate("findable", "Content {{v}}", List.of("v"));
        Optional<PromptTemplate> found = service.findTemplateByCode("findable");
        assertTrue(found.isPresent());
        assertEquals("findable", found.get().code());
    }

    @Test
    void findTemplateByCodeReturnsEmptyForUnknown() {
        Optional<PromptTemplate> found = service.findTemplateByCode("missing");
        assertTrue(found.isEmpty());
    }

    @Test
    void findTemplateByIdReturnsTemplate() {
        PromptTemplate created = service.createTemplate("byid", "Content", List.of());
        Optional<PromptTemplate> found = service.findTemplateById(created.id());
        assertTrue(found.isPresent());
        assertEquals("byid", found.get().code());
    }

    @Test
    void listTemplatesReturnsAllCreated() {
        service.createTemplate("t1", "C1", List.of());
        service.createTemplate("t2", "C2", List.of());
        assertEquals(2, service.listTemplates().size());
    }

    @Test
    void createVersionReturnsVersionWithIncrementingNumbers() {
        PromptTemplate template = service.createTemplate("ver", "v1", List.of());
        PromptVersion v1 = service.createVersion(template.id(), "content v1", "initial");
        PromptVersion v2 = service.createVersion(template.id(), "content v2", "updated");

        assertEquals(1, v1.version());
        assertEquals(2, v2.version());
        assertEquals(template.id(), v1.templateId());
        assertEquals(template.id(), v2.templateId());
        assertEquals("content v1", v1.content());
        assertEquals("content v2", v2.content());
        assertEquals("initial", v1.changelog());
        assertEquals("updated", v2.changelog());
    }

    @Test
    void createVersionThrowsForUnknownTemplateId() {
        assertThrows(IllegalArgumentException.class,
                () -> service.createVersion("pt-999", "content", "changelog"));
    }

    @Test
    void listVersionsReturnsAllVersionsForTemplate() {
        PromptTemplate template = service.createTemplate("vlist", "v1", List.of());
        service.createVersion(template.id(), "c1", "ch1");
        service.createVersion(template.id(), "c2", "ch2");
        List<PromptVersion> versions = service.listVersions(template.id());
        assertEquals(2, versions.size());
    }

    @Test
    void listVersionsReturnsEmptyForNoVersions() {
        PromptTemplate template = service.createTemplate("novers", "v1", List.of());
        List<PromptVersion> versions = service.listVersions(template.id());
        assertTrue(versions.isEmpty());
    }

    @Test
    void listTemplatesReturnsImmutableList() {
        service.createTemplate("immutable", "C", List.of());
        List<PromptTemplate> templates = service.listTemplates();
        assertThrows(UnsupportedOperationException.class, () -> templates.add(
                new PromptTemplate("x", "x", "x", List.of(), "x")));
    }
}
