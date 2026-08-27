# Roadmap #22 Phase 19 — CI Runtime Capability Correction

TASK_ID=ROADMAP_22_PHASE_19_CI_RUNTIME_CAPABILITY_CORRECTION_V1
CLASSIFICATION=CI_RUNTIME_PROVISIONING_AND_VALIDATION_ONLY
STATUS=IMPLEMENTATION_CANDIDATE_PENDING_CHATGPT_FINAL_REVIEW
ORIGINAL_SHA=d1550e5979cb2d691f0500399ef0ce0f1f536344
ORIGINAL_TREE=9eaab286a6f0a3bfbdddf85ca9c41371bbb87baa
ORIGINAL_PHASE_19_STATUS=IMPLEMENTATION_CANDIDATE_PENDING_CHATGPT_FINAL_REVIEW
ORIGINAL_WORKTREE_STATUS_AT_PREFLIGHT=CLEAN
CORRECTION_SHA=DERIVED_FROM_GIT_AFTER_FREEZE
CORRECTION_TREE=DERIVED_FROM_GIT_AFTER_FREEZE
CORRECTION_PARENT_SHA=d1550e5979cb2d691f0500399ef0ce0f1f536344
CORRECTION_IDENTITY_SOURCE=GIT_AFTER_FREEZE
REMOTE_EVIDENCE_IDENTITY_SOURCE=FROZEN_CORRECTION_SHA_AND_TREE
PHASE_19_CLOSED=NO
PHASE_20_STARTED=NO
PHASE_21_STARTED=NO
ROADMAP_23=NOT_STARTED
BMF_STARTED=NO
OPENCUE_STARTED=NO
FAOF_3=NOT_AUTHORIZED
NO_SEMANTIC_AUTHORITY_CHANGES=YES

## Governing scope and precedence

The repository-root `AGENTS.md` applies to all touched paths. No nested
instruction file applies. The Owner task is the newer, narrower authorization:
it permits only CI runtime provisioning and validation, one small contract
test, this governance record, and a directly necessary classifier test. The
implementation executor was separately prohibited from Git mutation. Hermes
verified the original SHA/tree and clean isolated correction worktree before
editing; no repository identity is inferred or fabricated.

## Root cause and bounded correction

The primary failure was runner capability, not provider semantics: the GitHub
runner had FFmpeg but did not have a usable executable at `/usr/bin/bwrap`, so
all three `FfmpegClosedLoopIntegrationTest` methods failed closed. The secondary
risk was that the remote FFmpeg and ffprobe binary, build, version, and package
identity was not declared and checked.

The exact intended correction is limited to:

- a deterministic mutation-backed source contract for the setup script;
- conditional `apt` installation of `bubblewrap` only when executable
  `/usr/bin/bwrap` is unavailable, followed by an exact-path requirement;
- a real, non-root, fail-closed bubblewrap preflight using the production-shape
  namespace/session/environment controls, a generated read-only input, an
  isolated `/workspace`, a trivial in-sandbox executable, and exact result
  verification;
- paired FFmpeg/ffprobe availability, absolute binary resolution, exact dpkg
  package identity, equal version tokens, major-version membership in the
  evidence-backed set `{6,7}`, and `--enable-libx264` build capability;
- machine-readable runtime evidence in the CI log.

Both existing workflows already call `scripts/ci/setup-test-runtime.sh`, so no
workflow change is intended. The pre-existing container API selection,
provisioning, PTEH-V1 validation, and persistence of only `DOCKER_HOST` to
`GITHUB_ENV` remain unchanged.

## Policy and authority boundary

`REMOTE_RUNTIME_IDENTITY_POLICY=BOUNDED_AND_VERIFIED` is runtime conformance
only. FFmpeg or ffprobe version and package identity do not become canonical
media, provider, worker, sandbox, placement, Artifact, fencing, completion, or
product semantics. This correction adds no fallback execution path, privileged
preflight, host/network expansion, or test exception. The functional
bubblewrap invocation runs as the current non-root runner and never through
`sudo`.

No production source, provider test, worker test, sandbox test, build graph, or
application configuration is changed. BMF, OpenCue, extension/render dependency
cleanup, Phase 20, Phase 21, FAOF-3, and Roadmap #23 remain outside this
authorization and have not started here.

## Lifecycle and evidence handling

Phase 19 remains an implementation candidate pending final review; this
correction does not close it. The immutable correction SHA/tree and remote CI
evidence must be derived only after the candidate is frozen. Persisting those
values inside this candidate would be self-referential and would change the
identity being recorded.
