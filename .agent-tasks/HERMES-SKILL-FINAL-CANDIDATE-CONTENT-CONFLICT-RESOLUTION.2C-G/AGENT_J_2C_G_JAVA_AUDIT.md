# Agent J: java-test-repair Skill Content Audit

**Working directory:** `~/.hermes/forensics/media-platform-2c-f-20260716155558/working/java-test-repair/`
**Expected SKILL.md hash:** `32a93c18d1d7ba48b35eb0e43153c14f511f676195df211725cdcad6cebd1be9`
**Audit mode:** READ-ONLY

---

## File Inventory

| File | Present | SHA256 Verified |
|------|---------|-----------------|
| SKILL.md | ✅ | ✅ (472 lines, 29,499 bytes) |
| TREE_SHA256SUMS | ✅ | ✅ (10 entries, all pass `sha256sum -c`) |
| scripts/verify-test-compile.sh | ✅ | ✅ (36 lines, 1,065 bytes) |
| references/bulk-test-repair-techniques.md | ✅ | ✅ (150 lines) |
| references/cascading-failure-discovery.md | ✅ | ✅ (93 lines) |
| references/cas-mock-pattern.md | ✅ | ✅ (45 lines) |
| references/gradle-hold-module-pattern.md | ✅ | ✅ (73 lines) |
| references/mockito-bytebuddy-java25-runtime-fix.md | ✅ | ✅ (153 lines) |
| references/mockito-silent-failure-patterns.md | ✅ | ✅ (82 lines) |
| references/objectprovider-mock-pattern.md | ✅ | ✅ (98 lines) |
| references/test-failure-patterns-and-tdd-markers.md | ✅ | ✅ (180 lines) |

**Tree completeness:** All 10 files referenced in TREE_SHA256SUMS exist on disk and verify. No extra files present.

---

## Verification Checklist

### 1. SKILL.md + ALL references/ + scripts/ + TREE_SHA256SUMS
**✅ PASS**
- SKILL.md: 472 lines, frontmatter + 12 numbered fix patterns + Constraints + Verification + Pitfalls
- 8 reference files in `references/`
- 1 script in `scripts/`
- TREE_SHA256SUMS has 10 entries, all `sha256sum -c` verified OK

### 2. No Production Modification Suggestions Remaining
**✅ PASS**
- Searched for `make public`, `should be public`, `needs to be public` — **none found**
- Searched for `move to shared`, `shared module`, `shared kernel` — **none found**
- The only mention of "production code change" (line 171) is explicitly **blocked**: "this requires a production code change. Escalate to the task lead; do not modify production code within this Skill's scope."
- Constraint section (line 307): "Never change production code — only test code"

### 3. BLOCKED_PRODUCTION_OR_ARCHITECTURE_CHANGE_REQUIRED Present
**✅ PASS** — 3 occurrences in SKILL.md:
1. Line 153: Cross-module reference error escalation
2. Line 171: Access modifier fix requiring public API change
3. Lines 313-322: Dedicated constraint section listing all blocked scenarios (production code, module boundary, build config, migration/schema, architecture docs)

### 4. BLOCKED_BUILD_CONFIGURATION_CHANGE_REQUIRED Present for ByteBuddy/Gradle
**✅ PASS** — 3 occurrences in SKILL.md:
1. Line 375: ByteBuddy agent attachment in `build.gradle.kts` — "escalate to a separate authorized build task"
2. Line 466: `org.gradle.jvmargs` not affecting test worker heap — "escalate to a separate authorized build task"
3. Line 467: Spring context explosion OOM — "escalate to a separate authorized build task"
- Reference: `references/mockito-bytebuddy-java25-runtime-fix.md` provides the full Gradle fix pattern but consistently frames it as a build configuration change requiring separate authorization

### 5. Fixture Sync Uses CURRENT_SCHEMA_DRIFT_CONFIRMED
**✅ PASS** — 2 occurrences in SKILL.md:
1. Line 311: Constraint: "Never mask schema drift — if a test fixture contains fields not in current Flyway migrations, report `CURRENT_SCHEMA_DRIFT_CONFIRMED`; do not modify the fixture to hide the gap"
2. Line 465: Pitfall: "Diagnosis: Check if the column exists in current Flyway migrations. If yes → update the fixture to match. If no → CURRENT_SCHEMA_DRIFT_CONFIRMED — do NOT add the column to the fixture to mask the drift."

### 6. Batch Operations Have Safety Rules (dry-run, path restriction, diff)
**✅ PASS** — Lines 326-333 define 7 mandatory rules for bulk edits:
1. **Restrict paths** to `src/test/**` only
2. **Dry-run first** — show what would change without writing
3. **Save file list** — record every file that will be modified
4. **Show per-file diff** — display before/after for each file
5. **No cross-module expansion** — do not extend scope
6. **Stop on anomaly** — halt and investigate unexpected changes
7. **Rollback capability** — keep backup or git stash

### 7. Downstream Scope Auto-Expansion Disabled
**✅ PASS** — 2 locations enforce this:
1. Lines 336-341: "Downstream module exposure rule" — "Do NOT automatically add B to scope" / "Record B's errors as a finding" / "Escalate to the task lead for scope authorization"
2. Line 403: Pitfall: "do NOT automatically add them to scope. Record the newly exposed errors as a finding, classify them, and escalate to the task lead for scope authorization."

### 8. verify-test-compile.sh Has --rerun-tasks
**✅ PASS** — Line 17 of `scripts/verify-test-compile.sh`:
```
OUTPUT=$(./gradlew "${MODULE}:compileTestJava" --rerun-tasks --no-build-cache --warning-mode all "$@" 2>&1)
```
- Uses `--rerun-tasks` (forces re-execution, avoids UP-TO-DATE false positives)
- Uses `--no-build-cache` (avoids stale cache)
- Uses `--warning-mode all` (maximum visibility)

### 9. Cross-Skill Dependencies Documented
**✅ PASS** — 1 explicit cross-skill reference in SKILL.md:
- Line 397: "See `spring-boot-test-infrastructure` skill's `references/junit-xml-result-parsing.md` for the parsing technique." — documents dependency on JUnit XML parsing technique from the spring-boot-test-infrastructure skill.

---

## Summary

| Check | Status |
|-------|--------|
| 1. File inventory + TREE_SHA256SUMS | ✅ PASS |
| 2. No production modification suggestions | ✅ PASS |
| 3. BLOCKED_PRODUCTION_OR_ARCHITECTURE_CHANGE_REQUIRED | ✅ PASS (3 occurrences) |
| 4. BLOCKED_BUILD_CONFIGURATION_CHANGE_REQUIRED | ✅ PASS (3 occurrences) |
| 5. CURRENT_SCHEMA_DRIFT_CONFIRMED | ✅ PASS (2 occurrences) |
| 6. Batch safety rules | ✅ PASS (7 rules at lines 326-333) |
| 7. Downstream auto-expansion disabled | ✅ PASS (2 locations) |
| 8. --rerun-tasks in verify script | ✅ PASS (line 17) |
| 9. Cross-Skill dependencies | ✅ PASS (spring-boot-test-infrastructure) |

**Overall: 9/9 checks PASS. The java-test-repair skill content is complete and correct.**
