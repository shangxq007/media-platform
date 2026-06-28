# DAG, Versioning, and Workflow Reference Systems Review (P2R.0)

## 1. Summary

Reviewed 9 external DAG/versioning/workflow systems against the media-platform's 15 graph models. No runtime dependencies should be added. Airflow, Git, GoatDB, VersiDAG, Merkle-DAG, and DAG GitOps patterns are reference systems only. Six future ADR candidates identified. Eight future work items queued.

**Key finding:** All platform graphs map cleanly to established categories (semantic, composition, workflow, dependency, capability, binding, execution, lineage, version, merkle). No category collapse is needed.

## 2. Sources Reviewed

| Source | Type | Maturity |
|--------|------|----------|
| Apache Airflow | Official docs | Production |
| Git / Pro Git | Official docs/book | Production |
| my-git | GitHub (educational) | Educational |
| ForgeVCS | GitHub | Experimental |
| GoatDB | Official site + GitHub | Alpha |
| VersiDAG | GitHub | Research |
| IPFS/IPLD | Official docs | Production concepts |
| Merkle-CRDT | Academic papers | Research |
| dag-sync patterns | Category | Pattern |

## 3. Current Platform Graphs

15 graphs identified and categorized. See primary reference document for full inventory.

## 4. Key Findings

1. **Workflow DAG ≠ Render Execution DAG** — must remain separate
2. **Composite Template DAG ≠ Workflow DAG** — templates compose, workflows orchestrate
3. **ProductDependency ≠ Execution DAG** — lineage, not execution
4. **Git-like versioning** is the right mental model for Timeline/Template/Workflow versions
5. **Merkle-DAG concepts** are appropriate for cache identity and lineage
6. **GoatDB concepts** are relevant for local-first editor drafts
7. **Airflow governance model** is the right reference for workflow deployment
8. **No external runtime dependency** is needed now

## 5. Recommended Documentation Additions

- `docs/architecture/reference/dag-versioning-and-workflow-systems.md` (primary reference)
- ADR candidate notes for 8 future decisions
- Future work queue with 8 items

## 6. Recommended Rejections

1. No Airflow runtime
2. No user-submitted Python DAGs
3. No IPFS/IPLD runtime
4. No Merkle-CRDT runtime
5. No GoatDB replacement for PostgreSQL
6. No ForgeVCS replacement for Git
7. No hot DAG sync
8. No workflow-execution graph collapse

## 7. Runtime Dependency Recommendation

**None.** All reference systems are documentation-only.

## 8. Impact on P2T.4

CompositeTemplate should be a TemplateDefinition type composing Atomic Templates with:
- TemplateTargetBinding
- TemplateParameterBinding
- TemplateMergePolicy
- TemplateConflictPolicy

Not a WorkflowDefinition.

## 9. Impact on P2W.1

P2W.1 should be a dry-run planner only:
- WorkflowDryRunPlanner validates and orders steps
- APPLY_TEMPLATE expanded semantically
- No render pipeline invocation
- No job scheduling

## 10. Open Questions

1. Should Timeline Git be a full VCS or lightweight version graph?
2. What merge conflict policy for concurrent template edits?
3. How should content-addressed cache keys interact with existing StorageRuntime?
4. What is the exact scope of local-first draft collaboration?

## 11. Files Changed

| File | Type |
|------|------|
| `docs/architecture/reference/dag-versioning-and-workflow-systems.md` | NEW |
| `docs/review/dag-versioning-workflow-reference-review-v0.md` | NEW |

## 12. Final Recommendation

No runtime changes. All reference systems adopted as documentation principles only. CompositeTemplate remains TemplateDefinition composition (P2W.0). WorkflowDefinition remains semantic DAG. No workflow engine introduced.
