> Historical status record. The current accepted development baseline and migration policy are in [canonical baseline](../development/canonical-baseline.md); old hashes/statuses below are historical evidence, not current release gates.

---
metadata_schema_version: 1
document_id: "project-governance-state-and-handoff"
title: "Project State and Handoff"
artifact_type: "GOVERNANCE_INDEX"
domain: "project-governance"
authority_class: "NORMATIVE_SUPPORTING"
lifecycle_state: "ACTIVE"
acceptance_state: "ACCEPTED"
owner: "owner"
document_version: "1.2"
created_at: "2026-10-15"
last_reviewed_at: "2026-10-22"
review_cadence_days: 30
supersedes: []
superseded_by: []
canonical_contracts: []
source_of_truth_domains: ["project-governance", "database-schema"]
retention_class: "PERMANENT"
generated: false
generated_by: null
do_not_edit: false
requires_explicit_approval: true
blocks_v5: false
---

# Project State and Handoff

> 目的：让**下一会话/下一位代理**在不依赖会话记忆的前提下恢复项目状态，并给出唯一的主线恢复顺序。
> 最近一次更新：**2026-10-22**。

## 0. 权威边界（先读这一段）

本文件是**导航与状态索引**，不是新的架构或流程权威，也不复制其他权威的事实：

* `AGENTS.md`（Tier 0）与 `.kilo/agents/main.md` 仍是**仓库指令权威**；
* `docs/README.md` 仍是**文档加载顺序索引**；
* `docs/architecture/current/architecture-entrypoint.md` 仍是**架构导航唯一入口**；
* `docs/governance/BACKLOG.md` 仍是**待办/延期/阻塞事项的规范登记处**（ID 为规范引用）；
* `docs/architecture/governance/project-state/current-state.yaml` 仍是**机器可读项目状态**（当前内容滞后，见 §9）。

本文件与其冲突时，以上文件优先。`acceptance_state: PENDING_OWNER_REGISTRATION` 表示：**本文件在被 Owner 授权注册进上述入口之前，尚未成为“唯一入口”**（见 §9 的最小注册动作）。

`acceptance_state: ACCEPTED` 在 front matter 中描述的是**本治理索引文档**的接受状态，不代表 F8 Flyway baseline 已通过技术验收；baseline 状态见 §3，仍为 `TECHNICAL_ACCEPTANCE_PENDING`。`last_reviewed_at` 仅表示本文件最近一次治理复核日期，不是 baseline 验收日期。

### 标签语义（全文强制）

* `VERIFIED` — 本任务在只读条件下**直接观察**（仓库文件、目录枚举、只读 `git`）；
* `REPORTED` — 仅来自既有代理/审计报告，**本轮未独立复核**（代理报告不等于独立核验）；
* `INFERRED` — 由已验证事实推断；
* `UNKNOWN` — 证据不足，需最小补充授权。

---

## 1. 基线（`VERIFIED`，2026-10-15 初测；2026-10-20 R2 复核）

| 项 | 值 |
|---|---|
| 仓库 | `/home/user/Documents/workspace/projects/media-platform` |
| Branch / HEAD | `main` / **`315d3df9c56c9c7cfab8437639696ba0e4ee83a9`** |
| Staged / Worktree | `48 staged` / **`100 M + 4 D + 473 ??`**（2026-10-22 F8 index 收敛后；staged 候选限定为 F8 范围；其余为保护性既有工作区内容与生成物） |
| 保护约定 | 上述脏工作区属**保护基线**，不得清理、重置或覆盖 |
| 证据来源日期 | G1 系列报告 2026-10-14 ~ 2026-10-15；本文件 2026-10-15；R2 复核 2026-10-20 |

`VERIFIED` 本文件创建时（2026-10-15）仅新增两份文档（本文件与
`audit-runs/G1-CLOSURE-DECISION/G1-CLOSURE-RECORD.md`），未改动任何既有文件；随后的登记任务按 Owner 批准
**追加式**修改了 `docs/README.md`、`docs/architecture/current/architecture-entrypoint.md` 与
`docs/governance/BACKLOG.md`（清单见 §9），未改动其他既有文件；`G1-R10A` 索引（sha256 前 16 位
`2eaf01a637386a29`）与 `G1-R11A` 矩阵（`36d9e892727f2620`）哈希未变。

---

## 2. G1 最终状态

| 项 | 状态 |
|---|---|
| **G1 只读审计阶段** | **`CLOSED`**（仅审计阶段） |
| G1 关闭记录 | `audit-runs/G1-CLOSURE-DECISION/G1-CLOSURE-RECORD.md` |
| G1 裁决材料 | `audit-runs/G1-CLOSURE-DECISION/G1-CLOSURE-DECISION.md` |
| 关闭依据 | Owner 在 `PROJECT-GOVERNANCE-CLOSURE-AND-HANDOFF`（2026-10-15）中指示收口（`REPORTED`：任务单原文） |

**关闭包含的证据链**（`REPORTED`，详见关闭记录 §2）：220 张迁移表对象全集、216 个 jOOQ 表类映射、
394 行 SQL 索引、独立证据复核 + 勘误、220 行表级引用矩阵（`REFERENCED 99 / NO_REFERENCE_FOUND 114 /
AMBIGUOUS 4 / UNRESOLVED 3`，`deletion_eligibility` 全 `NOT_ASSESSED`）。

**关闭不代表**：Flyway 单一基线完成、jOOQ 已重新生成、数据库已初始化、容器治理已验收。

**硬规则（不得违反）**：`NO_REFERENCE_FOUND` / “未发现引用”一律**不得**解释为“未使用”或“可删除”。

---

## 3. Flyway / 单一数据库基线：状态冻结

| 子项 | 状态 | 依据 |
|---|---|---|
| 迁移文件治理 | **`APPROVED_FOR_IMPLEMENTATION`** | Owner decision R1 approved `V1__baseline.sql` as the single candidate (210,121 B, sha256 `49c79810db8e9286…`). The current worktree contains only this active migration; old V1–V4 are absent. |
| 版本控制状态 | **`INDEX_CONVERGED_REVIEW_PENDING`** | On 2026-10-22 the reviewed R1 baseline candidate, R2 fixes, and pre-existing F8 worktree fixes were converged into the index (48 paths). The index is a self-consistent F8 candidate pending independent candidate review. No commit is authorized. V5 audit evidence is retained under `docs/database/evidence/flyway-single-baseline/`. |
| Schema 对象覆盖 | **`TECHNICAL_ACCEPTANCE_PENDING`** | The prior 2026-10-15 object-count and fingerprint evidence applies to the older `c1a2cd35…` bytes, not the current `49c79810…` candidate. |
| jOOQ 一致性 | **`TECHNICAL_ACCEPTANCE_PENDING`** | Prior jOOQ parity evidence is historical and does not bind the current baseline hash. |
| 迁移验收（空库单次 migrate） | **`TECHNICAL_ACCEPTANCE_PENDING`** | The current hash has not yet been migrated on an authorized isolated empty database in this task. |
| R2 修复状态（2026-10-22） | **`R2_REVIEW_PASSED_INDEX_CONVERGED`** | R2 passed independent read-only review; the reviewed F8 differences are now staged in the index. Independent candidate review remains pending; technical acceptance remains pending. |
| canonical 目标库 | **已裁决（D3）** | Owner 冻结：验收使用全新、隔离、可丢弃实例；现有 `media_platform` 保留不动 |

**当前剩余阻塞（R3/F8）**：完成当前 `49c79810…` baseline 的独立空库 Flyway、
Schema fingerprint 和 jOOQ parity 验收；完成 2026-10-20 R2 工作区修复的 index 收敛
（单独 Git 授权）；并在单独授权下完成 Git 提交与 F8 关闭审查。

**实施验收条件（后续实施阶段，两者都需独立授权）**：

1. 在空库上**单次** `flyway migrate` 成功，`flyway_schema_history` 只有一行；
2. jOOQ 再生成后**无差异**（生成路径须覆盖全部 `V*__*.sql`）。

**实施状态（2026-10-19）**：Owner decision R1 approved the current candidate
for controlled governance implementation. The guard, policy document, V5 audit
evidence, and current status records have been synchronized. **Still pending**:
independent empty-database Flyway/Schema/jOOQ acceptance, an authorized Git
commit, F8 close review, and all unrelated governance workflows.

**R2 修复状态（2026-10-22）**：`F8-BASELINE-GOVERNANCE-REMEDIATION-R2` 通过独立只读复审。
2026-10-22 `F8-GIT-INDEX-CONVERGENCE-R1` 已将 R2 修复及预存 F8 工作区修复收敛到 index
（48 路径，含 baseline、V5 证据与治理记录）。index 候选待独立复审；**仍未执行**：
空库 Flyway、Schema/fingerprint、jOOQ 再生成、授权提交、F8 关闭审查。

`VERIFIED`（2026-10-20 R2 复核）：
`docs/database/flyway-migration-baseline.md` 的 `V1–V10` 漂移已在 staged v3.0 + worktree v3.1 中修正；
`scripts/guards/check-local-guardrails.sh:47` 已改为匹配
`platform-app/src/main/resources/db/migration/V1__baseline.sql`，但该修改**仅在 worktree**，
index 仍为 `*/flyway/sql/V1*`。因此 index 收敛仍需单独 Git 任务；在此之前不得把 index
视为可提交候选。

---

## 4. 容器治理：状态冻结

| 子项 | 状态 | 依据（`REPORTED`，来自 G1/G1-R1/G1-R2） |
|---|---|---|
| 盘点 | **`COMPLETE`** | 161 容器；120 个 `postgres:15-alpine`；119 个带 `org.testcontainers=true`（其中 2 个 running）；1 个项目命名卷 `media-platform-postgres-data` |
| 归属判定 | **`INCOMPLETE`** | 119 个仅为 `PROBABLE_PROJECT_TESTCONTAINER`；**`CONFIRMED_*` = 0**；running 的 2 个 TC 容器为 `UNKNOWN_OWNER`；其他项目（ERPNext / Dify / …）按镜像可证 |
| 清理授权 | **未授权** | G1/G1-R1/G1-R2 均声明不得清理；无逐 ID 授权记录 |
| 清理执行 | **未执行** | 全部材料仅含提案与规范；**未见任何删除/prune 执行记录**（该结论仅指“材料未证明”，不等于断言从未发生） |
| 生命周期修复（Ryuk） | **`INCOMPLETE`** | `~/.testcontainers.properties` 的 `ryuk.disabled=true`（G1-R1 读取值，本轮未复核）；修复仅停留在方案 |
| 治理验收记录 | **`INCOMPLETE`** | 无“完成标准 + 逐 ID 验收表” |

### 禁止操作（在本轮及后续未获专门授权前）

不得操作 Podman / 容器 / 卷 / 镜像 / 网络 / 服务；不得启动 `podman.socket`；不得修改 `~/.testcontainers.properties` 或其他用户级配置；不得删除任何容器或卷；**未确认归属的对象必须继续受保护**（尤其 `UNKNOWN_OWNER`）。

### 未来治理所需的最小授权与验收

1. 先授权**只读**的逐 ID 归属判定（7 项证据，含会话时间关联与“非 running”判定）→ 产出 ID 表；
2. 再针对**具体 ID**逐个授权删除（禁止“一键清理全部”）；带命名卷的容器一律停止并升级裁决；
3. 前置：Ryuk 生命周期修复完成并验证；任何删除前记录备份/豁免决策。

`VERIFIED` `podman.socket` 的历史状态：各检查点均为 `inactive`。**能证明**：该命名的用户级 socket 单元当时未启动；
**不能证明**：容器从未被创建或启动，也不能覆盖泄漏期（2026-10-14 之前）的状态；
`~/.testcontainers.properties` 指向 `unix:///run/user/1000/podman/podman.sock`，其与“socket 单元 inactive”的关系在既有材料中**未解释**（`UNKNOWN`）。

---

## 5. 主线恢复顺序（唯一顺序）

| # | 阶段 | 状态 |
|---|---|---|
| 1 | **G2 — Artifact fencing / recovery 设计闭环** | **下一项任务（NEXT）** |
| 2 | L2–L4 — Capability / Provider / Planning 契约收敛 | 排期 |
| 3 | First Render Phase 7 — 真实端到端闭环 | 排期 |
| 4 | 最小 DSL / ANTLR 落地 | 排期 |
| 5 | AI Creative Planning | 排期 |
| 6 | 3D Scene / Asset Pipeline | 排期 |

`VERIFIED`（来自任务单）：**G1 审计关闭后立即恢复 G2**；数据库基线与容器治理属**并行、按需授权**的独立工作流，
不作为主线恢复前置条件，也不得因其未完成而阻塞上述 1–3 项。

当前**下一项任务 = G2 Artifact fencing / recovery 设计闭环**（设计阶段，只读探测 + 证据 + Owner 裁决 + 范围冻结，随后才是独立实施授权）。

---

## 6. 已冻结的决策与重要边界

* **治理规则（全程有效）**：只读探测 → 证据 → Owner 裁决 → 范围冻结 → 独立实施授权。任何实施都必须单独授权，不得由审计或建议自动升级。
* **First Render 与 G2 已冻结的合同不得因本文件或治理收口被重新打开。**
* 脏工作区是保护基线：登记前为 `45 M + 4 D + 32 ??`，登记后为 **`47 M + 4 D + 33 ??`**（`+2 M` = `docs/README.md`、`docs/architecture/current/architecture-entrypoint.md`；`+1 ??` = 本文件所在的 `docs/project-governance/`；`docs/governance/BACKLOG.md` 保持原有 `??` 状态）；2026-10-22 F8 index 收敛后为 **`48 staged / 100 M + 4 D + 473 ??`**（staged 候选为 F8 范围；其余为既有 First Render/治理在途工作与生成物）。不得 reset/clean/stash/覆盖。
* `docs/governance/BACKLOG.md` 是待办唯一规范登记处（`REPORTED` 其自述）；`VERIFIED` 该文件当前**未纳入版本控制**（`??`）→ 属治理风险，建议随下一次文档变更提交。
* V6 唯一约束：既有建议为**并入单一基线、取消独立 V6**（`REPORTED`，未实施）。
* 以下**待 Owner 裁决**项仍未决（`REPORTED`，来源 `C-2R-…` 报告）：`D-Q1′`（planning/candidate projection 的角色归属）、`D-Q3`（API 侧运行期守卫）、`D-Q4`（COMBINED 状态）；`D-Q2` 已在后续提交中处理。
* **正交维度（Owner 裁决，2026-10-15）**：`docs/architecture/governance/project-state/current-state.yaml` 中的 **roadmap-22 phase ledger**（`governance.next_gate` 等阶段门字段）与本文件 §5 的**项目主线顺序**是两个**正交维度**——前者记录既有路线图的阶段门与验收记录，后者记录治理收口后恢复产品能力的优先级；二者互不覆盖、互不派生，任一维度推进都不自动改写另一维度。因此 `current-state.yaml` **本轮不修改**，其 `next_gate` 与本文件 §5 的差异属**已解释的范围差异**，不是冲突。

---

## 7. 未授权事项清单（不得自行推进）

> 下列清单为 2026-10-15 登记时的未授权事项。2026-10-20 `F8-BASELINE-GOVERNANCE-REMEDIATION-R2`
> 已由 Owner 单独授权进行 **F8 文件级定向修复**；该授权**不**包含构建/测试/数据库/服务/Git 写操作，
> 也不包含 G2、C-2R-3、First Render E2E 或其他架构任务。

1. 创建 `V1__baseline.sql`、合并/删除/重命名 `V1–V5`、创建或实施 V6；
2. 运行 Flyway、构建、测试、代码生成或 jOOQ 再生成；
3. 连接、初始化、迁移或修改任何数据库（含现有 `media_platform` 的处置）；
4. 处置未提交的 `V5__provider_candidate_snapshot.sql`；
5. 选择 canonical 库或新建数据库；
6. 任何 Podman/容器/卷/镜像/网络/服务操作，`podman.socket` 启停，用户级配置修改；
7. 容器清理（含“逐 ID 归属判定”本身，也需要单独授权）；
8. 任何 Git 写操作（commit / add / reset / clean / stash / checkout / restore）；
9. 修改业务源码、迁移、生成类、运行配置或既有审计报告；
10. 新增 G1 扩展审计（除非 Owner 明确要求）。

---

## 8. 下一会话启动时必须先读取的文档

1. `AGENTS.md`（含适用路径上的嵌套 `AGENTS.md`）— 仓库指令权威；
2. **本文件** `docs/project-governance/PROJECT-STATE-AND-HANDOFF.md` — 状态与顺序索引；
3. `audit-runs/G1-CLOSURE-DECISION/G1-CLOSURE-RECORD.md` — G1 关闭记录与残余风险；
4. `audit-runs/G1-CLOSURE-DECISION/G1-CLOSURE-DECISION.md` — 三领域裁决材料与证据矩阵；
5. `docs/architecture/current/architecture-entrypoint.md` — 架构导航（恢复 G2 时）；
6. `docs/governance/BACKLOG.md` — 待办登记处（注意其当前未提交）。

恢复 G2 设计时另需读取 G2 相关冻结合同与其最近一次设计报告（`docs/architecture/**` 与 `audit-runs/` 对应目录）。

---

## 9. 登记状态与残留项（Owner 批准后的实施记录）

Owner 于 2026-10-15 批准执行 `PROJECT-GOVERNANCE-REGISTRATION-AND-G1-CLOSURE` 的 M1、M2、M4，以及**有条件** M3（仅追加经只读核查确认缺失的前两项）。实施结果：

| 项 | 目标 | 结果 |
|---|---|---|
| M1 | `docs/README.md` Tier 1 增加一行指向本文件，`Last Updated` → 2026-10-15 | `DONE` |
| M2 | `docs/architecture/current/architecture-entrypoint.md` Views 表增加一行（**单向引用**，不复制事实） | `DONE` |
| M3 | `docs/governance/BACKLOG.md` "Governance / Cross-cutting" 段追加 `FLYWAY-V5-UNCOMMITTED-BASELINE-BLOCKER`、`CONTAINER-GOVERNANCE-CLEANUP-PENDING`（重复条目只读核查：无相同或实质重复项） | `DONE` |
| M4 | 本文件：状态字段、本节登记记录、§6 正交维度说明、§10 来源路径消歧 | `DONE` |

**本轮明确未做（Owner 裁决，不是遗漏）**：

1. `docs/architecture/governance/project-state/current-state.yaml` **不修改**——`canonical_main` 是既定集成 SHA（`moving_branch_tip.persisted_value: false`，不得用 HEAD 覆盖）；其 `governance.*` 字段只指向 `docs/` 内**已跟踪**记录，而关闭记录位于未跟踪的 `audit-runs/`；该文件被 phase17/phase19 脚本与 CI 影响分类器读取；
2. `docs/architecture/governance/document-classification-inventory.tsv` **不修改**——无守卫消费该清单，登记收益低于治理成本；
3. `AGENTS.md` **不修改**——它不含文档索引职责；
4. `docs/database/flyway-migration-baseline.md`（`requires_explicit_approval: true`）与 `scripts/guards/check-local-guardrails.sh` 在 2026-10-15 登记任务中**不修改**；2026-10-20 `F8-BASELINE-GOVERNANCE-REMEDIATION-R2` 已由 Owner 单独授权在工作区修正两处漂移（Flyway 文档 v3.1、guard 路径），index 收敛仍开放；对应的 `GOVERNANCE-DOC-DRIFT-FLYWAY-V1-V10` 条目仍未登记；
5. **没有 Git 写操作**：`docs/project-governance/`、`docs/governance/BACKLOG.md`、`audit-runs/` 仍为未跟踪状态，提交时机由 Owner 决定。

**残留项（登记完成后仍开放）**：

* 上述未做的 1–3 项；第 4 项的两处漂移已在 2026-10-20 R2 工作区修正，index 收敛仍待单独授权；
* `AUDIT-RUNS-TRACKING-DECISION`（既有 registry 条目）未解决前，本文件引用的 `audit-runs/` 证据不进入版本历史。

---

## 10. 事实与报告来源（最近一次更新）

| 事实 | 级别 | 来源 |
|---|---|---|
| 基线与工作区、指纹哈希 | `VERIFIED` | 本轮只读 `git`/`sha256sum` |
| 迁移目录内容、生成类缺失、脚本与守卫、文档漂移 | `VERIFIED` | 本轮只读文件检查（`…/db/migration/`、`typed-schema-module/…/jooq/generated/tables/`、`typed-schema-module/regenerate-jooq-schema.sh`、`docs/database/flyway-migration-baseline.md`、`scripts/guards/check-local-guardrails.sh`） |
| R2 工作区修复与 2026-10-22 index 收敛 | `VERIFIED` | 2026-10-22 只读 `git status/diff`：48 个 F8 路径已 staged；3 个混合文件仅 F8 hunk 入 index；其余在途差异未 staged；详见 R1 收敛报告 |
| 对象计数复算（220/302/220/135/122/21/22/0/14、总行 4955） | `VERIFIED` | 本轮对 `V*.sql` 的只读计数 |
| 容器盘点与分类、Ryuk 状态 | `REPORTED` | **仓库外档案** `../audit-runs/G1-FLYWAY-POSTGRES-CONSOLIDATION/G1-CONTAINER-INVENTORY.md`、`../audit-runs/G1-R1-BASELINE-AND-CONTAINER-RECONCILIATION/G1-R1-TESTCONTAINERS-OWNERSHIP.md`、`../audit-runs/G1-R2-FINAL-SCHEMA-RECONCILIATION/G1-R2-TESTCONTAINERS-CLEANUP-SPEC.md` |
| 表级矩阵与 SQL 索引结论 | `REPORTED` | 仓库内 `audit-runs/G1-R10A-SQL-INDEX-MATERIALIZATION/`、`audit-runs/G1-R11A-TABLE-REFERENCE-MATRIX/` |
| 现有库 lineage 解释（=2026-10-02 合并前迁移集） | `INFERRED`（名称级匹配 `VERIFIED`） | `git log --diff-filter=D`（`d7c26cb2`） + **仓库外档案** `../audit-runs/C-2I-3A-2-UNIT1-R1-READONLY-PRECHECK/C-2I-3A-2-UNIT1-R1-READONLY-PRECHECK-REPORT.md` §3 |
| 待裁决决策项（D-Q1′/D-Q3/D-Q4）、V6 建议 | `REPORTED` | **仓库外档案** `../audit-runs/C-2R-ROLE-BOUNDARY-ARCHITECTURE-DECISIONS/C-2R-ROLE-BOUNDARY-ARCHITECTURE-DECISIONS-REPORT.md`、`../audit-runs/G1-R2-FINAL-SCHEMA-RECONCILIATION/G1-R2-FINAL-OWNER-DECISION.md` §6 |

> **路径约定**：仓库内证据使用仓库相对路径（`audit-runs/…`）；G1、G1-R1、G1-R2 与 C-2I/C-2R 系列报告位于**仓库外**的治理档案 `/home/user/Documents/workspace/audit-runs/`，故以 `../audit-runs/…` 标注。这些路径**未纳入版本控制**，其可追溯性受 `AUDIT-RUNS-TRACKING-DECISION` 约束（见 §9）。

**本文件的 2026-10-15 初版为只读审查产物；2026-10-20 R2 修复与 2026-10-22 index 收敛任务对状态字段、工作区快照、守卫描述、候选状态和验收语义进行了治理性更正。** 本次 index 收敛仅执行 F8 定向 `git add` / index-only patch，未修改 baseline SQL、未运行 Flyway/构建/测试/代码生成、未连接或修改数据库、未操作 Podman/容器/卷/镜像/网络/服务或用户级配置、未执行 commit/push。技术验收仍未执行，F8 仍未关闭。
如本文件与既有权威文件冲突，以 `AGENTS.md`、`docs/README.md`、`docs/architecture/current/architecture-entrypoint.md`、
`docs/governance/BACKLOG.md` 为准，并请 Owner 裁决修正。
