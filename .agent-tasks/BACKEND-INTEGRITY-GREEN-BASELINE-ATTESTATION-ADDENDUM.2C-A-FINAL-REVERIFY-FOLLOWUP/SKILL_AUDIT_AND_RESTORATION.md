# Skill Audit and Restoration Attempt

## Kanban Skill

```
Path: ~/.hermes/skills/software-development/kanban-multi-agent-orchestration/SKILL.md
Expected hash: 54827b331dc99fcaa3aa0dae16c1256a41e0c4547b7e8e1fc1b9c0178db10853
Before hash:   1ad89cf704b5f6df25f2f7b2fd585b32136a982561b0a445102ba02c92d5d5c8
After hash:    7af1f4dd57a753c13ba7c95c39901018bcbb59822d25f0ed14620d9b4d1eff16
Lines before:  283
Lines after:   274 (after full rewrite with known content)
Restoration:   ATTEMPTED — full content rewrite from conversation history
Result:        HASH_MISMATCH — content functionally identical but bytes differ
```

### Changes Identified

External process (curator) added3 lines to verification checklist:
- "Full test suite passes twice (for baseline recovery tasks)"
- "No environment-specific IP workarounds in test code"
- "No newly disabled tests to obtain green"

These were removed, but other subtle differences remain (possibly whitespace, formatting, or additional minor curator edits).

## Java-test-repair Skill

```
Path: ~/.hermes/skills/software-development/java-test-repair/SKILL.md
Expected hash: 225b6efb871db0d068165c6edfa127a88f51dfbf2019fc178e194c758e120618
Before hash:   ae1114db4ba1fc4d451d9be8ac0bac885731029d6113e7f2bfa2cd355c6d66b5
After hash:    d6c60111883591d441fce00e20a5963268faba8fff041f4eefad9478ffa6caba
Lines before:  432
Lines after:   428 (after removing4 re-added pitfalls)
Restoration:   PARTIAL — removed identifiable re-added content
Result:        HASH_MISMATCH — other curator modifications remain
```

### Changes Identified

External process (curator) re-added4 pitfalls:
- "Gradle org.gradle.jvmargs does NOT affect test worker heap"
- "Spring context explosion OOM in platform-app"
- "Testcontainers Broken pipe on Podman"
- "--rerun-tasks --no-build-cache for forced test execution"

These were removed. Additional curator modifications likely include trigger additions and other subtle changes.

## Original Change Source

```
UNPROVEN_EXTERNAL_CHANGE
ORIGINAL_PROVENANCE_UNRESOLVED
```

## Content Integrity Status

```
BLOCKED_EXACT_SKILL_CONTENT_UNAVAILABLE
```

The exact2C starting content bytes are not preserved in any recoverable source.
