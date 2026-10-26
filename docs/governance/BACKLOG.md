# Backlog Registry

> **Canonical source of truth** for all pending / deferred / blocked work items.
>
> - Other reports, probes, tag messages and task briefs **SHALL reference IDs defined here**; they are not canonical.
> - New backlog items **MUST be registered here** before (or with) the report that introduces them.
> - Conversation-only or tag-only items are **not canonical until registered here**; their `Evidence` records the exact source and, where applicable, the honest marker `CONVERSATION_ONLY`.
>
> Schema per item: `ID / Title / Layer / Status / Origin / Decision / Reason / Dependency / Trigger / Evidence`.
> Status vocabulary: `ACTIVE` (待实施，已排期) · `DEFERRED` (明确延后，有触发条件) · `BLOCKED` (被前置阻塞) · `PLANNED` (已设计未实施) · `SUPERSEDED` (被替代/已闭合).
>
> Registry created: 2026-10-06 (HEAD `a5a3a06d`). Owner ruling: BACKLOG.md is the single source of truth; other artifacts reference IDs only.

---

## Artifact

### FONT-6C-ARTIFACT-BYTES

| Field | Value |
|---|---|
| ID | `FONT-6C-ARTIFACT-BYTES` |
| Title | Font pipeline Phase 6c — Artifact → bytes retrieval path |
| Layer | Artifact |
| Status | `DEFERRED` |
| Origin | Font V1 Phase 6 split (Phase 6c), `font-v1-phase6b-catalog-wiring` |
| Decision | DEFERRED / REQUIREMENT-TRIGGERED (owner, Phase 6b tag) |
| Reason | 无 ArtifactStore；`ArtifactRetrievalPort` 无实现且其结果类型不携带 bytes；字体字节只能经文件系统（`app.fonts.dir`），故 6c 无落地路径。 |
| Dependency | NONE |
| Trigger | 产品需求明确要求从 Artifact 体系（而非文件系统）获取字体字节 |
| Evidence | tag `font-v1-phase6b-catalog-wiring`（"Phase 6c (Artifact -> Bytes) DEFERRED / REQUIREMENT-TRIGGERED"）；`audit-runs/FONT_REMAINING_20261005/FONT_REMAINING_SCOPE_PROBE.md`（目标 4）；`audit-runs/FONT_V1_PHASE6_20261005/PHASE6_SCOPE_PROBE.md:416`；`artifact-module/.../contract/ArtifactRetrievalPort.java:6-8` |

---

## Capability

（当前无已登记项。）

---

## Provider

（当前无已登记项。）

---

## Planning

### UNIFY-PLAN-APPLY-CONTRACT

| Field | Value |
|---|---|
| ID | `UNIFY-PLAN-APPLY-CONTRACT` |
| Title | Unify the Operation plan→apply contract |
| Layer | Planning |
| Status | `ACTIVE` |
| Origin | P2-5.5 收尾 tag backlog；L6 Operation framework probe |
| Decision | UNKNOWN |
| Reason | L6 框架技术债：plan→apply 契约存在历史分叉（text plan 与非 text plan 的 re-plan 行为差异），需统一为单一契约。仓库当前仅记录名称，未展开条目明细。 |
| Dependency | NONE |
| Trigger | L6 技术债清理排期 |
| Evidence | `audit-runs/L6_OPERATION_20261005/OPERATION_FRAMEWORK_PROBE.md:272`；`audit-runs/TAG_P2_5_5_20261004/TAG_REPORT.md`；tag `p2-5.5-text-apply-npe-fixed` |

---

## Timeline

### ALIGNMENT-TYPED-DELTA-SEMANTICS

| Field | Value |
|---|---|
| ID | `ALIGNMENT-TYPED-DELTA-SEMANTICS` |
| Title | Typed incremental delta semantics for alignment endpoint changes |
| Layer | Timeline (L5 lineage) |
| Status | `DEFERRED` |
| Origin | Gate 5 Design Freeze；Operation V2 joint implementation |
| Decision | V1 先保守 replace（remove+add）；typed 增量留 backlog（owner 裁定 3） |
| Reason | alignment 端点集合变更当前由 `CanonicalTimelineDiffCalculator` 走保守 replace（`RELATIONSHIP_REMOVED`+`RELATIONSHIP_ADDED`），typed 增量语义未定义，故无法表达"同 identity 的端点增删"。 |
| Dependency | NONE |
| Trigger | 需要对齐端点变更的 typed 增量（可辨识的成员级 diff）时 |
| Evidence | tag `gate-4-5-v2-joint-design-freeze`（"typed delta semantics deferred as ALIGNMENT-TYPED-DELTA-SEMANTICS"）；tag `l5-operation-v2-joint-implementation`；`audit-runs/L5_OPERATION_V2_SCOPE_20261005/IMPLEMENTATION_SCOPE_FREEZE.md`；`timeline-module/.../diff/calculation/CanonicalTimelineDiffCalculator.java`（保守 replace 注释） |

### L5-BASELINE-SOURCE-UNIT-SCOPED-DIGEST

| Field | Value |
|---|---|
| ID | `L5-BASELINE-SOURCE-UNIT-SCOPED-DIGEST` |
| Title | Source-unit-scoped baseline digest for L5 lineage |
| Layer | Timeline (L5 lineage) |
| Status | `DEFERRED` |
| Origin | Gate 7；Operation V2 joint implementation |
| Decision | V1 使用整文档 digest；source-unit-scoped digest 登记为 backlog（owner 裁定） |
| Reason | `SourceBaseline.sourceContentDigest` 当前写入的是整文档 digest（`digester.digest(base)`），任何无关 canonical 编辑都会改变它，从而无法区分"被对齐 source 变化"与"无关变化"。 |
| Dependency | `L5-SOURCE-UNIT-RESOLUTION-SEMANTICS` |
| Trigger | L5 V2 设计门（SourceUnit Resolution Semantics）之后 |
| Evidence | tag `l5-operation-v2-joint-implementation`（"Out of scope (deferred): … L5-BASELINE-SOURCE-UNIT-SCOPED-DIGEST …"）；`operation-module/.../plan/OperationPlanner.java:549`；`audit-runs/L5_STALE_DIVERGED_20261005/STALE_DIVERGED_PROBE.md`（问题 4 / B4） |

### L5-SOURCE-UNIT-RESOLUTION-SEMANTICS

| Field | Value |
|---|---|
| ID | `L5-SOURCE-UNIT-RESOLUTION-SEMANTICS` |
| Title | Resolve SourceUnitId to a canonical document entity |
| Layer | Timeline (L5 lineage) |
| Status | `BLOCKED` |
| Origin | `l5-lineage-relationship-v1-freeze` architecture boundary |
| Decision | "Future L5 V2 must open a new design gate starting from 'SourceUnit Resolution Semantics'"（owner） |
| Reason | `SourceUnitId` 无到文档内实体的映射（`TextElement` 无 lineage 字段，C5 禁止新增 `TextElement.sourceUnitId`；无 index/lookup）；L5 STALE/DIVERGED 的精度依赖该解析。 |
| Dependency | NONE（本项是 `L5-STALE-SEMANTICS` / `L5-DIVERGED-SEMANTICS` / `L5-BASELINE-SOURCE-UNIT-SCOPED-DIGEST` 的前置） |
| Trigger | L5 V2 设计门开启 |
| Evidence | tag `l5-lineage-relationship-v1-freeze`（"Architecture boundary (frozen) … starting from 'SourceUnit Resolution Semantics'"）；`audit-runs/L5_STALE_DIVERGED_20261005/STALE_DIVERGED_PROBE.md`（问题 1/4，B1/B4）；`audit-runs/L5_OPERATION_V2_JOINT_20261005/JOINT_IMPLEMENTATION.md`。ID 串本身在仓库 0 命中 ⇒ ID 为 **CONVERSATION_ONLY**（语义等价记录见左）；本 registry 首次登记。 |

### L5-STALE-SEMANTICS

| Field | Value |
|---|---|
| ID | `L5-STALE-SEMANTICS` |
| Title | STALE detection semantics for L5 lineage |
| Layer | Timeline (L5 lineage) |
| Status | `DEFERRED` |
| Origin | `l5-lineage-relationship-v1-freeze`（OUT OF V1 列表） |
| Decision | OUT OF V1（deferred；owner）；Lineage state 默认按需计算，不持久化 |
| Reason | STALE 的业务定义与判定输入未定；整文档 baseline 会结构性误报（无关编辑即判 STALE）。 |
| Dependency | `L5-SOURCE-UNIT-RESOLUTION-SEMANTICS` |
| Trigger | L5 V2 设计门 |
| Evidence | tag `l5-lineage-relationship-v1-freeze`（"Explicitly OUT OF V1 (deferred…) - STALE detection …"）；`audit-runs/L5_STALE_DIVERGED_20261005/STALE_DIVERGED_PROBE.md`（问题 1）。ID 串本身在仓库 0 命中 ⇒ ID 为 **CONVERSATION_ONLY**；本 registry 首次登记。 |

### L5-DIVERGED-SEMANTICS

| Field | Value |
|---|---|
| ID | `L5-DIVERGED-SEMANTICS` |
| Title | DIVERGED detection semantics for L5 lineage |
| Layer | Timeline (L5 lineage) |
| Status | `BLOCKED` |
| Origin | `l5-lineage-relationship-v1-freeze`（OUT OF V1 列表） |
| Decision | OUT OF V1（deferred / blocked；owner） |
| Reason | `TimelineMergeConflictDetector` 需要三方 snapshot `(base, ours, theirs)`，而 L5 的 source/derived 位于同一文档、无第二分支；三方语义无法映射 ⇒ 复用 detector 不可达。 |
| Dependency | `L5-SOURCE-UNIT-RESOLUTION-SEMANTICS`（+ 需 Owner 定义三方/投影语义） |
| Trigger | L5 V2 设计门 + 三方语义裁定 |
| Evidence | tag `l5-lineage-relationship-v1-freeze`（"Explicitly OUT OF V1 (deferred…) - DIVERGED detection …"）；`audit-runs/L5_STALE_DIVERGED_20261005/STALE_DIVERGED_PROBE.md`（问题 2/3，B2/B3）。ID 串本身在仓库 0 命中 ⇒ ID 为 **CONVERSATION_ONLY**；本 registry 首次登记。 |

### TIMELINE-DOCUMENT-FIELD-PRESERVATION-P1

| Field | Value |
|---|---|
| ID | `TIMELINE-DOCUMENT-FIELD-PRESERVATION-P1` |
| Title | TimelineDocument structural hardening (copyOf / builder / short-ctor deprecation) |
| Layer | Timeline |
| Status | `DEFERRED` |
| Origin | `timeline-field-preservation-p0`（Gate 8 P0 CLOSED） |
| Decision | P0 = 最小字段完整性修复；P1 = `copyOf(base)` 派生机制迁移 + 短构造器 Deprecated（deferred） |
| Reason | P0 只对 4 个 sink 做止血；`TimelineDocument` 仍有 4 个短构造器（3/4/5/6-arg）静默默认字段，且无 `copyOf`/builder，未来 sink 仍可能重复丢字段。 |
| Dependency | NONE |
| Trigger | P1 排期（结构加固） |
| Evidence | tag `timeline-field-preservation-p0`（"No copyOf / withXxx / builder (P1 deferred)"、"No short-ctor deprecation (P1 deferred)"、"Gate 8 P0 CLOSED. P1 (structural hardening) deferred."）；`audit-runs/TIMELINE_FIELD_PRESERVATION_20261005/P0_IMPLEMENTATION.md`（Backlog 记录）。tag 未给出精确 ID ⇒ ID 由本 registry 首次登记。 |

---

## Operation

### CONTROLLER-DIGEST-VALIDATION-CONVENTION

| Field | Value |
|---|---|
| ID | `CONTROLLER-DIGEST-VALIDATION-CONVENTION` |
| Title | Unify controller input-validation convention for canonical content digest |
| Layer | Operation |
| Status | `DEFERRED` |
| Origin | Operation V2 joint implementation（out of scope 列表） |
| Decision | V1 不改 DTO 结构；约定统一登记为 backlog |
| Reason | alignment controller 将 `baseContentHash` 校验为 bounded text（canonical digest 为 Base64），而 legacy media-clip controller 要求 64-hex 且该 controller 处于 `@Profile("legacy-media-disabled")`；两处校验约定不一致。 |
| Dependency | NONE |
| Trigger | 统一 controller 输入校验约定时 |
| Evidence | tag `l5-operation-v2-joint-implementation`（"Out of scope (deferred): … CONTROLLER-DIGEST-VALIDATION-CONVENTION."）；`platform-app/.../web/render/AlignmentOperationController.java`（bounded text 校验）；`platform-app/.../web/render/TimelineMediaClipOperationController.java:171-175`（64-hex `requireDigest`） |

### OPERATION-MODULE-TEST-CLASSPATH-MEDIA-TYPES

| Field | Value |
|---|---|
| ID | `OPERATION-MODULE-TEST-CLASSPATH-MEDIA-TYPES` |
| Title | operation-module test classpath lacks media-module types |
| Layer | Operation |
| Status | `ACTIVE` |
| Origin | Timeline Field-Preservation P0 implementation（Gap 记录） |
| Decision | UNKNOWN |
| Reason | `operation-module` 的测试类路径不含 media-module 类型（`MediaAssetId`/`MediaStreamId` 等），导致 `timeline.media-clip.add` 的字段保留测试被迫落在 render-module；测试归属与模块边界不一致。 |
| Dependency | NONE |
| Trigger | 测试基建清理排期 |
| Evidence | `audit-runs/L4_L6_CONVERGENCE_20261005/L4_L6_CONVERGENCE_PROBE.md`（L6-Q6 #3）；`audit-runs/TIMELINE_FIELD_PRESERVATION_20261005/P0_IMPLEMENTATION.md`（将 sink 3 测试置于 render-module 的理由）。精确 ID 串仅见于上述报告与任务简报 ⇒ ID 为 **CONVERSATION_ONLY**；本 registry 首次登记。 |

### CANONICAL-FIELDS-ASSERT-SHARED

| Field | Value |
|---|---|
| ID | `CANONICAL-FIELDS-ASSERT-SHARED` |
| Title | Share the CanonicalFieldsAssert test helper across modules |
| Layer | Operation |
| Status | `ACTIVE` |
| Origin | Timeline Field-Preservation P0 implementation |
| Decision | UNKNOWN |
| Reason | `CanonicalFieldsAssert` 在 timeline-module / operation-module / render-module 各复制一份（跨模块 test 依赖未接线），三份内容重复，存在漂移风险。 |
| Dependency | NONE |
| Trigger | 测试基建清理排期 |
| Evidence | `audit-runs/L4_L6_CONVERGENCE_20261005/L4_L6_CONVERGENCE_PROBE.md`（L6-Q6 #4）；`audit-runs/TIMELINE_FIELD_PRESERVATION_20261005/P0_IMPLEMENTATION.md`（模块本地副本说明）。精确 ID 串仅见于上述报告与任务简报 ⇒ ID 为 **CONVERSATION_ONLY**；本 registry 首次登记。 |

### PHASE-E-15-OP-WIRING

| Field | Value |
|---|---|
| ID | `PHASE-E-15-OP-WIRING` |
| Title | Wire the 15 remaining V1 operations (non-text, non-media-clip) |
| Layer | Operation |
| Status | `BLOCKED` |
| Origin | Phase E remaining-15 probe；L6 Operation framework probe |
| Decision | PRODUCT-DECISION-BLOCKED（owner 未裁决是否接线） |
| Reason | `OperationDefinition.V1` 的 25 项中 15 项未接线（MOVE/DELETE/TRIM/temporal×3/audio×3/group×3/sync×3）；L6 probe 记为 15 项 `PRODUCT-DECISION-BLOCKED` op。 |
| Dependency | NONE |
| Trigger | 产品裁定需要接线这 15 项 operation 时 |
| Evidence | `audit-runs/PHASE_E_20261004/REMAINING_15_PROBE.md`（"TARGET 1: 剩余 15 项清单"）；`audit-runs/L6_OPERATION_20261005/OPERATION_FRAMEWORK_PROBE.md:271`（"15 项 PRODUCT-DECISION-BLOCKED op"）。精确 ID 串在仓库 0 命中 ⇒ ID 为 **CONVERSATION_ONLY**；本 registry 首次登记。 |

---

## Font

### FONT-FACEMANIFEST-ACCESSOR-GAP

| Field | Value |
|---|---|
| ID | `FONT-FACEMANIFEST-ACCESSOR-GAP` |
| Title | FontFaceManifest exposes only 11 of 21 fields via accessors |
| Layer | Font |
| Status | `ACTIVE` |
| Origin | Font V1 Phase 6/6b probes；L6 Operation framework probe（技术债） |
| Decision | UNKNOWN（记为技术债，未裁定补/不补） |
| Reason | `FontFaceManifest` 有 21 个字段但仅 11 个 accessor；其余 10 个（`subfamilyName`/`weightClass`/`widthClass`/`slantStyle`/`glyphCount`/`hasGsub`/`hasGpos`/`hasGdef`/`validationState`/`conformanceState`）无 accessor，测试只能以反射读取（生产不需要 getter，因类上 `@JsonAutoDetect(fieldVisibility=ANY)`）。 |
| Dependency | NONE |
| Trigger | 需要丰富 `FontFaceManifest` API 或消除测试反射时 |
| Evidence | `font-text-module/.../fonttext/manifest/FontFaceManifest.java:17,24-44,77-87`；`font-pipeline-module/src/test/.../manifest/DefaultFontFaceManifestAssemblerTest.java:28-29,193-201`；`audit-runs/L6_OPERATION_20261005/OPERATION_FRAMEWORK_PROBE.md:272`；`audit-runs/FONT_REMAINING_20261005/FONT_REMAINING_SCOPE_PROBE.md`（目标 1） |

### FONT-GOVERNANCE

| Field | Value |
|---|---|
| ID | `FONT-GOVERNANCE` |
| Title | Font governance model (license / scope / fallback chain / evidence layering) |
| Layer | Font |
| Status | `DEFERRED` |
| Origin | Font V1 backlog（V2）；Phase 2 governance drift fix reports |
| Decision | V2 backlog（owner 记录；V1 不建设） |
| Reason | 5 类治理字段（许可 / 作用范围 / fallback chain / 证据分层 / subset 约束）在代码中零实现：无 `FontGovernanceProvider`、无 `FontLicense`/`FontScope` 类型，font 生产代码零 `license` 命中；doc 的 pipeline "License 检查" 阶段属 DOCUMENTED_ONLY。 |
| Dependency | NONE |
| Trigger | 字体分发 / 嵌入许可需要治理时（V2） |
| Evidence | `docs/render/font-pipeline.md:26,83,99`；`docs/media-rendering/subtitle-font-pipeline-readiness.md:87`（PLANNED, "Policy definitions only, no enforcement"）；`audit-runs/FONT_REMAINING_20261005/FONT_REMAINING_SCOPE_PROBE.md`（目标 2）；`audit-runs/FONT_V1_PHASE2_20261004/GOVERNANCE_DRIFT_FIX.md`、`GOVERNANCE_DRIFT_FIX_B.md` |

### FONT-SUBSET-ATLAS

| Field | Value |
|---|---|
| ID | `FONT-SUBSET-ATLAS` |
| Title | License-constrained font subsetting and glyph atlas |
| Layer | Font |
| Status | `DEFERRED` |
| Origin | Font V1 backlog（V2） |
| Decision | V2 backlog |
| Reason | subset 侧仅有接口与禁用实现（`PyftsubsetFontSubsetter` 默认 `enabled=false`，flag `render.font.subset.enabled` 未在任何 yml/properties 接线；`FontSubsetCache` 无实现；无许可约束检查）；"atlas" 在 Java 中零命中。 |
| Dependency | `FONT-GOVERNANCE`（subset 需受许可约束） |
| Trigger | 需要按许可生成字体派生物时（V2） |
| Evidence | `render-module/.../infrastructure/font/FontSubsetter.java:6-29`；`PyftsubsetFontSubsetter.java:33,52-60`；`FontSubsetCache.java:5-16`；`audit-runs/FONT_REMAINING_20261005/FONT_REMAINING_SCOPE_PROBE.md`（目标 3） |

### FONT-SHAPING-RUNTIME

| Field | Value |
|---|---|
| ID | `FONT-SHAPING-RUNTIME` |
| Title | Text shaping runtime (HarfBuzz) |
| Layer | Font |
| Status | `DEFERRED` |
| Origin | Font V1 backlog（rendering stage）；`font-v1-phase1-foundation`（parsing/shaping 一并延后） |
| Decision | deferred（`font-pipeline-module/build.gradle.kts` 明文 "Shaping (lwjgl-harfbuzz) is intentionally out of scope."） |
| Reason | `HarfBuzzShapingValidator.enabled()` 恒 `false`（返回空 coverage）；shaping 值类型 `TextShapingRequest`/`ShapedGlyphRun` 无生产者与消费者；模块无 lwjgl-harfbuzz 依赖。 |
| Dependency | NONE |
| Trigger | 渲染阶段需要真实 shaping（HarfBuzz）时 |
| Evidence | `render-module/.../infrastructure/font/HarfBuzzShapingValidator.java:17-19,22-25`；`font-pipeline-module/build.gradle.kts:22`；`font-text-module/.../execution/ShapedGlyphRun.java:7-10`；`audit-runs/FONT_REMAINING_20261005/FONT_REMAINING_SCOPE_PROBE.md`（目标 5） |

### FONT-RESOLUTION-INVOCATION-COUPLING

| Field | Value |
|---|---|
| ID | `FONT-RESOLUTION-INVOCATION-COUPLING` |
| Title | FontResolutionInput is always non-null, forcing resolveFonts on non-font ops |
| Layer | Font |
| Status | `ACTIVE` |
| Origin | Font Subsystem backlog（关联问题 1） |
| Decision | 不实施修复（避免为绕 422 改 op 语义）；记录为独立 backlog |
| Reason | `FontResolutionInput` bean 永远非空 → `withContent`/`withStyledText` 无条件触发 `resolveFonts`；当 catalog 为空（`app.fonts.dir` 无字体）时 5 项 text op 抛 422，即使其语义上不需要字体解析。 |
| Dependency | NONE（与 Phase 6b catalog 内容相关，但无前置 backlog ID） |
| Trigger | caption 字体解析可用性影响这 5 项 text op 时 |
| Evidence | `audit-runs/FONT_SUBSYSTEM_BACKLOG_20261004/FONT_SUBSYSTEM_BACKLOG.md:80-85`；`audit-runs/TEXT_OP_APPLY_NPE_20261004/FIX_REPORT.md`；`audit-runs/TAG_P2_5_5_20261004/TAG_REPORT.md`；tag `p2-5.5-text-apply-npe-fixed`（"Backlog: UNIFY-PLAN-APPLY-CONTRACT, FONT-RESOLUTION-INVOCATION-COUPLING"） |

---

## Governance / Cross-cutting

### GOVERNANCE-LEDGER-COUNT-STALENESS

| Field | Value |
|---|---|
| ID | `GOVERNANCE-LEDGER-COUNT-STALENESS` |
| Title | Governance ledger discovered-test counts go stale |
| Layer | Governance |
| Status | `ACTIVE` |
| Origin | L4/L6 convergence probe；测试执行拓扑治理 |
| Decision | UNKNOWN |
| Reason | 测试执行拓扑 ledger 的 `DISCOVERED_TEST_COUNT` 等计数只在 owner 行为主时回填，长期无回填机制，计数随套件演进变陈旧。 |
| Dependency | NONE |
| Trigger | 治理 ledger 回填/校验排期 |
| Evidence | `audit-runs/L4_L6_CONVERGENCE_20261005/L4_L6_CONVERGENCE_PROBE.md`（L6-Q6）；`governance/TEST_EXECUTION_TOPOLOGY_LEDGER.tsv`（含 DISCOVERED_TEST_COUNT 列）。该 ID 串在本仓库仅见于上述报告与任务简报 ⇒ ID 为 **CONVERSATION_ONLY**；本 registry 首次登记。 |

### HASH-GUARD-MECHANISM-REDESIGN

| Field | Value |
|---|---|
| ID | `HASH-GUARD-MECHANISM-REDESIGN` |
| Title | Redesign the H8 hash-guard mechanism |
| Layer | Governance |
| Status | `ACTIVE` |
| Origin | `operation-observability-baseline`（H8 hash-guard drift） |
| Decision | 记录为独立治理债（"not addressed here"） |
| Reason | H8 hash-guard 存在既有漂移（guard 期望哈希与仓库内容不同步）；机制本身需重新设计，而非单次修数。 |
| Dependency | NONE |
| Trigger | 治理债清理排期 |
| Evidence | tag `operation-observability-baseline`（"H8 hash-guard drift noted as separate governance debt (not addressed here)"）；别名 `HASH-GUARD-DRIFT-PREEXISTING` 见 `audit-runs/L6_OPERATION_20261005/OPERATION_FRAMEWORK_PROBE.md:272`。该 ID 串在仓库 0 命中（tag 用 "H8 hash-guard drift"）⇒ 别名映射 + **CONVERSATION_ONLY**（精确串）。 |

### PLATFORM-APP-TEST-TIMEOUT-INVESTIGATION

| Field | Value |
|---|---|
| ID | `PLATFORM-APP-TEST-TIMEOUT-INVESTIGATION` |
| Title | platform-app full test suite exceeds practical timeout |
| Layer | Governance |
| Status | `ACTIVE` |
| Origin | Timeline Field-Preservation P0（全量 `:platform-app:test` 超时中断）；L4/L6 probe |
| Decision | UNKNOWN |
| Reason | `:platform-app:test` 全量运行在本环境超时（45 分钟被 `timeout` 终止），且部分 DB 集成测试在基线即失败（schema 未迁移），导致无法在全量口径上验收。 |
| Dependency | NONE |
| Trigger | 需要全量 platform-app 验收时 |
| Evidence | `audit-runs/TIMELINE_FIELD_PRESERVATION_20261005/P0_IMPLEMENTATION.md`（STEP 7 超时说明）；`audit-runs/L4_L6_CONVERGENCE_20261005/L4_L6_CONVERGENCE_PROBE.md`（L6-Q6）。该 ID 串在仓库 0 命中 ⇒ ID 为 **CONVERSATION_ONLY**；本 registry 首次登记。 |

### GOVERNANCE-VALIDATOR-MAIN-BRANCH-ENFORCEMENT

| Field | Value |
|---|---|
| ID | `GOVERNANCE-VALIDATOR-MAIN-BRANCH-ENFORCEMENT` |
| Title | Enforce main-branch validator preconditions |
| Layer | Governance |
| Status | `ACTIVE` |
| Origin | Font V1 Phase 2 governance drift fix |
| Decision | UNKNOWN（记录为治理项） |
| Reason | 治理校验器未强制 main-branch 前置条件，允许在非预期分支状态上执行校验，产生漂移证据。 |
| Dependency | NONE |
| Trigger | 治理校验器加固排期 |
| Evidence | `audit-runs/FONT_V1_PHASE2_20261004/GOVERNANCE_DRIFT_FIX.md` |

### GOVERNANCE-CENSUS-CONSTANT-SINGLE-SOURCE

| Field | Value |
|---|---|
| ID | `GOVERNANCE-CENSUS-CONSTANT-SINGLE-SOURCE` |
| Title | Single source for census constants |
| Layer | Governance |
| Status | `ACTIVE` |
| Origin | Font V1 Phase 2 governance drift fix (B) |
| Decision | UNKNOWN（记录为治理项） |
| Reason | census 常量存在多处定义，缺少单一事实来源，导致计数漂移与跨报告不一致。 |
| Dependency | NONE |
| Trigger | 治理/计数常量收敛排期 |
| Evidence | `audit-runs/FONT_V1_PHASE2_20261004/GOVERNANCE_DRIFT_FIX_B.md` |

### AUDIT-RUNS-TRACKING-DECISION

| Field | Value |
|---|---|
| ID | `AUDIT-RUNS-TRACKING-DECISION` |
| Title | Audit artifact classification / retention / version-control policy |
| Layer | Governance |
| Status | `ACTIVE` |
| Origin | Freeze-Origin Probe（P7.0.7 后续只读探针） |
| Decision | UNKNOWN（Owner 授权登记；策略未定；优先级：高） |
| Reason | `audit-runs/` 整体**未被 git 跟踪**（`git ls-files audit-runs/` = 0）⇒ First Render 全部治理/探针产物不在版本历史中，冻结依据（§8/§9 原文）不可追溯。需决定：哪些审计产物纳入版本控制、哪些保留为临时、保留期、以及 canonical 存放位置。 |
| Dependency | NONE |
| Trigger | Owner 排期（审计产物治理策略） |
| Evidence | `audit-runs/FIRST_RENDER_FREEZE_ORIGIN_PROBE_20261010/FIRST-RENDER-FREEZE-ORIGIN-PROBE-REPORT.md`（BASELINE / 交付物 4-6） |

### FREEZE-SECTION-9-CLARIFICATION-REGISTRATION-DRIFT

| Field | Value |
|---|---|
| ID | `FREEZE-SECTION-9-CLARIFICATION-REGISTRATION-DRIFT` |
| Title | Registration drift: FREEZE-SECTION-9-CHAIN-SEMANTICS-CLARIFICATION listed as pre-existing but absent |
| Layer | Governance |
| Status | `ACTIVE` |
| Origin | Freeze-Origin Probe（`FREEZE-SECTION-9-CHAIN-SEMANTICS-CLARIFICATION` 不在 registry 中） |
| Decision | UNVERIFIED / PENDING OWNER CLARIFICATION（不新增该 ID；优先级：中） |
| Reason | 设计提案 `FIRST-RENDER-FREEZE-AMENDMENT-DESIGN-PROPOSAL.md:323` 将 `FREEZE-SECTION-9-CHAIN-SEMANTICS-CLARIFICATION` 列为**"既有"** backlog 项，但 `docs/governance/BACKLOG.md` **无该 ID**（仓内唯一出现 = 该提案 `:323` 与探针报告 `:336`）。批准状态 **UNVERIFIED**；无法判定是"已批准漏登记"还是"提案误记"。本轮**未授权**新增该 ID 到 registry。 |
| Dependency | NONE |
| Trigger | Owner 澄清批准状态与正确处理方式（新增 / 记为 CONVERSATION_ONLY / 判为误记） |
| Evidence | `audit-runs/FIRST_RENDER_FREEZE_AMENDMENT_DESIGN_20261010/FIRST-RENDER-FREEZE-AMENDMENT-DESIGN-PROPOSAL.md:323`；`audit-runs/FIRST_RENDER_FREEZE_ORIGIN_PROBE_20261010/FIRST-RENDER-FREEZE-ORIGIN-PROBE-REPORT.md:336` |

### FLYWAY-V5-UNCOMMITTED-BASELINE-BLOCKER

| Field | Value |
|---|---|
| ID | `FLYWAY-V5-UNCOMMITTED-BASELINE-BLOCKER` |
| Title | Single Flyway baseline is untracked, blocking reproducibility |
| Layer | Governance |
| Status | `APPROVED_FOR_IMPLEMENTATION — TECHNICAL_ACCEPTANCE_PENDING` |
| Remediation (2026-10-20 / index convergence 2026-10-22) | `F8-BASELINE-GOVERNANCE-REMEDIATION-R2` passed independent review. `F8-GIT-INDEX-CONVERGENCE-R1` staged 48 reviewed F8 paths on 2026-10-22; independent candidate review is pending, and technical acceptance remains unexecuted. |
| Git index convergence (2026-10-22) | `F8-GIT-INDEX-CONVERGENCE-R1` staged 48 F8 paths; index candidate review pending; no commit authorized. |
| V5 source evidence | The original `/tmp/g1-baseline-sources/V5__provider_candidate_snapshot.sql` is missing; the repository audit copy (`9de6c8c3…`) is the only retained evidence, and byte-for-byte re-verification against the original is not possible in this round. |
| Origin | G1 Flyway/PostgreSQL 治理（G1-R11A → G1-CLOSURE-DECISION → G1-CLOSURE-RECORD）；Owner 批准于 `PROJECT-GOVERNANCE-REGISTRATION-AND-G1-CLOSURE`（2026-10-15）；**2026-10-15 由 `FLYWAY-SINGLE-BASELINE-GOVERNANCE` Phase 3 更新** |
| Decision | Owner decision R1 approved `V1__baseline.sql` SHA-256 `49c79810db8e928626402b10f102613a5ffe8f6073b265458c0c09a964c58795` for controlled implementation. The baseline is staged for version control; no commit is authorized by this task. |
| Reason | The active migration set is now the single V1 baseline, while the old V1–V4 files are absent. The V5 source audit copy is retained as auditable evidence at `docs/database/evidence/flyway-single-baseline/V5__provider_candidate_snapshot.sql` and is not an active V5 migration. The original temporary source is missing (recorded 2026-10-20), so the audit copy is the only retained evidence. The current baseline bytes still require independent technical acceptance before F8 can close. |
| Dependency | `FLYWAY-BASELINE-TECHNICAL-ACCEPTANCE` (independent authorization) |
| Trigger | Owner approval of the implementation result, followed by separate technical acceptance and commit authorization. |
| Evidence | Current baseline: `platform-app/src/main/resources/db/migration/V1__baseline.sql`, 210,121 bytes, SHA-256 `49c79810db8e928626402b10f102613a5ffe8f6073b265458c0c09a964c58795`. V5 audit copy SHA-256 `9de6c8c3289934b55be718ae3d58c1d38950cc6bbc4b3d5e0f52ef99957e2021`; original source missing. 2026-10-20 R2 worktree remediation and 2026-10-22 index convergence (48 F8 paths staged; candidate review pending) record the active-reference convergence and remaining governance steps. Prior `c1a2cd35…` empty-database/fingerprint/jOOQ evidence remains historical only and does not apply to the current hash. |

### CONTAINER-GOVERNANCE-CLEANUP-PENDING

| Field | Value |
|---|---|
| ID | `CONTAINER-GOVERNANCE-CLEANUP-PENDING` |
| Title | Container governance: leftover Testcontainers fleet never confirmed or cleaned |
| Layer | Governance |
| Status | `ACTIVE` |
| Origin | G1 容器治理（G1 / G1-R1 / G1-R2 → G1-CLOSURE-DECISION）；Owner 批准于 `PROJECT-GOVERNANCE-REGISTRATION-AND-G1-CLOSURE`（2026-10-15） |
| Decision | 未授权、未执行清理；顺序固定为：只读逐 ID 归属判定 → 备份/豁免决策 → **逐 ID** 删除授权 |
| Reason | 盘点记录 161 个容器，其中 120 个 `postgres:15-alpine`、119 个带 `org.testcontainers=true` 标签；但**无一个**进入 `CONFIRMED_PROJECT_TESTCONTAINER`（逐 ID 会话/时间关联从未执行），2 个 running 的 Testcontainers 容器属 `UNKNOWN_OWNER`；`~/.testcontainers.properties` 的 `ryuk.disabled=true` 使 JVM 异常退出时容器泄漏。未确认归属的对象（尤其 `UNKNOWN_OWNER`）必须继续受保护。 |
| Dependency | NONE |
| Trigger | Owner 授权容器治理与经授权的安全清理（队列第 3 项） |
| Evidence | `docs/project-governance/PROJECT-STATE-AND-HANDOFF.md` §4；仓库外档案 `../audit-runs/G1-FLYWAY-POSTGRES-CONSOLIDATION/G1-CONTAINER-INVENTORY.md`、`../audit-runs/G1-R1-BASELINE-AND-CONTAINER-RECONCILIATION/G1-R1-TESTCONTAINERS-OWNERSHIP.md`、`../audit-runs/G1-R2-FINAL-SCHEMA-RECONCILIATION/G1-R2-TESTCONTAINERS-CLEANUP-SPEC.md`。ID 由本次登记首次引入本 registry。 |

---

## First Render / Worker-Fabric

### LOCAL-HOST-REGISTRATION-LOOP-JAVADOC-DRIFT

| Field | Value |
|---|---|
| ID | `LOCAL-HOST-REGISTRATION-LOOP-JAVADOC-DRIFT` |
| Title | LocalHostRegistrationLoop javadoc vs code drift on snapshot generation |
| Layer | Worker-Fabric |
| Status | `RESOLVED`（核实于 2026-10-13；原状态 `ACTIVE`） |
| Origin | P7.0.5 probe（First Render Gate） |
| Decision | RESOLVED — 文档与实现已一致，无需再改任一方 |
| Reason | `LocalHostRegistrationLoop` javadoc（`:24-25`）声明 `snapshot generation stays first() for bounded V1 (never advanced per tick)`，但代码 `registerOnce`（`:84-87`）每 tick 取 `current.value() + 1`（或 `first()`）。文档与实现分歧；其真实行为决定 G5（fingerprint 随 `capturedAt`/`generation` 变化）的程度。 |
| Dependency | NONE |
| Trigger | 运行态核实：`registerHost` 是否推进 `wf_host_snapshot_generation_authority`，以及 `currentSnapshotGeneration` 跨 tick 的返回值 |
| Resolution | 2026-10-13 复核：javadoc `:25-26` 现写明 "snapshot generation advances by one on every registration tick … each later tick at `current.value() + 1`"，与代码 `:100` (`current.value() + 1`) / `:101` (`first()`) 完全一致；分歧已不存在。 |
| Evidence | `worker-fabric-module/.../domain/LocalHostRegistrationLoop.java:25-26,100-101`（原引用 `:24-25,84-87` 已被后续改动推进）；`audit-runs/FIRST_RENDER_PHASE_7_P7.0.5_20261010/FIRST-RENDER-P7.0.5-REPORT.md`（补充 2 / 交付物 5） |

### WORKER-CRASH-GRANT-NON-INVALIDATION

| Field | Value |
|---|---|
| ID | `WORKER-CRASH-GRANT-NON-INVALIDATION` |
| Title | Worker crash leaves AssignmentGrant non-invalidated (no incarnation change on normal restart) |
| Layer | Worker-Fabric |
| Status | `ACTIVE` |
| Origin | P7.0.5 probe（First Render Gate）；关联 `LEASE-EXPIRY-VS-FENCING-COVERAGE-GAP` |
| Decision | UNKNOWN（Owner 裁定 3：独立高优先级缺口，**不扩入** Gate；不预判"必须启用 lease expiry"或"仅靠 fencing 足够"；优先级：高） |
| Reason | runtime incarnation 为**常量**（`LocalHostRegistrationLoop.java:40` `local-ffmpeg-v1`），host incarnation 为静态容量指纹（`:78-79,138-157`）⇒ **普通重启不改变 incarnation** ⇒ `JooqWorkerFabricRegistrationBoundary` 的再注册 fencing（`:55,180`）**不触发** ⇒ 旧 grant 残留。`findCurrentGrant` 不校验 `expires_at`（`JooqAtomicAssignmentGrantBoundary.java:205-213`）；Task E `expireLease`/`disconnect*` 无生产调用者（`JooqExecutionLifecycleBoundary.java:100-208`）。 |
| Dependency | `LEASE-EXPIRY-VS-FENCING-COVERAGE-GAP` |
| Trigger | 崩溃恢复语义裁定（启用 lease expiry 或新增 fencing 触发），本项本次未排期 |
| Evidence | `worker-fabric-module/.../domain/LocalHostRegistrationLoop.java:40,78-79,138-157`；`worker-fabric-module/.../infrastructure/JooqWorkerFabricRegistrationBoundary.java:55,180`；`.../JooqAtomicAssignmentGrantBoundary.java:205-213`；`.../JooqExecutionLifecycleBoundary.java:100-208`；`audit-runs/FIRST_RENDER_PHASE_7_P7.0.5_20261010/FIRST-RENDER-P7.0.5-REPORT.md`（组 4 / 交付物 4） |

### REQUEST-WORK-ID-VS-CONTEXT-VERSION-EXPLICIT-TYPE

| Field | Value |
|---|---|
| ID | `REQUEST-WORK-ID-VS-CONTEXT-VERSION-EXPLICIT-TYPE` |
| Title | Explicit type for logical request identity vs validation-context version |
| Layer | Worker-Fabric |
| Status | `ACTIVE` |
| Origin | P7.0.5 probe（First Render Gate） |
| Decision | UNKNOWN（G5 规则**不冻结**、禁机械绑定；优先级：中） |
| Reason | 隐含区分**已存在**：`wf_request_work_resolution.request_work_id`（PK，身份）与 `request_context_fingerprint`（64-hex，上下文版本）（`V1__initial_schema.sql:4246-4263`）；`toResult` 以二者判定 `REJECTED(REQUEST_ID_REUSED_WITH_DIFFERENT_CONTEXT)`（`JooqAtomicAssignmentGrantBoundary.java:524-539`）。代码层**无显式类型**（均为 String/列）。 |
| Dependency | NONE |
| Trigger | 需要显式"上下文版本"类型/端口（或冻结 G5 规则）时 |
| Evidence | `platform-app/src/main/resources/db/migration/V1__initial_schema.sql:4246-4263`；`worker-fabric-module/.../infrastructure/JooqAtomicAssignmentGrantBoundary.java:524-539`；`audit-runs/FIRST_RENDER_PHASE_7_P7.0.5_20261010/FIRST-RENDER-P7.0.5-REPORT.md`（组 2）；`audit-runs/FIRST_RENDER_FREEZE_AMENDMENT_DESIGN_20261010/FIRST-RENDER-FREEZE-AMENDMENT-DESIGN-PROPOSAL.md`（组 C） |

### RENDER-GRAPH-TO-REQUESTWORK-FIELD-MAPPING

| Field | Value |
|---|---|
| ID | `RENDER-GRAPH-TO-REQUESTWORK-FIELD-MAPPING` |
| Title | Map a saved render graph onto RequestWork admission input |
| Layer | First Render（Planning → Worker bridge） |
| Status | `RESOLVED`（核实于 2026-10-13；原状态 `ACTIVE`） |
| Origin | P7.0.5 probe（First Render Gate）；G2 细化 |
| Decision | RESOLVED — 映射已在生产代码中实现（P1-1A.6/7） |
| Reason | 候选投影已存在：`PendingNativeWorkProjection.projectAll(graph, contribution)`（`provider-plugin-runtime-module/.../PendingNativeWorkProjection.java:55-109`）；但已保存 graph 与 `RequestWork`（`worker-fabric-module/.../domain/RequestWork.java:13-27`）**无共同字段**（`RequestWork` 不含 graph/plan/task id）⇒ 需定义"graph 的哪一部分决定 `RequestWork` 内容"，并需 validation-context read authority（现仅 `currentSnapshotGeneration`，`worker-fabric-module/.../domain/WorkerFabricRegistrationBoundary.java:15-17`）。 |
| Dependency | `RENDER-GRAPH-TO-VALIDATION-CONTEXT-BRIDGE`、`WORKER-REGISTRATION-READ-AUTHORITY` |
| Trigger | Freeze Amendment 定稿（D2 / D1-经调整） |
| Resolution | 2026-10-13 复核：映射现由 `FirstRenderGrantMinter.mint(...)`（`platform-app/.../mediatask/FirstRenderGrantMinter.java:227`）与 `PendingNativeWorkAdmission`（`provider-plugin-runtime-module/.../PendingNativeWorkAdmission.java:51`）通过 `PendingNativeWorkProjection.projectAll(graph, contribution)` 完成；`RequestWork` 由持久化 registration + descriptor + snapshot + readiness + 精确 provider contribution 组装，validation-context read authority 已扩展为 `WorkerFabricRegistrationBoundary` 的 read model（`:16,26,37,42,48,60,82,102`），不再仅 `currentSnapshotGeneration`。 |
| Evidence | `platform-app/.../mediatask/FirstRenderGrantMinter.java:227`；`provider-plugin-runtime-module/.../PendingNativeWorkAdmission.java:51`；`worker-fabric-module/.../domain/WorkerFabricRegistrationBoundary.java:16,26,37,42,48,60,82,102`；`provider-plugin-runtime-module/.../providerplugin/PendingNativeWorkProjection.java:55-109`；`audit-runs/FIRST_RENDER_FREEZE_AMENDMENT_DESIGN_20261010/FIRST-RENDER-FREEZE-AMENDMENT-DESIGN-PROPOSAL.md`（组 B） |

### PENDING-NATIVE-WORK-PROJECTION-CALLER

| Field | Value |
|---|---|
| ID | `PENDING-NATIVE-WORK-PROJECTION-CALLER` |
| Title | Trace the caller and production wiring of PendingNativeWorkProjection.projectAll |
| Layer | Provider |
| Status | `ACTIVE` |
| Origin | P7.0.2 / P7.0.5 probes（First Render Gate） |
| Decision | UNKNOWN（G3 admission 编排未定；优先级：高） |
| Reason | `PendingNativeWorkProjection.projectAll(...)`（`PendingNativeWorkProjection.java:55-109`）的唯一调用者是 `PendingNativeWorkAdmission.admit(...)`（`.../PendingNativeWorkAdmission.java:50-52`），而后者**无生产调用者**；`NativePullAdmissionPort` 仅有 bean 定义（`platform-app/.../runtime/FfmpegWorkerRuntimeConfiguration.java:144-146`）。 |
| Dependency | `ADMISSION-ORCHESTRATION-ENTRY` |
| Trigger | Freeze Amendment 定稿（D2 / D1-经调整）后接线 |
| Evidence | `provider-plugin-runtime-module/.../providerplugin/{PendingNativeWorkProjection,PendingNativeWorkAdmission}.java`；`platform-app/.../runtime/FfmpegWorkerRuntimeConfiguration.java:144-146`；`audit-runs/FIRST_RENDER_PHASE_7_P7.0.2_20261010/FIRST-RENDER-P7.0.2-REPORT.md`（Q5/Q6） |

### FREEZE-SECTION-8-9-ORIGIN-LOCATION

| Field | Value |
|---|---|
| ID | `FREEZE-SECTION-8-9-ORIGIN-LOCATION` |
| Title | Repository-exhaustive location of the First Render Freeze §8/§9 original text |
| Layer | First Render |
| Status | `ACTIVE` |
| Origin | P7.0.7 probe → Freeze-Origin Probe |
| Decision | NOT FOUND IN REPO（Owner 授权登记；优先级：高） |
| Reason | 穷尽搜索（工作区全扩展名 / `audit-runs/` / `docs/` / `.agent-tasks/` / `git grep` / `git log --all -S` / 全部 tag / 分支 / reflog / stash / FETCH_HEAD / ignored）后，First Render Freeze §8/§9 **原文不在仓库可检索范围**；所有 §8/§9 命中均为**二次引用**（Agent 报告），零逐字原文。冻结文件名 `FIRST-RENDER-OWNER-DECISION-FREEZE` 仅 1 处按名引用（`READINESS_AND_STOP.md:21`），且从未被 git 跟踪。 |
| Dependency | NONE |
| Trigger | Owner 提供 §8/§9 原文，或裁定 conversation-level 权威 |
| Evidence | `audit-runs/FIRST_RENDER_FREEZE_ORIGIN_PROBE_20261010/FIRST-RENDER-FREEZE-ORIGIN-PROBE-REPORT.md`（范围 1-5 / 交付物 4：`NOT FOUND IN REPO`） |

### FIRST-RENDER-CONVERSATION-LEVEL-FREEZE-BOUNDARY

| Field | Value |
|---|---|
| ID | `FIRST-RENDER-CONVERSATION-LEVEL-FREEZE-BOUNDARY` |
| Title | Authority boundary / evidence level / revision rule for the conversation-level First Render freeze |
| Layer | First Render |
| Status | `ACTIVE` |
| Origin | P7.0.7 probe → Freeze-Origin Probe（B1 vs B3 未裁定） |
| Decision | UNKNOWN（Owner 授权登记；B1/B3 未选；优先级：高） |
| Reason | 冻结文件 `FIRST-RENDER-OWNER-DECISION-FREEZE`（Owner ruling, 2026-10-06）在仓内**只有按名引用、无本体**（`READINESS_AND_STOP.md:21`），全 git 历史从未出现该串；§8/§9 无逐字原文，仅二次引用流通。需明确：对话级冻结的权威边界、证据等级（`FACT` / `UNVERIFIED`）、以及后续修订规则。**不得**将二次引用升级为权威契约。 |
| Dependency | `FREEZE-SECTION-8-9-ORIGIN-LOCATION` |
| Trigger | Owner 对 B1 vs B3 的裁定 |
| Evidence | `audit-runs/FIRST_RENDER_FREEZE_ORIGIN_PROBE_20261010/FIRST-RENDER-FREEZE-ORIGIN-PROBE-REPORT.md`（范围 4 / 交付物 5）；`audit-runs/FIRST_RENDER_IMPLEMENTATION_20261006/FIRST_RENDER_IMPLEMENTATION_READINESS_AND_STOP.md:21` |

### D2-A-REPLAY-CONTEXT-REUSE-DESIGN

| Field | Value |
|---|---|
| ID | `D2-A-REPLAY-CONTEXT-REUSE-DESIGN` |
| Title | Replay / context-reuse design for candidate D2-a (worker-side pending-work discovery) |
| Layer | First Render |
| Status | `ACTIVE` |
| Origin | Freeze Amendment design proposal（组 D，2a） |
| Decision | UNKNOWN（D2/D1 未选；Owner 授权登记；优先级：高） |
| Reason | 提案 2a 需新增 worker 侧"待准入"发现机制/标记（当前不存在：`BoundGraphInputStore` 仅 save/load；`render_binding_inputs` 无 pending 列）。若实现 2a，则需定义同一 `RequestWorkId` 跨 tick 重放时的 validation-context 生成/复用规则（提案 `:165`、`:188`）；该行为与未冻结的身份-vs-上下文版本规则（G5）耦合。 |
| Dependency | `RENDER-GRAPH-TO-REQUESTWORK-FIELD-MAPPING`、`REQUEST-WORK-ID-VS-CONTEXT-VERSION-EXPLICIT-TYPE` |
| Trigger | Freeze Amendment 定稿（D2-a） |
| Evidence | `audit-runs/FIRST_RENDER_FREEZE_AMENDMENT_DESIGN_20261010/FIRST-RENDER-FREEZE-AMENDMENT-DESIGN-PROPOSAL.md`（组 D） |

### GRANT-ABSENT-TEMPORAL-RETRY-SEMANTICS

| Field | Value |
|---|---|
| ID | `GRANT-ABSENT-TEMPORAL-RETRY-SEMANTICS` |
| Title | Temporal retry semantics when worker execution fails with GRANT_ABSENT |
| Layer | Worker-Fabric |
| Status | `ACTIVE` |
| Origin | P7.0.7 probe（First Render Gate） |
| Decision | UNKNOWN（Owner 授权登记；优先级：中） |
| Reason | `MediaTaskActivity.executeTask` 在无 per-task grant 时 fail-closed 抛 `TaskRuntimeExecutionConstructionException`（`MediaTaskActivity.java:205-209`，`GRANT_ABSENT`）；P7.0.7 探针记录：`IllegalStateException` 不在 `doNotRetry` 列表 ⇒ Temporal 可能按 retry policy 重调同一 activity，而准入从未被调用、无 grant 可恢复（既不"重试原准入"，也不"恢复已有 grant"）。重试语义归属与边界未定义。 |
| Dependency | `ADMISSION-ORCHESTRATION-ENTRY` |
| Trigger | Freeze Amendment 定稿后接线 |
| Evidence | `audit-runs/FIRST_RENDER_PHASE_7_P7.0.7_20261010/FIRST-RENDER-P7.0.7-REPORT.md`（Q2） |

---

## Closed / Superseded

> 保留供追溯。Status 用 `SUPERSEDED` 表示"已闭合或被替代"。

### TEXT-OP-APPLY-SOURCEINSTANCE-NULL

| Field | Value |
|---|---|
| ID | `TEXT-OP-APPLY-SOURCEINSTANCE-NULL` |
| Title | TextOperationPlanner produced OperationPlan with null sourceInstance |
| Layer | Operation |
| Status | `SUPERSEDED` |
| Origin | `audit-runs/FONT_SUBSYSTEM_BACKLOG_20261004`（关联问题 2）/ `TEXT_OP_APPLY_NPE_20261004` |
| Decision | 已修复（`OperationPlan` 增 `replanRequired`；text plan 保持 `sourceInstance`） |
| Reason | 原缺陷：text plan 的 `sourceInstance == null` 使 `OperationPlanApplyService` re-plan 触发 NPE。已由 `fd68b8bf`（4-arg overload 组装 instance）与 `9ff9e75f`（apply 按 `replanRequired` 跳过 text re-plan）修复。 |
| Dependency | NONE |
| Trigger | —（已闭合） |
| Evidence | `audit-runs/TEXT_OP_APPLY_NPE_20261004/FIX_REPORT.md`（commit `fd68b8bf` / `9ff9e75f`）；`render-module/.../app/plan/OperationPlanApplyService.java:111-131`（`replanRequired` 分支注释）；tag `p2-5.5-text-apply-npe-fixed` |

### REVISION-COMMAND-MODEL-DOC-DRIFT

| Field | Value |
|---|---|
| ID | `REVISION-COMMAND-MODEL-DOC-DRIFT` |
| Title | revision-command-model-v1.md declared unimplemented services as CLOSED |
| Layer | Governance |
| Status | `SUPERSEDED` |
| Origin | `l5-timeline-revision-semantics-probe`（"governance drift to be fixed separately"） |
| Decision | 修正文档，不实现 Service（owner） |
| Reason | 原文档 status `CLOSED` 宣称 `RevisionCommandApplyService`（零实现）与 `RevisionCommandConcurrencyIT`（仓库不存在）；已由 DOC-FIX 修正为 `PARTIAL / SPECIFIED_NOT_IMPLEMENTED` 并加 "Implementation status (2026-10-05)"。 |
| Dependency | NONE |
| Trigger | —（已闭合） |
| Evidence | `docs/architecture/governance/revision-command-model-v1.md:5,9,53,102-105`（status 与 Implementation status / Drift correction 段）；tag `l5-timeline-revision-semantics-probe` |

### FONT-PHASE1-PARSING-INFRASTRUCTURE

| Field | Value |
|---|---|
| ID | `FONT-PHASE1-PARSING-INFRASTRUCTURE` |
| Title | Font parsing infrastructure (FontBox / LWJGL / OTS) |
| Layer | Font |
| Status | `SUPERSEDED` |
| Origin | `font-v1-phase1-foundation`（"No font parsing infrastructure (FontBox/LWJGL/OTS deferred)"） |
| Decision | 由 Phase 2-6b 交付（owner 分阶段推进） |
| Reason | Phase 1 时延后的解析/适配/装配/净化能力，已分别在 Phase 2（FontBox）、3（LWJGL）、4（Assembler）、5（OTS）、6a/6b（编排 + catalog 接线）交付。 |
| Dependency | NONE |
| Trigger | —（已闭合） |
| Evidence | tags `font-v1-phase1-foundation`…`font-v1-phase6b-catalog-wiring`；`audit-runs/FONT_V1_PHASE6B_20261005/PHASE6B_IMPLEMENTATION.md:93` |

---

## Registry notes

- **Aliases**: `HASH-GUARD-DRIFT-PREEXISTING` ≡ `HASH-GUARD-MECHANISM-REDESIGN`（same item, tag wording differs）。
- **Evidence markers**: `CONVERSATION_ONLY` 表示该 ID 或该条目的**精确字符串**在仓库文件与 tag message 中均不存在，来源为会话/任务简报；条目本体在首次登记时被写入本 registry。
- **Neighbouring governance artifacts（非本 registry，勿混淆）**: root `governance/*.tsv`（测试执行拓扑/CI 依赖账本）、`docs/architecture/governance/**`（roadmap/ADR/来源权威语料、以及 `h2-phase19-deferred-capability-reassessment-v1.json` 等**范围受限**的 deferred/disposition 台账）。它们是 scoped 证据，不是 canonical backlog registry。
- **Non-goals of this file**: 不裁决优先级（priority 由 Owner 排期）；不替代 probe/设计文档；不承载 spec。
