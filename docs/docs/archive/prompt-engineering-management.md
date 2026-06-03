# Prompt Engineering Management Platform

## Overview

The Prompt Engineering Management Platform provides comprehensive lifecycle management for AI prompt templates, including creation, versioning, validation, rendering, execution tracking, risk analysis, and audit capabilities.

## Architecture

### Module Structure

| Module | Purpose |
|--------|---------|
| `prompt-module` | Core prompt template management |
| `compatibility-migration-module` | Schema migration for prompt templates |
| `audit-compliance-module` | Audit trail for prompt operations |
| `policy-governance-module` | Safety policy enforcement |
| `billing-module` | Cost tracking for prompt executions |

### Domain Model

```
PromptTemplate (1:N) PromptTemplateVersion
    |
    +-- PromptVariableSchema (1:N) PromptVariableDefinition
    |
    +-- PromptExecutionRun (1:1) PromptEvaluationResult
    |
    +-- PromptAuditContext
```

### Key Components

| Component | Type | Purpose |
|-----------|------|---------|
| `PromptTemplateService` | Service | Template CRUD, versioning, render, risk analysis |
| `PromptSafetyPolicyService` | Service | Secret scanning, command risk classification |
| `PromptController` | REST API | 20+ endpoints for full lifecycle |

## Features

### Template Management
- Create, update, activate, deprecate, archive templates
- Full version history with diff and rollback
- Variable schema with type validation

### Rendering
- Variable substitution with `{{var}}` syntax
- Sensitive variable redaction
- Dry-run preview
- Missing variable detection

### Safety
- Secret detection (API keys, passwords, tokens)
- Destructive command classification
- Production access pattern detection
- Risk levels: LOW, MEDIUM, HIGH, CRITICAL

### Execution Tracking
- Token and cost estimation
- Redacted input variable storage
- Status tracking: PENDING → RUNNING → SUCCEEDED/FAILED
- Risk level per execution

### Evaluation
- 8-dimension quality assessment
- PASS/FAIL/NEEDS_REVIEW verdicts
- Human review workflow

### Audit
- All operations recorded via `AuditPort`
- `AuditCategory.PROMPT` category
- Execution audit trail

## API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| POST | `/api/v1/prompts/templates` | Create template |
| GET | `/api/v1/prompts/templates` | List templates |
| GET | `/api/v1/prompts/templates/{id}` | Get template |
| PUT | `/api/v1/prompts/templates/{id}` | Update template |
| GET | `/api/v1/prompts/templates/{id}/versions` | List versions |
| POST | `/api/v1/prompts/templates/{id}/versions` | Create version |
| POST | `/api/v1/prompts/templates/{id}/rollback` | Rollback |
| POST | `/api/v1/prompts/templates/{id}/deprecate` | Deprecate |
| POST | `/api/v1/prompts/templates/{id}/render` | Render preview |
| POST | `/api/v1/prompts/templates/{id}/validate` | Validate |
| POST | `/api/v1/prompts/risk/analyze` | Risk analysis |
| POST | `/api/v1/prompts/executions` | Start execution |
| GET | `/api/v1/prompts/executions` | List executions |
| POST | `/api/v1/prompts/executions/{id}/evaluate` | Evaluate |
| POST | `/api/v1/prompts/files/scan` | Scan files |
| POST | `/api/v1/prompts/files/import` | Import file |
| POST | `/api/v1/prompts/manifest/validate` | Validate manifest |

## Error Codes

| Code | Description |
|------|-------------|
| `PROMPT-400-001` | Invalid request |
| `PROMPT-400-002` | Missing required variables |
| `PROMPT-400-003` | Variable validation failed |
| `PROMPT-404-001` | Template not found |
| `PROMPT-409-001` | Code already exists |
| `PROMPT-403-001` | Blocked by safety policy |
| `PROMPT-403-002` | Requires manual review |
| `PROMPT-422-001` | Risk analysis failed |
| `PROMPT-422-002` | Secret detected |
| `PROMPT-500-001` | Execution failed |

## Frontend Components

| Component | Purpose |
|-----------|---------|
| `PromptTemplateList.vue` | Template list with search/filter |
| `PromptTemplateEditor.vue` | Edit, version, render, risk tabs |

## Schema Migration

Prompt templates support schema migration via `compatibility-migration-module`:
- `PROMPT_TEMPLATE` v1.0.0 → v2.0.0
- `rawPrompt` → `templateBody`
- `variables` → `variableSchema`
- Adds `promptVersion`, `auditRequired`, `patchHistory`

## Cost Integration

Prompt executions track estimated token usage and cost via `CostEstimationPort` (Prompt 44 billing module). Frontend displays estimated cost per execution.

## Security

- No secrets stored in plain text
- Sensitive variables redacted in all outputs
- Risk analysis blocks or requires review for dangerous content
- All operations audited
