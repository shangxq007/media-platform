# Document Validation Policy

> **Last Updated:** 2026-06-22  
> **Purpose:** Define how documents are validated against code to prevent drift

## Validation Sources

The following code artifacts are the **ground truth** for documentation validation:

| Source | Truth Type | Location |
|--------|-----------|----------|
| `settings.gradle.kts` | Module count, module names | Root |
| `build.gradle.kts` | Versions (Java, Spring Boot, Spring Modulith, jOOQ) | Root |
| `frontend/package.json` | Frontend framework, dependencies | `frontend/` |
| `V1__init_full_schema.sql` | Database schema, table count | `platform-app/src/main/resources/db/migration/` |
| `ModularityTest.java` | Allowed module boundary violations | `platform-app/src/test/java/` |
| `ProductionSafetyValidator.java` | Production safety checks | `platform-app/src/main/java/` |
| `package-info.java` | Module boundaries, NamedInterfaces, allowedDependencies | Each module root |
| `docker-compose.yml` | Database version, services | Root |
| `.github/workflows/ci.yml` | CI pipeline definition | `.github/` |
| `QuotaService.java` | Quota persistence state | `quota-billing-module/` |

## Validation Frequency

| Validation | Frequency | Responsible |
|-----------|-----------|-------------|
| Tier 0 docs (`AGENTS.md`, `.kilo/agents/main.md`) | Every PR touching modules | CI (recommended) |
| Tier 1 docs (architecture, boundaries) | Monthly | Architect |
| Tier 2 docs (blueprints, ADRs) | Quarterly | Architect |
| Tier 3 docs (historical) | Never (archived) | — |

## Validation Checklist

For each Tier 0-1 document, verify:

- [ ] Module count matches `settings.gradle.kts` (currently 35)
- [ ] Frontend framework matches `frontend/package.json` (currently React 19)
- [ ] Flyway state matches `db/migration/` directory (currently 1 V1 file)
- [ ] Database version matches `docker-compose.yml` (currently postgres:16-alpine)
- [ ] Allowed violations match `ModularityTest.java` ALLOWED_VIOLATIONS (currently 2)
- [ ] Security checks match `ProductionSafetyValidator.java`
- [ ] Render provider count matches actual provider files in `render-module/`
- [ ] Quota persistence state matches `QuotaService.java` (currently in-memory)

## Known Drift Patterns to Watch

| Pattern | Correct Value | Common Wrong Values |
|---------|--------------|-------------------|
| Frontend framework | React 19 | Vue 3, Vue 3.5, Vue 3 + Pinia |
| Module count | 35 | 30, 31, 32, 34 |
| Flyway migrations | 1 (V1 consolidated) | 17, 22, V1-V22 |
| Database tables | 133 | 28+, 50+, 70+ |
| PostgreSQL version | 16 | 15 |
| Allowed violations | 2 | 8 |
| Render providers | 7+ (FFmpeg, GStreamer, MLT, Remotion, GPAC, OFX, Natron) | 1, 6, FFmpeg/JavaCV only |
| Quota persistence | In-memory (ConcurrentHashMap) | DB-backed, persistent |
| Temporal/LiteFlow | Implemented | Not implemented |

## Validation Commands

```bash
# Module count
grep -c 'include(' settings.gradle.kts

# Flyway files
ls platform-app/src/main/resources/db/migration/

# Table count
grep -ci 'CREATE TABLE' platform-app/src/main/resources/db/migration/V1__init_full_schema.sql

# Frontend framework
grep '"react"' frontend/package.json

# Vue files (should be 0)
find frontend/src -name '*.vue' | wc -l

# Allowed violations
grep -A 20 'ALLOWED_VIOLATIONS' platform-app/src/test/java/com/example/platform/ModularityTest.java

# Render providers
find render-module/src/main/java -name '*Provider*' -path '*/infrastructure/*' | sort

# Quota persistence
grep -c 'ConcurrentHashMap' quota-billing-module/src/main/java/com/example/platform/quota/app/QuotaService.java

# PostgreSQL version
grep 'postgres:' docker-compose.yml
```
