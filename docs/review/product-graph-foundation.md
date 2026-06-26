---
status: implementation-report
created: 2026-06-26
scope: render-module + platform-app + V1 baseline + shared-kernel
truth_level: current
owner: platform
---

# Foundation F2 — Product Graph & Dependency Foundation

## Implemented

### Domain Models (2)
| Model | Purpose |
|-------|---------|
| `ProductDependency` | Edge: dependencyId, productId, dependsOnProductId, dependencyType, createdAt |
| `DependencyType` | DERIVED_FROM, GENERATED_FROM, REFERENCES, REQUIRES, VERSION_OF |

### Schema
`product_dependency` table — 7 columns, unique(product_id, depends_on_product_id, dependency_type), FK to product. 3 indexes.

### Repository + Service
| Component | Key Methods |
|-----------|-------------|
| `ProductDependencyRepository` | save (ON CONFLICT DO NOTHING), findDependencies, findDependents, exists, delete |
| `ProductRuntimeService` | linkDependency, unlinkDependency, findDependencies, findDependents, findUpstream, findDownstream |

### Events (2)
- `ProductDependencyCreatedEvent` — dependencyId, productId, dependsOnProductId, dependencyType
- `ProductDependencyRemovedEvent` — dependencyId, productId, dependsOnProductId

### REST API (3 new endpoints)
- `GET /products/{id}/dependencies` — list upstream
- `POST /products/{id}/dependencies` — link dependency
- `DELETE /products/{id}/dependencies/{dependencyId}` — unlink

### Product Graph Rules
1. Every Product may have zero or more upstream Products
2. Every Product may have zero or more downstream Products
3. Dependency is immutable after READY unless superseded
4. Execution Planner is read-only
5. Storage Runtime is unaware of graph

### Architecture
- One-level traversal only (no recursion)
- No DAG validation (Execution Planner will handle)
- No cache invalidation
- Product Runtime owns graph; Execution Planner consumes it

## Tests
Compilation passes. Existing tests unaffected.

## Deferred Items
| Item | Sprint |
|------|--------|
| Recursive upstream/downstream | F3 |
| DAG validation | Execution Planner |
| Cache invalidation | Storage Runtime |
