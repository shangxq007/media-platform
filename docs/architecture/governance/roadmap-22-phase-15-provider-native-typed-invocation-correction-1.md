# Roadmap #22 Phase 15 — Provider-Native Typed Invocation Correction 1

Mode: APPEND_FORWARD_CORRECTION

ORIGINAL_CANDIDATE_SHA=622c3cdf84d63011aeb12d2daa55804c8787c59b
ARCHITECTURE_REOPEN=NO
CLEAN_FORWARD=YES
REAL_BACKEND_SUBMISSION_TYPED_MODEL=DEFERRED_UNTIL_REAL_CONSUMER

## Finding

The original Phase-15 candidate exposed `BackendSubmissionInvocationSpec` with a string `submissionType` and `Map<String, String> typedFields`. That shape was a generic untyped provider/backend command parameter bag and violated `NO_GENERIC_UNTYPED_COMMAND_OR_PROVIDER_PARAMETER_BAG_V1`.

## Correction

- Delete `BackendSubmissionInvocationSpec` from the production provider-native SPI.
- Remove `InvocationKind.BACKEND_SUBMISSION`.
- Narrow the sealed production `InvocationSpec` root so it permits only `ProcessInvocationSpec`.
- Keep the Phase-15 fixture process-only.
- Guard structurally against restoration of the deleted shape or introduction of generic provider/backend parameter bags, while explicitly retaining `ProcessInvocationSpec.environmentOverrides` as typed process mechanics.

The accepted Phase-15 boundaries and remaining types are otherwise unchanged: `ProviderNativeExecutionPlan`, `PlanLowerer`, `StaticProviderExecutionContext`, `ExecutionCommand`, `RuntimeAdapter`, `RuntimeExecutionContext`, `RuntimeExecutionBundle`, `ProviderNativeExecutionFailure`, `ProviderNativeFailureCode`, and `ProcessInvocationSpec`.

## Disposition

This is a clean forward correction of the Phase-15 candidate, not an architecture reopen. HTTP, gRPC, native-library, generic invocation, and real backend-submission request models are not introduced. Any future backend submission model must be derived from a real consumer and expose concrete typed semantics rather than a generic parameter bag.
