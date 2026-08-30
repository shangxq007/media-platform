# Roadmap 22 Phase 19 C3 FFmpeg Plugin Migration and Dual Distribution Correction

> Historical Phase 19 evidence. Phase 0 later removed the modular external-directory
> distribution clean-forward. Current production distribution is the controlled embedded
> bundle only; the task names and dual-mode evidence below describe the frozen Phase 19 state.

TASK=ROADMAP_22_PHASE_19_C3_FFMPEG_PLUGIN_MIGRATION_AND_DUAL_DISTRIBUTION_CORRECTION_V1

BASE_SHA=c058e187cfbb2fdb8037aca21ae333a7df27a4bb

BASE_TREE=7629f79e7025e3dabd4ed19278f6de147e229892

PHASE_19_STARTED=YES

PHASE_19_CLOSED=NO

C2_DEVELOPMENT_MACHINE_DISPOSABLE_RUNNER_POC_STATUS=PASS_FOR_BOUNDED_OUTER_SANDBOX_EVALUATION

C2_EXACT_SHA_REMOTE_SECURITY_CONFORMANCE=PENDING

PHASE_20_STATUS=NOT_STARTED

PHASE_21_STATUS=NOT_STARTED

FAOF_3_STATUS=NOT_STARTED

ROADMAP_23_STATUS=NOT_STARTED

MARKETPLACE_STATUS=NOT_STARTED

C3_IMPLEMENTATION_COMPLETE=YES

C3_IMPLEMENTATION_CANDIDATE_CREATED=YES_BY_THIS_COMMIT

C3_LOCAL_FUNCTIONAL_VALIDATION=PASS

C3_CLASSIFIER_REQUIRED_GATE_SET=FAIL_INHERITED_SEMGREP_ONLY

C3_SEMGREP_WAIVER_SCOPE=IMPLEMENTATION_CANDIDATE_FREEZE_ONLY

C3_SEMGREP_WAIVER_DOES_NOT_AUTHORIZE=FINAL_PHASE19_CANDIDATE,PHASE19_CLOSURE,CANONICAL_MAIN_INTEGRATION

C3_PRODUCTION_SCOPE_ACCEPTED=YES

FINAL_PHASE19_CANDIDATE_CREATED=NO

C3_FROZEN_SHA_SOURCE=DERIVED_FROM_GIT_COMMIT_CONTAINING_THIS_RECORD

MODULITH_TYPED_ALLOWANCE_FOLLOWUP=DEFERRED_NON_BLOCKING

## Scope and instruction precedence

The Owner task authorized production, test, build, configuration, and documentation changes in the named linked worktree and prohibited working-tree Git mutation, commits, pushes, and ref mutation. The repository-root `AGENTS.md` applies repository-wide; no nested instruction file applies and no instruction conflict was found. Read-only `git show` access to the frozen base was used only where the Owner explicitly required exact-byte reconstruction.

The frozen distinctions remain explicit:

- plugin identity: `media.transcode.ffmpeg@1.0.0`;
- provider identity: `ffmpeg`;
- provider implementation: `ffmpeg.cpu.native-pull.v1@1.0.0`;
- capability: `media.transcode@1.0`;
- typed handled object: `ExecutableTask@1`;
- execution binding: `ProviderNativeRuntimeBinding`, created only from a bounded `ProviderPluginRuntimeContext`;
- worker/runtime identity, execution backend, capability implementation, plugin registry, capability registry, and provider catalog remain separate concepts;
- artifact staging, commit, reuse publication, fencing, and completion remain platform-owned.

The legacy JSON `PluginRuntimeProviderBinding` is not used by this path.

## Exact authority census and disposition

The tracked machine-readable source is `roadmap-22-phase-19-c3-ffmpeg-authority-disposition-ledger.json` in this directory. It manifests every one of the 97 FFmpeg/ffprobe-named main Java types at the frozen base:

| Disposition | Count | Result |
|---|---:|---|
| `REUSE_AS_CANONICAL` | 9 | Existing bounded CPU transcode provider/runtime/probe types retained in the plugin JAR. |
| `RETAIN_SEMANTIC_NON_EXECUTABLE` | 67 | Render-domain semantic planner/record/enum types retained; the guard rejects process/runtime/argv/artifact authority in these files. |
| `DEFER_NON_RENDER_EXECUTION_AUTHORITY` | 3 | The media normalizer plus the platform-app ingest FFprobe provider/result remain byte-identical to the base because they do not own render execution. |
| `DELETE_EXECUTION_COMMAND_PROCESS_AUTHORITY` | 18 | Render concrete/process/command/probe surfaces deleted. |
| `UNCLASSIFIED` | 0 | Complete. |
| **Total** | **97** | `9 + 67 + 3 + 18 = 97`. |

The ledger separately classifies the two post-base PF4J types and non-FFmpeg-named smoke/filtergraph/probe surfaces so they do not distort the frozen 97-type arithmetic.

## Clean-forward architecture

The old concrete render provider, command factory, render-side direct probes, local smoke command builders, render worker runner, render self-description, and queue/FFmpeg tool wrappers are deleted. The ingest FFprobe provider remains deferred and unchanged. `RenderProviderAutoConfiguration` no longer registers a concrete FFmpeg bean. Provider fallback graphs, Natron/VapourSynth fallback switches, application profiles, and CLI tool recipes no longer route to FFmpeg. Remaining legacy generic application render entry points fail closed with `TYPED_PROVIDER_PLUGIN_EXECUTION_REQUIRED`; they do not build argv or invoke a process.

`scripts/phase19-clean-forward-guards.py` loads the ledger, checks its exact arithmetic and active/deleted paths, proves semantic-only sources cannot acquire execution/artifact authority, discovers unclassified FFmpeg-named main declarations, and emits these enforced results:

```text
OLD_RENDER_FFMPEG_EXECUTION_AUTHORITY_COUNT=0
OLD_RENDER_DIRECT_FFMPEG_PROCESS_INVOCATION_COUNT=0
OLD_RENDER_FFMPEG_COMMAND_BUILDING_AUTHORITY_COUNT=0
RENDER_TO_CONCRETE_FFMPEG_DEPENDENCY_COUNT=0
CORE_TO_CONCRETE_FFMPEG_DEPENDENCY_COUNT=0
LEGACY_FFMPEG_FALLBACK_COUNT=0
FFMPEG_COMPATIBILITY_WRAPPER_COUNT=0
DUAL_FFMPEG_EXECUTION_AUTHORITY_COUNT=0
LEGACY_DIRECT_FFMPEG_APPLICATION_SERVICE_COUNT=0
UNCLASSIFIED_FFMPEG_AUTHORITY_SURFACES=0
```

`scripts/test_phase19_clean_forward_guards.py` proves both the repository pass and a mutation RED in which a render-side `new ProcessBuilder("ffmpeg", ...)` is rejected with count 1.

## Real PF4J typed provider plugin

`ffmpeg-provider-module` produces the independently distributable, reproducible `ffmpeg-provider-plugin-1.0.0.jar`. It contains:

- PF4J lifecycle class `FfmpegProviderPlugin`;
- `plugin.properties` and matching manifest attributes;
- indexed `@Extension` `FfmpegProviderPluginContribution`;
- the existing bounded provider implementation and runtime binding factory.

The contribution contract and the single PF4J host runtime live in the focused `provider-plugin-runtime-module`, above the frozen worker/extension boundary. The module depends downward on `extension-module`, `worker-fabric-module`, `media-execution-plan-module`, `sandbox-isolation-module`, and PF4J, with no concrete FFmpeg dependency. The contract exposes plugin identity/version, platform `PluginDescriptor`, provider descriptor, execution contract, capability profile, runtime support requirement, exact provider binding pin, and the bounded runtime binding factory. It exposes no JSON invocation, lifecycle authority, repositories, raw shell, or artifact authority.

`ProviderPluginHost` is the one PF4J loader. It uses `DefaultPluginManager.loadPlugins()`, `startPlugins()`, and typed `getExtensions()`, validates PF4J/platform/contribution identities and provider metadata, registers the platform descriptor into `PluginRegistryImpl`, and registers the typed contribution into the separate `ProviderPluginCatalog`. Invalid descriptors, duplicate plugin ID/version, duplicate binding pins, or inconsistent metadata fail closed. Stop, unload, disable, load failure, and host close use PF4J lifecycle operations and remove active typed catalog contributions.

Lifecycle support is `PARTIAL_WITH_JUSTIFICATION`: PF4J stop/disable/unload and typed `ProviderPluginCatalog` removal are supported, while `PluginRegistryImpl` remains the frozen `STARTUP_REGISTRATION` descriptor authority and intentionally exposes no unregister or hot-reload API. Its descriptor entries are startup metadata retained until process end; this phase does not claim full descriptor unload. `FfmpegRuntimeProbe` remains a separate availability observation and grants no scheduling eligibility.

`PluginDescriptorValidator` recognizes `ExecutableTask` truthfully; the FFmpeg descriptor does not claim `RenderExecutionPlan`.

## Dual distribution

`platform-distribution` has no source compile dependency on `ffmpeg-provider-module`. Its only concrete-provider relation is a Gradle producer-JAR artifact input.

- modular producer task: `:platform-distribution:stageModularDistribution`;
- modular archive task: `:platform-distribution:modularDistribution`;
- modular layout: `platform-distribution/build/distributions/modular/media-platform-launcher.jar` plus `plugins/ffmpeg-provider-plugin-1.0.0.jar`;
- all-in-one task: `:platform-distribution:allInOneJar`;
- all-in-one path: `platform-distribution/build/libs/media-platform-all-in-one.jar`;
- nested path: `embedded-plugins/ffmpeg-provider-plugin-1.0.0.jar`;
- equivalence task: `:platform-distribution:verifyDualDistributionPluginDigest`.

`EmbeddedPluginExtractor` reads the one nested plugin entry, rejects unsafe/cardinality-invalid input, writes to a controlled plugin directory, and compares the nested and extracted SHA-256 before invoking the same `ProviderPluginHost`. The launcher owns that embedded-mode temporary directory and removes it after the host closes (and also after extraction failure). No static concrete provider construction or classpath registration bypass exists.

All archive tasks disable preserved timestamps and enable reproducible file order. The provider JAR is copied unchanged; it is not transformed, shaded, or duplicated under a second name.

## Conformance evidence

The real distribution test builds the producer plugin JAR, loads the modular copy from an external filesystem directory with PF4J, extracts the all-in-one nested bytes, then loads that extracted JAR with the same host. It asserts identical plugin/provider identities and every exposed contract. Both modes execute the same real `media.transcode` task through the discovered contribution, provider-native runtime binding, bubblewrap sandbox, staging, platform artifact commit, reuse publication, and completion.

The success outputs and content digests are identical. Non-zero media failure is `PROCESS_NONZERO_EXIT` in both modes. Pre-cancellation is `PROCESS_CANCELLED` in both modes. Both failure paths assert zero artifact commits, completions, and reuse publications. Discovery, PF4J load, and the media execution path use no mocks.

Evidence derived during local execution:

```text
:platform-distribution:test --tests DualDistributionPluginConformanceTest = PASS
:platform-distribution:verifyDualDistributionPluginDigest = PASS
FFMPEG_PROVIDER_PLUGIN_SHA256=df496276e7a087431d9e5ded07163d92d2ccacaede2c0250fb9f8d9ea0319c30
```

Hermes completed the authoritative final local validation after diff review. The
full serial `test --rerun-tasks` execution passed with 8,138 tests, zero failures,
zero errors, 32 skipped tests, and 200 executed Gradle tasks. PFIRR1 remediation,
PFIRR1 fail-closed negative proofs, `platform-app:bootJar`, frontend lint/test/build,
GitOps staging/production checks, FAOF-2 Lean/Coq validation, the runtime image
build, architecture drift, and the clean-forward guards also passed.

The classifier-required Semgrep command exits 2 on both this C3 delta and an
isolated exact-base `c058e187cfbb2fdb8037aca21ae333a7df27a4bb` worktree with the
same two inherited pattern parse errors and the same `DevAuthController` finding.
C3 changes neither `.semgrep/**` nor `DevAuthController`; introduced Semgrep delta
is zero. Governance decision
`EXACT_BASE_INHERITED_GATE_FAILURE_MAY_BE_ACCEPTED_FOR_BOUNDED_IMPLEMENTATION_CANDIDATE_FREEZE_V1`
authorizes this implementation-candidate freeze only. It does not authorize a
final Phase 19 candidate, Phase 19 closure, canonical-main integration, disabling
Semgrep, or leaving the inherited gate uncorrected.

Final machine-readable JUnit arithmetic:

| Module | Tests | Passed | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|---:|
| `provider-plugin-runtime-module` | 4 | 4 | 0 | 0 | 0 |
| `ffmpeg-provider-module` | 11 | 11 | 0 | 0 | 0 |
| `extension-module` | 260 | 260 | 0 | 0 | 0 |
| `worker-fabric-module` | 318 | 318 | 0 | 0 | 0 |
| `sandbox-isolation-module` | 37 | 37 | 0 | 0 | 0 |
| `platform-distribution` | 3 | 3 | 0 | 0 | 0 |
| **Total** | **633** | **633** | **0** | **0** | **0** |

Final auxiliary gates:

```text
phase19 clean-forward repository guard = PASS
phase19 clean-forward mutation tests = PASS (2/2)
architecture drift = PASS
Phase 17 append-forward ledger RED matrix = PASS (44 mutations)
change-impact classifier RED matrix = PASS (19 cases; 11 workflow, 7 governance, 1 classifier mutations)
modular launcher process smoke = PASS
all-in-one launcher process smoke = PASS
Gradle distribution build/digest equivalence = PASS
diff whitespace/error check = PASS
```

The targeted module aggregate is `633 = 4 + 11 + 260 + 318 + 37 + 3`; passed plus failures plus errors plus skipped equals the total for every module and for the aggregate.

## C2 POC status

The C2 disposable development-machine runner result is evidence-derived POC material only, recorded in `roadmap-22-phase-19-development-machine-disposable-runner-poc.md`. It did not register a GitHub runner and does not close Phase 19.
