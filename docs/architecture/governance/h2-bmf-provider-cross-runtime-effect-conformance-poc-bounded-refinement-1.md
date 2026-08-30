# H2 BMF Provider POC Bounded Refinement 1

```text
TASK=H2_BOUNDED_REFINEMENT_REQUIRED_BEFORE_B0
REVIEW_RESULT=H2_ARCHITECTURE_REVIEW_PASS_WITH_BOUNDED_REFINEMENTS
ARCHITECTURE_DECISION=BMF_POC_DECISION_RECOVERY_ARCHITECTURALLY_ACCEPTED
CORRECTION_MODE=MINIMAL_APPEND_FORWARD_GOVERNANCE_ONLY
REVIEWED_CANDIDATE_SHA=997a4cc177a67aae2a98258818c7c1279370820c
REVIEWED_CANDIDATE_TREE=5c9b4bfb2929ee22267fc994ceb4c43f94c23b30
CORRECTION_PARENT_REQUIRED=997a4cc177a67aae2a98258818c7c1279370820c
BASE_SHA=e02579181ba3049ae65ed81080c93a7212f5833d
HISTORY_REWRITE=FORBIDDEN
PUSH_OR_MERGE=FORBIDDEN
BMF_POC_IMPLEMENTATION_AUTHORIZATION=NO_GO
B0_AUTHORIZED=NO
```

This immutable append-forward record refines, and otherwise preserves, `BMF_PROVIDER_AND_CROSS_RUNTIME_EFFECT_CONFORMANCE_POC_BOUNDED_ARCHITECTURE_CONTRACT_V1` at the reviewed candidate. It does not restart Decision Recovery, alter the accepted provider-local graph or cross-runtime conformance architecture, authorize B0, or begin implementation. Where this record conflicts with C13, C15, C30, C31, C32, C37, C38, or their Q-table summaries in the reviewed contract, this record is the later authority.

## R1 — Provider implementation identity is not runtime packaging

The following inequality is frozen:

```text
ProviderImplementationId != ProviderRuntimeBundleId != RuntimeDependencyFingerprint
```

The bounded BMF identities are:

```text
ProviderId=bmf
ProviderImplementationId=bmf.cpu.v1
ProviderImplementationId=bmf.cuda.v1
```

`ProviderImplementationId` identifies a stable implementation strategy and behavior under one provider family. It does not encode `container` merely because the POC is packaged as OCI. OCI is a replaceable provider-local runtime packaging mechanism; changing an image digest, base image, dependency patch, linkage closure, or equivalent packaging technology does not by itself create a new ProviderImplementation identity.

`ProviderRuntimeBundleId`, if H1 justifies and freezes it, identifies an exact assembled runtime bundle independently of ProviderImplementation identity. `RuntimeDependencyFingerprint` is the H1-owned deterministic fingerprint of the exact declared and observed runtime dependency closure. The exact OCI image digest, BMF commit/tree, FFmpeg/libav dependency, CUDA runtime, compiler/build flags, enabled modules, dynamic-linkage inventory, lock manifest, SBOM, and license evidence belong to the H1-owned provider-local runtime dependency/bundle observation model, not canonical media semantics and not ProviderImplementation identity.

No `container`-bearing ProviderImplementation identity is authorized by this correction. A future proposal to restore such an identity must independently prove that container execution changes stable implementation behavior rather than replaceable packaging or runtime mechanics.

## R2 — Exact FFmpeg patch-release policy

Ambiguous `FFmpeg 4.4` is not an acceptable runtime dependency identity. The pinned BMF v0.2.0 tree contains one compatibility statement for FFmpeg 4.2 through 5.1 and separately recommends FFmpeg 4.4+ for the examined GPU-frame-extraction issue.[1] Its build script downloads the unpatched `ffmpeg-4.4.tar.bz2`, which is upstream build evidence, not an acceptable immutable POC lock.[2]

The architecture review supplied a broader 4.0–5.1 compatibility statement. The pinned v0.2.0 source inspected in this lane mechanically states 4.2–5.1. This lower-bound discrepancy does not affect the first B0 candidate because 4.4.8 is inside both ranges; neither range is accepted as runtime proof. B0 must decide compatibility from the exact pinned build and execution evidence.

B0 must first evaluate:

```text
BMF_SOURCE_COMMIT=c39146c636c6b2b68ffaf741095ce737bf123254
BMF_SOURCE_TREE=f072467431ad2d5d571eeda04510b93d25156a3a
FFMPEG_B0_FIRST_CANDIDATE=4.4.8
FFMPEG_4_4_8_SOURCE_URL=https://ffmpeg.org/releases/ffmpeg-4.4.8.tar.xz
FFMPEG_4_4_8_SIGNATURE_URL=https://ffmpeg.org/releases/ffmpeg-4.4.8.tar.xz.asc
FFMPEG_4_4_8_SHA256=c73848c4ae283d9eaee7be3b276affbc3543380483555500d0dd2c9b7e1c39c3
FFMPEG_4_4_8_SIGNATURE_SHA256=162b4a899f2204563235185c1de980d8d40fd7bcbec4739a132f823ea1fc885f
FFMPEG_RELEASE_KEY_FINGERPRINT=FCF986EA15E6E293A5644F10B4322F04D67658D8
FFMPEG_RELEASE_KEY_SOURCE=https://ffmpeg.org/ffmpeg-devel.asc
FFMPEG_RELEASE_KEY_SHA256=397b3becedcd5a98769967ff1ff8501ddc89f8368b8f766e4701377d7dbaabe5
```

The official release index exposes both the 4.4.8 archive and detached signature.[3] Mechanical research for this correction downloaded both, imported the FFmpeg release key from the official key URL, and obtained a valid detached-signature result for fingerprint `FCF986EA15E6E293A5644F10B4322F04D67658D8`; the imported key remains an evidence input whose source and fingerprint must be controlled.[4]

These observations select the first B0 candidate only; they do not claim that BMF builds or runs with 4.4.8. B0 acceptance requires, at minimum:

1. exact archive digest and detached-signature verification from controlled evidence;
2. clean build against pinned BMF v0.2.0 source with complete build flags and dependency locks;
3. dynamic-linkage proof with no ambient host FFmpeg/libav resolution;
4. normalized runtime probe and H1-owned runtime dependency observation/fingerprint match;
5. minimum CPU blur-path load and execution proof;
6. GPU linkage/module proof only if the separately eligible H1 runtime/device environment exists;
7. SBOM and license/distribution review.

If 4.4.8 fails concrete source, build, linkage, probe, or runtime compatibility, B0 must record the failing command, logs, dependency observations, and typed incompatibility decision before selecting another exact immutable FFmpeg revision. No floating `4.4`, mutable branch, silent fallback, or host-provided native dependency is authorized. Every subsequently accepted dependency must have an exact immutable identity plus digest and, where upstream supplies it, verified signature evidence.

## Cross-lane authority binding

H2 consumes, and does not duplicate, H1 authority for:

- `RuntimeDependencyRequirement`;
- `RuntimeDependencyObservation`;
- `RuntimeDependencyFingerprint`;
- `ProviderRuntimeBundleId`, if H1 justifies it;
- `WorkerRuntime` and Device observations;
- `RuntimeEligibilityEvaluator`.

H2 may supply BMF-specific requirements, probes, and incompatibility diagnostics through those H1 contracts. It must not create H2-local competing types, registries, fingerprints, runtime bundle authority, WorkerRuntime models, Device models, or eligibility evaluators.

The following separations are frozen:

```text
RUNTIME_ELIGIBILITY != SEMANTIC_CONFORMANCE
CAN_RUN != OUTPUT_EQUIVALENCE
```

A runtime-eligible BMF implementation may still fail effect conformance. A semantically conforming output from one observed run does not prove future runtime eligibility. Both gates fail closed and retain separate evidence and decisions.

## Final contract guard and authorization boundary

```text
H2_BOUNDED_REFINEMENT_1_FINAL_CONTRACT_GUARD=PASS
PROVIDER_IMPLEMENTATION_RUNTIME_IDENTITY_SEPARATION=FROZEN
FFMPEG_EXACT_PATCH_RELEASE_POLICY=FROZEN
H1_CROSS_LANE_RUNTIME_AUTHORITY=CONSUMED_NOT_DUPLICATED
RUNTIME_ELIGIBILITY_SEMANTIC_CONFORMANCE_SEPARATION=FROZEN
BMF_POC_IMPLEMENTATION_AUTHORIZATION=NO_GO
B0_AUTHORIZED=NO
REFINEMENT_RECEIPT_REQUIRED=YES
H2_REFINED_CANDIDATE_READY_FOR_B0_AUTHORIZATION=PENDING_POST_COMMIT_RECEIPT
```

Only a detached post-commit receipt that proves the exact correction SHA/tree/parent, two-commit base-to-tip topology, one-path docs-only scope, clean worktree, no history rewrite, contract/JSON/citation gates, and a verified SHA-256 evidence manifest may advance this candidate to `H2_REFINED_CANDIDATE_READY_FOR_B0_AUTHORIZATION`. That token means ready for a separate authorization decision; it is not B0 authorization.

## Sources

[1] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/bmf/demo/video_frame_extraction/Readme.md
[2] https://github.com/BabitMF/bmf/blob/c39146c636c6b2b68ffaf741095ce737bf123254/scripts/build_ffmpeg.sh
[3] https://ffmpeg.org/releases/?C=N;O=D
[4] https://ffmpeg.org/ffmpeg-devel.asc
