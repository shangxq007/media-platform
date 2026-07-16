# Agent A — Skill Provenance and Restoration Report

**Task:** BACKEND-INTEGRITY-GREEN-BASELINE-CLOSEOUT.2B
**Agent:** A (READ-ONLY investigator)
**Date:** 2026-07-16
**Status:** COMPLETE

---

## Executive Summary

Both modified Skills are **local-only** — they exist only in `~/.hermes/skills/` with no upstream registry, no bundled manifest entry, no optional-skills source, no version history, and no backups. However, the unauthorized additions are **cleanly delineated sections** that can be precisely identified and removed without affecting the rest of the file. Both skills are classified as **UNAUTHORIZED_ADDITION_CAN_BE_REMOVED_EXACTLY**.

---

## Restoration Source Investigation Results

### Sources Checked (All Negative)

| Source | Result |
|--------|--------|
| `hermes skills list-modified` | "No user-modified bundled skills" — neither is bundled |
| `hermes skills diff <name>` | "not a tracked bundled skill" — no stock version to diff against |
| `hermes skills inspect <name>` | "No skill named X found in any source" — not in any registry |
| `hermes skills repair-official <name>` | "Official optional skill not found" — not in optional-skills repo |
| `~/.hermes/hermes-agent/optional-skills/software-development/` | Contains 3 skills: code-wiki, rest-graphql-debug, subagent-driven-development. Neither target skill present. |
| `~/.hermes/hermes-agent/` git history | No commits referencing either skill path |
| `~/.hermes/skills/.bundled_manifest` | Lists ~60 bundled skills; neither target present |
| `~/.hermes/packages/` | Contains delegation/, documents/, images/, screenshots/, vision/ — no skill packages |
| `~/.hermes/cache/` | No skill cache found |
| `*.bak`, `*.backup`, `*~` files under `~/.hermes` | None found |
| `hermes backup` | Creates zip archives of entire config; no existing backups found |
| `hermes checkpoints` | Tracks working-directory snapshots for file operations, not skill versions |
| `hermes skills snapshot` | Export/import mechanism; no existing snapshots found |
| Session search for `skill_manage` calls | No results found (sessions may have been pruned or were from a different profile) |
| `hermes-agent` docs | No skill versioning or restore mechanism beyond bundled/optional-skills |

### Conclusion

**No external restoration source exists.** These are user-created local skills that were never published to a registry, never bundled, and never backed up.

---

## Skill 1: java-test-repair/SKILL.md

### Metadata

- **Path:** `/home/user/.hermes/skills/software-development/java-test-repair/SKILL.md`
- **SHA256:** `f17154cd0133e6e187e778bb3afec8df4bc63cf2177757798f907411caa2801a`
- **Size:** 33,420 bytes / 481 lines
- **Modified:** 2026-07-16 03:33:49 (unauthorized) + 2026-07-16 09:29 (closeout task — legitimate)
- **Classification:** `UNAUTHORIZED_ADDITION_CAN_BE_REMOVED_EXACTLY`

### Unauthorized Region 1: YAML Frontmatter Triggers

- **Lines:** 22–29 (8 lines)
- **Content:** 8 new trigger entries added to the `triggers:` list

```
22:   - void mock silently skips DB update
23:   - expected FAILED but was EXECUTING
24:   - fail-closed contract violation
25:   - error message mismatch production vs test
26:   - "URI fallback instead of fail-closed"
27:   - "missing history for state transition"
28:   - "getDeclaredFields count mismatch"
29:   - "assertion drift after adding constants"
```

- **Context:** The preceding trigger at line 21 (`- CAS mock thenAnswer DSL update`) and the closing `---` at line 30 are both legitimate boundaries. Lines 22-29 can be removed as a contiguous block.
- **Removal method:** Delete lines 22–29 from the YAML frontmatter.

### Unauthorized Region 2: Support Files List Entries

- **Lines:** 375–378 (4 lines — 3 unauthorized entries + 1 entry from a different task)
- **Content:** 3 new reference file entries added to the `## Support Files` list

```
375: - `references/mockito-silent-failure-patterns.md` — Mockito boolean/boxed default causing silent execution short-circuit...
376: - `references/cas-mock-pattern.md` — mocking CAS database operations with `thenAnswer` + direct DSL update...
377: - `references/fail-closed-contract-pattern.md` — production code falling through to fallback instead of failing closed...
378: - `references/durability-proof-assessment-pattern.md` — assessing what mock stubs prove vs real integration tests...
```

- **Line 375:** `mockito-silent-failure-patterns.md` — reference file created 2026-07-16 00:08 (unauthorized period). Listed as unauthorized reference.
- **Lines 376–377:** `cas-mock-pattern.md` (created 01:31) and `fail-closed-contract-pattern.md` (created 01:30) — both explicitly listed as unauthorized.
- **Line 378:** `durability-proof-assessment-pattern.md` — reference file created 2026-07-16 09:29 (closeout task). This entry was added by the closeout task, NOT the unauthorized task. **Do NOT remove this line.**
- **Removal method:** Delete lines 375–377 only. Keep line 378 (durability-proof-assessment-pattern.md — legitimate closeout addition).

### Unauthorized Region 3: Pitfalls Section Additions

- **Lines:** 450–481 (32 lines, 9 pitfall entries)
- **Content:** 9 new pitfall entries appended to the end of the `## Pitfalls` section

| Line(s) | Pitfall | Matching Spec Item |
|---------|---------|-------------------|
| 450–464 | Void-returning service mock silently skips DB update | "Void-returning mock" |
| 465 | Fail-closed contract violation produces wrong error message | "fail-closed contract" |
| 466 | Missing history record for state transition | "missing history" |
| 467 | Test stubs wrong repository method | (not in spec — adjacent to unauthorized block) |
| 468 | Test schema fixture missing columns | (not in spec — adjacent to unauthorized block) |
| 469 | Gradle `org.gradle.jvmargs` does NOT affect test worker heap | "Gradle heap" |
| 470–477 | ByteBuddy agent JAR lazy resolution: use `jvmArgumentProviders`, NOT `doFirst` | "ByteBuddy agent" |
| 478 | Spring context explosion OOM in platform-app | "Spring context OOM" |
| 479 | Testcontainers "Broken pipe" on Podman | "Testcontainers Broken pipe" |
| 480 | JUnit XML "Could not write" errors | "JUnit XML write" |
| 481 | `getDeclaredFields().length` assertion drift | "getDeclaredFields drift" |

- **Context:** The preceding pitfall at line 449 (ending with "See `references/cas-mock-pattern.md`.") is part of the "Mocking CAS database operations" pitfall which was added during the same unauthorized modification session. However, this pitfall (lines 438–449) is **not** listed in the task spec's 9 unauthorized pitfalls.
- **Decision:** Remove only lines 450–481 as specified. Lines 438–449 (CAS mock pitfall) were not listed as unauthorized in the task spec and will be left in place. If a stricter restoration is desired, lines 438–449 should also be removed (see note below).
- **Removal method:** Delete lines 450–481 (the last line is blank/EOF boundary).

### Unauthorized Reference Files (on disk)

These files were created during the unauthorized modification session but are **not listed in the task spec as requiring removal**. They are referenced by the unauthorized SKILL.md entries being removed:

| File | Created | Status |
|------|---------|--------|
| `references/cas-mock-pattern.md` | 2026-07-16 01:31 | Unauthorized — but not in spec for removal |
| `references/fail-closed-contract-pattern.md` | 2026-07-16 01:30 | Unauthorized — but not in spec for removal |
| `references/mockito-silent-failure-patterns.md` | 2026-07-16 00:08 | Unauthorized — but not in spec for removal |

**Note:** After removing the SKILL.md entries at lines 375–377, these files become orphaned references. They should be removed in a follow-up action if full cleanup is desired.

### Restoration Method

Exact line-range deletion of three contiguous blocks:
1. Lines 22–29 (8 trigger entries)
2. Lines 375–377 (3 support file entries)
3. Lines 450–481 (9 pitfall entries)

This is a deterministic, reversible operation. No content interpolation needed.

---

## Skill 2: kanban-multi-agent-orchestration/SKILL.md

### Metadata

- **Path:** `/home/user/.hermes/skills/software-development/kanban-multi-agent-orchestration/SKILL.md`
- **SHA256:** `3216b69f87c57b8ede93d6bd4f92ee627b94c0893bdd242ead56c36afce1863a`
- **Size:** 17,216 bytes / 410 lines
- **Modified:** 2026-07-16 03:34:01 (unauthorized) + 2026-07-16 09:27 (closeout task — legitimate)
- **Classification:** `UNAUTHORIZED_ADDITION_CAN_BE_REMOVED_EXACTLY`

### Unauthorized Region 1: Pitfalls Section Additions

- **Lines:** 259–261 (3 lines, 3 pitfall entries)
- **Content:** 3 new pitfall entries appended after the original 5 pitfalls

```
259: 6. **Agent D (Claude Code) may not commit** — Claude Code sometimes completes all changes but fails to run `git commit`...
260: 7. **Agent D may choose test-only fix over production contract fix** — When the task requires implementing a production contract...
261: 8. **Parallel agents may produce conflicting changes** — When multiple agents modify different files...
```

- **Context:** The preceding pitfall #5 at line 258 ("Don't add new capabilities during repair") is the last original pitfall. Line 262 is blank. Lines 259–261 can be removed as a contiguous block.
- **Removal method:** Delete lines 259–261.

### Unauthorized Region 2: Four New Sections

- **Lines:** 263–325 (63 lines, 4 sections)
- **Content:** Four new `##`/`###` sections added between the Pitfalls section and the legitimate "Closeout and Forced Re-Execution Pattern" section

| Lines | Section | Subsections |
|-------|---------|-------------|
| 263–273 | `## Test Baseline Recovery Pattern` | Phase descriptions, code block |
| 275–289 | `### Failure Classification Categories` | 11 classification categories in code block |
| 291–298 | `### Pitfalls` | 6 sub-pitfalls for test baseline recovery |
| 300–316 | `### FFmpeg Test Environment Pattern` | FFmpeg static build instructions |
| 318–324 | `### DNS/Tailscale Pitfall` | Tailscale DNS intercept guidance |

- **Context:** Line 325 is blank. Line 326 begins `## Closeout and Forced Re-Execution Pattern` — this section was added by the **closeout task** (09:27) and is **legitimate**. Lines 263–325 can be removed as a contiguous block without affecting the closeout section.
- **Removal method:** Delete lines 263–325 (including the trailing blank line at 325).

### Unauthorized Reference Files

No reference files were created by the unauthorized task for this skill. The `references/` directory contains only files from before (Jul 15) and after (Jul 16 09:28 — closeout task) the unauthorized period.

### Restoration Method

Exact line-range deletion of two contiguous blocks:
1. Lines 259–261 (3 pitfall entries)
2. Lines 263–325 (4 new sections, 63 lines)

This is a deterministic, reversible operation. No content interpolation needed.

---

## Summary Table

| Skill | Path | SHA256 | Unauthorized Regions | Lines to Remove | Classification |
|-------|------|--------|---------------------|-----------------|----------------|
| java-test-repair | `~/.hermes/skills/software-development/java-test-repair/SKILL.md` | `f17154cd...` | 3 (triggers, support files, pitfalls) | 22–29, 375–377, 450–481 (43 lines total) | **UNAUTHORIZED_ADDITION_CAN_BE_REMOVED_EXACTLY** |
| kanban-multi-agent-orchestration | `~/.hermes/skills/software-development/kanban-multi-agent-orchestration/SKILL.md` | `3216b69f...` | 2 (pitfalls, 4 sections) | 259–261, 263–325 (66 lines total) | **UNAUTHORIZED_ADDITION_CAN_BE_REMOVED_EXACTLY** |

## Restoration Action Required

Both skills require a single coordinated patch operation:
1. Apply line-range deletions to both SKILL.md files
2. Optionally remove 3 orphaned reference files from `java-test-repair/references/`
3. Verify post-removal SHA256 hashes match expected values

**No external restoration source is needed.** The unauthorized additions are self-contained sections that can be surgically removed.

## Open Questions for Lead

1. **Lines 438–449 of java-test-repair/SKILL.md** — The "Mocking CAS database operations" pitfall references `cas-mock-pattern.md` (an unauthorized reference file) and was added during the same unauthorized session. Should it also be removed? It is NOT listed in the task spec's 9 unauthorized pitfalls.

2. **Orphaned reference files** — After removing the SKILL.md entries, 3 reference files (`cas-mock-pattern.md`, `fail-closed-contract-pattern.md`, `mockito-silent-failure-patterns.md`) become orphaned. Should they be deleted?

3. **Lines 467–468 of java-test-repair/SKILL.md** — Two pitfall entries ("Test stubs wrong repository method" and "Test schema fixture missing columns") fall between the explicitly-listed unauthorized items but are not named in the task spec. They appear to be part of the same unauthorized addition batch. Should they be preserved or removed?
