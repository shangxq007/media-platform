# Modulith Contract Closure R1 — Declaration Matrix (AV-001 … AV-033)

**Base:** `cf0bb5adb334ef3192c99b2d698e9faea763cb9f`
**Legend:** *status* = REMOVED (rule deleted) / KEPT (graph rule retained);
*contract* = the declaration that now carries the dependency.
All consumer declarations live in the consumer module's root `package-info.java`; all target
interfaces live in the target package's `package-info.java` unless marked *type-level*.

| AV | Original rule (scope) | Status | Contract now carrying it | Implementation files |
|---|---|---|---|---|
| AV-001 | identity → storage contract | REMOVED | identity declares `storage :: contract` | `identity-access-module/.../identity/package-info.java` |
| AV-002 | render → outbox | REMOVED | render declares `outbox :: coordination` (+ `outbox :: app/events`) | `render-module/.../render/package-info.java` |
| AV-003 | web → storage :: contract | REMOVED | web declares `storage :: contract` | `platform-app/.../web/package-info.java` |
| AV-004 | web → render app/domain | REMOVED | web declares the consumed `render :: app.*` / `render :: domain` interfaces | `platform-app/.../web/package-info.java`, `render-module/.../render/app/*/package-info.java` |
| AV-005 | web → outbox app | REMOVED | web declares `outbox :: app` | `platform-app/.../web/package-info.java` |
| AV-006 | web → ingest diagnostics | REMOVED | web declares the ingest diagnostics packages (named) | `platform-app/.../web/package-info.java`, `platform-app/.../ingest/.../package-info.java` |
| AV-007 | web → storage delivery diagnostics | REMOVED | web declares the storage delivery diagnostics interface | `platform-app/.../web/package-info.java` |
| AV-008 | root → ingest non-exposed | REMOVED (stale) | root declares `ingest :: config/contract/tika` | `platform-app/.../package-info.java` |
| AV-009 | typedschema jooq-tables (6 consumers) | REMOVED | each consumer declares `typedschema :: jooq-tables` (interface pre-existed) | consumer root package-infos |
| AV-010 | typedschema records (5 consumers) | REMOVED | `typedschema :: records` package-level NamedInterface (D3-A) + per-consumer entries | `typed-schema-module/src/main/modulith/java/.../tables/records/package-info.java` |
| AV-011 | workflow → graph | **KEPT** | unchanged precise rule | `platform-app/.../ModularityTest.java` |
| AV-012 | render → extension :: runtime | REMOVED | render declares `extension :: runtime` | `render-module/.../render/package-info.java` |
| AV-013 | render → fonttext | REMOVED | render declares the consumed fonttext value interfaces | `render-module/.../render/package-info.java`, `font-text-module/.../fonttext/*/package-info.java` |
| AV-014 | web → fonttext | REMOVED | web declares the consumed fonttext interfaces | `platform-app/.../web/package-info.java` |
| AV-015 | render → media | REMOVED (stale) | media interfaces + render declaration | `render-module/.../render/package-info.java` |
| AV-016 | render → audio | REMOVED | render declares `audio :: mix` | `render-module/.../render/package-info.java`, `audio-module/.../audio/domain/mix/package-info.java` |
| AV-017 | render → timeline | REMOVED | render declares the consumed timeline interfaces | `render-module/.../render/package-info.java`, `timeline-module/.../timeline/*/package-info.java` |
| AV-018 | render → operation | REMOVED | render declares `operation :: plan` (**type-level** interface) | `operation-module/.../operation/plan/*.java` annotations |
| AV-019 | web → timeline (incl. one adapter edge) | REMOVED | web declares the consumed timeline interfaces; the `timeline.adapter` edge is **refactored away** (D6) | `platform-app/.../web/package-info.java`, `ProjectDashboardController.java` |
| AV-020 | timeline → audio | REMOVED | timeline declares `audio :: mix` | `timeline-module/.../timeline/package-info.java`, `audio-module/.../mix/package-info.java` |
| AV-021 | timeline → fonttext | REMOVED | timeline declares the consumed fonttext interfaces | `timeline-module/.../timeline/package-info.java` |
| AV-022 | timeline → media | REMOVED (stale) | media interfaces + timeline declaration | `timeline-module/.../timeline/package-info.java` |
| AV-023 | operation → timeline | REMOVED | operation root `@ApplicationModule` + consumed timeline semantics interfaces | `operation-module/.../operation/package-info.java`, `timeline-module/.../semantics/*/package-info.java` |
| AV-024 | operation → audio | REMOVED | operation declares `audio :: mix` | `operation-module/.../operation/package-info.java` |
| AV-025 | operation → fonttext | REMOVED | operation declares the consumed fonttext interfaces | `operation-module/.../operation/package-info.java` |
| AV-026 | render → colorimage | REMOVED | render declares `colorimage` | `render-module/.../render/package-info.java`, `color-image-module/.../colorimage/package-info.java` |
| AV-027 | render → graph | **KEPT** | unchanged precise rule | `ModularityTest.java` |
| AV-028 | execution → render.renderplan | REMOVED (stale) | execution declares `render :: renderplan` (interface pre-existing) | `media-execution-plan-module/.../execution/package-info.java` |
| AV-029 | execution → audio | REMOVED | execution declares `audio :: mix` | `media-execution-plan-module/.../execution/package-info.java` |
| AV-030 | execution → graph | **KEPT** | unchanged precise rule | `ModularityTest.java` |
| AV-031 | workerfabric → storage.contract | REMOVED (stale) | worker-fabric declares `storage :: contract` | `worker-fabric-module/.../workerfabric/package-info.java` |
| AV-032 | providerplugin → workerfabric providernative | REMOVED (stale) | host declares `workerfabric :: providernative` (interface pre-existing) | `provider-plugin-runtime-module/.../providerplugin/package-info.java` |
| AV-033 | providerplugin → workerfabric domain | REMOVED (stale) | host declares `workerfabric :: domain` (interface pre-existing) | `provider-plugin-runtime-module/.../providerplugin/package-info.java` |

## Notes

- The three KEPT rules are byte-identical to the pre-round text; no new or broadened pattern was
  introduced.
- `operation.plan` and `operation.operation` use **type-level** `@NamedInterface` because
  `OperationInvocationContractTest` forbids package-level interfaces for those packages; the
  obsolete `operation/operation/package-info.java` is deleted (that guard was red at the base and
  now passes).
- No `infrastructure`, `adapter` or `internal` package was newly exposed; every such
  `@NamedInterface` in the tree predates this round and is unchanged.
