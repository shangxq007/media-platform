# Code Style & Naming Conventions

> **Last Updated:** 2026-05-18

## Java Naming Conventions

### Acronym Casing

| Acronym | Correct | Incorrect |
|---------|---------|-----------|
| FFmpeg | `FFmpegRenderProvider` | `FfmpegRenderProvider` |
| GPAC | `GPACRenderProvider` | `GpacRenderProvider` |
| MLT | `MLTCommandFactory` | `MeltCommandFactory` |
| OTIO | `OpenTimelineioAdapter` | — |
| API | `AiGatewayPort` (in package `ai.api`) | — |

**Rule:** Acronyms of 3+ letters are fully uppercase. Acronyms of 2 letters follow standard camelCase.

### Stereotype Annotations

| Annotation | Usage |
|------------|-------|
| `@Service` | Business logic classes |
| `@Component` | Technical infrastructure (registries, policies, schedulers) |
| `@Controller` | REST API controllers |
| `@Repository` | Data access classes |

**Rule:** Use `@Service` for business logic, `@Component` for technical infrastructure.

### Package Naming

| Package | Contents |
|---------|----------|
| `*.api` | Controllers, DTOs |
| `*.app` | Application services |
| `*.domain` | Domain models, value objects |
| `*.spi` | Port interfaces |
| `*.infrastructure` | Adapters, external integrations |

### Class Naming

| Type | Convention | Example |
|------|-----------|---------|
| Service | `<Domain>Service` | `RenderJobService` |
| Controller | `<Domain>Controller` | `RenderController` |
| Repository | `<Domain>Repository` | `RenderJobRepository` |
| Port | `<Domain>Port` | `AiGatewayPort` |
| Adapter | `<Domain>Adapter` | `JavaCVMediaProbeAdapter` |
| Provider | `<Name>Provider` | `JavaCVRenderProvider` |
| Factory | `<Domain>Factory` | `FFmpegCommandFactory` |
| Registry | `<Domain>Registry` | `RenderProviderRegistry` |
| Policy | `<Domain>Policy` | `RenderProviderSelectionPolicy` |
| Event | `<Domain>Event` | `RenderJobCompletedEvent` |
| Exception | `<Domain>Exception` | `RenderException` |
| Record (DTO) | `<Domain><Type>` | `SubmitRenderJobRequest` |

## Database Naming Conventions

| Category | Convention | Example |
|----------|-----------|---------|
| Table names | lowercase + underscore | `render_job` |
| Column names | lowercase + underscore | `created_at` |
| Primary key | `id varchar(64)` | — |
| Timestamps | `created_at timestamp not null` | — |
| Status columns | `status varchar(32) not null` | — |
| Foreign keys | `<entity>_id varchar(64) not null` | `tenant_id` |
| Indexes | `ix_<table>_<column>` | `ix_render_job_tenant_id` |

## Error Code Format

```
{MODULE}-{HTTP_STATUS}-{SEQUENCE}
```

| Module | Prefix |
|--------|--------|
| Common | `COMMON-` |
| Render | `RENDER-` |
| Subtitle | `SUBTITLE-` |
| Effect | `EFFECT-` |
| Timeline | `TIMELINE-` |
| Migration | `MIGRATION-` |
| Entitlement | `ENTITLEMENT-` |
| Feature Flag | `FF-` |
| NLQ | `NLQ-` |
| Monitoring | `MONITORING-` |
| Feedback | `FEEDBACK-` |

## Testing Conventions

| Convention | Rule |
|------------|------|
| Test class name | `<ClassName>Test` |
| Test method name | `should<ExpectedBehavior>When<Condition>` |
| Test package | Same as production class |
| Unit tests | Plain JUnit, no Spring context |
| Integration tests | `@SpringBootTest` |
| Test database | H2 in-memory |

## Frontend Naming Conventions

| Type | Convention | Example |
|------|-----------|---------|
| Components | PascalCase | `TimelineEditor.vue` |
| Composables | `use<Name>.ts` | `usePlayback.ts` |
| Stores | `<name>Store.ts` | `projectStore.ts` |
| Types | PascalCase interface | `interface Clip { ... }` |
| Routes | kebab-case | `/me/capabilities` |
