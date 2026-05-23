package com.example.platform.prompt.app;

import com.example.platform.prompt.domain.*;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.web.ConfigurableErrorCode;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.prompt.infrastructure.PromptJdbcRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Comprehensive prompt template management service.
 */
@Service
public class PromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    private final Map<String, PromptTemplate> templatesById = new ConcurrentHashMap<>();
    private final Map<String, PromptTemplate> templatesByCode = new ConcurrentHashMap<>();
    private final Map<String, PromptTemplateVersion> versionsById = new ConcurrentHashMap<>();
    private final Map<String, List<PromptTemplateVersion>> versionsByTemplateId = new ConcurrentHashMap<>();
    private final Map<String, PromptExecutionRun> executionsById = new ConcurrentHashMap<>();
    private final AtomicLong templateSeq = new AtomicLong(0);
    private final AtomicLong versionSeq = new AtomicLong(0);
    private final AtomicLong executionSeq = new AtomicLong(0);
    private final Optional<PromptJdbcRepository> jdbcRepository;
    private AuditPort auditPort;

    public PromptTemplateService() {
        this(Optional.empty());
    }

    @Autowired
    public PromptTemplateService(Optional<PromptJdbcRepository> jdbcRepository) {
        this.jdbcRepository = jdbcRepository != null ? jdbcRepository : Optional.empty();
    }

    public void setAuditPort(AuditPort port) { this.auditPort = port; }

    /** Restores state from JDBC on application startup. */
    public void hydrateTemplate(PromptTemplate template) {
        templatesById.put(template.templateId(), template);
        templatesByCode.put(generateCode(template.name()), template);
        versionsByTemplateId.putIfAbsent(template.templateId(), new ArrayList<>());
        bumpSeq(template.templateId(), "pt-", templateSeq);
    }

    public void hydrateVersion(PromptTemplateVersion version) {
        versionsById.put(version.versionId(), version);
        List<PromptTemplateVersion> versions = versionsByTemplateId.computeIfAbsent(
                version.templateId(), id -> new ArrayList<>());
        if (versions.stream().noneMatch(v -> v.versionId().equals(version.versionId()))) {
            versions.add(version);
        }
        bumpSeq(version.versionId(), "pv-", versionSeq);
    }

    public void hydrateExecution(PromptExecutionRun run) {
        executionsById.put(run.executionId(), run);
        bumpSeq(run.executionId(), "pe-", executionSeq);
    }

    // -------------------------------------------------------------------------
    // Template CRUD
    // -------------------------------------------------------------------------

    public PromptTemplate createTemplate(String name, String description, String category,
            List<String> tags, String owner, String schemaVersion) {
        String templateId = "pt-" + templateSeq.incrementAndGet();
        String code = generateCode(name);
        if (codeExists(code)) {
            throw new PlatformException(
                    new ConfigurableErrorCode("PROMPT-409-001", 409601,
                            Map.of("en", "Prompt template code already exists", "zh", "提示词模板编码已存在"),
                            "prompt", 409),
                    "Template code already exists: " + code,
                    Map.of("code", code), "en");
        }
        OffsetDateTime now = OffsetDateTime.now();
        PromptTemplate template = new PromptTemplate(templateId, name, description, category,
                tags != null ? List.copyOf(tags) : List.of(), owner,
                PromptTemplateStatus.DRAFT, schemaVersion, null, now, now);
        storeTemplate(template, code);
        versionsByTemplateId.put(templateId, new ArrayList<>());
        audit("CREATE", templateId, Map.of("name", name, "category", category));
        log.info("PromptTemplateService: created template {} ({})", templateId, code);
        return template;
    }

    public PromptTemplate updateTemplate(String templateId, String name, String description,
            String category, List<String> tags) {
        PromptTemplate existing = templatesById.get(templateId);
        if (existing == null) {
            throw notFound(templateId);
        }
        PromptTemplate updated = existing.withUpdatedFields(name, description, category, tags);
        storeTemplate(updated, generateCode(name));
        audit("UPDATE", templateId, Map.of("name", name));
        return updated;
    }

    public PromptTemplate getTemplate(String templateId) {
        PromptTemplate template = templatesById.get(templateId);
        if (template == null) throw notFound(templateId);
        return template;
    }

    public Optional<PromptTemplate> findTemplateByCode(String code) {
        return Optional.ofNullable(templatesByCode.get(code));
    }

    public Optional<PromptTemplate> findTemplateById(String templateId) {
        return Optional.ofNullable(templatesById.get(templateId));
    }

    public List<PromptTemplate> listTemplates() {
        return List.copyOf(templatesById.values());
    }

    public List<PromptTemplate> listTemplatesByStatus(PromptTemplateStatus status) {
        return templatesById.values().stream()
                .filter(t -> t.status() == status)
                .toList();
    }

    public PromptTemplate activateTemplate(String templateId) {
        PromptTemplate existing = templatesById.get(templateId);
        if (existing == null) throw notFound(templateId);
        PromptTemplate activated = existing.withStatus(PromptTemplateStatus.ACTIVE);
        storeTemplate(activated, generateCode(activated.name()));
        audit("ACTIVATE", templateId, Map.of());
        return activated;
    }

    public PromptTemplate deprecateTemplate(String templateId) {
        PromptTemplate existing = templatesById.get(templateId);
        if (existing == null) throw notFound(templateId);
        PromptTemplate deprecated = existing.withStatus(PromptTemplateStatus.DEPRECATED);
        storeTemplate(deprecated, generateCode(deprecated.name()));
        audit("DEPRECATE", templateId, Map.of());
        return deprecated;
    }

    public PromptTemplate archiveTemplate(String templateId) {
        PromptTemplate existing = templatesById.get(templateId);
        if (existing == null) throw notFound(templateId);
        PromptTemplate archived = existing.withStatus(PromptTemplateStatus.ARCHIVED);
        storeTemplate(archived, generateCode(archived.name()));
        audit("ARCHIVE", templateId, Map.of());
        return archived;
    }

    // -------------------------------------------------------------------------
    // Version Management
    // -------------------------------------------------------------------------

    public PromptTemplateVersion createVersion(String templateId, String templateBody,
            String variableSchemaJson, String changelog, String createdBy) {
        PromptTemplate template = templatesById.get(templateId);
        if (template == null) throw notFound(templateId);

        List<PromptTemplateVersion> versions = versionsByTemplateId.getOrDefault(templateId, new ArrayList<>());
        String newVersion = incrementVersion(versions);
        String versionId = "pv-" + versionSeq.incrementAndGet();
        String previousVersion = versions.isEmpty() ? null : versions.get(versions.size() - 1).promptVersion();
        String checksum = computeChecksum(templateBody);

        PromptTemplateVersion version = new PromptTemplateVersion(
                versionId, templateId, newVersion, templateBody, variableSchemaJson,
                changelog, createdBy, OffsetDateTime.now(), checksum, previousVersion, false);
        versions.add(version);
        versionsById.put(versionId, version);
        versionsByTemplateId.put(templateId, versions);
        jdbcRepository.ifPresent(r -> r.saveVersion(version));

        // Update template's current version
        PromptTemplate updated = template.withCurrentVersion(newVersion);
        storeTemplate(updated, generateCode(updated.name()));

        audit("CREATE_VERSION", templateId, Map.of("version", newVersion));
        log.info("PromptTemplateService: created version {} for template {}", newVersion, templateId);
        return version;
    }

    public PromptTemplateVersion getVersion(String versionId) {
        PromptTemplateVersion version = versionsById.get(versionId);
        if (version == null) {
            throw new PlatformException(
                    new ConfigurableErrorCode("PROMPT-404-001", 404601,
                            Map.of("en", "Prompt template version not found", "zh", "提示词模板版本不存在"),
                            "prompt", 404),
                    "Version not found: " + versionId,
                    Map.of("versionId", versionId), "en");
        }
        return version;
    }

    public List<PromptTemplateVersion> listVersions(String templateId) {
        return List.copyOf(versionsByTemplateId.getOrDefault(templateId, List.of()));
    }

    public PromptTemplateVersion getCurrentVersion(String templateId) {
        List<PromptTemplateVersion> versions = versionsByTemplateId.getOrDefault(templateId, List.of());
        if (versions.isEmpty()) return null;
        return versions.get(versions.size() - 1);
    }

    // -------------------------------------------------------------------------
    // Diff
    // -------------------------------------------------------------------------

    public PromptVersionDiff diffVersions(String versionId1, String versionId2) {
        PromptTemplateVersion v1 = getVersion(versionId1);
        PromptTemplateVersion v2 = getVersion(versionId2);
        return new PromptVersionDiff(
                v1.promptVersion(), v2.promptVersion(),
                diffBody(v1.templateBody(), v2.templateBody()),
                !Objects.equals(v1.variableSchemaJson(), v2.variableSchemaJson()));
    }

    // -------------------------------------------------------------------------
    // Rollback
    // -------------------------------------------------------------------------

    public PromptTemplateVersion rollbackToVersion(String templateId, String targetVersion) {
        List<PromptTemplateVersion> versions = versionsByTemplateId.getOrDefault(templateId, List.of());
        PromptTemplateVersion target = versions.stream()
                .filter(v -> v.promptVersion().equals(targetVersion))
                .findFirst()
                .orElseThrow(() -> new PlatformException(
                        new ConfigurableErrorCode("PROMPT-404-001", 404601,
                                Map.of("en", "Target version not found", "zh", "目标版本不存在"),
                                "prompt", 404),
                        "Version not found: " + targetVersion,
                        Map.of("templateId", templateId, "version", targetVersion), "en"));

        // Create new version with old content
        return createVersion(templateId, target.templateBody(), target.variableSchemaJson(),
                "Rollback to version " + targetVersion, "system");
    }

    // -------------------------------------------------------------------------
    // Render
    // -------------------------------------------------------------------------

    public PromptRenderResult render(String templateId, String promptVersion,
            Map<String, Object> variables, boolean dryRun) {
        PromptTemplateVersion version;
        if (promptVersion != null) {
            version = versionsByTemplateId.getOrDefault(templateId, List.of()).stream()
                    .filter(v -> v.promptVersion().equals(promptVersion))
                    .findFirst()
                    .orElseThrow(() -> notFound(templateId));
        } else {
            version = getCurrentVersion(templateId);
            if (version == null) throw notFound(templateId);
        }

        String templateBody = version.templateBody();
        List<String> missingVars = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Extract variables from template
        Matcher matcher = VARIABLE_PATTERN.matcher(templateBody);
        StringBuilder rendered = new StringBuilder();
        StringBuilder redacted = new StringBuilder();
        int end = 0;

        while (matcher.find()) {
            rendered.append(templateBody, end, matcher.start());
            redacted.append(templateBody, end, matcher.start());
            String varName = matcher.group(1).trim();
            Object value = variables != null ? variables.get(varName) : null;
            if (value == null) {
                if (variables == null || !variables.containsKey(varName)) {
                    missingVars.add(varName);
                    warnings.add("Missing variable: " + varName);
                }
                rendered.append("{{").append(varName).append("}}");
                redacted.append("{{").append(varName).append("}}");
            } else {
                String strValue = value.toString();
                rendered.append(strValue);
                // Redact sensitive-looking variables
                if (isSensitiveVariable(varName)) {
                    redacted.append("[REDACTED]");
                    warnings.add("Sensitive variable redacted: " + varName);
                } else {
                    redacted.append(strValue);
                }
            }
            end = matcher.end();
        }
        rendered.append(templateBody.substring(end));
        redacted.append(templateBody.substring(end));

        // Check for secrets in rendered content
        if (containsSecrets(rendered.toString())) {
            warnings.add("Potential secret detected in rendered output");
        }

        return new PromptRenderResult(rendered.toString(), redacted.toString(), missingVars, warnings);
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    public PromptValidationResult validateTemplate(String templateId) {
        PromptTemplate template = templatesById.get(templateId);
        if (template == null) return PromptValidationResult.error(List.of("Template not found"));

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (template.name() == null || template.name().isBlank()) {
            errors.add("Template name is required");
        }
        if (template.schemaVersion() == null || template.schemaVersion().isBlank()) {
            warnings.add("Schema version not set, defaulting to 1.0.0");
        }

        PromptTemplateVersion currentVersion = getCurrentVersion(templateId);
        if (currentVersion == null) {
            warnings.add("No versions defined for template");
        } else {
            if (currentVersion.templateBody() == null || currentVersion.templateBody().isBlank()) {
                errors.add("Current version has empty template body");
            }
            if (containsSecrets(currentVersion.templateBody())) {
                errors.add("Template body contains potential secrets");
            }
        }

        return errors.isEmpty()
                ? new PromptValidationResult(true, List.of(), warnings)
                : new PromptValidationResult(false, errors, warnings);
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    public PromptExecutionRun startExecution(String templateId, String promptVersion,
            String tenantId, String userId, String modelProvider, String modelName,
            Map<String, Object> inputVariables, String relatedPromptFile,
            String relatedManifestEntry) {
        PromptTemplateVersion version = promptVersion != null
                ? versionsByTemplateId.getOrDefault(templateId, List.of()).stream()
                        .filter(v -> v.promptVersion().equals(promptVersion))
                        .findFirst().orElse(null)
                : getCurrentVersion(templateId);

        if (version == null) throw notFound(templateId);

        String executionId = "pe-" + executionSeq.incrementAndGet();
        String renderedPromptHash = computeChecksum(version.templateBody());
        String redactedPreview = version.templateBody().length() > 200
                ? version.templateBody().substring(0, 200) + "..."
                : version.templateBody();

        // Redact sensitive variables
        String redactedVars = "{}";
        if (inputVariables != null) {
            Map<String, Object> redacted = new LinkedHashMap<>();
            for (var entry : inputVariables.entrySet()) {
                if (isSensitiveVariable(entry.getKey())) {
                    redacted.put(entry.getKey(), "[REDACTED]");
                } else {
                    redacted.put(entry.getKey(), entry.getValue());
                }
            }
            try {
                redactedVars = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(redacted);
            } catch (Exception e) {
                redactedVars = "{}";
            }
        }

        // Risk analysis
        PromptRiskLevel riskLevel = analyzeRisk(version.templateBody(), inputVariables);

        PromptExecutionRun run = new PromptExecutionRun(
                executionId, templateId, version.promptVersion(), tenantId, userId,
                modelProvider, modelName, renderedPromptHash, redactedPreview,
                redactedVars, null, PromptExecutionStatus.PENDING, riskLevel,
                estimateTokens(version.templateBody()), estimateCost(version.templateBody(), modelProvider),
                OffsetDateTime.now(), null, null, null,
                relatedPromptFile, relatedManifestEntry);

        storeExecution(run);
        audit("EXECUTION_START", executionId, Map.of("templateId", templateId, "riskLevel", riskLevel.name()));
        log.info("PromptTemplateService: started execution {} for template {}", executionId, templateId);
        return run;
    }

    public PromptExecutionRun completeExecution(String executionId, String outputSummary) {
        PromptExecutionRun existing = executionsById.get(executionId);
        if (existing == null) throw notFound("Execution: " + executionId);
        PromptExecutionRun completed = new PromptExecutionRun(
                existing.executionId(), existing.templateId(), existing.promptVersion(),
                existing.tenantId(), existing.userId(), existing.modelProvider(), existing.modelName(),
                existing.renderedPromptHash(), existing.redactedPromptPreview(),
                existing.inputVariablesRedactedJson(), outputSummary,
                PromptExecutionStatus.SUCCEEDED, existing.riskLevel(),
                existing.tokenEstimate(), existing.costEstimate(),
                existing.startedAt(), OffsetDateTime.now(), null, null,
                existing.relatedPromptFile(), existing.relatedManifestEntry());
        storeExecution(completed);
        audit("EXECUTION_COMPLETE", executionId, Map.of());
        return completed;
    }

    public PromptExecutionRun failExecution(String executionId, String errorCode, String errorDetails) {
        PromptExecutionRun existing = executionsById.get(executionId);
        if (existing == null) throw notFound("Execution: " + executionId);
        PromptExecutionRun failed = new PromptExecutionRun(
                existing.executionId(), existing.templateId(), existing.promptVersion(),
                existing.tenantId(), existing.userId(), existing.modelProvider(), existing.modelName(),
                existing.renderedPromptHash(), existing.redactedPromptPreview(),
                existing.inputVariablesRedactedJson(), null,
                PromptExecutionStatus.FAILED, existing.riskLevel(),
                existing.tokenEstimate(), existing.costEstimate(),
                existing.startedAt(), OffsetDateTime.now(), errorCode, errorDetails,
                existing.relatedPromptFile(), existing.relatedManifestEntry());
        storeExecution(failed);
        audit("EXECUTION_FAIL", executionId, Map.of("errorCode", errorCode));
        return failed;
    }

    public PromptExecutionRun getExecution(String executionId) {
        PromptExecutionRun run = executionsById.get(executionId);
        if (run == null) throw notFound("Execution: " + executionId);
        return run;
    }

    public List<PromptExecutionRun> listExecutions(String templateId) {
        return executionsById.values().stream()
                .filter(e -> e.templateId().equals(templateId))
                .toList();
    }

    public List<PromptExecutionRun> listAllExecutions() {
        return List.copyOf(executionsById.values());
    }

    // -------------------------------------------------------------------------
    // Risk Analysis
    // -------------------------------------------------------------------------

    public PromptRiskLevel analyzeRisk(String content, Map<String, Object> variables) {
        if (content == null) return PromptRiskLevel.LOW;

        int score = 0;

        // Check for secrets
        if (containsSecrets(content)) score += 40;
        if (variables != null) {
            for (var entry : variables.entrySet()) {
                if (isSensitiveVariable(entry.getKey()) && entry.getValue() != null) {
                    String val = entry.getValue().toString();
                    if (val.length() > 8 && !val.startsWith("sk-") && !val.contains("***")) {
                        score += 30; // Potential unredacted secret
                    }
                }
            }
        }

        // Check for destructive commands
        if (containsDestructiveCommands(content)) score += 30;

        // Check for production access patterns
        if (containsProductionAccess(content)) score += 20;

        if (score >= 60) return PromptRiskLevel.CRITICAL;
        if (score >= 40) return PromptRiskLevel.HIGH;
        if (score >= 20) return PromptRiskLevel.MEDIUM;
        return PromptRiskLevel.LOW;
    }

    // -------------------------------------------------------------------------
    // Evaluation
    // -------------------------------------------------------------------------

    public PromptEvaluationResult evaluateExecution(String executionId, String evaluatorUserId,
            boolean acceptanceCriteriaMet, boolean documentationUpdated, boolean manifestUpdated,
            boolean testsPass, boolean hasHighRiskChanges, boolean hasHumanReviewItems,
            boolean hasScopeCreep, boolean hasFalseClaims) {
        PromptExecutionRun run = executionsById.get(executionId);
        if (run == null) throw notFound("Execution: " + executionId);

        Map<String, String> scores = new LinkedHashMap<>();
        scores.put("acceptanceCriteria", acceptanceCriteriaMet ? "PASS" : "FAIL");
        scores.put("documentation", documentationUpdated ? "PASS" : "FAIL");
        scores.put("manifest", manifestUpdated ? "PASS" : "FAIL");
        scores.put("tests", testsPass ? "PASS" : "FAIL");
        scores.put("risk", !hasHighRiskChanges ? "PASS" : "FAIL");
        scores.put("scope", !hasScopeCreep ? "PASS" : "FAIL");
        scores.put("accuracy", !hasFalseClaims ? "PASS" : "FAIL");

        long passCount = scores.values().stream().filter("PASS"::equals).count();
        String verdict;
        if (passCount == scores.size()) verdict = "PASS";
        else if (passCount >= scores.size() * 0.7) verdict = "PASS_WITH_WARNINGS";
        else if (passCount >= scores.size() * 0.5) verdict = "NEEDS_REVIEW";
        else verdict = "FAIL";

        PromptEvaluationResult result = new PromptEvaluationResult(
                "eval-" + executionSeq.incrementAndGet(), executionId, run.templateId(),
                evaluatorUserId, acceptanceCriteriaMet, documentationUpdated, manifestUpdated,
                testsPass, hasHighRiskChanges, hasHumanReviewItems, hasScopeCreep, hasFalseClaims,
                verdict, scores, OffsetDateTime.now());

        audit("EVALUATE", executionId, Map.of("verdict", verdict));
        return result;
    }

    // -------------------------------------------------------------------------
    // File Scanning
    // -------------------------------------------------------------------------

    public PromptFileScanResult scanPromptFiles(List<String> fileContents, List<String> fileNames) {
        int imported = 0;
        int conflicts = 0;
        int skipped = 0;
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < fileContents.size(); i++) {
            String content = fileContents.get(i);
            String fileName = fileNames.get(i);
            try {
                PromptFileMetadata metadata = parsePromptFile(content, fileName);
                if (metadata == null) {
                    skipped++;
                    continue;
                }
                if (templatesByCode.containsKey(metadata.code())) {
                    conflicts++;
                    errors.add("Conflict: " + metadata.code() + " already exists from " + fileName);
                } else {
                    imported++;
                }
            } catch (Exception e) {
                errors.add("Error parsing " + fileName + ": " + e.getMessage());
            }
        }

        return new PromptFileScanResult(imported, conflicts, skipped, errors);
    }

    public PromptTemplate importPromptFile(String content, String fileName, String owner) {
        PromptFileMetadata metadata = parsePromptFile(content, fileName);
        if (metadata == null) {
            throw new PlatformException(
                    new ConfigurableErrorCode("PROMPT-400-001", 400601,
                            Map.of("en", "Invalid prompt file format", "zh", "无效的提示词文件格式"),
                            "prompt", 400),
                    "Could not parse prompt file: " + fileName,
                    Map.of("fileName", fileName), "en");
        }
        if (templatesByCode.containsKey(metadata.code())) {
            throw new PlatformException(
                    new ConfigurableErrorCode("PROMPT-409-001", 409601,
                            Map.of("en", "Prompt template code already exists", "zh", "提示词模板编码已存在"),
                            "prompt", 409),
                    "Template code already exists: " + metadata.code(),
                    Map.of("code", metadata.code()), "en");
        }

        PromptTemplate template = createTemplate(metadata.name(), metadata.description(),
                metadata.category(), metadata.tags(), owner, metadata.schemaVersion());
        createVersion(template.templateId(), metadata.body(), metadata.variableSchemaJson(),
                "Imported from " + fileName, owner);
        activateTemplate(template.templateId());

        audit("IMPORT", template.templateId(), Map.of("fileName", fileName));
        return template;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void storeTemplate(PromptTemplate template, String code) {
        templatesById.put(template.templateId(), template);
        templatesByCode.put(code, template);
        jdbcRepository.ifPresent(r -> r.saveTemplate(template));
    }

    private void storeExecution(PromptExecutionRun run) {
        executionsById.put(run.executionId(), run);
        jdbcRepository.ifPresent(r -> r.saveExecution(run));
    }

    private boolean codeExists(String code) {
        if (templatesByCode.containsKey(code)) {
            return true;
        }
        return jdbcRepository.map(r -> r.existsByDerivedCode(code)).orElse(false);
    }

    private static void bumpSeq(String id, String prefix, AtomicLong seq) {
        if (id != null && id.startsWith(prefix)) {
            try {
                long n = Long.parseLong(id.substring(prefix.length()));
                seq.updateAndGet(cur -> Math.max(cur, n));
            } catch (NumberFormatException ignored) {
                // keep sequence
            }
        }
    }

    private String generateCode(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private String incrementVersion(List<PromptTemplateVersion> versions) {
        if (versions.isEmpty()) return "1.0.0";
        String lastVersion = versions.get(versions.size() - 1).promptVersion();
        String[] parts = lastVersion.split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);
        patch++;
        return major + "." + minor + "." + patch;
    }

    private String computeChecksum(String content) {
        return Integer.toHexString(Objects.hash(content));
    }

    private boolean isSensitiveVariable(String name) {
        String lower = name.toLowerCase();
        return lower.contains("key") || lower.contains("secret") || lower.contains("password")
                || lower.contains("token") || lower.contains("api_key") || lower.contains("credential");
    }

    private boolean containsSecrets(String content) {
        if (content == null) return false;
        String lower = content.toLowerCase();
        return lower.contains("sk-") || lower.contains("api_key") || lower.contains("apikey")
                || lower.contains("password:") || lower.contains("secret:")
                || lower.contains("private key") || lower.contains("privatekey");
    }

    private boolean containsDestructiveCommands(String content) {
        if (content == null) return false;
        String lower = content.toLowerCase();
        return lower.contains("rm -rf") || lower.contains("terraform destroy")
                || lower.contains("tofu destroy") || lower.contains("chmod 777")
                || lower.contains("chmod -r") || lower.contains("chown -r");
    }

    private boolean containsProductionAccess(String content) {
        if (content == null) return false;
        String lower = content.toLowerCase();
        return lower.contains("production") && (lower.contains("deploy") || lower.contains("apply") || lower.contains("destroy"));
    }

    private int estimateTokens(String content) {
        if (content == null) return 0;
        return content.length() / 4; // Rough estimate: 4 chars per token
    }

    private double estimateCost(String content, String provider) {
        int tokens = estimateTokens(content);
        double costPerToken = switch (provider != null ? provider.toLowerCase() : "") {
            case "openai", "gpt-4" -> 0.00003;
            case "anthropic", "claude" -> 0.00002;
            case "glm", "glm-4" -> 0.00001;
            default -> 0.00001;
        };
        return tokens * costPerToken;
    }

    private String diffBody(String body1, String body2) {
        if (Objects.equals(body1, body2)) return "No changes";
        // Simple line-based diff
        String[] lines1 = body1.split("\n");
        String[] lines2 = body2.split("\n");
        StringBuilder diff = new StringBuilder();
        int maxLen = Math.max(lines1.length, lines2.length);
        for (int i = 0; i < maxLen; i++) {
            String l1 = i < lines1.length ? lines1[i] : "";
            String l2 = i < lines2.length ? lines2[i] : "";
            if (!l1.equals(l2)) {
                diff.append("- ").append(l1).append("\n");
                diff.append("+ ").append(l2).append("\n");
            }
        }
        return diff.toString();
    }

    private PromptFileMetadata parsePromptFile(String content, String fileName) {
        if (content == null || content.isBlank()) return null;

        // Extract metadata from markdown prompt files
        String name = fileName.replace(".md", "").replace("-", " ");
        String code = generateCode(name);
        String description = "";
        String category = "general";
        List<String> tags = List.of();
        String schemaVersion = "1.0.0";
        String variableSchemaJson = "{}";
        String body = content;

        // Parse frontmatter if present
        if (content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3);
            if (endIdx > 0) {
                String frontmatter = content.substring(3, endIdx);
                body = content.substring(endIdx + 3).trim();
                for (String line : frontmatter.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("name:")) name = line.substring(5).trim();
                    else if (line.startsWith("code:")) code = line.substring(5).trim();
                    else if (line.startsWith("description:")) description = line.substring(12).trim();
                    else if (line.startsWith("category:")) category = line.substring(9).trim();
                    else if (line.startsWith("tags:")) tags = parseTags(line.substring(5).trim());
                    else if (line.startsWith("schemaVersion:")) schemaVersion = line.substring(15).trim();
                }
            }
        }

        return new PromptFileMetadata(name, code, description, category, tags, schemaVersion, variableSchemaJson, body);
    }

    private List<String> parseTags(String tagsStr) {
        if (tagsStr.startsWith("[") && tagsStr.endsWith("]")) {
            tagsStr = tagsStr.substring(1, tagsStr.length() - 1);
        }
        return Arrays.stream(tagsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private PlatformException notFound(String id) {
        return new PlatformException(
                new ConfigurableErrorCode("PROMPT-404-001", 404601,
                        Map.of("en", "Prompt template not found", "zh", "提示词模板不存在"),
                        "prompt", 404),
                "Not found: " + id,
                Map.of("id", id), "en");
    }

    private void audit(String action, String resourceId, Map<String, Object> details) {
        if (auditPort != null) {
            auditPort.record("system", action, "PROMPT", "prompt_template", resourceId, details);
        }
    }

    // -------------------------------------------------------------------------
    // Inner records
    // -------------------------------------------------------------------------

    public record PromptVersionDiff(
            String fromVersion,
            String toVersion,
            String bodyDiff,
            boolean variableSchemaChanged) {}

    public record PromptFileScanResult(
            int imported,
            int conflicts,
            int skipped,
            List<String> errors) {}

    private record PromptFileMetadata(
            String name,
            String code,
            String description,
            String category,
            List<String> tags,
            String schemaVersion,
            String variableSchemaJson,
            String body) {}
}
