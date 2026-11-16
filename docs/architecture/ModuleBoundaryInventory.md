# Module Boundary Inventory — MEDIA_PLATFORM_MODULE_BOUNDARY_CONSOLIDATION_R1

**Base:** `4765eda5f0527c25d61139b2af4dbb5824db3efc` / tree `636186a192c509fca6c326400b1b6bcb8386d3a1`
**Method:** machine extraction from `settings.gradle.kts`, every module's `build.gradle.kts`, the Java
package tree and import graph, plus targeted reads of the boot/config/plugin classes. Raw data:
`module-inventory.json` and `module-dependency-graph.txt` in the task evidence directory
(`media-platform-module-boundary-consolidation-r1-evidence/`). Nothing in this inventory is inferred
from historical documents.

## 1. Project set

| Dimension | Value |
|---|---|
| Active Gradle projects | **53** (`include(...)` in `settings.gradle.kts`) |
| Nested project | `:platform-algorithms:graph` (path `platform-algorithms/graph`) |
| HOLD project | `spring-ai-adapter` — only included with `-PincludeHoldModules=true`; excluded from the default build |
| Build plugins | `java-library` / `java` (+ `org.springframework.boot` for the three bootable projects) |
| Toolchain | Java 25 everywhere |

No project appears only in documentation; every directory with a `build.gradle.kts` is included
except the HOLD project, and every included project has a build file.

## 2. Module roles

Classification is descriptive; it does not rename or restructure anything.

### 2.1 Deployment / composition

| Module | Files | Main project deps | Consumers | Responsibility |
|---|---|---|---|---|
| `platform-app` | 297 | 40 | — (root) | Spring Boot composition root: API role (`PlatformApplication`), WORKER role (`PlatformFfmpegWorkerApplication`), COMBINED assembly, provider-plugin host wiring, Temporal activities, REST controllers. |
| `platform-distribution` | 1 | 9 + task dep on `:ffmpeg-provider-module` | — | Builds `media-platform-launcher.jar`, nests `embedded-plugins/` and stages `plugins/exact-provider-plugin.jar`. Packaging only. |
| `sandbox-worker` | 6 | 1 | — | Separate sandbox worker binary (`SandboxWorkerApplication`). |
| `remote-render-worker` | 5 | 6 | — | Separate remote-render worker binary (`RemoteRenderWorkerApplication`, no DataSource auto-config). |

### 2.2 Provider runtime and provider implementations

| Module | Files | Main project deps | Consumers | Provider role |
|---|---|---|---|---|
| `provider-plugin-runtime-module` | 25 | `api`: extension, worker-fabric, media-execution-plan, sandbox-isolation, pf4j; `implementation`: **bmf-provider-module**, artifact, shared-kernel | 4 | PF4J host (`ProviderPluginHost`), typed catalog, in-process registration helper, execution-backend registry. **Also hosts `BmfProviderExtension`** (see F-02). |
| `ffmpeg-provider-module` | 16 | extension, provider-plugin-runtime, media-execution-plan, worker-fabric, sandbox-isolation (+compileOnly render/font-text/pf4j) | 1 (distribution task dep) | **External PF4J plugin**: `plugin.properties` (`media.transcode.ffmpeg` / 1.2.0), `plugin.class`, `@Extension FfmpegProviderPluginContribution`. Shipped as `ffmpeg-provider-plugin-1.2.0.jar`. |
| `bmf-provider-module` | 14 | media-execution-plan, worker-fabric, sandbox-isolation (+compileOnly extension/render) | 1 (provider-plugin-runtime) | **In-process provider implementation**: `BmfCpuProvider`, binding factory, runtime bundle verifier, `BmfExecutionBackend`. No plugin descriptor, no `@Extension`. |

**Loading mode: HYBRID.** FFmpeg = `EXTERNAL_PF4J_PLUGIN` loaded by `DefaultPluginManager` from
`app.extensions.plugins-dir` (default `./plugins`). BMF = `CLASSPATH_EXTENSION` / in-process
contribution merged by `InProcessProviderPlugins.catalogWith(host.catalog(), …)` behind
`platform.bmf-provider.enabled` (default `false`). Neither mode is hot-reload/upgrade: plugins load
once in `ProviderPluginHost.loadAndStart()`.

### 2.3 Platform contracts / kernels

| Module | Files | Consumers | Responsibility |
|---|---|---|---|
| `shared-kernel` | 37 | 43 | Shared value types, ids, errors, web/security primitives. |
| `typed-schema-module` | 439 | 17 | jOOQ-generated typed schema and shared table access. |
| `extension-module` | 110 | 10 | Extension registry and plugin-registration ports/contracts. |
| `media-capability-contracts` | 3 | 1 | Provider-neutral capability contract values. |
| `audit-contract-module` | 4 | 12 | Audit event contract (no infrastructure). |
| `usage-contract-module` | 11 | 4 | Usage metering contract. |
| `platform-algorithms:graph` | 5 pkgs | 3 | Pure graph-algorithm kernel (`com.example.platform.graph.api`), non-Spring. |

### 2.4 Infrastructure and cross-cutting

| Module | Files | Consumers | Responsibility |
|---|---|---|---|
| `storage-module` | 104 | 11 | StorageRuntime ownership: materialization, checksum, storage references. |
| `artifact-module` | 122 | 15 | Artifact authority: versions, pins, commit/fencing-facing state. |
| `datasource-module` | 9 | 2 | JDBC/jOOQ datasource wiring. |
| `config-module` | 4 | 1 | Shared configuration primitives. |
| `secrets-config-module` | 24 | 2 | Secret material resolution. |
| `observability-module` | 15 | 6 | Metrics/tracing adapters. |
| `outbox-event-module` | 43 | 10 | Transactional outbox. |
| `scheduler-module` | 7 | 1 | Scheduling primitives. |
| `sandbox-isolation-module` | 53 | 11 | Sandbox execution contracts and bounded process launching. |
| `worker-fabric-module` | 259 | 7 | Worker admission, runtime bindings, provider-native runtime contracts. |
| `storage-provider-opendal` | 11 | 0 | Optional OpenDAL storage provider (guarded by `OpenDalProviderBoundaryTest`, no production consumer). |

### 2.5 Feature (application + domain) modules

| Module | Files | Consumers | Responsibility |
|---|---|---|---|
| `timeline-module` | 352 | 8 | Canonical Timeline model, revisions, snapshots, operation adapter. |
| `operation-module` | 30 | 3 | Canonical Operation planner/authority. |
| `render-module` | 696 | 12 | C20 planning, C21 physical planning, render/operation application services, revision services. |
| `media-execution-plan-module` | 122 | 9 | Execution plan model, admission, provider binding (C22-facing). |
| `media-module` | 29 | 6 | Canonical media model and probes. |
| `composition-module` | 36 | 1 | Composition/output assembly services. |
| `delivery-module` | 11 | 2 | Delivery and download orchestration. |
| `identity-access-module` | 130 | 11 | Identity, authorization, tenant scope. |
| `audit-compliance-module` | 45 | 2 | Audit capture and compliance queries. |
| `billing-module` | 129 | 3 | Billing and usage accounting. |
| `entitlement-module` | 85 | 6 | Entitlement decisions. |
| `commerce-module` | 48 | 2 | Commerce/catalog. |
| `payment-module` | 43 | 1 | Payment provider adapters. |
| `marketplace-module` | 22 | 4 | Marketplace listing surfaces. |
| `policy-governance-module` | 49 | 4 | Policy definitions and evaluation. |
| `notification-module` | 50 | 1 | Notification delivery. |
| `workflow-module` | 68 | 1 | Workflow definitions and graph reuse. |
| `prompt-module` | 26 | 2 | Prompt templates and versioning. |
| `ai-module` | 33 | 3 | AI capability adapters. |
| `federation-query-module` | 103 | 1 | Federated query surfaces. |
| `user-analytics-module` | 31 | 2 | Analytics projections. |
| `social-publish-module` | 37 | 1 | Social publishing. |
| `cloud-resource-module` | 10 | 1 | Cloud resource descriptors. |
| `font-text-module` | 39 | 7 | Typed text/font value semantics. |
| `font-pipeline-module` | 22 | 1 | Font pipeline lowering. |
| `color-image-module` | 23 | 3 | Color/raster value semantics. |
| `audio-module` | 10 | 7 | Audio mix value semantics (domain only). |
| `composite-resource-module` | 41 | **0** | Composite-resource domain model with its own modularity test; no production consumer (F-05). |

## 3. Compile-time dependency graph

- **Main configurations (`api`, `implementation`, `compileOnly`, `runtimeOnly`): 0 cycles.**
- **Test-only configurations: 0 cycles.**
- `platform-distribution` references `:ffmpeg-provider-module` through
  `evaluationDependsOn(...)` + the provider `Jar` task, i.e. a packaging task dependency, not a code
  dependency. Counting it as a compile edge produced the only apparent cycle candidates; it is not one.
- Hubs (most-consumed): `shared-kernel` (43), `typed-schema-module` (17), `artifact-module` (15),
  `render-module` (12), `audit-contract-module` (12), `storage-module` (11),
  `identity-access-module` (11), `sandbox-isolation-module` (11), `extension-module` (10),
  `outbox-event-module` (10).
- `platform-app` has 40 `implementation` project dependencies. That is expected for a composition
  root and is **not** treated as a defect.

## 4. Package / public-API boundary facts

- **0** imports of `*.internal`, `*.infrastructure` or `*.persistence` types outside the declaring
  module, except 9 wiring imports inside `platform-app` (composition root: storage 3, commerce 2,
  usage 1, billing 1, identity 1, render 1). These are Spring configuration references to concrete
  beans, not domain code reaching into another module's internals.
- **0** `public` types inside `internal` packages.
- Domain/API packages importing `org.springframework`, `org.jooq`, `jakarta.servlet` or `org.pf4j`:
  46 occurrences, all in `*.api` packages that hold REST controllers
  (`@RestController`/`@RequestMapping`). The contract *types* are clean; the observation is that
  `api` is overloaded to mean both "public contract" and "HTTP transport" (F-07, style only).
- Spring Modulith annotations (`@ApplicationModule`, `@NamedInterface`) are used across modules and
  `platform-app/src/test/.../ModularityTest.java` is the zero-tolerance boundary test with a curated
  `ALLOWED_VIOLATIONS` list (`docs/modulith-debt-register.md`). **That test is red at the base
  commit** — see F-01.

## 5. Spring assembly and runtime roles

| Role | Boot class | Activation | Scope |
|---|---|---|---|
| API | `PlatformApplication` | default (`matchIfMissing = true` for API beans) | Full component scan of product packages; `ProviderPluginHost` + catalog; PF4J plugins from `./plugins`; BMF contribution only when `platform.bmf-provider.enabled=true`. |
| WORKER | `PlatformFfmpegWorkerApplication` | `@ConditionalOnProperty(platform.runtime.role=WORKER)` | Restricted scan (storage, artifact, datasource, shared, workerfabric, sandbox, providerplugin, runtime packages) — no HTTP controllers. |
| COMBINED | `PlatformApplication.CombinedRuntimeAssembly` | `platform.runtime.role=COMBINED` | Imports the worker runtime configurations into the API process; inert for API and WORKER. |
| Sandbox worker | `SandboxWorkerApplication` | separate binary | Sandbox execution only. |
| Remote render worker | `RemoteRenderWorkerApplication` | separate binary | Remote render, no DataSource auto-config. |

Provider beans are registered under mutually exclusive conditions (`API` vs `WORKER`/`COMBINED`), so
no duplicate `ProviderPluginHost`/`ProviderPluginCatalog` bean exists in any role. No domain service
was found living in the composition root beyond `config.mediaexecution.ArtifactCapabilityApplicationService`
(the F-01 cycle participant).

## 6. Provider identity and runtime boundary

- Identity: `pluginId` + `pluginVersion` (`media.transcode.ffmpeg@1.2.0`); PF4J wrapper id/version are
  cross-checked against the typed contribution in `ProviderPluginHost.preflight`; duplicate
  `id@version` is rejected. Bindings use `ProviderBindingPin`/capability contract references; no
  parallel provider identity exists.
- Runtime layers are distinct: Java provider adapter (in-process) → native executable
  (`FFMPEG_WORKER_BINARY`, BMF python entry) → sandboxed process (bubblewrap via
  `sandbox-isolation-module`) → worker sandbox. PF4J classloader isolation applies only to the
  FFmpeg plugin; BMF is a normal classpath contribution.

**Cost of a third minimal provider** (evidence-based):

1. New Gradle module implementing `ProviderPluginContribution` (+ `ProviderDescriptor`,
   `ProviderExecutionContract`, `ProviderCapabilityProfile`) — depends on
   `provider-plugin-runtime-module` (SPI + host), `media-execution-plan-module`,
   `worker-fabric-module`, `sandbox-isolation-module`.
2. External plugin: add `plugin.properties` + `@Extension` and ship the JAR through
   `platform-distribution` (`embedded-plugins/`, `plugins/`). No core code change.
3. In-process plugin: add a contribution class and a `@Bean` in
   `ProviderPluginManagerConfiguration` / `FfmpegWorkerPluginConfiguration`, plus a
   `platform-app` dependency line — i.e. a composition-root edit.
4. Runtime: provide the executable/entry point and the runtime bundle digest; register the
   capability→backend mapping. `RuntimeExecutionBackends.create()` currently hardcodes exactly two
   backends, so a third execution backend needs a composition-root edit too (F-02).

## 7. Confirmed findings (summary)

| ID | Severity | Modules | Fact | Disposition |
|---|---|---|---|---|
| F-01 | P1 | `platform-app` (`config` ↔ `runtime`), `bmf` → `workerfabric` | `ModularityTest` is red at the base commit: 1004 unexpected-violation messages, including `Slice config -> Slice runtime -> Slice config` and `bmf` depending on non-exposed `workerfabric` types | **CONFIRMED_PREEXISTING**, not fixed (architecture decision + debt register), reported |
| F-02 | P1 | `provider-plugin-runtime-module`, `bmf-provider-module`, `ffmpeg-provider-module` | Host module depends on a concrete provider (`implementation :bmf-provider-module`), hosts `BmfProviderExtension`, and `RuntimeExecutionBackends.create()` instantiates `BmfExecutionBackend`; every provider plugin therefore builds against BMF; javadoc claims "without concrete provider dependencies" | **HOLD** — bounded remediation planned, needs a real BMF regression; Owner scheduling |
| F-03 | P2 | `platform-app` | `implementation(project(":extension-module"))` declared twice | **FIXED** — one line removed; resolved runtime classpath unchanged (0-line diff) |
| F-04 | P2 | 12 modules | 23 `implementation` project deps have no direct import in main or test sources | **NO_CHANGE** — import absence does not prove removability (Spring scanning, service loading); recorded as a measurement |
| F-05 | P2 | `composite-resource-module` | 41 files, no production consumer, no Spring components, own modularity test | **HOLD** — foundation or retire; Owner decision |
| F-06 | P2 | `storage-provider-opendal` | No production consumer; direction enforced by `OpenDalProviderBoundaryTest` | **KEEP** — optional storage provider |
| F-07 | P2 | `artifact`, `render`, `media`, … `*.api` | REST controllers live in `api` packages (46 framework imports) | **NO_CHANGE** — style; renaming is churn without runtime benefit |
| F-08 | INFO | — | Three-role Spring assembly is explicit and mutually exclusive; no duplicate provider beans | **PASS** |
| F-09 | INFO | provider modules | Loading mode is HYBRID (FFmpeg external PF4J, BMF in-process), documented and contained | **PASS** (mode classification) |
| F-10 | INFO | — | Main dependency graph acyclic; no cross-module implementation leakage outside the composition root; no public types in `internal` packages | **PASS** |
