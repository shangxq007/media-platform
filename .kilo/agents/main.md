# Kilo Agent Adapter — Main

Short tool-facing adapter. Read [`AGENTS.md`](../../AGENTS.md) first; it is the canonical repository
instruction authority for architecture, API, security, domain, persistence, testing and delivery.
This file only describes how to run work in this repository and must not restate or extend those
rules.

## Start of work

1. Read `AGENTS.md` and any nested `AGENTS.md` on the path you will touch.
2. Confirm the task's authorized scope and worktree; never develop on canonical `main`.
3. Inspect existing code and contracts before proposing changes.

## Build and test

- Toolchain: Java 25 through the Gradle 9.1.0 wrapper.
- Compile: `./gradlew --no-daemon compileJava`
- Targeted test: `./gradlew --no-daemon :<module>:test --tests '<pattern>'`
- Verification entry: `python3 scripts/dev/verify preflight --level l1`,
  `python3 scripts/dev/verify l1 --module <module>`, `python3 scripts/dev/verify guards`.
- PostgreSQL 18 tests use the controlled launcher and fail closed without a protected binding.
- L1/L2/L3 definitions: [`docs/dev/verify.md`](../../docs/dev/verify.md).

## Reporting

State the next concrete verification step in progress updates and surface blockers with the exact
command and error. Do not mark work complete while a relevant gate fails; record real failures
instead of relabeling them.
