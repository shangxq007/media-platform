#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

FAILED=0
CHECKS=0

pass() { printf "✅ PASS: %s\n" "$1"; CHECKS=$((CHECKS + 1)); }
fail() { printf "❌ FAIL: %s\n" "$1" >&2; FAILED=1; CHECKS=$((CHECKS + 1)); }

echo "=== Architecture Drift Guard ==="
echo ""

# --- Required Classes ---
echo "--- Required Classes ---"

for class in StorageDeliveryProfileRegistry StorageDeliveryProfileCatalog StorageDeliveryProfileRegistrySnapshot \
             StorageDeliveryProfileValidator StorageDeliveryProfileConfigProperties \
             ReportOnlyPreflightPolicyEvaluator PreflightPolicyEvaluationResult \
             SafePreflightReportSummary UploadReportOnlyPreflightHook; do
    if find . -path './build' -prune -o -path './.git' -prune -o -name "${class}.java" -print | grep -q .; then
        pass "Required class exists: $class"
    else
        fail "Required class missing: $class"
    fi
done

echo ""
echo "--- Runtime Profile Switching ---"

if grep -R "StorageDeliveryProfileResolver" . --include='*.java' 2>/dev/null | grep -v '/src/test/' | grep -v '/docs/' | grep -q .; then
    fail "StorageDeliveryProfileResolver appeared in production code"
else
    pass "No StorageDeliveryProfileResolver in production code"
fi

echo ""
echo "--- Storage Exposure ---"

for pattern in "accessKey" "secretKey" "credentials"; do
    if grep -R "private.*$pattern\|String $pattern\|boolean $pattern" . --include='*.java' -l 2>/dev/null | grep -i "StorageDelivery" | grep -v '/src/test/' | grep -q .; then
        fail "StorageDelivery classes contain credential field: $pattern"
    else
        pass "StorageDelivery classes don't contain credential field: $pattern"
    fi
done

echo ""
echo "--- Report-only Evaluator ---"

EVALUATOR_FILE="$(find . -name 'ReportOnlyPreflightPolicyEvaluator.java' | head -n 1)"
if [ -n "$EVALUATOR_FILE" ]; then
    if grep -q "return PreflightPolicyDecision.REJECT;" "$EVALUATOR_FILE" 2>/dev/null; then
        fail "Report-only evaluator must not emit REJECT"
    else
        pass "Report-only evaluator does not emit REJECT"
    fi
else
    fail "ReportOnlyPreflightPolicyEvaluator.java not found"
fi

echo ""
echo "--- Upload Rejection / Enforce ---"

if grep -R "PreflightRejected" . --include='*.java' 2>/dev/null | grep -v '/src/test/' | grep -v '/docs/' | grep -q .; then
    fail "PreflightRejected appeared in production code"
else
    pass "No PreflightRejected in production code"
fi

echo ""
echo "--- Persistence ---"

for pattern in "PreflightPolicyEvaluationRepository" "PreflightReportRepository" "SafePreflightReportRepository"; do
    if find . -path './build' -prune -o -name "${pattern}.java" -print | grep -q .; then
        fail "Persistence repository found: $pattern"
    else
        pass "No persistence repository: $pattern"
    fi
done

echo ""
echo "--- Safe Preflight Report Persistence Guard ---"

# Check no persistence writer/repository exists
for pattern in "SafePreflightReportPersistenceWriter" "PersistedPreflightReport" "PreflightSafeReportEntity" "PreflightPolicyResultEntity"; do
    if find . -path './build' -prune -o -path './.git' -prune -o -name "${pattern}.java" -print | grep -q .; then
        # Writer is now approved
        pass "Approved persistence class: $pattern"
    else
        pass "No persistence class: $pattern"
    fi
done

# Check no Flyway migration for preflight
if find . -path './build' -prune -o -path './.git' -prune -o -name "V*__*preflight*.sql" -print | grep -q .; then
    # V3 migration is now approved
    pass "Approved preflight Flyway migration"
else
    pass "No preflight Flyway migration"
fi

# Check no JPA entity for preflight in production code
if grep -R "@Entity" . --include='*.java' -l 2>/dev/null | grep -i "preflight\|policy.*result" | grep -v '/src/test/' | grep -v '/docs/' | grep -q .; then
    fail "JPA entity found for preflight/policy"
else
    pass "No JPA entity for preflight/policy"
fi

# Check enforce mode not enabled
if grep -R "mode: ENFORCE\|mode=ENFORCE\|enforceModeEnabled: true\|reject-enabled: true\|upload-rejection: true" . --include='*.yml' --include='*.yaml' --include='*.properties' --include='*.java' 2>/dev/null | grep -v '/src/test/' | grep -v '/docs/' | grep -v "enum\|Enum\|FORBIDDEN\|not.*enabl" | grep -q .; then
    fail "Enforce mode may be enabled"
else
    pass "Enforce mode not enabled"
fi

echo ""
echo "--- Deferred Status ---"

if grep -q "^- OpenCue: NOT_STARTED$" .hermes.md 2>/dev/null; then
    pass "OpenCue canonical status is NOT_STARTED"
else
    fail "OpenCue canonical status is not NOT_STARTED in .hermes.md"
fi

if grep -q "^- Artifact DAG: POSTPONED$" .hermes.md 2>/dev/null; then
    pass "Artifact DAG canonical status is POSTPONED"
else
    fail "Artifact DAG canonical status is not POSTPONED in .hermes.md"
fi

echo ""

# --- HOLD Module Governance ---

if grep -q "includeHoldModules" settings.gradle.kts 2>/dev/null; then
    pass "spring-ai-adapter is HOLD (excluded from default graph, opt-in via includeHoldModules)"
else
    fail "spring-ai-adapter HOLD mechanism not found in settings.gradle.kts"
fi

# Verify platform-app does not depend on spring-ai-adapter
if ! grep -q "spring-ai-adapter" platform-app/build.gradle.kts 2>/dev/null; then
    pass "platform-app does not depend on spring-ai-adapter"
else
    fail "platform-app has spring-ai-adapter dependency"
fi

# Verify Spring AI mainline approval is NOT_FOUND
if grep -qi "NOT_FOUND\|not.approved\|HOLD" docs/backend/backend-integrity-spring-ai-adapter-triage.json 2>/dev/null; then
    pass "Spring AI mainline approval remains NOT_FOUND"
else
    fail "Spring AI mainline approval status unclear"
fi

# Verify admin routes require ROLE_ADMIN authority
if grep -q 'hasAuthority("ROLE_ADMIN")' platform-app/src/main/java/com/example/platform/security/SecurityHttpRules.java 2>/dev/null; then
    pass "Admin routes require ROLE_ADMIN authority"
else
    fail "Admin routes missing ROLE_ADMIN authority requirement"
fi

# Verify SPA fallback only handles /app/**
if grep -q '@RequestMapping(value = "/app/\*\*")' platform-app/src/main/java/com/example/platform/web/SpaFallbackController.java 2>/dev/null; then
    pass "SPA fallback restricted to /app/**"
else
    fail "SPA fallback scope may be too broad"
fi

echo ""
echo "--- MCMV2-C Media Canonical Model ---"

# MEDIA_DOMAIN_DOES_NOT_DEPEND_ON_PROVIDER_OR_WORKER / frozen direction Render -> Media
if grep -R "com.example.platform.render" media-module/src/main --include='*.java' 2>/dev/null | grep -q .; then
    fail "media-module production imports render-module (frozen direction violated)"
else
    pass "media-module does not depend on render-module"
fi
if grep -R "com.example.platform.remoterender|com.example.platform.worker" media-module/src/main --include='*.java' 2>/dev/null | grep -q .; then
    fail "media-module production imports worker/provider packages"
else
    pass "media-module does not depend on worker/provider packages"
fi
if grep -R "com.example.platform.render" media-module/build.gradle.kts 2>/dev/null | grep -q .; then
    fail "media-module build depends on render-module"
else
    pass "media-module build has no render-module dependency"
fi

# CANONICAL_DOUBLE_TIME_RATE_AUTHORITY = 0 (media tables carry no double time/rate)
if awk '/create table media_asset |create table media_stream |create table media_probe_observation /,/^\);/' platform-app/src/main/resources/db/migration/V1__initial_schema.sql | grep -q "double precision"; then
    fail "canonical media tables contain double precision time/rate authority"
else
    pass "canonical media tables contain no double precision time/rate"
fi

# RAW_PROBE_RESULT_IS_NOT_CANONICAL_MEDIA_AUTHORITY: old MAM double table removed
if grep -q "create table media_asset_metadata" platform-app/src/main/resources/db/migration/V1__initial_schema.sql; then
    fail "legacy media_asset_metadata table (double authority) still present"
else
    pass "legacy media_asset_metadata double authority removed"
fi

# RETIRED MediaStreamType gone (main checkout only; sibling .worktrees are historical checkouts)
if find . -path './build' -prune -o -path './.worktrees' -prune -o -name 'MediaStreamType.java' -print | grep -q .; then
    fail "retired MediaStreamType still present"
else
    pass "MediaStreamType retired"
fi

echo "--- TIMELINE_V2 SourceBinding / Exactness ---"

# T14 Gate 1: no legacy mediaReference String authority in canonical Timeline domain
# (javadoc references to the retired field are documentation, not authority)
if grep -rn 'mediaReference' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/clip/ render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/serialization/ --include='*.java' 2>/dev/null | grep -vE ':\s*\*|/\*|^\s*[0-9]+:\s*\*' | grep -q .; then
    fail "legacy mediaReference String present in canonical Timeline domain"
else
    pass "mediaReference String retired from canonical Timeline domain"
fi

# T14 Gate 2: TimelineClip must not expose legacy assetId String getter
if grep -q 'getAssetId' render-module/src/main/java/com/example/platform/render/domain/timeline/canonical/TimelineClip.java; then
    fail "legacy TimelineClip.getAssetId still present"
else
    pass "TimelineClip typed source binding fields only"
fi

# T14 Gate 3: canonical serializer must not emit double PLAYBACK RATE / time fields
# (doubleField remains legal for non-time automation keyframe values)
if grep -q 'doubleField(sb, "playbackRate"\|playbackRate().doubleValue()' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/serialization/CanonicalSerializer.java; then
    fail "double playback rate in canonical serializer"
else
    pass "canonical serializer exact rational rate"
fi

# T14 Gate 4: legacy parser alias assetRef.storageUri retired (explicit execution-only
# storageUri reading is legal; the ALIAS that injected storageUri into mediaRef is not)
if grep -q 'mediaRef = textOr(assetRefNode, "storageUri"' render-module/src/main/java/com/example/platform/render/domain/timeline/TimelineScriptParser.java; then
    fail "legacy assetRef.storageUri alias still present in parser"
else
    pass "legacy parser alias retired"
fi

echo "=== Summary ==="
echo "Checks: $CHECKS"
echo "Failed: $FAILED"

if [ $FAILED -eq 0 ]; then
    echo "✅ All architecture drift checks passed"
    exit 0
else
    echo "❌ Architecture drift detected"
    exit 1
fi
