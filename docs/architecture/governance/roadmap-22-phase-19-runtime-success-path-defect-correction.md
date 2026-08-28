# Roadmap #22 Phase 19 runtime success-path defect correction

TASK_ID=ROADMAP_22_PHASE_19_RUNTIME_SUCCESS_PATH_DEFECT_ROOT_CAUSE_AND_BOUNDED_CORRECTION
CLASSIFICATION=ROOT_CAUSE_DIAGNOSTICS_TEST_ONLY_CORRECTION_AND_RUNTIME_REPROOF_ROUTING
STATUS=D2_CORRECTION_AND_D3_ROUTING_CANDIDATE_CREATED_BY_THIS_COMMIT_REMOTE_REPROOF_PENDING
BRANCH=agent/roadmap22-phase19-runtime-success-path-defect-correction
D1_DIAGNOSTIC_SHA=021c610da87ebac9f90db355a77bbbea58ac7e99
D1_DIAGNOSTIC_PARENT=19adce75ca2e28f28c9602471018ab0737678cfb
D2_FIX_SHA=COMMIT_CONTAINING_THIS_RECORD
FINAL_PHASE19_CANDIDATE=NONE
REMOTE_TARGETED_RESULT=NOT_RUN
REMOTE_FULL_RESULT=NOT_RUN

## Governance and scope

The repository-root `AGENTS.md` governs every changed path; no nested
instruction applies. The current Owner task is newer and narrower. It permits
the enumerated workflow, CI contract, verifier, targeted runtime script,
governance, and two test-helper correction paths. The D3 routing executor
preserved the pre-existing D2 helper changes byte-for-byte. There is no
instruction conflict.

D3 changes no production source, build file, application configuration,
database migration, runtime authority, or remote reference. The D1 production
diagnostic contribution is the committed parent named above. The commit
containing this record carries the D2 test-only correction in the two
previously authorized helpers plus D3 routing. It does not claim integration,
publication, deployment, a remote result, or final Phase19 acceptance.

## Discovery truth

The accepted C2 infrastructure candidate was
`19adce75ca2e28f28c9602471018ab0737678cfb`. Runtime conformance run
`33144937665`, job `98763781835`, checked out that exact SHA with a clean
worktree. Setup, bubblewrap functional preflight, sandbox network/loopback,
FFmpeg/ffprobe identity, and container API preflight passed. The runtime job
failed in
`FfmpegClosedLoopIntegrationTest.real_ffmpeg_stdout_flows_through_staging_platform_artifact_commit_and_completion`;
the other two required methods passed. This was discovery evidence, not a
final Phase 19 candidate or a successful runtime-conformance result.

## D1 root cause and bounded diagnostic evidence

D1 diagnostic commit:

- SHA: `021c610da87ebac9f90db355a77bbbea58ac7e99`
- parent: `19adce75ca2e28f28c9602471018ab0737678cfb`
- classification: `MULTI_FACTOR_BOUNDED_DEFECT`
- primary cause: `FFMPEG_RUNTIME_IDENTITY_SELECTION_DEFECT`
- secondary cause: `WORKER_FABRIC_MAPPING_DEFECT`

The secondary defect discarded already bounded process evidence when mapping
a failed `SandboxExecutionResult`. D1 retained the typed
`ProviderNativeFailureCode` as authority and added only these bounded
diagnostic fields where a process result exists:

- `sandboxFailureCode`
- `processExitCode` (decimal, or `NOT_AVAILABLE`)
- `boundedStderr`
- `stderrTruncated`
- `boundedStdoutSize`
- `stdoutTruncated`

The focused diagnostic fixture mapped `PROCESS_CRASHED` to
`PROCESS_NONZERO_EXIT` with exit `8`, bounded stderr
`stable diagnostic fixture: process rejected its arguments`,
`stderrTruncated=true`, bounded stdout size `14`, and
`stdoutTruncated=false`. Its unrelated secret-like invocation-environment
fixture was absent from the diagnostics. Policy-only mapping retained its
existing bounded field; no environment, HOME content, credential, or
filesystem inventory was added.

D1 test evidence:

- RED log SHA-256:
  `89f0a931073264bb5c67af55d1365e1c42b7027dca74142fb442eb2b63e47839`.
  The focused test compiled and failed because the observed map contained
  only `sandboxFailureCode=PROCESS_CRASHED`, rather than all six bounded
  fields.
- GREEN log SHA-256:
  `72d71e08daaf9d4a91d2d675e7d8efdd0559ec5f740a247a5f90001709107905`.
  The focused test passed; the full executor class was 6/6 and the relevant
  provider-native slice was 31/31, with zero failures, errors, or skips.

## Exact raw identity matrix

The standalone reproduction held the generated input, FFmpeg argv, production
bubblewrap mechanics, filesystem/network/environment policy, and translated
paths constant. The exact bounded matrix was:

| Binary identity | Outside production bwrap | Inside production bwrap |
| --- | --- | --- |
| system `/usr/bin/ffmpeg`, version `7.1.4`, SHA-256 `d54230b48bbc750b24e8967361c483ac6669aa7a5570ff08bb5e4251175d170e` | exit `8`; stdout `0`; bounded stderr `Unrecognized option 'x264-params'. Error splitting the argument list: Option not found` | exit `8`; stdout `0`; same bounded stderr |
| accepted static FFmpeg, version `7.0.2-static`, SHA-256 `e7e7fb30477f717e6f55f9180a70386c62677ef8a4d4d1a5d948f4098aa3eb99` | exit `0`; stdout `2717`; output SHA-256 `d1d1fd11bcb4edbf8a745843a252f3f6bc2381a521fe9e8de864ce8e0ec359e7` | exit `0`; stdout `2717`; identical output SHA-256 `d1d1fd11bcb4edbf8a745843a252f3f6bc2381a521fe9e8de864ce8e0ec359e7` |

This proves the sandbox and path translation were not the primary defect. The
authoritative setup put the accepted static identity first on `PATH`, but both
test helpers ignored `PATH`. With a clean disposable-runner HOME they selected
the incompatible system build. Production contribution uses
`ProviderPluginRuntimeContext.executable()` and does not use either test
helper, so the evidence justified no D2 production-source change.

## D2 minimal test-only correction

D2 changes only `binary(String name)` in:

- `ffmpeg-provider-module/src/test/java/com/example/platform/ffmpeg/FfmpegClosedLoopIntegrationTest.java`
- `platform-distribution/src/test/java/com/example/platform/distribution/DualDistributionPluginConformanceTest.java`

Both helpers now honor `PATH` order, skip blank segments, normalize absolute
candidates, require a regular executable, return the first match without
dereferencing alternatives-style executable symlinks, and fail closed when
`PATH` is absent or no candidate exists. They contain no special-case `/opt`,
runner, distribution, nonce, or CI logic. Production source is unchanged by
D2.

D2 evidence:

- accepted external RED log SHA-256:
  `6886d81f2a877d9149b4461fbeca535f3dd8fa135aac88f5f0300898ea7645ef`;
  clean HOME plus controlled `PATH` ran the three integration methods, with
  the success path failing and the other two passing.
- controlled GREEN log SHA-256:
  `544ffe99aa7535d2fe578068cc259886eac4769d2fad60df6c0b16d426aa828a`;
  3 tests passed, with zero failures, errors, or skips.
- full FFmpeg provider module log SHA-256:
  `0c3bd080feeea846d885da9807ffd1f391f47781a4c84ccf697d2cec61b40ec3`;
  11 tests passed, with zero failures, errors, or skips.
- dual-distribution conformance log SHA-256:
  `b360c8d9448730a755cc58b6f281e1d641047e6811d394df7692a6d5445232c0`;
  3 tests passed, with zero failures, errors, or skips.

## D3 targeted-then-full routing

Both workflows trigger only on a push to
`agent/roadmap22-phase19-runtime-success-path-defect-correction`. They share
the generic `media-runtime-conformance` capability label and have disjoint task
nonces:

- targeted: `media-runtime-targeted-37ce33387e26`
- full: `media-runtime-full-18e2193c23b1`

The targeted workflow queues one `targeted-runtime-reproof` job and runs only
the three required `FfmpegClosedLoopIntegrationTest` methods. Its fresh,
SHA-bound JUnit verifier requires exactly 3 tests, 3 passed, 0 failures,
0 errors, and 0 skipped before either targeted PASS marker is emitted. The
existing full workflow retains its exact checkout, setup, full-suite,
distribution, guard, verifier, digest, and PASS-marker semantics; only its
controlled branch and nonce label change.

At push, both workflows may queue concurrently. The control plane must first
provision only the targeted nonce runner. The full job must remain queued and
blocking, never skipped or treated as green by absence. Only after the targeted
job reports a verified 3/3 pass may the control plane retire it and provision a
new, fresh runner with the full nonce. Before publication and runner
provisioning, neither workflow has run against the commit containing this
record, both remote results remain pending, and no final candidate claim
exists.

## Preserved security, authority, and exclusions

Exact-SHA checkout, complete credential-free history, clean-worktree guard,
authoritative runtime setup, non-root execution, isolated HOME, bounded
identity values, zero fallback/privileged-path markers, production bubblewrap
authority, provider executable authority, output staging, platform Artifact
commit, completion, nonzero behavior, and cancellation behavior remain
unchanged. Raw diagnostics remain bounded evidence and do not become provider,
worker, sandbox, placement, Artifact, fencing, completion, or product semantic
authority.

This task does not authorize or claim Phase 19 closure, Phase 20, Phase 21,
Roadmap #23, BMF, OpenCue, FAOF-3, architecture redesign, deployment,
publication, canonical-main integration, remote-ref mutation, or runner
provisioning. Those scopes remain excluded.
