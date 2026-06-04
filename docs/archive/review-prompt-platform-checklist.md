# Prompt Platform Review Checklist

> **Purpose:** Verify prompt engineering management platform.  
> **Reviewer:** _______________  
> **Date:** _______________

---

## PromptTemplate Management

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Create template with name/description/category | ⬜ | |
| 2 | Update template metadata | ⬜ | |
| 3 | Get template by ID | ⬜ | |
| 4 | Find template by code | ⬜ | |
| 5 | List all templates | ⬜ | |
| 6 | Filter templates by status | ⬜ | |
| 7 | Activate template (DRAFT→ACTIVE) | ⬜ | |
| 8 | Deprecate template | ⬜ | |
| 9 | Archive template | ⬜ | |
| 10 | PROMPT-404-001 on not found | ⬜ | |
| 11 | PROMPT-409-001 on duplicate code | ⬜ | |

## PromptTemplateVersion

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Create version with template body | ⬜ | |
| 2 | Auto-increment version (1.0.0 → 1.0.1) | ⬜ | |
| 3 | Get version by ID | ⬜ | |
| 4 | List versions for template | ⬜ | |
| 5 | Get current version | ⬜ | |
| 6 | Diff between versions | ⬜ | |
| 7 | Checksum generated | ⬜ | |
| 8 | Previous version tracked | ⬜ | |
| 9 | Deprecate version | ⬜ | |

## PromptVariableSchema

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | String variable type | ⬜ | |
| 2 | Number variable type | ⬜ | |
| 3 | Boolean variable type | ⬜ | |
| 4 | Enum variable type | ⬜ | |
| 5 | Array variable type | ⬜ | |
| 6 | Object variable type | ⬜ | |
| 7 | SECRET_REFERENCE type | ⬜ | |
| 8 | FILE_REFERENCE type | ⬜ | |
| 9 | Required/optional variables | ⬜ | |
| 10 | Default values | ⬜ | |
| 11 | Min/max length constraints | ⬜ | |
| 12 | Allowed values constraint | ⬜ | |
| 13 | Sensitive flag | ⬜ | |
| 14 | Redaction policy (FULL/PARTIAL/HASH/MASK) | ⬜ | |

## PromptRenderPreview

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Render with variables | ⬜ | |
| 2 | Missing variable detection | ⬜ | |
| 3 | Sensitive variable redaction | ⬜ | |
| 4 | Redacted prompt output | ⬜ | |
| 5 | Warning for secrets in output | ⬜ | |
| 6 | Dry-run mode | ⬜ | |
| 7 | Specific version render | ⬜ | |

## PromptValidation

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Valid template passes | ⬜ | |
| 2 | Empty name fails | ⬜ | |
| 3 | Empty body fails | ⬜ | |
| 4 | Secret in body fails | ⬜ | |
| 5 | Missing version warning | ⬜ | |

## PromptExecutionRun

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Start execution with context | ⬜ | |
| 2 | Token estimate calculated | ⬜ | |
| 3 | Cost estimate calculated | ⬜ | |
| 4 | Redacted input variables stored | ⬜ | |
| 5 | Rendered prompt hash stored | ⬜ | |
| 6 | Risk level assigned | ⬜ | |
| 7 | Complete execution | ⬜ | |
| 8 | Fail execution with error | ⬜ | |
| 9 | Get execution by ID | ⬜ | |
| 10 | List executions by template | ⬜ | |

## PromptEvaluation

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Evaluate 8 quality dimensions | ⬜ | |
| 2 | PASS verdict | ⬜ | |
| 3 | PASS_WITH_WARNINGS verdict | ⬜ | |
| 4 | NEEDS_REVIEW verdict | ⬜ | |
| 5 | FAIL verdict | ⬜ | |
| 6 | Evaluation audit trail | ⬜ | |

## PromptRiskAnalyzer

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | LOW risk for safe content | ⬜ | |
| 2 | MEDIUM risk for minor issues | ⬜ | |
| 3 | HIGH risk for destructive commands | ⬜ | |
| 4 | CRITICAL risk for secrets + destructive | ⬜ | |
| 5 | Risk with variables context | ⬜ | |

## PromptSecretScanner

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Detect API keys (sk-...) | ⬜ | |
| 2 | Detect passwords | ⬜ | |
| 3 | Detect AWS keys (AKIA...) | ⬜ | |
| 4 | Detect GitHub tokens (ghp_...) | ⬜ | |
| 5 | Detect private keys | ⬜ | |
| 6 | Detect generic secrets | ⬜ | |
| 7 | Blocked flag for critical patterns | ⬜ | |

## PromptCommandRiskClassifier

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Detect rm -rf | ⬜ | |
| 2 | Detect terraform destroy | ⬜ | |
| 3 | Detect chmod 777 | ⬜ | |
| 4 | Detect production apply | ⬜ | |
| 5 | Detect delete tests | ⬜ | |
| 6 | Detect disable security | ⬜ | |
| 7 | Detect upload private key | ⬜ | |
| 8 | Risk level per pattern | ⬜ | |

## PromptSafetyPolicy

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | ALLOW for safe content | ⬜ | |
| 2 | WARN for minor issues | ⬜ | |
| 3 | REQUIRE_REVIEW for destructive | ⬜ | |
| 4 | BLOCK for secrets | ⬜ | |
| 5 | PROMPT-403-001 on block | ⬜ | |
| 6 | PROMPT-422-002 on secret detected | ⬜ | |
| 7 | Explanation provided | ⬜ | |

## PromptFileScanner

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Scan prompt files | ⬜ | |
| 2 | Parse frontmatter | ⬜ | |
| 3 | Detect conflicts | ⬜ | |
| 4 | Import file as template | ⬜ | |
| 5 | MANIFEST validation | ⬜ | |

## Sensitive Variables Not Persisted

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | API keys not stored in execution records | ⬜ | |
| 2 | Passwords not stored | ⬜ | |
| 3 | Tokens not stored | ⬜ | |
| 4 | Redacted in audit logs | ⬜ | |
| 5 | Hash stored instead of plaintext | ⬜ | |

## Audit Trail

| # | Check | Status | Notes |
|---|-------|--------|-------|
| 1 | Template creation audited | ⬜ | |
| 2 | Version creation audited | ⬜ | |
| 3 | Risk analysis audited | ⬜ | |
| 4 | Execution start audited | ⬜ | |
| 5 | Execution complete audited | ⬜ | |
| 6 | All linked to tenant/user | ⬜ | |

---

## Summary

| Category | Passed | Total | % |
|----------|--------|-------|---|
| Template Management | ___/11 | 11 | |
| Version Management | ___/9 | 9 | |
| Variable Schema | ___/14 | 14 | |
| Render Preview | ___/7 | 7 | |
| Validation | ___/5 | 5 | |
| Execution Run | ___/10 | 10 | |
| Evaluation | ___/6 | 6 | |
| Risk Analysis | ___/5 | 5 | |
| Secret Scanner | ___/7 | 7 | |
| Command Risk | ___/8 | 8 | |
| Safety Policy | ___/7 | 7 | |
| File Scanner | ___/5 | 5 | |
| Sensitive Data | ___/5 | 5 | |
| Audit Trail | ___/6 | 6 | |
| **Total** | ___/105 | **105** | |

**Reviewer Signature:** _______________  
**Date:** _______________
