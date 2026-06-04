# Problematic Data Report

> **Generated:** 2026-05-14  
> **Type:** Automated Detection System  
> **Status:** Active

---

## Summary

The problematic data detection system automatically identifies, isolates, and handles bug-caused data issues and behavior anomalies across the media-platform.

### Detection Coverage

| Data Source | Detection Rules | Auto-Fixable | Requires Review |
|---|---|---|---|
| RenderJob | 5 rules | 2 | 3 |
| PromptExecution | 3 rules | 0 | 3 |
| Provider/Worker | 2 rules | 1 | 1 |
| KPI/SLA | 2 rules | 0 | 2 |
| **Total** | **12 rules** | **3** | **9** |

---

## Detection Rules

### RenderJob Rules

| Rule ID | Type | Severity | Auto-Fix | Description |
|---------|------|----------|----------|-------------|
| RJB-001 | MISSING_FIELD | HIGH | No | RenderJob completed but has no output artifact |
| RJB-002 | INVALID_STATE_TRANSITION | MEDIUM | Yes | RenderJob stuck in non-terminal state for too long |
| RJB-003 | DUPLICATE_ENTRY | LOW | Yes | Multiple render jobs with same project+profile+timeline |
| SLA-001 | SLA_BREACH | CRITICAL | No | Render job exceeded SLA time limit |
| CST-001 | COST_ANOMALY | HIGH | No | Render job cost significantly exceeds estimated cost |

### PromptExecution Rules

| Rule ID | Type | Severity | Auto-Fix | Description |
|---------|------|----------|----------|-------------|
| PMT-001 | MISSING_FIELD | CRITICAL | No | Sensitive prompt variable found in execution record |
| PMT-002 | OUTPUT_MISMATCH | HIGH | No | Prompt execution output does not match expected format |
| PMT-003 | LOGIC_CONFLICT | HIGH | No | Prompt risk level escalated after execution |

### Provider/Worker Rules

| Rule ID | Type | Severity | Auto-Fix | Description |
|---------|------|----------|----------|-------------|
| PRV-001 | ERROR_RATE_SPIKE | HIGH | No | Provider error rate exceeds threshold |
| WRK-001 | PERFORMANCE_ANOMALY | MEDIUM | Yes | Worker heartbeat is stale |

---

## Auto-Fix Capabilities

### Automatically Fixed

| Issue | Fix Action |
|-------|------------|
| Missing fields | Fill with default values |
| Format errors | Convert to expected format |
| Duplicate entries | Mark as duplicate, retain original |
| Stuck jobs | Reset to QUEUED for retry |
| Stale worker heartbeat | Mark offline, redistribute jobs |

### Requires Human Review

| Issue | Reason |
|-------|--------|
| SLA breaches | Business impact assessment needed |
| Output mismatches | Complex logic, may need re-render |
| Quality degradation | May need manual intervention |
| Cost anomalies | May indicate pricing model issues |
| Sensitive data leaks | Security incident response required |

---

## Quarantine Strategy

| Severity | Action |
|----------|--------|
| CRITICAL | Immediate quarantine + Sentry alert |
| HIGH | Quarantine + notification |
| MEDIUM | Mark for review, continue processing |
| LOW | Log and auto-fix if possible |

---

## Database Schema

### Tables

| Table | Purpose |
|-------|---------|
| `problematic_data_record` | Main table for all detected issues |
| `quarantined_render_jobs` | Quarantined render job data |
| `quarantined_prompt_executions` | Quarantined prompt execution data |
| `quarantined_provider_workers` | Quarantined provider/worker data |
| `problematic_data_rule_config` | Detection rule configuration |

---

## Integration

### Audit Integration
All detection, fix, and quarantine operations are recorded in the audit trail with category `AUDIT`.

### Sentry Integration
Critical issues trigger Sentry alerts via `SentryMonitoringService.captureException()`.

### OpenReplay Integration
User-reported issues from FeedbackButton are correlated with session replay IDs.

### Event Publishing
`ProblematicDataDetectedEvent` is published for each detected issue, enabling downstream consumers to react.

---

## Metrics

| Metric | Description |
|--------|-------------|
| `problematic_data_detected_total` | Total number of problematic data records detected |
| `problematic_data_auto_fixed_total` | Total number of records auto-fixed |
| `problematic_data_quarantined_total` | Total number of records quarantined |
| `problematic_data_human_review_total` | Total number of records requiring human review |
| `problematic_data_detection_latency_ms` | Time to detect issues |

---

## Retry/Rerun Pipeline

For quarantined render jobs:

1. Review the quarantine record
2. Determine root cause
3. If re-runnable:
   - Reset job status to QUEUED
   - Clear quarantine record
   - Job will be picked up by the render pipeline
4. If not re-runnable:
   - Mark as RESOLVED with resolution notes
   - Notify user
