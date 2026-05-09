# Testing Policy

Every implementation task must:

1. Add or update tests.
2. Run the narrowest relevant test first.
3. Run the affected module test.
4. Run the broader build before marking the task complete.

Required commands when relevant:

- `./gradlew test`
- `./gradlew :platform-app:test`
- `./gradlew :platform-app:bootJar`
- `./gradlew :platform-app:bootRun` only when smoke testing is required

Spring Modulith:

- Keep ModularityTest passing.
- Do not introduce forbidden package dependencies.

When tests fail:

- Diagnose root cause.
- Fix implementation or tests.
- Re-run the failing test.
- Then re-run the broader build.

Never mark a task complete with failing tests unless the failure is unrelated, isolated, and documented in `docs/roo-execution-log.md`.
