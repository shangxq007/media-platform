# spring-ai-adapter Integrity and Governance Triage

**Date:** 2026-07-14
**Status:** COMPLETE
**Authority:** BACKEND-INTEGRITY-SPRING-AI-ADAPTER-TRIAGE.0
**Decision:** SPRING_AI_ADAPTER_UNAPPROVED_HOLD_MODULE

---

## Architecture Constraint

Spring AI runtime is not approved for the media-platform mainline.

The presence of spring-ai-adapter does not constitute approval.

No Spring AI integration work was authorized by this task.

## Repository Baseline

| Item | Value |
|------|-------|
| Branch | main |
| Base commit | 845b19e |
| Gradle | 9.1.0 |
| Java | 25.0.3 |
| Spring Boot | 4.0.4 |
| Module path | spring-ai-adapter/ |

## Module Provenance

| Item | Value |
|------|-------|
| Introducing commit | b3676c8 |
| Commit message | `chore(ai): remove spring ai from active runtime path` |
| Author/agent | Not determinable from commit metadata |
| Original purpose | Isolate Spring AI classes from platform-app into a separate module |
| Original lifecycle | Isolated/experimental — explicitly "NOT included in platform-app by default" |

The commit `b3676c8` MOVED Spring AI provider classes FROM `platform-app` INTO the new `spring-ai-adapter` module. This was an intentional isolation, not a new feature introduction.

## Architecture Approval Search

| Evidence | Location | Explicit Approval | Valid |
|----------|----------|----------------:|------:|
| Blueprint: "isolated, not active in runtime" | docs/architecture/blueprint/module-blueprint-ai-provider.md | NO (isolation statement) | N/A |
| Blueprint: "AI providers should not require Spring AI in platform-app" | docs/architecture/blueprint/module-blueprint-ai-provider.md | NO (exclusion statement) | N/A |
| Module status: "⚠️ Isolated \| Not in platform-app" | docs/architecture/current/current-module-status.md | NO (status label) | N/A |
| Semgrep rule: `arch-no-spring-ai-platform-app` | .semgrep/media-platform-architecture.yml | NO (enforcement rule) | N/A |
| System state: "Isolated in ai-module, not in platform-app runtime" | docs/architecture/current/current-system-state.md | NO (status label) | N/A |
| build.gradle.kts comment: "NOT included in platform-app by default" | spring-ai-adapter/build.gradle.kts | NO (self-documentation) | N/A |

**SPRING_AI_MAINLINE_APPROVAL: NOT_FOUND**

No ADR, roadmap entry, approved design, or explicit architecture decision was found authorizing Spring AI for mainline production use.

## Module Inventory

| File | Type | Purpose |
|------|------|---------|
| build.gradle.kts | BUILD_CONFIG | Module dependencies (Spring AI OpenAI starter, shared-kernel, ai-module) |
| SpringAiOpenAiChatProvider.java | PRODUCTION_SOURCE | ChatProvider backed by Spring AI ChatClient, property-gated |
| TenantAwareLitellmChatProvider.java | PRODUCTION_SOURCE | ChatProvider with per-tenant virtual keys, references TenantLitellmKeyService from platform-app |

- Production source files: 2
- Test source files: 0
- Spring components: 2 (@Component, @ConditionalOnProperty gated)
- Controllers: 0
- Configuration files: 0
- Resources: 0

## Compile Failure

```
Command: ./gradlew :spring-ai-adapter:compileJava
Exit code: non-zero
Errors: 3 (all in TenantAwareLitellmChatProvider.java)
Missing symbol: TenantLitellmKeyService
```

All 3 errors reference `TenantLitellmKeyService` which exists in `platform-app` but is unreachable from `spring-ai-adapter` (no dependency, would be circular).

## TenantLitellmKeyService Trace

| Finding | Detail |
|---------|--------|
| Type exists? | **YES** — at `platform-app/src/main/java/com/example/platform/app/ai/TenantLitellmKeyService.java` |
| Status | `TYPE_BELONGS_TO_ANOTHER_MODULE` |
| References in platform-app | 5 files (TenantLitellmKeyService.java, PlatformDeploymentReadinessController, TenantAiAdminController, TenantLitellmKeyServiceTest, TenantAiAdminControllerTest) |
| References in spring-ai-adapter | 1 file (TenantAwareLitellmChatProvider.java — 4 references) |
| Why compile fails | spring-ai-adapter cannot depend on platform-app (circular dependency) |

The type is NOT missing — it was implemented in platform-app. The compile error is a cross-module reference design error: `spring-ai-adapter` references a type that belongs to the application module.

## Gradle Dependency Graph

### Outbound dependencies (spring-ai-adapter)

| Dependency | Scope | Spring AI-related |
|-----------|-------|----------------:|
| shared-kernel | api | NO |
| ai-module | api | NO |
| spring-boot-starter | api | NO |
| spring-boot-starter-web | api | NO |
| spring-boot-starter-validation | api | NO |
| spring-ai-starter-model-openai | api | **YES** |
| spring-boot-starter-test | testImplementation | NO |

### Inbound dependencies

| Consumer | Depends on spring-ai-adapter? |
|----------|------------------------------:|
| platform-app | **NO** |
| render-module | **NO** |
| Any other module | **NO** |

**No module depends on spring-ai-adapter.**

## platform-app Impact

| Check | Result |
|-------|--------|
| Compile dependency | **NO** |
| Runtime dependency | **NO** |
| On runtimeClasspath | **NO** |
| In bootJar | **NO** |
| In container image | **NO** (no Dockerfile found) |

## Deployment Impact

| Build path | spring-ai-adapter compiled | Blocks build | Runtime included |
|-----------|--------------------------:|-------------:|----------------:|
| Local full build | YES (in settings.gradle.kts) | YES | NO |
| Core backend build | NO (excluded) | NO | NO |
| CI build | Likely YES (full build) | YES | NO |
| bootJar | NO | NO | NO |
| Docker image | NO | NO | NO |

## Spring Activation

| Component | Registration | Condition | Reachable at runtime |
|-----------|-------------|-----------|---------------------:|
| SpringAiOpenAiChatProvider | @Component | `app.ai.providers.openai.enabled=true` AND `tenant-virtual-keys-enabled=false` | NO (not on classpath) |
| TenantAwareLitellmChatProvider | @Component | `app.ai.providers.openai.enabled=true` AND `tenant-virtual-keys-enabled=true` | NO (not on classpath) |

No `AutoConfiguration.imports` file exists. Components are NOT scanned by platform-app.

## Configuration and Secret Boundary

| Property | Module | Profile | Required |
|----------|--------|---------|---------:|
| `app.ai.providers.openai.enabled` | spring-ai-adapter | N/A | For activation |
| `app.ai.providers.openai.tenant-virtual-keys-enabled` | spring-ai-adapter | N/A | For activation |
| `spring.ai.openai.api-key` | platform-app | litellm | For LiteLLM profile |
| `spring.ai.openai.base-url` | platform-app | litellm | For LiteLLM profile |
| `LITELLM_BASE_URL` | platform-app | litellm | For LiteLLM profile |

The `application-litellm.yml` profile exists in platform-app but is NOT active by default. It references `spring.ai.openai.*` properties but the Spring AI starter is not on the classpath.

## Test and Maturity Assessment

- Test files: **0**
- Test count: **0**
- Classification: **NO_MEANINGFUL_TESTS**

The module has no tests whatsoever. It does not meet any production readiness standard.

## Current Capability Dependency

| Capability | Required? |
|-----------|----------:|
| Current render execution | **NO** |
| Product behavior | **NO** |
| Artifact behavior | **NO** |
| Storage behavior | **NO** |
| Timeline behavior | **NO** |
| Current frontend pages | **NO** |
| Planned upload API | **NO** |
| Preview deployment | **NO** |
| Any production-ready capability | **NO** |

## Lifecycle Decision

| Lifecycle | Evidence for | Evidence against | Appropriate |
|-----------|-------------|-----------------|------------:|
| PRODUCTION | None | No approval, no tests, compile error, no runtime consumer | NO |
| OPTIONAL | Blueprint says "isolated optional" | No explicit approval | MAYBE |
| HOLD | Intentionally isolated, no runtime consumer, incomplete | Has some code value | **YES** |
| DEPRECATED | Superseded by ai-module SPI | Some design value retained | MAYBE |
| POC/SPIKE | No tests, incomplete | Moved from platform-app intentionally | MAYBE |

**Recommended lifecycle: HOLD**

## Default Build Decision

Default aggregate builds should NOT include spring-ai-adapter as a mandatory module. The current inclusion in `settings.gradle.kts` causes full-repository builds to fail. Command-line exclusions are a `TEMPORARY_VALID_DIAGNOSTIC_EXCLUSION` that must be formalized.

## Runtime Validation Decision

**CORE_RUNTIME_VALIDATION_CAN_PROCEED_WITH_SPRING_AI_EXCLUDED**

platform-app does not depend on spring-ai-adapter. Core Spring runtime validation can proceed with the module excluded.

## Containment Recommendation

**BACKEND-INTEGRITY-PLACE-SPRING-AI-ADAPTER-ON-HOLD.0**

The next task should formally place spring-ai-adapter on HOLD by:
1. Either removing it from `settings.gradle.kts` or marking it as optional/disabled
2. Documenting the HOLD status in module governance
3. Removing the need for command-line build exclusions
