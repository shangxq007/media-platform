# Usage Anomaly Alerting

## Overview

The usage anomaly detection system monitors user behavior for unusual patterns and applies graduated mitigation actions that prioritize user experience. The system follows a "warn first, degrade gracefully, block last" philosophy.

## Detection Rules

| Rule Type | Description | Severity | Default Threshold |
|-----------|-------------|----------|-------------------|
| `render_burst` | Too many render jobs in short period | MEDIUM | 10 jobs/hour |
| `repeated_render_failures` | Multiple consecutive failures | LOW | 5 consecutive |
| `gpu_cost_spike` | Abnormal GPU cost increase | HIGH | 200% increase |
| `remote_worker_abuse` | Excessive remote worker usage | HIGH | 20 jobs/hour |
| `storage_egress_spike` | Abnormal storage/egress usage | MEDIUM | 100 GB/day |
| `ai_provider_spike` | Abnormal AI provider usage | MEDIUM | 100 calls/hour |
| `subtitle_font_upload_abuse` | Excessive font uploads | LOW | 10 uploads/day |
| `api_key_multi_region_spike` | API key used from multiple regions | HIGH | 3 regions/hour |

## Mitigation Actions

Actions are applied in increasing severity:

| Action Level | Description | User Impact |
|-------------|-------------|-------------|
| `OBSERVE` | Log and monitor only | None |
| `WARN` | Notify user of unusual usage | Informational |
| `SOFT_LIMIT` | Apply soft rate limiting | Slight delay |
| `DEGRADE` | Switch to lower quality preset | Reduced quality |
| `HARD_BLOCK` | Block new submissions | Cannot submit |
| `REQUIRE_REVIEW` | Escalate to manual review | May continue |

## User Experience Protection

### Core Principles

1. **Never cancel running jobs** - In-progress renders always complete
2. **Degrade before blocking** - Switch to lower preset before denying service
3. **Provide alternatives** - Always recommend a preset that will work
4. **High-value user protection** - ENTERPRISE/EXPERIMENTAL users go to manual review instead of auto-block
5. **Allow retry** - Users can retry with different settings

### Tier-Based Protection

| User Tier | Hard Block Behavior |
|-----------|-------------------|
| FREE | Degrade to recommended preset |
| PRO | Degrade to recommended preset |
| TEAM | Degrade to recommended preset |
| ENTERPRISE | Escalate to manual review |
| EXPERIMENTAL | Escalate to manual review |

## Audit Integration

All anomaly detections and mitigation actions are recorded in the audit trail:
- `UsageAnomalyDetectedEvent` - Published when anomaly is detected
- `UsageMitigationAction` - Recorded when action is applied
- Audit entries written for all anomaly-related actions

## Error Codes

| Code | Description |
|------|-------------|
| `USAGE-429-001` | Unusual usage detected, exports adjusted |
| `USAGE-429-002` | Soft limit applied |
| `USAGE-429-003` | Degraded to recommended preset |

## Configuration

Rules are defined as static factory methods in `UsageAnomalyRule`. In production, thresholds should be loaded from external configuration.
