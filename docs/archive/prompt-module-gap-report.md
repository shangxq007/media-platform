# Prompt Module Gap Report

## Date: 2026-05-13
## Reviewer: Autonomous Orchestrator (Prompt 45, Task 0)

---

## Implemented Capabilities

### Domain Models (Stub)
- `PromptTemplate` - Minimal: id, code, content, variables(List<String>), status(String)
- `PromptVersion` - Minimal: id, templateId, version(int), content, changelog

### Services
- `PromptRenderService` - Functional core with in-memory storage
  - `render()` - Variable substitution with {{var}} syntax
  - `createTemplate()` - Create with auto-generated ID
  - `createVersion()` - Create version with auto-increment
  - `findTemplateByCode()`, `findTemplateById()`, `listTemplates()`, `listVersions()`

### API
- `POST /api/v1/prompts/render` - Render a template with variables

### Tests
- `PromptRenderServiceTest` - 17 tests covering render, create, find, list, version operations

### Migration (compatibility-migration-module)
- `SchemaFamily.PROMPT_TEMPLATE` - Defined
- `JsonPatchMigrationAdapter` - v1->v2 migration implemented and tested
  - rawPrompt → templateBody, variables → variableSchema
  - Adds promptVersion, auditRequired, patchHistory

### Audit (audit-compliance-module)
- `AuditCategory.PROMPT` - Defined and tested

---

## Missing Capabilities

### Domain Models (Task 1)
- ❌ `PromptTemplateVersion` - Full version with variableSchemaJson, checksum, previousVersion, deprecated
- ❌ `PromptVariableSchema` - Typed variable definitions (string/number/boolean/enum/array/object/secret_reference/file_reference)
- ❌ `PromptVariableDefinition` - With required, defaultValue, description, minLength, maxLength, allowedValues, sensitive, redactionPolicy
- ❌ `PromptRenderRequest` - Has only templateCode + variables, missing dryRun, variableOverrides
- ❌ `PromptRenderResult` - Missing: renderedPrompt, redactedPrompt, missingVariables, warnings
- ❌ `PromptValidationResult` - Missing entirely
- ❌ `PromptExecutionRun` - Missing entirely
- ❌ `PromptExecutionStatus` - Missing enum
- ❌ `PromptExecutionResult` - Missing entirely
- ❌ `PromptEvaluationResult` - Missing entirely
- ❌ `PromptRiskLevel` - Missing enum
- ❌ `PromptAuditContext` - Missing entirely

### Repositories & Services (Task 2)
- ❌ `PromptTemplateRepository` - No repository layer
- ❌ `PromptTemplateVersionRepository` - No repository layer
- ❌ `PromptTemplateService` - No dedicated service
- ❌ `PromptVersionService` - No version management service
- ❌ `PromptValidationService` - No validation service
- ❌ `PromptDiffService` - No diff capability
- ❌ `PromptRollbackService` - No rollback capability

### Variable Schema & Rendering (Task 3)
- ❌ Typed variable schema (string/number/boolean/enum/array/object/secret_reference/file_reference)
- ❌ Required/optional variables
- ❌ Default values
- ❌ Sensitive variable redaction
- ❌ Secret reference support
- ❌ Dry-run rendering
- ❌ Missing variable detection
- ❌ Redacted prompt output

### Execution Recording (Task 4)
- ❌ `PromptExecutionRunRepository` - No repository
- ❌ `PromptExecutionService` - No execution service
- ❌ Execution status tracking (PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED/REQUIRE_REVIEW)
- ❌ Token/cost estimation
- ❌ Redacted input variable storage
- ❌ Execution audit

### Risk & Safety (Task 5)
- ❌ `PromptRiskAnalyzer` - No risk analysis
- ❌ `PromptSafetyPolicyService` - No safety policy
- ❌ `PromptSecretScanner` - No secret scanning
- ❌ `PromptCommandRiskClassifier` - No command risk classification
- ❌ Integration with policy-governance-module
- ❌ Risk levels: LOW/MEDIUM/HIGH/CRITICAL
- ❌ Actions: ALLOW/WARN/REQUIRE_REVIEW/BLOCK

### Evaluation (Task 6)
- ❌ `PromptEvaluationService` - No evaluation
- ❌ `PromptAcceptanceCriteria` - No acceptance criteria
- ❌ `PromptQualityGateResult` - No quality gates
- ❌ `PromptExecutionReview` - No execution review

### File/Manifest (Task 7)
- ❌ `PromptFileScanner` - No file scanning
- ❌ `PromptManifestService` - No manifest management
- ❌ `PromptFileImportService` - No import service
- ❌ prompts/MANIFEST.md validation
- ❌ Prompt file ↔ Template sync

### Migration Integration (Task 8)
- ⚠️ Basic v1->v2 exists but needs integration testing
- ❌ No dedicated prompt migration test samples

### REST API (Task 9)
- ❌ Template CRUD (5 endpoints)
- ❌ Version management (3 endpoints)
- ❌ Render/validate/risk analyze (3 endpoints)
- ❌ Execution endpoints (5 endpoints)
- ❌ File/manifest endpoints (3 endpoints)

### Frontend (Task 10)
- ❌ `PromptTemplateList.vue`
- ❌ `PromptTemplateEditor.vue`
- ❌ `PromptVersionHistory.vue`
- ❌ `PromptRenderPreview.vue`
- ❌ `PromptExecutionList.vue`
- ❌ `PromptExecutionDetail.vue`
- ❌ `PromptRiskBadge.vue`
- ❌ `PromptManifestPanel.vue`
- ❌ No prompt API client
- ❌ No prompt types
- ❌ No prompt routes
- ❌ No prompt stores

### Error Codes
- ❌ No `PROMPT-xxx-xxx` error codes defined

### Cost Integration (Task 11)
- ❌ No token/cost tracking for prompt executions
- ❌ No integration with billing cost metering

---

## Stubs/Placeholders
- `PromptTemplate` - Minimal stub (missing 8 required fields)
- `PromptVersion` - Minimal stub (missing 6 required fields)
- `PromptRenderService` - Functional but incomplete
- `PromptController` - Single endpoint stub
- `PolicyGovernanceService.explain()` - Returns ALLOW/DENY only

## Human Review Points
1. Should PromptTemplate/PromptVersion use database persistence or stay in-memory?
2. What prompt categories should be pre-defined?
3. What is the secret scanning scope - just API keys or also custom patterns?
4. Should prompt execution actually call AI models or just record intent?
5. What acceptance criteria should be auto-evaluated vs manual review?
