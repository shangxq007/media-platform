# Roadmap #22 Phase 19 C2 disposable-runner final conformance

TASK=ROADMAP_22_PHASE_19_C2_DISPOSABLE_RUNNER_WORKFLOW_CORRECTION
BASE_SHA=646fcf010955e68ce3a0e3a9edaeeaa707360946
CONTROLLED_BRANCH=agent/roadmap22-phase19-c2-disposable-runner-final-conformance
NONCE_LABEL=media-runtime-conformance-807efc0476d8
CLASSIFICATION=CI_RUNTIME_SECURITY_CONFORMANCE_AND_GOVERNANCE_ONLY
STATUS=C2_REMOTE_RUNTIME_CONFORMANCE_PENDING

## Scope and split topology

The repository-root `AGENTS.md` governs every changed path; no nested instruction file applies. The Owner task is the newer and narrower authorization. It prohibits Git mutation, production-source changes, application test-source changes outside the authorized CI contract tests, build-file changes, application-configuration changes, and full-suite local execution. This correction stays within the enumerated CI scripts, workflows, contract tests, result verifier, and this governance record.

General hosted CI and runtime security conformance are separate capability classes with one change-impact classifier authority. Standard CI retains PFIRR1, production/test compilation, `platform-app:bootJar`, and Docker build smoke. Foundation Verification retains shell syntax, the jOOQ fail-closed negative proof, PFIRR1, production/test compilation, and `platform-app:bootJar`. Runtime setup and the full authoritative test suite move to the dedicated push-only Phase19 Runtime Conformance workflow. Hosted policy-summary jobs continue to decide only their hosted-compatible jobs; the dedicated runtime workflow conclusion is the combined runtime-policy evidence.

## Exact disposable-runner selection and checkout

The runtime job requires all five labels exactly: `self-hosted`, `linux`, `x64`, `media-runtime-conformance`, and `media-runtime-conformance-807efc0476d8`. The workflow triggers only from a push to `agent/roadmap22-phase19-c2-disposable-runner-final-conformance`, has `contents: read`, checks out complete credential-free history at the triggering SHA, requires a clean checkout, and proves `git rev-parse HEAD == GITHUB_SHA` before printing the checked SHA. External queue selection must independently verify the final frozen SHA and nonce. If no exact runner is online, the job must remain queued and blocking; there is no skip, hosted alternative, fallback, privileged execution, or green-by-absence path.

The expected runner is disposable, non-root, and isolated from operator control material. Its `HOME` must contain none of `.ssh`, `.config/bws`, `.hermes`, or `.codex*`. Bounded identity reporting includes only uid, uname, OS ID/version, HOME/workspace paths, checked SHA, and setup-produced runtime identities; it does not enumerate files or print secrets.

## Setup identity and security contract

`scripts/ci/setup-test-runtime.sh` remains the authoritative setup and preserves the existing bubblewrap functional-preflight bytes and semantics. Every required absolute executable resolves through a fail-closed identity ladder: one exact installed dpkg owner and package/version; otherwise one exact installed rpm owner and NEVRA; otherwise the SHA-256 of the existing absolute executable. `UNKNOWN` identity and silent bypass are forbidden. Debian `apt` installation remains conditional on a missing binary and requires the non-root runner plus `sudo`; the intended development runner already contains the binaries and therefore must record no privileged install path. No root fallback is added.

After all setup checks succeed, `GITHUB_ENV` receives `DOCKER_HOST`, bounded bubblewrap/FFmpeg/ffprobe identities, explicit zero fallback/privileged-path markers, and `MEDIA_RUNTIME_SETUP_CONFORMANT=1`. These values contain no credentials or secret material. Runtime conformance fails closed unless the sentinel, identity values, zero markers, exact expected SHA, triggering SHA, clean checkout, and non-root isolation all hold.

## Authoritative runtime manifest

The dedicated runtime script runs exactly `./gradlew --no-daemon --max-workers=1 test --rerun-tasks`, then the sole executable `bootJar` and `verifyBundledDistributionPluginDigest`. Phase 0 subsequently removed the modular external-directory distribution and the separate plain launcher artifact. The script then runs the Phase 19 clean-forward repository guard and mutation tests and verifies fresh JUnit XML created after a SHA-bound runtime start marker.

Required fresh module XML roots are:

- `ffmpeg-provider-module`
- `sandbox-isolation-module`
- `worker-fabric-module`
- `provider-plugin-runtime-module`
- `artifact-module`
- `platform-distribution`

The verifier requires aggregate failures and errors to remain zero and requires each of these `FfmpegClosedLoopIntegrationTest` methods exactly once, passed and not skipped:

- `nonzero_and_cancellation_publish_no_artifact_or_completion()`
- `bounded_probe_returns_exact_version_build_evidence_without_eligibility_authority()`
- `real_ffmpeg_stdout_flows_through_staging_platform_artifact_commit_and_completion()`

Producer and embedded plugin bytes must each equal SHA-256 `df496276e7a087431d9e5ded07163d92d2ccacaede2c0250fb9f8d9ea0319c30`. Embedded bytes are streamed from the all-in-one archive through `unzip` to SHA-256 without modifying the artifact. Runtime-security, plugin-distribution, and artifact/cancellation-equivalence PASS markers are printed only after every command, verifier, and digest comparison succeeds.

## Lifecycle boundary

This correction adds no security weakening and preserves the Phase 19 clean-forward guards. C2 remains pending until the exact remote runtime job completes successfully against a frozen final SHA on the nonce-selected disposable runner. A final Phase 19 candidate or closure has not been created by this task. Phase 20, Phase 21, Roadmap #23, BMF, OpenCue, FAOF-3, deployment, publication, canonical-main integration, and remote-ref mutation are prohibited scopes and have not started.
