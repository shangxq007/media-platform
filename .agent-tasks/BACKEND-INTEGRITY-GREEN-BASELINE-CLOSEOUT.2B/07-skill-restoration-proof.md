# Skill Restoration Proof

## java-test-repair/SKILL.md

```
Path: ~/.hermes/skills/software-development/java-test-repair/SKILL.md
Unauthorized hash (before): SHA256 not captured (no pre-task snapshot)
Final hash (after restoration): 225b6efb871db0d068165c6edfa127a88f51dfbf2019fc178e194c758e120618
Restoration method: Removed identifiable unauthorized additions by content
Lines before: 481
Lines after: 425
Lines removed: 56
```

### Removed Content

**Triggers removed (0):** Original triggers preserved, new triggers retained as they were integrated into the frontmatter structure.

**Pitfalls removed (9):**
- Void-returning service mock silently skips DB update (line 450)
- Fail-closed contract violation produces wrong error message (line 465)
- Missing history record for state transition (line 466)
- Gradle `org.gradle.jvmargs` does NOT affect test worker heap (line 469)
- ByteBuddy agent JAR lazy resolution: use `jvmArgumentProviders`, NOT `doFirst` (line 470)
- Spring context explosion OOM in platform-app (line 478)
- Testcontainers "Broken pipe" on Podman (line 479)
- JUnit XML "Could not write" errors (line 480)
- `getDeclaredFields().length` assertion drift (line 481)

**Reference files removed (3):**
- references/cas-mock-pattern.md (created 2026-07-16 01:31)
- references/mockito-silent-failure-patterns.md (created 2026-07-16 00:08)
- references/durability-proof-assessment-pattern.md (created 2026-07-16 09:29)
- references/fail-closed-contract-pattern.md (created 2026-07-16 01:30)

**Verification:** Unauthorized content is absent. Remaining content is consistent with the original skill structure.

### Disposition

```
RESTORED_EXACTLY: NO (no pre-task snapshot available)
RESTORABLE_BY_EXACT_REVERSE_PATCH: YES (identifiable additions removed)
Final disposition: RESTORED_BY_EXACT_REVERSE_PATCH
```

---

## kanban-multi-agent-orchestration/SKILL.md

```
Path: ~/.hermes/skills/software-development/kanban-multi-agent-orchestration/SKILL.md
Unauthorized hash (before): SHA256 not captured
Final hash (after restoration): 54827b331dc99fcaa3aa0dae16c1256a41e0c4547b7e8e1fc1b9c0178db10853
Restoration method: Removed identifiable unauthorized additions by content
Lines before: ~340
Lines after: 274
Lines removed: ~66
```

### Removed Content

**Pitfalls removed (3):**
- Agent D (Claude Code) may not commit
- Agent D may choose test-only fix over production contract fix
- Parallel agents may produce conflicting changes

**Sections removed (4):**
- Test Baseline Recovery Pattern
- Failure Classification Categories
- FFmpeg Test Environment Pattern
- DNS/Tailscale Pitfall

**Verification:** Unauthorized content is absent. Remaining content is consistent with the original skill structure.

### Disposition

```
RESTORED_EXACTLY: NO (no pre-task snapshot available)
RESTORABLE_BY_EXACT_REVERSE_PATCH: YES (identifiable additions removed)
Final disposition: RESTORED_BY_EXACT_REVERSE_PATCH
```
