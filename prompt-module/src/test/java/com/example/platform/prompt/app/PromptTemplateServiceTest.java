package com.example.platform.prompt.app;

import com.example.platform.prompt.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PromptTemplateServiceTest {

    private PromptTemplateService service;

    @BeforeEach
    void setUp() {
        service = new PromptTemplateService();
    }

    @Test
    void shouldCreateTemplate() {
        PromptTemplate template = service.createTemplate(
                "Test Template", "A test", "test", List.of("tag1"), "user-1", "1.0.0");
        assertNotNull(template);
        assertNotNull(template.templateId());
        assertEquals("Test Template", template.name());
        assertEquals(PromptTemplateStatus.DRAFT, template.status());
        assertEquals("1.0.0", template.schemaVersion());
    }

    @Test
    void shouldRejectDuplicateCode() {
        service.createTemplate("Test Template", "A test", "test", List.of(), "user-1", "1.0.0");
        assertThrows(Exception.class, () ->
                service.createTemplate("Test Template", "Dup", "test", List.of(), "user-1", "1.0.0"));
    }

    @Test
    void shouldCreateVersion() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        PromptTemplateVersion version = service.createVersion(template.templateId(),
                "Hello {{name}}", "{\"type\":\"object\"}", "Initial version", "user-1");
        assertNotNull(version);
        assertEquals("1.0.0", version.promptVersion());
        assertEquals("Hello {{name}}", version.templateBody());
        assertNotNull(version.checksum());
    }

    @Test
    void shouldIncrementVersion() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        service.createVersion(template.templateId(), "V1", "{}", "V1", "user-1");
        PromptTemplateVersion v2 = service.createVersion(template.templateId(), "V2", "{}", "V2", "user-1");
        assertEquals("1.0.1", v2.promptVersion());
    }

    @Test
    void shouldRenderTemplate() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        service.createVersion(template.templateId(), "Hello {{name}}!", "{}", "V1", "user-1");
        PromptRenderResult result = service.render(template.templateId(), null,
                Map.of("name", "World"), false);
        assertEquals("Hello World!", result.renderedPrompt());
        assertTrue(result.missingVariables().isEmpty());
    }

    @Test
    void shouldDetectMissingVariables() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        service.createVersion(template.templateId(), "Hello {{name}} from {{place}}!", "{}", "V1", "user-1");
        PromptRenderResult result = service.render(template.templateId(), null,
                Map.of("name", "World"), false);
        assertTrue(result.missingVariables().contains("place"));
    }

    @Test
    void shouldRedactSensitiveVariables() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        service.createVersion(template.templateId(), "API: {{api_key}}", "{}", "V1", "user-1");
        PromptRenderResult result = service.render(template.templateId(), null,
                Map.of("api_key", "secret123"), false);
        assertTrue(result.redactedPrompt().contains("[REDACTED]"));
        assertFalse(result.redactedPrompt().contains("secret123"));
    }

    @Test
    void shouldValidateTemplate() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        service.createVersion(template.templateId(), "Valid body", "{}", "V1", "user-1");
        PromptValidationResult result = service.validateTemplate(template.templateId());
        assertTrue(result.valid());
    }

    @Test
    void shouldValidateTemplateWithErrors() {
        PromptTemplate template = service.createTemplate("", "Desc", "cat", List.of(), "user-1", "1.0.0");
        PromptValidationResult result = service.validateTemplate(template.templateId());
        assertFalse(result.valid());
        assertFalse(result.errors().isEmpty());
    }

    @Test
    void shouldDiffVersions() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        PromptTemplateVersion v1 = service.createVersion(template.templateId(), "Version 1", "{}", "V1", "user-1");
        PromptTemplateVersion v2 = service.createVersion(template.templateId(), "Version 2", "{}", "V2", "user-1");
        PromptTemplateService.PromptVersionDiff diff = service.diffVersions(v1.versionId(), v2.versionId());
        assertNotNull(diff.bodyDiff());
        assertTrue(diff.bodyDiff().contains("- Version 1"));
        assertTrue(diff.bodyDiff().contains("+ Version 2"));
    }

    @Test
    void shouldRollbackVersion() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        service.createVersion(template.templateId(), "Version 1", "{}", "V1", "user-1");
        service.createVersion(template.templateId(), "Version 2", "{}", "V2", "user-1");
        PromptTemplateVersion rolled = service.rollbackToVersion(template.templateId(), "1.0.0");
        assertNotNull(rolled);
        assertTrue(rolled.templateBody().contains("Version 1"));
    }

    @Test
    void shouldActivateTemplate() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        PromptTemplate activated = service.activateTemplate(template.templateId());
        assertEquals(PromptTemplateStatus.ACTIVE, activated.status());
    }

    @Test
    void shouldDeprecateTemplate() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        PromptTemplate deprecated = service.deprecateTemplate(template.templateId());
        assertEquals(PromptTemplateStatus.DEPRECATED, deprecated.status());
    }

    @Test
    void shouldStartExecution() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        service.createVersion(template.templateId(), "Hello {{name}}", "{}", "V1", "user-1");
        service.activateTemplate(template.templateId());
        PromptExecutionRun run = service.startExecution(template.templateId(), null,
                "tenant-1", "user-1", "openai", "gpt-4",
                Map.of("name", "World"), null, null);
        assertNotNull(run);
        assertEquals(PromptExecutionStatus.PENDING, run.status());
        assertNotNull(run.renderedPromptHash());
        assertTrue(run.tokenEstimate() > 0);
    }

    @Test
    void shouldCompleteExecution() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        service.createVersion(template.templateId(), "Hello", "{}", "V1", "user-1");
        service.activateTemplate(template.templateId());
        PromptExecutionRun run = service.startExecution(template.templateId(), null,
                "tenant-1", "user-1", "openai", "gpt-4", Map.of(), null, null);
        PromptExecutionRun completed = service.completeExecution(run.executionId(), "Success");
        assertEquals(PromptExecutionStatus.SUCCEEDED, completed.status());
        assertNotNull(completed.finishedAt());
    }

    @Test
    void shouldAnalyzeRisk() {
        PromptRiskLevel lowRisk = service.analyzeRisk("Hello world", Map.of());
        assertEquals(PromptRiskLevel.LOW, lowRisk);

        // Content with destructive commands + secrets = CRITICAL
        PromptRiskLevel criticalRisk = service.analyzeRisk(
                "rm -rf / && password: secret12345678", Map.of());
        assertTrue(criticalRisk.ordinal() >= PromptRiskLevel.HIGH.ordinal());

        // Content with only destructive commands = MEDIUM
        PromptRiskLevel mediumRisk = service.analyzeRisk(
                "terraform destroy all resources", Map.of());
        assertTrue(mediumRisk.ordinal() >= PromptRiskLevel.MEDIUM.ordinal());
    }

    @Test
    void shouldScanPromptFiles() {
        List<String> contents = List.of(
                "---\nname: Test Prompt\ntest-prompt\n---\nHello {{name}}",
                "---\nname: Another Prompt\nanother-prompt\n---\nWorld");
        List<String> names = List.of("test-prompt.md", "another-prompt.md");
        PromptTemplateService.PromptFileScanResult result = service.scanPromptFiles(contents, names);
        assertEquals(2, result.imported());
        assertEquals(0, result.conflicts());
    }

    @Test
    void shouldImportPromptFile() {
        String content = "---\nname: Imported Prompt\ncategory: test\n---\nHello {{name}}!";
        PromptTemplate imported = service.importPromptFile(content, "imported.md", "user-1");
        assertNotNull(imported);
        assertEquals("Imported Prompt", imported.name());
        // Template is created as DRAFT, then activated
        assertTrue(imported.status() == PromptTemplateStatus.ACTIVE || imported.status() == PromptTemplateStatus.DRAFT);
    }

    @Test
    void shouldEvaluateExecution() {
        PromptTemplate template = service.createTemplate("Test", "Desc", "cat", List.of(), "user-1", "1.0.0");
        service.createVersion(template.templateId(), "Hello", "{}", "V1", "user-1");
        service.activateTemplate(template.templateId());
        PromptExecutionRun run = service.startExecution(template.templateId(), null,
                "tenant-1", "user-1", "openai", "gpt-4", Map.of(), null, null);
        PromptEvaluationResult result = service.evaluateExecution(run.executionId(), "reviewer-1",
                true, true, true, true, false, false, false, false);
        assertNotNull(result);
        assertEquals("PASS", result.overallVerdict());
    }
}
