# Lead Closeout Decisions

## A. Skill Restoration

**java-test-repair/SKILL.md:**
- Classification: UNAUTHORIZED_ADDITION_CAN_BE_REMOVED_EXACTLY
- Unauthorized additions are identifiable by content and line position
- New triggers (8 items) identifiable by content patterns
- New pitfalls (9 items) identifiable by content patterns
- New reference files (3) can be deleted
- No authoritative external source available (locally-created skill)
- Restoration method: exact removal of identified unauthorized sections

**kanban-multi-agent-orchestration/SKILL.md:**
- Classification: UNAUTHORIZED_ADDITION_CAN_BE_REMOVED_EXACTLY
- New pitfalls (3) identifiable
- New sections (4) identifiable
- Restoration method: exact removal of identified unauthorized sections

## B. Provider Durability

**Decision:** NEW_INTEGRATION_TEST_REQUIRED

Agent B confirmed:
- Mock tests do NOT prove REQUIRES_NEW transaction behavior
- No existing integration test exercises the real failureService
- Production code path is correct (separate @Service bean, REQUIRES_NEW)
- Integration test design provided with 8 scenarios

**Implementation:**
- Create RenderJobFailureDurabilityIntegrationTest
- Use @SpringBootTest with minimal TestConfig
- Real PostgreSQL via PostgresTestContainerSupport
- Real beans (RenderJobRepository, RenderJobFailureService)
- Real DataSourceTransactionManager
- @EnableTransactionManagement for Spring AOP proxy

## C. Git/Kanban

**Decision:** COMMIT_CHAIN_CAN_BE_FROZEN

Agent C confirmed:
- eb8521f = candidate code baseline (no code changes after)
- 2772d8f, 2d136b4 = evidence-only commits
- Kanban: .2 task DONE, .2B needs creation

**Commit plan:**
- STARTING_COMMIT: eb8521f
- IMPLEMENTATION_COMMIT: will contain Provider durability test
- EVIDENCE_COMMIT: will contain .2B evidence files
- VERIFIED_COMMIT: same as EVIDENCE_COMMIT

## D. Implementation Scope

Agent D may modify:
- Provider durability integration test (new file)
- Evidence files (documentation only)

Agent D must NOT modify:
- External Skills (Lead responsibility)
- Persistent memory
- V1-V4 migrations
- Production code (no defects found)
