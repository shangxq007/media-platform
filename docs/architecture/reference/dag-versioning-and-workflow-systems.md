# DAG, Versioning, and Workflow Systems Reference

## 1. Purpose

Compare external DAG/versioning/workflow reference systems against the media-platform's current and planned graph models. Decide what to adopt as design principles, what to reject, and what to queue for future ADRs.

## 2. Scope and Non-goals

**Scope:** Apache Airflow, Git, my-git, ForgeVCS, GoatDB, dag-sync patterns, VersiDAG, Merkle-DAG, Merkle-CRDT, IPFS/IPLD.

**Non-goals:** Add runtime dependencies, replace PostgreSQL, replace Git, implement workflow engine, execute user-submitted code, expose provider internals.

## 3. Current Platform Graph Inventory

| Graph | Purpose | Owner | Canonical | Stable | Provider-bound | Executing | Versioned | Content-address candidate |
|-------|---------|-------|-----------|--------|---------------|-----------|-----------|--------------------------|
| Timeline semantic graph | Editing model | domain/timeline | Yes | Yes | No | No | Yes | Yes (revision hash) |
| TemplateDefinition graph | Reusable operations | domain/template | Yes | Yes | No | No | Yes | Yes |
| Atomic Template profile | Single-purpose profile | domain/template/profile | Yes | Yes | No | No | Yes | Yes |
| Composite Template graph | Multi-template composition | domain/template (P2T.4) | Yes | Yes | No | No | Yes | Yes |
| WorkflowDefinition graph | Processing flow | domain/workflow | Yes | Yes | No | No | Yes | Yes |
| WorkflowStep dependency DAG | Step ordering | domain/workflow | Yes | Yes | No | No | No | No |
| Artifact Dependency Graph | Compile-time DAG | compile/ | Yes | Volatile | No | No | No | Yes (graph hash) |
| Logical Capability Graph | Capability requirements | compile/ | Derived | Volatile | No | No | No | Yes (graph hash) |
| Provider Binding Plan | Provider binding | compile/binding | Derived | Volatile | Yes | No | No | No |
| Render Execution Graph | Execution steps | compile/executionplan | Derived | Volatile | Yes | Yes | No | No |
| ProductDependency lineage | Product lineage | product/ | Yes | Yes | No | No | No | Yes |
| TimelineRevision graph | Version history | timeline/ | Yes | Yes | No | No | Yes | Yes |
| TemplateVersion graph | Template versions | template/ (future) | Yes | Yes | No | No | Yes | Yes |
| WorkflowVersion graph | Workflow versions | workflow/ (future) | Yes | Yes | No | No | Yes | Yes |
| Cache identity graph | Artifact cache keys | compile/ | Derived | Stable | No | No | No | Yes (hash) |

## 4. Platform Graph Taxonomy

| Category | Description | Platform examples |
|----------|-------------|-------------------|
| **Semantic Graph** | Describes domain intent | TemplateDefinition, WorkflowDefinition, Timeline |
| **Composition Graph** | Composes semantic units | CompositeTemplate (P2T.4) |
| **Workflow DAG** | Ordered processing steps | WorkflowStep dependency graph |
| **Dependency DAG** | Compile-time dependencies | Artifact Dependency Graph |
| **Capability Graph** | Provider-neutral requirements | Logical Capability Graph |
| **Binding Graph** | Provider-bound assignments | Provider Binding Plan |
| **Execution DAG** | Runtime execution steps | Render Execution Graph |
| **Lineage DAG** | Output-to-input provenance | ProductDependency |
| **Version DAG** | Version history | TimelineRevision, TemplateVersion, WorkflowVersion |
| **Merkle DAG** | Content-addressed, hash-linked | Cache identity (future) |
| **Sync DAG** | Cross-environment promotion | GitOps promotion (future) |

**Critical boundaries:**
- Workflow DAG ≠ Render Execution DAG
- Composite Template DAG ≠ Workflow DAG
- Product Lineage DAG ≠ Execution DAG
- Artifact Dependency DAG ≠ Product Lineage DAG
- Version DAG ≠ Execution DAG
- Capability Graph ≠ Provider Binding Plan

## 5. Reference System Categories

| Category | Systems | Primary use |
|----------|---------|-------------|
| Workflow-as-code | Apache Airflow | DAG scheduling, dependency semantics |
| Version control | Git, my-git, ForgeVCS | Commit DAG, content-addressing, merge |
| Local-first | GoatDB | Offline collaboration, Git-like DB |
| DAG GitOps | dag-sync patterns | Promotion, sync, rollback |
| Version DAG | VersiDAG | Concurrent version history |
| Content-addressing | Merkle-DAG, Merkle-CRDT, IPFS/IPLD | Hash-linked identity, dedup, sync |

## 6. Apache Airflow Review

**Source:** Apache Airflow official documentation (https://airflow.apache.org/docs/)
**Project maturity:** Production-grade, widely adopted
**DAG type:** Workflow DAG (task dependency + scheduling)

**Core concepts:**
- DAG: Directed Acyclic Graph of tasks with scheduling/retry/timeout
- Task: Atomic unit of work (Python callable, operator)
- Operator: Pre-built task template (Bash, Python, etc.)
- DAG-as-code: Python files defining DAGs, deployed to scheduler
- XCom: Inter-task data passing (small payloads)

**Relevance to media-platform:**
- Workflow-as-code governance model: DAG definitions in version control, CI validation, deployment
- Task dependency semantics: upstream/downstream, trigger rules
- Scheduling and retry semantics
- DAG file deployment model

**Adopted lessons:**
1. Workflow-as-code governance: definitions in version control, tested before deployment
2. Task dependency model: clear upstream/downstream semantics
3. Separation of definition and execution
4. Timeout and retry concepts for future workflow steps

**Rejected lessons:**
1. Python DAG execution — not safe for user-submitted code
2. Operator model — too generic, not aligned with platform template/workflow model
3. XCom model — platform uses Product as canonical communication object
4. Scheduler model — platform uses PLAN_BASED execution, not cron-like scheduling

**Implementation dependency:** None. Reference only.

## 7. Git / my-git / ForgeVCS Review

**Source:** Git official documentation, Pro Git
**Project maturity:** Production-grade (Git), reference/educational (my-git, ForgeVCS)

**Core concepts:**
- Commit DAG: Immutable objects linked by parent hash
- Content-addressable storage: SHA-256 object addressing
- Branch/merge/revert/rebase/cherry-pick
- Diff/patch model
- Merge base / ancestor traversal

**Relevance to media-platform:**
- TimelineRevision version graph (commit-like)
- TemplateVersion graph
- WorkflowVersion graph
- CompositeTemplate versioning
- Branching template/workflow drafts
- Merge conflict policy for timeline/template operations

**Adopted lessons:**
1. Git-like mental model for Timeline/Template/Workflow versioning
2. Content-addressable identity for stable cache keys and lineage
3. Immutable version objects linked by parent hash
4. Branch/merge semantics for concurrent drafts

**Rejected lessons:**
1. Replace Git with ForgeVCS — no
2. Custom VCS repository format — no
3. Raw Git operations as platform API — no

**Implementation dependency:** None. Git-like versioning semantics in documentation only.

## 8. GoatDB Review

**Source:** GoatDB official website and GitHub
**Project maturity:** Alpha/experimental
**DAG type:** Local-first DAG with Git-like commit model

**Core concepts:**
- Git-like database: commit, branch, merge
- Local-first: works offline, syncs when online
- Signed commits: actor attribution
- Three-way merge: conflict resolution
- CRDT-inspired merge strategies

**Relevance to media-platform:**
- Frontend timeline/template/workflow draft collaboration
- AI agent collaborative editing
- Offline-first editor state
- Conflict resolution for Timeline/Template operations

**Adopted lessons:**
1. Local-first draft model for editor state
2. Actor attribution for collaborative edits
3. Conflict resolution patterns for concurrent operations

**Rejected lessons:**
1. Replace PostgreSQL — no
2. Use for ProductRuntime/RenderJob state — no
3. Introduce as dependency now — no (experimental)

**Implementation dependency:** None. Reference for future local-first design.

## 9. dag-sync / DAG GitOps Sync Pattern Review

**Note:** "dag-sync" as a specific project could not be identified with certainty. Treated as a pattern category.

**Pattern description:**
- DAG definitions stored in Git
- CI validation before deployment
- Promotion between environments (staging → production)
- Sync to runtime
- Rollback capability
- Environment-specific config separation

**Relevance to media-platform:**
- WorkflowDefinition GitOps promotion
- TemplateDefinition GitOps promotion
- CompositeTemplate promotion
- Tenant-approved template packs
- Safe plugin package lifecycle
- Staging/prod workflow release control

**Adopted lessons:**
1. GitOps promotion pattern as future governance reference
2. CI validation before deployment
3. Environment-specific configuration separation
4. Rollback capability

**Rejected lessons:**
1. Hot DAG sync — no (requires review/testing)
2. Arbitrary user code sync — no
3. Bypass review/testing — no

**Implementation dependency:** None. Governance pattern reference only.

## 10. VersiDAG Review

**Source:** VersiDAG GitHub repository
**Project maturity:** Research/experimental
**DAG type:** Concurrent version history DAG

**Core concepts:**
- Merkle-DAG based version history
- Concurrent branches
- Merge-friendly history model
- Ancestor/parent graph
- Content-addressed nodes

**Relevance to media-platform:**
- TimelineRevision version graph
- TemplateVersion/WorkflowVersion graphs
- CompositeTemplateVersion graph
- Merge-friendly concurrent edit history

**Adopted lessons:**
1. Merge-friendly version history model
2. Content-addressed nodes for stable identity

**Rejected lessons:**
1. Replace current version model — no
2. Introduce VersiDAG runtime — no

**Implementation dependency:** None. Conceptual reference only.

## 11. Merkle-DAG / Merkle-CRDT Review

**Source:** IPFS/IPLD documentation, Merkle-CRDT papers
**Project maturity:** Production-grade concepts (IPFS), research (Merkle-CRDT)

**Core concepts:**
- Content-addressing: hash-linked nodes
- Deduplication via content identity
- Causal history tracking
- CRDT merge potential for concurrent edits
- Sync over unreliable networks

**Relevance to media-platform:**
- Cache identity / stable artifact keys
- ProductDependency lineage
- Incremental render reuse
- Timeline/Template concurrent edit merging
- Version graph identity

**Adopted lessons:**
1. Merkle-DAG as conceptual reference for versioning and cache identity
2. Content-addressed identity for stable artifact keys
3. Causal history for lineage tracking

**Rejected lessons:**
1. Introduce IPFS/IPLD runtime — no
2. Introduce Merkle-CRDT runtime — no
3. Force all Product storage to be content-addressed — no

**Implementation dependency:** None. Conceptual reference for future design.

## 12. Comparison Matrix

| Reference | DAG Type | Primary Use | Maturity | Useful For Platform | Adopted Lessons | Rejected Lessons | Runtime Dep? | ADR Candidate |
|-----------|----------|-------------|----------|--------------------|-----------------|-----------------|--------------|--------------|
| Apache Airflow | Workflow DAG | Workflow-as-code, scheduling | Production | Workflow governance, step semantics | Governance, dependency model, retry/timeout | Python execution, operators, scheduler | No | No |
| Git | Commit DAG | Version control | Production | Versioning mental model, content-addressing | Immutable versions, branch/merge, content-identity | Custom VCS, raw Git API | No | Yes (Timeline Git) |
| my-git | Commit DAG | Learning/reference | Educational | Version DAG understanding | Commit graph concepts | Replace Git | No | No |
| ForgeVCS | Commit DAG | VCS engine reference | Experimental | VCS design patterns | Engine architecture concepts | Replace Git | No | No |
| GoatDB | Local-first DAG | Offline collaboration | Alpha | Draft collaboration, conflict resolution | Local-first model, actor attribution | Replace PostgreSQL | No | Yes (local-first) |
| dag-sync patterns | DAG GitOps | Promotion, sync | Pattern | Workflow/Template promotion | GitOps governance, CI validation | Hot sync, arbitrary code sync | No | Yes (GitOps) |
| VersiDAG | Version DAG | Concurrent version history | Research | Version graph semantics | Merge-friendly history | Replace current model | No | No |
| Merkle-DAG | Content DAG | Hash-linked identity | Production concepts | Cache identity, lineage | Content-addressing | IPFS/IPLD runtime | No | Yes (cache identity) |
| Merkle-CRDT | CRDT DAG | Concurrent merge | Research | Concurrent edit merging | CRDT merge concepts | Merkle-CRDT runtime | No | No |
| IPFS/IPLD | Merkle-DAG | Content-addressed storage | Production concepts | Conceptual reference | Hash-linked identity concepts | IPFS/IPLD runtime | No | No |

## 13. Mapping to Current Platform DAGs

| Platform Graph | Category | Canonical | Stable | Executes | Provider-bound | Versioned | Merge Candidate | Content-Address Candidate | Primary Reference | Adopted Principles | Non-goals |
|---------------|----------|-----------|--------|----------|---------------|-----------|-----------------|--------------------------|-------------------|-------------------|-----------|
| Timeline semantic | Semantic | Yes | Yes | No | No | Yes | Yes | Yes | Git, Merkle-DAG | Git-like versioning, content-identity | Replace Git |
| TemplateDefinition | Semantic | Yes | Yes | No | No | Yes | Yes | Yes | Git, Merkle-DAG | Version semantics | User-executable code |
| Atomic Template profile | Semantic | Yes | Yes | No | No | Yes | Yes | Yes | Git | Profile composition | Arbitrary templates |
| Composite Template | Composition | Yes | Yes | No | No | Yes | Yes | Yes | Git, VersiDAG | Merge-friendly composition | Workflow execution |
| WorkflowDefinition | Workflow DAG | Yes | Yes | No | No | Yes | Yes | Yes | Airflow, Git | Step dependency, governance | Python execution |
| WorkflowStep deps | Workflow DAG | Yes | Yes | No | No | No | No | No | Airflow | Dependency semantics | Scheduling |
| Artifact Dependency | Dependency | Yes | Volatile | No | No | No | No | Yes | Merkle-DAG | Content-addressed identity | Runtime |
| Logical Capability | Capability | Derived | Volatile | No | No | No | No | Yes | — | Provider-neutral requirements | Provider selection |
| Provider Binding | Binding | Derived | Volatile | No | Yes | No | No | No | — | Binding semantics | Expose internals |
| Render Execution | Execution | Derived | Volatile | Yes | Yes | No | No | No | — | Execution steps | Public exposure |
| ProductDependency | Lineage | Yes | Yes | No | No | No | No | Yes | Merkle-DAG | Lineage tracking | Execution workflow |
| TimelineRevision | Version | Yes | Yes | No | No | Yes | Yes | Yes | Git | Immutable versions | Replace Git |
| TemplateVersion | Version | Yes | Yes | No | No | Yes | Yes | Yes | Git | Version semantics | — |
| WorkflowVersion | Version | Yes | Yes | No | No | Yes | Yes | Yes | Git | Version semantics | — |
| Cache identity | Merkle | Derived | Stable | No | No | No | No | Yes | Merkle-DAG | Content-addressed keys | IPFS runtime |

## 14. Adopted Lessons

1. **Keep workflow DAG and render execution DAG separate.** WorkflowDefinition describes semantic flow; RenderExecutionGraph is volatile/provider-bound.
2. **Keep composite template graph separate from workflow DAG.** CompositeTemplate composes templates; WorkflowDefinition orchestrates steps.
3. **Treat ProductDependency as lineage, not execution.** ProductDependency tracks output-to-input provenance, not workflow.
4. **Treat RenderExecutionGraph as volatile/provider-bound.** It is derived and not canonical.
5. **Use Git-like semantics for Timeline/Template/Workflow versioning.** Immutable version objects, parent references, branch/merge.
6. **Use Merkle-DAG concepts for cache identity and stable artifact lineage.** Content-addressed keys for dedup and incremental reuse.
7. **Use GoatDB/local-first concepts for editor drafts and AI-agent collaboration.** Local-first draft model, actor attribution.
8. **Use Airflow workflow-as-code lessons for governance, testing, deployment.** Definitions in version control, CI validation.
9. **Use DAG sync/GitOps patterns for Template/Workflow promotion.** Environment-specific promotion with rollback.
10. **Do not expose provider/backend/storage internals through any graph API.**

## 15. Rejected Lessons

1. Do not adopt Airflow as current workflow engine.
2. Do not execute user-submitted Python DAGs.
3. Do not use Airflow operators as platform primitives.
4. Do not replace PostgreSQL with GoatDB.
5. Do not replace Git with ForgeVCS.
6. Do not introduce IPFS/IPLD runtime.
7. Do not introduce Merkle-CRDT runtime.
8. Do not implement hot DAG sync.
9. Do not collapse WorkflowDefinition into RenderExecutionGraph.
10. Do not model CompositeTemplate as WorkflowDefinition.
11. Do not expose provider selection through any graph.
12. Do not let plugins bypass PLAN_BASED / ProductRuntime / StorageRuntime.

## 16. Dependency Recommendation

No external runtime dependencies should be added now. All reference systems are documentation-only.

## 17. Impact on P2T.4 Composite Template Semantics

CompositeTemplate should be a TemplateDefinition type that composes Atomic Templates:

- `CompositeTemplateDefinition` extends TemplateDefinition
- `CompositeTemplateChild` references child TemplateDefinitions
- `TemplateTargetBinding` maps parent targets to child targets
- `TemplateParameterBinding` maps parent parameters to child parameters
- `TemplateMergePolicy` controls how child results combine
- `TemplateConflictPolicy` resolves conflicting child operations

CompositeTemplate is NOT WorkflowDefinition. It does not execute workflow steps.

## 18. Impact on P2W.1 Workflow Dry-run Planner

P2W.1 should be dry-run only:

- `WorkflowDryRunPlanner` validates and orders steps
- `WorkflowDryRunPlan` represents the ordered plan
- `WorkflowDryRunStep` represents each planned step
- `WorkflowGraphValidator` checks structural validity
- `WorkflowCycleDetector` ensures acyclicity
- `WorkflowStepOrderResolver` topologically orders steps

APPLY_TEMPLATE steps are expanded semantically only — no render pipeline invocation.

## 19. Future ADR Candidates

| Candidate | Problem | Trigger Condition |
|-----------|---------|-------------------|
| Timeline Git and Version DAG Semantics | How TimelineRevision/TemplateVersion/WorkflowVersion implement Git-like versioning | When version branching/merging is needed |
| Composite Template Semantics | How templates compose without becoming workflows | P2T.4 implementation |
| Workflow Definition GitOps Promotion | How WorkflowDefinitions deploy across environments | When staging/prod workflow control is needed |
| Local-first Timeline/Template Drafts | How editor drafts collaborate offline | When offline editing is needed |
| Content-addressed Artifact Identity | How cache keys and lineage use content-addressing | When incremental render dedup is needed |
| Product Lineage DAG Semantics | How ProductDependency represents complete lineage | When lineage queries exceed simple parent-child |
| Workflow Runtime Selection | Temporal vs LiteFlow vs OpenCue vs Airflow | When workflow execution is needed |
| Plugin Package Manifest | How plugins declare capabilities safely | P2P.0 implementation |

## 20. Future Work Queue

| ID | Goal | Why Now/Not Now | Dependencies |
|----|------|----------------|--------------|
| P2T.4 | Composite Template Semantics | Validates template composition model | P2T.1-P2T.3 |
| P2W.1 | Workflow Dry-run Planner | Validates workflow ordering without execution | P2W.0 |
| P2P.0 | Plugin Package Manifest ADR | Defines safe plugin extension model | ADR-022 |
| P2V.0 | Timeline Git ADR | Defines version DAG semantics | Timeline model |
| P2D.0 | Artifact DAG Cache Identity | Defines content-addressed cache keys | Artifact graph |
| P2L.0 | Product Lineage DAG | Defines lineage query semantics | ProductRuntime |
| P2S.0 | GitOps Promotion | Defines template/workflow deployment | P2T.4, P2W.1 |
| P2E.0 | Workflow Runtime Evaluation | Evaluates Temporal/LiteFlow/OpenCue | P2W.1 |

## 21. Source List

| Name | URL | Source Type | Maturity | Core Concept |
|------|-----|-----------|----------|-------------|
| Apache Airflow | https://airflow.apache.org/docs/ | Official docs | Production | Workflow-as-code DAG |
| Git | https://git-scm.com/doc | Official docs | Production | Commit DAG, content-addressing |
| Pro Git | https://git-scm.com/book/en/v2 | Reference book | Production | Git internals |
| ForgeVCS | GitHub | Research project | Experimental | Git-inspired VCS engine |
| GoatDB | https://goatdb.org/ + GitHub | Official site + repo | Alpha | Local-first Git-like DB |
| VersiDAG | GitHub | Research project | Research | Concurrent version DAG |
| IPFS/IPLD | https://docs.ipfs.tech/ | Official docs | Production concepts | Merkle-DAG, content-addressing |
| Merkle-CRDT | Academic papers | Research | Research | CRDT + Merkle-DAG |
| dag-sync patterns | Category (not single project) | Pattern | Pattern | DAG GitOps sync |

**Note:** `dag-sync` could not be identified as a single specific project. Treated as a pattern category for DAG-based GitOps synchronization.
