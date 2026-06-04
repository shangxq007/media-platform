# Problematic Data Handling

> **Purpose:** Guide for detecting, isolating, auto-fixing, and handling problematic data.  
> **Last Updated:** 2026-05-14

---

## Overview

The problematic data detection and handling system automatically identifies bug-caused data issues and behavior anomalies across the media-platform. It provides a unified pipeline for detection → isolation → auto-fix → human review → resolution.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Data Sources                              │
│  RenderJob │ PromptExecution │ Provider │ Worker │ KPI/SLA  │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              ProblematicDataDetectionService                 │
│  - 12 detection rules                                       │
│  - Per-data-type detection methods                          │
│  - Event publishing (ProblematicDataDetectedEvent)          │
│  - Audit logging                                            │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              ProblematicDataAutoFixService                    │
│  - Auto-fixable: missing fields, format errors, duplicates  │
│  - Quarantine: critical issues, SLA breaches                │
│  - Human review: complex logic, output mismatches           │
│  - Batch processing                                         │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Storage                                         │
│  - problematic_data_record (main table)                     │
│  - quarantined_render_jobs                                  │
│  - quarantined_prompt_executions                            │
│  - quarantined_provider_workers                             │
│  - problematic_data_rule_config                             │
└─────────────────────────────────────────────────────────────┘
```

## Detection Rules

### RenderJob Rules

| Rule ID | Type | Severity | Auto-Fix | Description |
|---------|------|----------|----------|-------------|
| RJB-001 | MISSING_FIELD | HIGH | No | Completed without output artifact |
| RJB-002 | INVALID_STATE_TRANSITION | MEDIUM | Yes | Stuck in non-terminal state >30min |
| RJB-003 | DUPLICATE_ENTRY | LOW | Yes | Same project+profile+timeline |
| SLA-001 | SLA_BREACH | CRITICAL | No | Exceeded SLA time limit |
| CST-001 | COST_ANOMALY | HIGH | No | Cost > 2x estimated |

### PromptExecution Rules

| Rule ID | Type | Severity | Auto-Fix | Description |
|---------|------|----------|----------|-------------|
| PMT-001 | MISSING_FIELD | CRITICAL | No | Sensitive data in execution record |
| PMT-002 | OUTPUT_MISMATCH | HIGH | No | Output doesn't match expected format |
| PMT-003 | LOGIC_CONFLICT | HIGH | No | Risk level escalated post-execution |

### Provider/Worker Rules

| Rule ID | Type | Severity | Auto-Fix | Description |
|---------|------|----------|----------|-------------|
| PRV-001 | ERROR_RATE_SPIKE | HIGH | No | Error rate > 20% |
| WRK-001 | PERFORMANCE_ANOMALY | MEDIUM | Yes | Stale heartbeat > 5min |

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
| SLA breaches | Business impact assessment |
| Output mismatches | Complex logic, may need re-render |
| Quality degradation | Manual intervention needed |
| Cost anomalies | Pricing model review |
| Sensitive data leaks | Security incident response |

## Quarantine Strategy

| Severity | Action |
|----------|--------|
| CRITICAL | Immediate quarantine + Sentry alert |
| HIGH | Quarantine + notification |
| MEDIUM | Mark for review |
| LOW | Log and auto-fix |

## Database Schema

### problematic_data_record

Main table for all detected issues. Fields:
- `record_id`, `data_type`, `data_id`, `tenant_id`, `user_id`
- `problematic_type`, `severity`, `detection_rule`, `description`
- `context_json`, `source_session_id`
- `render_job_id`, `prompt_execution_id`, `provider_key`, `worker_id`
- `status`, `auto_fix_applied`, `quarantine_table`
- `requires_human_review`, `human_review_notes`
- `detected_at`, `resolved_at`, `resolved_by`

### Quarantine Tables

- `quarantined_render_jobs` - Quarantined render job data
- `quarantined_prompt_executions` - Quarantined prompt execution data
- `quarantined_provider_workers` - Quarantined provider/worker data

## Integration

### Audit Integration
All operations recorded with category `AUDIT`:
- `PROBLEMATIC_DATA_DETECTED` - When issue is detected
- `PROBLEMATIC_DATA_AUTO_FIXED` - When auto-fix applied
- `PROBLEMATIC_DATA_QUARANTINED` - When quarantined

### Event Publishing
`ProblematicDataDetectedEvent` published for each detected issue.

### Sentry Integration
Critical issues trigger Sentry alerts.

## Retry/Rerun Pipeline

1. Review quarantine record
2. Determine root cause
3. If re-runnable: reset status to QUEUED
4. If not re-runnable: mark RESOLVED with notes
