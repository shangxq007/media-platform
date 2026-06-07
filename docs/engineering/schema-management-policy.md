# Schema Management Policy

## 1. Central Flyway DDL Policy

**Decision:** Keep 1 active central Flyway migration (V1 baseline). No further squash before RC.

**Rationale:**
- V1 baseline contains complete schema including P4 additions (taxonomy columns, project_import_metadata table)
- V2-V5 archived migrations are backfill-only and not needed for new databases
- Squashing introduces H2/PostgreSQL compatibility risk without significant benefit
- CI is now stable with current configuration

**Rules:**
- Central Flyway is the **only DDL source of truth** for production and test databases
- All schema changes must be additive migrations (V7, V8, etc.)
- Archived migrations (V2-V5) remain in `docs/archive/prelaunch-migrations/` for historical reference
- Migration history must never be rewritten after first production deploy
- New databases are initialized from V1 baseline + all subsequent migrations

## 2. Module Bootstrap Policy

**Allowed:**
- JDBC read operations
- Hydrate in-memory registry/cache
- Load reference mappings
- Validate existing schema/read model

**Forbidden:**
- CREATE TABLE
- ALTER TABLE
- DROP TABLE
- CREATE INDEX
- Schema backfill
- Auto-add columns
- Any DDL that replaces Flyway migration

**Verification:** No DDL statements found in any module's Java source code.

## 3. Test Schema Policy

**Current Configuration:**
- Test profile: `application-test.yml`
- Flyway: disabled in test profile
- Schema initialization: `spring.sql.init.mode=always` with `classpath:schema.sql`
- `schema.sql` is a complete baseline including all P4 columns

**Rules:**
- `schema.sql` must stay synchronized with V1 baseline
- Test profile uses `schema.sql` directly (Flyway disabled) for predictable test environment
- This is a deliberate test-only optimization; production always uses Flyway
- Any schema changes to V1 baseline must be reflected in `schema.sql`

## 4. Migration Rewrite Policy

**Prelaunch (current):**
- Archived migrations (V2-V5) are not executed
- V1 baseline supersedes all previous incremental migrations
- Safe because no production database exists

**Post-launch:**
- Migration history must never be rewritten
- All changes via additive migrations only
- Archived migrations remain for audit trail

## 5. Modulith Debt Policy

**Current Violations:**
- identity → artifact/storage (8 violations)

**Policy:**
- Tracked in `docs/modulith-debt-register.md`
- No module merge to circumvent violations
- New violations cause test failure
- Existing violations have explicit allowlist with owner and deadline
- Long-term fix: shared-kernel ports, adapter relocation, dependency inversion

## 6. P4 RC Status

- platform-app tests: ✅ BUILD SUCCESSFUL
- identity-access-module tests: ✅ 361/361
- Frontend typecheck: ✅ 0 errors
- Remaining failures: RenderPipelineDagIT (integration profile), 8 pre-existing in unrelated modules

**RC can proceed with documented debt.**
