#!/usr/bin/env bash
# ROADMAP20_COMMITTED_SOURCE_COMPLETENESS_GATE_V1 (§28/§29)
# Fails if any critical Roadmap #20 source/test file required by the closure
# is NOT tracked by git (i.e. exists only in a dirty worktree / ignored path).
set -u
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

CRITICAL_FILES=(
  # version model / context (B1 — the exact files that were omitted before)
  timeline-module/src/main/java/com/example/platform/timeline/version/TimelineRevision.java
  timeline-module/src/main/java/com/example/platform/timeline/version/TimelineRevisionSemanticContext.java
  timeline-module/src/main/java/com/example/platform/timeline/version/TimelineRevisionSemanticContextJsonCodec.java
  timeline-module/src/main/java/com/example/platform/timeline/version/TimelineRevisionSemanticContextStore.java
  # semantic context adapter
  timeline-module/src/main/java/com/example/platform/timeline/adapter/JdbcTimelineRevisionSemanticContextStore.java
  # effect authority / model
  timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/EffectSemanticSnapshotAuthority.java
  timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/EffectSemanticSnapshotAuthorityInternal.java
  timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/EffectSemanticSnapshot.java
  timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/EffectSemanticSnapshotReference.java
  timeline-module/src/main/java/com/example/platform/timeline/semantics/effect/EffectSemanticSnapshotStore.java
  # durable adapters
  timeline-module/src/main/java/com/example/platform/timeline/adapter/JdbcEffectSemanticSnapshotStore.java
  timeline-module/src/main/java/com/example/platform/timeline/adapter/JdbcEffectDefinitionVersionRegistry.java
  # canonical writer + ports (B2)
  timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionSaveService.java
  timeline-module/src/main/java/com/example/platform/timeline/app/TimelineRevisionPersistencePort.java
  timeline-module/src/main/java/com/example/platform/timeline/app/DefaultTimelineRevisionPersistence.java
  timeline-module/src/main/java/com/example/platform/timeline/app/HeadUpdatePort.java
  timeline-module/src/main/java/com/example/platform/timeline/app/ProductCurrentRevisionHeadUpdateAdapter.java
  # render verification
  render-module/src/main/java/com/example/platform/render/domain/renderplan/VerifiedEffectSemanticSnapshotFactory.java
  render-module/src/main/java/com/example/platform/render/domain/renderplan/VerifiedRenderSemanticSnapshotFactory.java
  # production wiring
  timeline-module/src/main/java/com/example/platform/timeline/app/Roadmap20EffectAuthorityConfiguration.java
  # closure tests
  render-module/src/test/java/com/example/platform/render/app/timeline/Roadmap20E2ESaveReloadRenderIntegrationTest.java
  render-module/src/test/java/com/example/platform/render/app/timeline/Roadmap20TransactionAtomicityTest.java
  render-module/src/test/java/com/example/platform/render/app/timeline/Roadmap20SnapshotOwnershipAndCorruptionTest.java
  render-module/src/test/java/com/example/platform/render/app/timeline/Roadmap20DefinitionConcurrencyAndCorruptionTest.java
  render-module/src/test/java/com/example/platform/render/app/timeline/Roadmap20RevisionContextOwnershipAndRestoreTest.java
  render-module/src/test/java/com/example/platform/render/domain/renderplan/Roadmap20AIIntegrationAcceptanceTest.java
  render-module/src/test/java/com/example/platform/render/domain/renderplan/Roadmap20CleanForwardGuardTest.java
  render-module/src/test/java/com/example/platform/render/domain/renderplan/Roadmap20MediaTypeAndParameterValidationTest.java
  platform-app/src/test/java/com/example/platform/Roadmap20ProductionWiringTest.java
)

FAILED=0
for f in "${CRITICAL_FILES[@]}"; do
  if [ ! -f "$f" ]; then
    echo "FAIL: critical file MISSING from repository: $f"
    FAILED=1
    continue
  fi
  if ! git ls-files --error-unmatch "$f" >/dev/null 2>&1; then
    echo "FAIL: critical file NOT TRACKED by git (worktree-only source): $f"
    FAILED=1
  fi
done

# any required build/test source left untracked?
UNTRACKED_JAVA=$(git status --porcelain --untracked-files=all | grep -E '^\?\?' | grep -E '\.(java|kt|kts|xml|properties|sql|json)$' | grep -v '^?? \.claude/' || true)
if [ -n "$UNTRACKED_JAVA" ]; then
  echo "FAIL: untracked source/build files present (exact-SHA FCV input must equal committed tree):"
  echo "$UNTRACKED_JAVA"
  FAILED=1
fi

if [ "$FAILED" -ne 0 ]; then
  echo "ROADMAP20_COMMITTED_SOURCE_COMPLETENESS_GATE_V1: FAIL"
  exit 1
fi
echo "ROADMAP20_COMMITTED_SOURCE_COMPLETENESS_GATE_V1: PASS ($(git ls-files | wc -l) tracked files)"
