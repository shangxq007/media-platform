# Phase 17 Sandbox Conformance Lane

The authoritative Phase 17 sandbox conformance lane is the push-only workflow
`.github/workflows/phase17-sandbox-conformance.yml`. It is bound to the governed
recovery branch and to an ephemeral self-hosted Linux x64 runner carrying both
`phase17-sandbox-conformance` and the Correction 15 nonce label
`phase17-c15-20260826`.

## Test authority

`MEDIA_PLATFORM_REQUIRE_PHASE17_SANDBOX_CONFORMANCE=true` is an exact,
case-sensitive test-only switch. When it is absent or has any other value, the
four manifested integration tests retain their portable capability-skip
behavior. When it is exactly `true`, a missing or unusable Bubblewrap launcher,
rootless container engine, FFmpeg binary, FFprobe binary, or generated media
fixture is a test failure. Production code does not read this switch.

The exact test denominator and coverage rationale are recorded in
`automated-guards/phase17-sandbox-conformance-tests.tsv`. The result guard
accepts only fresh Gradle JUnit XML for those four precise methods at the passed
source SHA; it rejects missing, extra, duplicated, skipped, aborted, failed,
errored, stale, or SHA-mismatched evidence.

The runner performs fail-closed Bubblewrap production-shape and rootless Podman
hardening/resource smokes before Gradle. The Podman smoke mounts only its
bounded temporary workspace and input. It does not mount a host or primary-user
container-engine socket.

## Long-term infrastructure state

Dedicated PVE-hosted Phase 17 conformance infrastructure is `PLANNED`. This
record creates no PVE automation, guest, runner registration, production
infrastructure, or deployment authority. Any implementation requires a
separate governed task and authorization.
