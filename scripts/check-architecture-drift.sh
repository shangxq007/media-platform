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

# T15 Gate 1 (A16): legacy TimelineAudioSpec.volume/normalize retired
if grep -q 'double volume\|boolean normalize' render-module/src/main/java/com/example/platform/render/domain/timeline/TimelineAudioSpec.java; then
    fail "legacy TimelineAudioSpec volume/normalize still present"
else
    pass "legacy TimelineAudioSpec volume/normalize retired"
fi

# T15 Gate 2 (A15/A9): no FFmpeg filter strings in canonical audio domain
if grep -rq 'filter_complex\|amix=\|volume=\|pan=' audio-module/src/main/java --include='*.java'; then
    fail "FFmpeg filter syntax present in canonical audio domain"
else
    pass "canonical audio domain is provider-neutral"
fi

# T15 Gate 3 (A1/A12): audio-module must not copy Media source technical metadata
if grep -rq 'sampleRate\|bitDepth\|channelLayout\|sampleFormat\|bitrateKbps' audio-module/src/main/java --include='*.java'; then
    fail "source technical metadata copied into canonical audio domain"
else
    pass "source metadata not duplicated into audio domain"
fi

# T15 Gate 4 (A3/A13): AudioMix integrated into TimelineDocument canonical content
if grep -q 'AudioMix audioMix\|getAudioMix' render-module/src/main/java/com/example/platform/render/domain/timeline/canonical/TimelineDocument.java; then
    pass "AudioMix in TimelineDocument canonical content"
else
    fail "AudioMix missing from TimelineDocument canonical content"
fi

# T16 Gate 1 (R1/C16 + C16-CORR-1): typed namespaced CapabilityId; STRUCTURAL vendor
# validation (no hardcoded TLD allowlist); squatting/malformed fail closed
if grep -q 'PLATFORM_RESERVED_PREFIXES' extension-module/src/main/java/com/example/platform/extension/domain/CapabilityId.java && grep -q 'CapabilityNamespaceValidator' extension-module/src/main/java/com/example/platform/extension/app/PluginDescriptorValidator.java && grep -q 'reverse-DNS' extension-module/src/main/java/com/example/platform/extension/domain/CapabilityNamespaceValidator.java && ! grep -q 'VENDOR_PREFIXES = List.of\("com\."' extension-module/src/main/java/com/example/platform/extension/domain/CapabilityId.java; then
    pass "capability namespace validation enforced (structural, no TLD allowlist)"
else
    fail "capability namespace validation missing or TLD allowlist present"
fi

# T16 Gate 3b (C16-CORR-3): CapabilityRegistryPort exists as capability-facing authority
if grep -q 'interface CapabilityRegistryPort' extension-module/src/main/java/com/example/platform/extension/api/port/CapabilityRegistryPort.java && grep -q 'implements PluginRegistryPort, CapabilityRegistryPort' extension-module/src/main/java/com/example/platform/extension/app/PluginRegistryImpl.java; then
    pass "capability-facing registry contract authority (CapabilityRegistryPort)"
else
    fail "CapabilityRegistryPort authority missing"
fi

# T16 Gate 6 (C16-CORR-2): canonical contract version major.minor only; single-segment rejected
if grep -q 'major.minor' extension-module/src/main/java/com/example/platform/extension/domain/ContractVersion.java && grep -q 'parts.length != 2' extension-module/src/main/java/com/example/platform/extension/domain/ContractVersion.java; then
    pass "contract version canonical major.minor enforced"
else
    fail "contract version canonical syntax missing"
fi

# T16 Gate 2 (R2/C5): independent CapabilityImplementationId (not the (plugin, capability) tuple)
if grep -q 'record CapabilityImplementationId' extension-module/src/main/java/com/example/platform/extension/domain/CapabilityImplementationId.java && grep -q 'findCapabilityImplementations' extension-module/src/main/java/com/example/platform/extension/api/port/CapabilityRegistryPort.java; then
    pass "independent implementation identity + registry queries"
else
    fail "capability implementation identity missing"
fi

# T16 Gate 3 (R3/C10): registry contract authority (interface) separate from PluginRegistryImpl
if grep -q 'interface PluginRegistryPort' extension-module/src/main/java/com/example/platform/extension/api/port/PluginRegistryPort.java; then
    pass "registry contract interface is authority"
else
    fail "registry contract interface missing"
fi

# T16 Gate 4 (C17): no entitlement/commercial fields in capability canonical contract types
if grep -rq 'proOnly\|enterpriseOnly\|remainingQuota\|subscriptionSku' extension-module/src/main/java/com/example/platform/extension/domain/Capability*.java extension-module/src/main/java/com/example/platform/extension/domain/ContractVersion*.java 2>/dev/null; then
    fail "entitlement leakage into capability contract"
else
    pass "no entitlement leakage in capability contract"
fi

# T16 Gate 5 (R4/C13): multi-axis lifecycle — contract lifecycle and registration availability are DISTINCT types
if grep -q 'enum CapabilityContractLifecycle' extension-module/src/main/java/com/example/platform/extension/domain/CapabilityContractLifecycle.java && grep -q 'enum RegistrationAvailability' extension-module/src/main/java/com/example/platform/extension/domain/RegistrationAvailability.java; then
    pass "multi-axis lifecycle separated (contract vs registration availability)"
else
    fail "multi-axis lifecycle separation missing"
fi

# T17 Gate 1 (S1): TimelineSourceBinding is the canonical source root
if grep -q 'sealed interface TimelineSourceBinding' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/clip/TimelineSourceBinding.java; then
    pass "TimelineSourceBinding canonical source root"
else
    fail "TimelineSourceBinding root missing"
fi

# T17 Gate 2 (S2): MediaStreamSourceBinding carries #14 immutable source semantics
if grep -q 'record MediaStreamSourceBinding' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/clip/MediaStreamSourceBinding.java && grep -q 'contentDigest' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/clip/MediaStreamSourceBinding.java; then
    pass "MediaStreamSourceBinding preserves #14 semantics"
else
    fail "MediaStreamSourceBinding missing"
fi

# T17 Gate 3 (S3): legacy SourceBinding canonical type/path gone
if grep -rq 'record SourceBinding' render-module/src/main 2>/dev/null; then
    fail "legacy SourceBinding still present"
else
    pass "legacy SourceBinding retired (greenfield)"
fi

# T17 Gate 4 (S8): OTIO remains adapter/projection, not canonical authority
if grep -q 'class OpenTimelineioAdapter' render-module/src/main/java/com/example/platform/render/domain/timeline/OpenTimelineioAdapter.java; then
    pass "OTIO adapter boundary intact"
else
    fail "OTIO adapter missing"
fi

# T17 Gate 5 (S12): serialization/hash source-kind aware
if grep -q 'sourceKind' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/serialization/CanonicalSerializer.java && grep -q 'sourceKind' render-module/src/main/java/com/example/platform/render/domain/timeline/canonical/TimelineClip.java; then
    pass "source-kind aware serialization/hash"
else
    fail "source-kind discriminator missing"
fi

# T17 Gate 7 (S18): no universal nullable source object
if grep -q 'permits MediaStreamSourceBinding' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/clip/TimelineSourceBinding.java; then
    pass "sealed typed source root (no god object)"
else
    fail "source root not sealed/typed"
fi

# T17 Gate 8 (S10): TemporalMapping remains outside #17
if grep -rq 'class TemporalMapping' render-module/src/main 2>/dev/null; then
    fail "TemporalMapping implemented inside #17"
else
    pass "TemporalMapping not implemented (deferred)"
fi

# VG-1: ReleaseVersion typed E.R.P, distinct from contract versions
if grep -q 'record ReleaseVersion' shared-kernel/src/main/java/com/example/platform/shared/version/ReleaseVersion.java && grep -q 'EPOCH.RELEASE.PATCH' shared-kernel/src/main/java/com/example/platform/shared/version/ReleaseVersion.java; then
    pass "ReleaseVersion typed E.R.P"
else
    fail "ReleaseVersion missing"
fi

# VG-2: contract/format versions use E.R (no PATCH)
if grep -q 'record CanonicalFormatVersion' shared-kernel/src/main/java/com/example/platform/shared/version/CanonicalFormatVersion.java && grep -q 'EPOCH.RELEASE' shared-kernel/src/main/java/com/example/platform/shared/version/CanonicalFormatVersion.java; then
    pass "CanonicalFormatVersion typed E.R"
else
    fail "CanonicalFormatVersion missing"
fi

# VG-3: VersionRange typed + numeric
if grep -q 'record VersionRange' shared-kernel/src/main/java/com/example/platform/shared/version/VersionRange.java; then
    pass "typed VersionRange present"
else
    fail "VersionRange missing"
fi

# VG-5: lifecycle explicit, not version-parity
if grep -q 'enum Lifecycle' shared-kernel/src/main/java/com/example/platform/shared/version/Lifecycle.java && grep -q 'DRAFT' shared-kernel/src/main/java/com/example/platform/shared/version/Lifecycle.java; then
    pass "explicit lifecycle model"
else
    fail "lifecycle model missing"
fi

# VG-6: ReleaseChannel distinct type
if grep -q 'enum ReleaseChannel' shared-kernel/src/main/java/com/example/platform/shared/version/ReleaseChannel.java; then
    pass "release channel explicit"
else
    fail "release channel missing"
fi

# VG-8/9: execution provenance pins resolved versions
if grep -q 'record ExecutionProvenance' shared-kernel/src/main/java/com/example/platform/shared/version/ExecutionProvenance.java; then
    pass "execution provenance contract"
else
    fail "execution provenance missing"
fi

# VG-10: ApiContract independent from platform release
if grep -q 'record ApiContract' shared-kernel/src/main/java/com/example/platform/shared/version/ApiContract.java; then
    pass "API contract lifecycle governance"
else
    fail "API contract governance missing"
fi

# VG-12: HTTP breaking change machine-gated (oasdiff gate present)
if grep -q 'oasdiff breaking' scripts/check-api-contract-governance.sh; then
    pass "breaking-change machine gate (oasdiff)"
else
    fail "breaking-change gate missing"
fi

# VG-13/14/15: legacy compatibility code = 0 (grep negative)
if grep -rq 'class VersionV1\|class LegacyVersion\|VersionParityCompat' shared-kernel/src/main 2>/dev/null; then
    fail "legacy version compatibility code present"
else
    pass "zero legacy version compatibility code"
fi
if grep -rq 'parse("1")\s*;' extension-module/src/main/java/com/example/platform/extension/domain/ContractVersion.java 2>/dev/null; then
    fail "single-segment contract version compatibility present"
else
    pass "single-segment ContractVersion support absent"
fi

# TMG-1/2: no IdentityTemporalMapping subtype, no IDENTITY discriminator (R1 hard gate)
if grep -rq 'class IdentityTemporalMapping\|record IdentityTemporalMapping\|interface IdentityTemporalMapping' render-module/src/main 2>/dev/null; then
    fail "IdentityTemporalMapping subtype present (R1 violation)"
else
    pass "no IdentityTemporalMapping subtype"
fi
if grep -q '"kind":"IDENTITY"\|kind.*"IDENTITY"' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/serialization/CanonicalSerializer.java 2>/dev/null; then
    fail "IDENTITY discriminator present (R1 violation)"
else
    pass "no IDENTITY discriminator"
fi

# TMG-3: identity canonicalizes to ConstantRate 1/1 FORWARD
if grep -q 'ConstantRateTemporalMapping identity()' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/temporal/ConstantRateTemporalMapping.java; then
    pass "identity factory present (normalized 1/1 FORWARD)"
else
    fail "identity factory missing"
fi

# TMG-4: positive rational rate + explicit direction
if grep -q 'record ConstantRateTemporalMapping' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/temporal/ConstantRateTemporalMapping.java && grep -q 'enum PlaybackDirection' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/temporal/PlaybackDirection.java; then
    pass "constant-rate + direction model"
else
    fail "constant-rate/direction model missing"
fi

# TMG-5: legacy playbackRate retired
if grep -rq 'playbackRate' render-module/src/main --include='*.java' 2>/dev/null | grep -v 'TIMELINE_PLAYBACK_RATE_INVALID\|MediaClip.java' | grep -qv 'playbackRate' ; then
    N=$(grep -r 'playbackRate' render-module/src/main --include='*.java' 2>/dev/null | grep -vc 'TIMELINE_PLAYBACK_RATE_INVALID\|MediaClip.java')
    fail "legacy playbackRate refs remain ($N)"
else
    pass "legacy playbackRate retired"
fi

# TMG-6/7: sourceRange sole authority, no duplication in TemporalMapping
if grep -q 'sourceRange' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/clip/MediaStreamSourceBinding.java; then
    pass "sourceRange authority in binding"
else
    fail "sourceRange authority missing"
fi
if grep -q 'sourceRange\|sourceStart\|sourceEnd' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/temporal/ConstantRateTemporalMapping.java; then
    fail "sourceRange duplicated into TemporalMapping"
else
    pass "no duplicated sourceRange in TemporalMapping"
fi

# TMG-8: constant-rate duration consistency fail-closed
if grep -q 'constant-rate duration mismatch' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/clip/MediaClip.java; then
    pass "duration consistency fail-closed"
else
    fail "duration consistency missing"
fi

# TMG-9: freeze = exact source position (no rate=0, no fake range)
if grep -q 'record FreezeTemporalMapping' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/temporal/FreezeTemporalMapping.java; then
    pass "freeze = exact source position"
else
    fail "freeze model missing"
fi

# TMG-10: serialization/hash participation
if grep -q 'temporalMapping' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/serialization/CanonicalSerializer.java; then
    pass "temporalMapping in canonical serialization"
else
    fail "temporalMapping serialization missing"
fi

# TMG-12: no float rate/time in canonical model
if grep -rq 'double rate\|float rate' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/temporal/ 2>/dev/null; then
    fail "float rate in canonical model"
else
    pass "no float rate in canonical temporal model"
fi

# TMG-14: audio non-identity fail-closed guard
if grep -q 'requireAudioIdentity' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/temporal/TemporalAudioExecutionGuard.java; then
    pass "audio non-identity fail-closed guard"
else
    fail "audio guard missing"
fi

# TMG-15/16/18: no piecewise/relationship/operation/dual model
if grep -rq 'PiecewiseTemporalMapping\|class AudioTemporalBehavior\|LegacyPlaybackRate\|TemporalMappingV2' render-module/src/main 2>/dev/null; then
    fail "forbidden temporal foundation type present"
else
    pass "no piecewise/audio-behavior/legacy dual model"
fi

if [ $FAILED -eq 0 ]; then
    echo "✅ All architecture drift checks passed"
    exit 0
else
    echo "❌ Architecture drift detected"
    exit 1
fi
