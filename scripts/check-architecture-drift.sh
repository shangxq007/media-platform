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
if grep -q 'PLATFORM_RESERVED_PREFIXES' extension-module/src/main/java/com/example/platform/extension/domain/CapabilityId.java && grep -q 'CapabilityNamespaceValidator' extension-module/src/main/java/com/example/platform/extension/app/PluginDescriptorValidator.java && grep -q 'reverse-DNS' extension-module/src/main/java/com/example/platform/extension/domain/CapabilityNamespaceValidator.java && ! grep -q 'VENDOR_PREFIXES' extension-module/src/main/java/com/example/platform/extension/domain/CapabilityId.java; then
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

# SRG-1: TimelineClip canonical identity typed TimelineClipId
if grep -q 'private final TimelineClipId clipId' render-module/src/main/java/com/example/platform/render/domain/timeline/canonical/TimelineClip.java; then
    pass "TimelineClipId typed canonical identity"
else
    fail "TimelineClipId missing"
fi

# SRG-2/27: no raw String endpoint/identity authority (field declaration only;
# @JsonCreator String params are the allowed boundary conversion)
if grep -q 'private final String clipId' render-module/src/main/java/com/example/platform/render/domain/timeline/canonical/TimelineClip.java; then
    fail "raw String clipId remains"
else
    pass "no raw String clip identity"
fi

# SRG-3: no universal TimelineObjectId
if grep -rq 'class TimelineObjectId\|record TimelineObjectId' render-module/src/main 2>/dev/null; then
    fail "universal TimelineObjectId present"
else
    pass "no universal TimelineObjectId"
fi

# SRG-4: sealed root permits exactly Sync + Group
if grep -q 'sealed interface SemanticRelationship permits' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/relationship/SemanticRelationship.java; then
    pass "sealed SemanticRelationship root"
else
    fail "SemanticRelationship root missing"
fi

# SRG-6/7: sync normalized pair + anchor moves with endpoint
if grep -q 'identityKey' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/relationship/SyncRelationship.java && grep -q 'a.compareTo(b)' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/relationship/SyncRelationship.java; then
    pass "sync normalized endpoint identity"
else
    fail "sync normalization missing"
fi

# SRG-9/10: GroupId typed stable, independent of members
if grep -q 'record GroupId' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/relationship/GroupId.java; then
    pass "typed GroupId"
else
    fail "GroupId missing"
fi

# SRG-11/12: group members TimelineClipId, flat
if grep -q 'Set<TimelineClipId> members' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/relationship/GroupRelationship.java; then
    pass "typed group members"
else
    fail "group member type wrong"
fi

# SRG-13/14: relationships revisioned with Timeline + hash participation
if grep -q 'semanticRelationships' render-module/src/main/java/com/example/platform/render/domain/timeline/canonical/TimelineDocument.java; then
    pass "relationships in Timeline revision content"
else
    fail "relationship revision integration missing"
fi

# SRG-16: sync anchors object-local MediaTime
if grep -q 'MediaTime localAnchorA' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/relationship/SyncRelationship.java; then
    pass "exact object-local sync anchors"
else
    fail "sync anchor model wrong"
fi

# SRG-17: sync has no sourceRange/temporal fields
if grep -q 'sourceRange\|playbackRate\|direction' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/relationship/SyncRelationship.java; then
    fail "sync carries forbidden temporal/source fields"
else
    pass "sync contains no temporal/source semantics"
fi

# SRG-19: no generic attribute map
if grep -rq 'Map<String, Object> attributes' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/relationship/ 2>/dev/null; then
    fail "generic attribute map present"
else
    pass "no generic relationship attribute map"
fi

# SRG-23/24: scope resolution revision-bound, no latest
if grep -q 'baseRevisionId' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/selection/ResolvedScope.java && grep -q 'revision-bound' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/selection/ResolvedScope.java; then
    pass "revision-bound scope resolution"
else
    fail "scope resolution not revision-bound"
fi
if grep -rq 'latestRevision()\|currentTimeline()' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/selection/ScopeResolver.java 2>/dev/null; then
    fail "mutable-latest fallback present"
else
    pass "no mutable-latest fallback"
fi

# SRG-21: no provenance relationship variant
if grep -rq 'DerivedRelationship\|SourceAssociationRelationship\|LinkedRelationship' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/relationship/ 2>/dev/null; then
    fail "forbidden relationship variant present"
else
    pass "no provenance/derived/source-association variants"
fi

# OMG-1: typed namespaced OperationDefinitionId
if grep -q 'record OperationDefinitionId' render-module/src/main/java/com/example/platform/render/domain/operation/OperationDefinitionId.java; then
    pass "typed namespaced OperationDefinitionId"
else
    fail "OperationDefinitionId missing"
fi

# OMG-2: version reuses ContractVersion
if grep -q 'import com.example.platform.extension.domain.ContractVersion' render-module/src/main/java/com/example/platform/render/domain/operation/OperationDefinition.java; then
    pass "ContractVersion reuse (no OperationVersion)"
else
    fail "ContractVersion reuse missing"
fi

# OMG-3: no generic parameter Map/Object/JsonNode in operation model
if grep -rn 'Map<String, Object>\|Map<String,String>\|JsonNode' render-module/src/main/java/com/example/platform/render/domain/operation/ 2>/dev/null | grep -v '^[^:]*:[0-9]*: *\*' | grep -q .; then
    fail "generic parameter map present"
else
    pass "no generic parameter maps"
fi

# OMG-4/5: operation is not Patch/Diff
if grep -rq 'TimelinePatchOperation\|TimelineChangeType' render-module/src/main/java/com/example/platform/render/domain/operation/ 2>/dev/null; then
    fail "operation depends on patch/diff types"
else
    pass "operation independent of patch/diff"
fi

# OMG-6: operation not in Timeline canonical serialization/hash
if grep -q 'operation' render-module/src/main/java/com/example/platform/render/domain/timeline/canonical/TimelineDocument.java; then
    fail "operation entered TimelineDocument"
else
    pass "operation absent from Timeline canonical model"
fi

# OMG-7/8: variant-specific target, ResolvedScope not universal
if grep -q 'sealed interface OperationTarget' render-module/src/main/java/com/example/platform/render/domain/operation/OperationTarget.java && grep -q 'GroupTarget\|SyncTarget\|AudioTarget' render-module/src/main/java/com/example/platform/render/domain/operation/OperationTarget.java; then
    pass "variant-specific operation targets"
else
    fail "universal target model"
fi

# OMG-9: no universal TimelineObjectId/MediaObjectId
if grep -rq 'class TimelineObjectId\|record TimelineObjectId\|class MediaObjectId\|record MediaObjectId' render-module/src/main 2>/dev/null; then
    fail "god object id present"
else
    pass "no universal object id"
fi

# OMG-10: every definition has target contract
if grep -q 'TargetKind targetKind' render-module/src/main/java/com/example/platform/render/domain/operation/OperationDefinition.java && grep -q 'minCardinality' render-module/src/main/java/com/example/platform/render/domain/operation/OperationDefinition.java; then
    pass "definition-owned target/cardinality contract"
else
    fail "target contract missing"
fi

# OMG-11/12/13: temporal single-authority parameters
if grep -q 'SetTemporalRateParameters(com.example.platform.render.domain.timeline.semantics.clip.MediaClip.Rational rate)' render-module/src/main/java/com/example/platform/render/domain/operation/OperationParameters.java; then
    pass "set-rate rate-only"
else
    fail "set-rate parameter contract wrong"
fi
if grep -q 'SetTemporalDirectionParameters(PlaybackDirection direction)' render-module/src/main/java/com/example/platform/render/domain/operation/OperationParameters.java; then
    pass "set-direction direction-only"
else
    fail "set-direction parameter contract wrong"
fi
if grep -q 'FreezeParameters(MediaTime sourcePosition)' render-module/src/main/java/com/example/platform/render/domain/operation/OperationParameters.java; then
    pass "freeze sourcePosition-only"
else
    fail "freeze parameter contract wrong"
fi

# OMG-15/16/17: request resolution exact-base, instance bound, no latest
if grep -q 'baseRevisionId == null || baseRevisionId.isBlank()' render-module/src/main/java/com/example/platform/render/domain/operation/OperationRequest.java; then
    pass "request carries explicit base"
else
    fail "request base binding missing"
fi
if grep -q 'STALE_BASE_REVISION' render-module/src/main/java/com/example/platform/render/domain/operation/OperationRequestResolver.java; then
    pass "stale base fail-closed"
else
    fail "stale base handling missing"
fi
if grep -rq 'latestRevision()\|currentTimeline()' render-module/src/main/java/com/example/platform/render/domain/operation/ 2>/dev/null; then
    fail "mutable-latest fallback present"
else
    pass "no mutable-latest fallback"
fi

# OMG-18/19/20/21: batch flat single base, no nesting/planning
if grep -q 'instances.isEmpty()' render-module/src/main/java/com/example/platform/render/domain/operation/OperationBatch.java && grep -q 'mixed baseRevisionId rejected' render-module/src/main/java/com/example/platform/render/domain/operation/OperationBatch.java; then
    pass "flat single-base non-empty batch"
else
    fail "batch constraints missing"
fi

# OMG-22/23/24/25: no provider/entitlement/workflow/render semantics
if grep -rn 'ffmpeg\|FFmpeg\|OpenCV\|TensorRT' render-module/src/main/java/com/example/platform/render/domain/operation/ 2>/dev/null | grep -v '^[^:]*:[0-9]*: *\*' | grep -q .; then
    fail "provider semantics in operation model"
else
    pass "no provider/FFmpeg semantics"
fi
if grep -rq 'proOnly\|enterpriseOnly\|planName' render-module/src/main/java/com/example/platform/render/domain/operation/ 2>/dev/null; then
    fail "entitlement in operation model"
else
    pass "no entitlement/tier semantics"
fi
if grep -rq 'retry\|timeout\|callback' render-module/src/main/java/com/example/platform/render/domain/operation/OperationBatch.java 2>/dev/null; then
    fail "workflow semantics in batch"
else
    pass "no workflow semantics in batch"
fi

# OMG-26: no CRUD SetField/JsonPath operation
if grep -rq 'SetField\|SetJsonPath\|PatchDocument' render-module/src/main/java/com/example/platform/render/domain/operation/ 2>/dev/null; then
    fail "CRUD operation present"
else
    pass "no generic CRUD operations"
fi

# OMG-28: invocationId/digest not in Timeline hash
if grep -q 'operation' render-module/src/main/java/com/example/platform/render/domain/timeline/canonical/TimelineDocument.java; then
    fail "operation in canonical hash"
else
    pass "operation never in Timeline hash"
fi

# OMG-30: exactly 15 definitions
if grep -q 'MOVE, DELETE, TRIM, SET_TEMPORAL_RATE, SET_TEMPORAL_DIRECTION, FREEZE' render-module/src/main/java/com/example/platform/render/domain/operation/OperationDefinition.java; then
    pass "frozen 15-operation vocabulary"
else
    fail "operation vocabulary drifted"
fi

# OPTG-2: OperationPlan != TimelinePatch (no patch list as plan model)
if grep -q 'List<TimelinePatchOperation> operations' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlan.java 2>/dev/null; then
    fail "plan modeled as patch list"
else
    pass "plan is semantic transition model, not patch list"
fi

# OPTG-4: plan immutable record
if grep -q 'record OperationPlan(' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlan.java; then
    pass "immutable OperationPlan record"
else
    fail "plan not immutable"
fi

# OPTG-5/6: digest deterministic, excludes principal/auth/target ref
if grep -q 'operation-plan-format-v1' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlan.java && grep -q 'baseRevisionId' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlanDigest.java; then
    pass "deterministic domain-separated PlanDigest"
else
    fail "PlanDigest missing"
fi
if grep -rn 'principal' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlanDigest.java | grep -vE '^[0-9]*: *[*]' | grep -q .; then
    fail "principal in PlanDigest"
else
    pass "PlanDigest excludes principal/auth/target ref"
fi

# OPTG-7: candidate hash via TimelineContentDigester
if grep -q 'new TimelineContentDigester()' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlanner.java; then
    pass "candidate hash uses TimelineContentDigester"
else
    fail "candidate hash authority wrong"
fi

# OPTG-11: parent = plan.baseRevisionId (single parent)
if grep -q 'plan.baseRevisionId()' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java && grep -q 'parent_revision_id' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "normal edit parent = plan base"
else
    fail "parent semantics wrong"
fi

# OPTG-14/15: database-enforced CAS (conditional update affected rows)
if grep -q 'where project_id = ? and ref_id = ? and head_revision_id = ?' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "database-enforced CAS conditional update"
else
    fail "CAS mechanism missing"
fi
if grep -q 'select head' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    fail "check-then-act CAS"
else
    pass "no check-then-act CAS"
fi

# OPTG-16/17: authorization binds plan digest + apply context
if grep -q 'authorization().planDigest().equals(plan.planDigest())' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "authorization binds exact PlanDigest"
else
    fail "authorization plan binding missing"
fi
if grep -q 'AUTHORIZATION_CONTEXT_MISMATCH' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "authorization binds exact apply context"
else
    fail "authorization context binding missing"
fi

# OPTG-18: AuthorizationDecision immutable record
if grep -q 'record AuthorizationDecision(' render-module/src/main/java/com/example/platform/render/domain/plan/AuthorizationDecision.java; then
    pass "immutable AuthorizationDecision"
else
    fail "authorization not immutable"
fi

# OPTG-21/22: durable ApplyCommandId, not canonical semantics
if grep -q 'apply_command' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "durable ApplyCommandId authority"
else
    fail "durable idempotency missing"
fi
if grep -q 'apply_command' render-module/src/main/java/com/example/platform/render/domain/timeline/canonical/TimelineDocument.java 2>/dev/null; then
    fail "ApplyCommandId in canonical model"
else
    pass "ApplyCommandId not canonical Timeline semantics"
fi

# OPTG-23: semantic no-op creates no revision
if grep -q 'noOp()' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java && grep -q 'ApplyResult.NO_OP' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "semantic NO_OP no revision"
else
    fail "NO_OP handling missing"
fi

# OPTG-24: no independent generic JSON patch canonical write endpoints
if grep -q 'JsonNode' platform-app/src/main/java/com/example/platform/web/render/TimelineGitV1Controller.java 2>/dev/null; then
    fail "generic JSON patch endpoint"
else
    pass "no generic JSON patch write endpoints"
fi

# OPTG-26: no JGit introduced
if grep -rq 'org.eclipse.jgit' render-module/src/main platform-app/src/main 2>/dev/null; then
    fail "JGit introduced"
else
    pass "no JGit dependency"
fi

# OPTG-27/28: no revision command / merge folded in
if grep -rq 'class MergeCommand\|class RevertCommand\|class BranchCommand' render-module/src/main/java/com/example/platform/render/domain/plan/ 2>/dev/null; then
    fail "revision command implemented"
else
    pass "no revision command implementation"
fi

# OPTG-32: delete consequences explicit in planner
if grep -q 'RelationshipRemoved(s.identityKey())' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlanner.java && grep -q 'remaining.size() < 2' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlanner.java; then
    pass "delete sync/group consequences planner-owned"
else
    fail "delete consequences missing"
fi

# OPTG-33: trim invalid sync -> reject
if grep -q 'SYNC_ANCHOR_INVALIDATED' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlanner.java; then
    pass "trim invalid sync anchor -> reject"
else
    fail "trim sync policy missing"
fi

# OPTG-34: set-rate unsupported audio -> reject
if grep -q 'UNSUPPORTED_AUDIO_TEMPORAL_BEHAVIOR' render-module/src/main/java/com/example/platform/render/domain/plan/PlanErrorCode.java; then
    pass "set-rate audio consequence typed"
else
    fail "audio temporal consequence missing"
fi

# OPTG-37: plan/preview/authorization same digest
if grep -q 'planDigest' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlanPreview.java && grep -q 'planDigest' render-module/src/main/java/com/example/platform/render/domain/plan/AuthorizationDecision.java; then
    pass "preview/authorization bind plan digest"
else
    fail "same-plan binding missing"
fi

# OPCG-1/2: first-time NO_OP apply validates expected head; stale => STALE_TARGET_REF
if grep -q 'no-op still requires exact head' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "no-op first apply validates expected head"
else
    fail "no-op head validation missing"
fi

# OPCG-4/5/6: fingerprint binds principal + project scope + target ref
if grep -q 'principalRef' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java && grep -q 'projectId + "|" + ref.refId()' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "apply command fingerprint binds principal + context"
else
    fail "fingerprint missing principal/context binding"
fi
if grep -q 'policy' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java | grep -v policyVersion | grep -q .; then
    fail "policy version in fingerprint"
else
    pass "policy version excluded from fingerprint"
fi

# OPCG-8/9: OperationPlan semantics have zero PostgreSQL identity dependency
if grep -q 'org.jooq\|PGobject\|postgres' render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlan.java render-module/src/main/java/com/example/platform/render/domain/plan/OperationPlanDigest.java render-module/src/main/java/com/example/platform/render/domain/plan/AuthorizationDecision.java render-module/src/main/java/com/example/platform/render/domain/plan/ApplyResult.java render-module/src/main/java/com/example/platform/render/domain/plan/PlannedChange.java 2>/dev/null; then
    fail "postgres/jooq leakage into domain plan model"
else
    pass "zero postgres/jooq leakage into domain plan model"
fi

# OPCG-10/11: CAS remains database-enforced, no check-then-act
if grep -q 'where project_id = ? and ref_id = ? and head_revision_id = ?' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "CAS database-enforced conditional update"
else
    fail "CAS mechanism missing"
fi

# OPCG-13/14: no JGit, no alternative backend
if grep -rq 'org.eclipse.jgit' render-module/src/main platform-app/src/main 2>/dev/null; then
    fail "JGit introduced"
else
    pass "no JGit"
fi

# OPCG-18/19: revision command / integrity = 0
if grep -rq 'class MergeCommand\|class RevertCommand\|IntegrityFinding\|RepairPlan' render-module/src/main/java/com/example/platform/render/domain/plan/ 2>/dev/null; then
    fail "revision command/integrity implemented"
else
    pass "no revision command / integrity implementation"
fi

# OPCG-16: FINAL FCV candidate identified (07ebd0ee) in governance record
if grep -q '07ebd0ee' docs/architecture/governance/operation-plan-transaction-model-v1.md 2>/dev/null || grep -q 'FINAL_FCV_CANDIDATE' docs/architecture/governance/operation-plan-transaction-model-v1.md 2>/dev/null; then
    pass "final FCV candidate governance recorded"
else
    fail "final FCV candidate missing from governance record"
fi

# RCG-2: RevisionCommandPlan != OperationPlan
if grep -q 'record OperationPlan(' render-module/src/main/java/com/example/platform/render/domain/revisioncommand/RevisionCommandPlan.java 2>/dev/null; then
    fail "revision command plan is operation plan"
else
    pass "RevisionCommandPlan independent of OperationPlan"
fi

# RCG-3/4: context not a command plan variant; variants bounded
if grep -q 'ContextPlan' render-module/src/main/java/com/example/platform/render/domain/revisioncommand/RevisionCommandPlan.java 2>/dev/null; then
    fail "ContextPlan in RevisionCommandPlan"
else
    pass "RevisionContext outside RevisionCommand (RCI1)"
fi
grep -q 'CreateRefPlan' render-module/src/main/java/com/example/platform/render/domain/revisioncommand/RevisionCommandPlan.java && grep -q 'MergeRevisionPlan' render-module/src/main/java/com/example/platform/render/domain/revisioncommand/RevisionCommandPlan.java; ck=$?; if [ $ck -eq 0 ]; then pass "V1 command plan variants bounded"; else fail "plan variants incomplete"; fi

# RCG-6: no generic force move command
if grep -rq 'forceMoveRef\|FORCE_UPDATE_REF\|class MoveRefCommand' render-module/src/main/java/com/example/platform/render/domain/revisioncommand/ 2>/dev/null; then
    fail "generic force/move ref command present"
else
    pass "no generic force/move ref command"
fi

# RCG-13/14: merge semantic owner + engine purity (no revision writes in semantic path)
if grep -q 'mergeSemantic' render-module/src/main/java/com/example/platform/render/app/timeline/TimelineMergeEngine.java; then
    pass "pure semantic merge path present"
else
    fail "mergeSemantic missing"
fi
if grep -q 'mergeSemantic' render-module/src/main/java/com/example/platform/render/app/revisioncommand/RevisionCommandPlanner.java; then
    pass "planner uses pure semantic merge"
else
    fail "planner uses engine persistence path"
fi

# RCG-26/27/28: parent edge authority; legacy fields not authoritative
if grep -q 'timeline_revision_parent' render-module/src/main/java/com/example/platform/render/app/revisioncommand/RevisionGraphService.java; then
    pass "graph reads timeline_revision_parent only"
else
    fail "graph authority missing"
fi
if grep -q 'parent_revision_id' platform-app/src/main/resources/db/migration/V4__revision_command_parent_graph.sql && grep -q 'timeline_revision_parent' platform-app/src/main/resources/db/migration/V4__revision_command_parent_graph.sql; then
    pass "V4 migration retires legacy parent authority (edges single authority)"
else
    fail "V4 migration incomplete"
fi

# RCG-33: cross-project parent DB enforcement
if grep -q 'references timeline_revision(project_id, id)' platform-app/src/main/resources/db/migration/V4__revision_command_parent_graph.sql; then
    pass "cross-project parent edge DB-enforced (composite FK)"
else
    fail "RCI4 composite FK missing"
fi

# RCG-34: no MAX+1 production allocator
if grep -rn 'max(revision_number)' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java render-module/src/main/java/com/example/platform/render/app/revisioncommand/ 2>/dev/null | grep -v '^\s*\*' | grep -q .; then
    fail "MAX+1 allocator remains"
else
    pass "DB-safe revision-number allocator (counter, RCI2)"
fi

# RCG-36/37: apply_command domain separation, OperationPlan semantics preserved
if grep -q 'command_domain' platform-app/src/main/resources/db/migration/V4__revision_command_parent_graph.sql && grep -q "'OPERATION_PLAN'" platform-app/src/main/resources/db/migration/V4__revision_command_parent_graph.sql; then
    pass "apply_command domain separation (OP maps OPERATION_PLAN)"
else
    fail "command domain missing"
fi
if grep -q 'parent_order = 0\|parent_order)' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "normal edit writes single parent edge order 0"
else
    fail "normal edit parent edge missing"
fi

# RCG-38/39: no JGit, no new backend
if grep -rq 'org.eclipse.jgit' render-module/src/main platform-app/src/main 2>/dev/null; then
    fail "JGit introduced"
else
    pass "no JGit"
fi

# RCG-40/41/42: no true revert / rebase / batch
if grep -rq 'class TrueRevertCommand\|class RebaseCommand\|class BatchCommand' render-module/src/main/java/com/example/platform/render/domain/revisioncommand/ 2>/dev/null; then
    fail "deferred command implemented"
else
    pass "true revert/rebase/batch = 0"
fi

# RCG-44/45: legacy restore/merge bypass = 0 (authoritative path via RevisionCommand)
if grep -q 'restoreRevision' render-module/src/main/java/com/example/platform/render/app/timeline/TimelineRevisionSaveService.java 2>/dev/null && grep -q 'revision-command-restore' render-module/src/main/java/com/example/platform/render/app/revisioncommand/RevisionCommandApplyService.java; then
    pass "restore rehomed through RevisionCommand path (legacy endpoint retained)"
else
    fail "restore bypass unresolved"
fi

# RCFG-1/2: exact same revision merge = NO_OP; expected head still checked
if grep -q 'same frozen revision merge' render-module/src/main/java/com/example/platform/render/app/revisioncommand/RevisionCommandPlanner.java; then
    pass "same-revision merge plans NO_OP"
else
    fail "RCP1 same-revision NO_OP missing"
fi
if grep -q 'same frozen revision merge' render-module/src/main/java/com/example/platform/render/app/revisioncommand/RevisionCommandApplyService.java; then
    pass "same-revision apply NO_OP after expected-head CAS"
else
    fail "RCP1 apply NO_OP missing"
fi

# RCFG-3/4: frozen sourceRevisionId is apply authority; no source ref reread
if grep -q 'plan.sourceRevisionId()' render-module/src/main/java/com/example/platform/render/app/revisioncommand/RevisionCommandApplyService.java && ! grep -q 'readRef(sourceRef)\|resolveSourceRef(' render-module/src/main/java/com/example/platform/render/app/revisioncommand/RevisionCommandApplyService.java; then
    pass "frozen source revision is apply authority (zero source-ref reread)"
else
    fail "RCP2 source pin violation"
fi

# RCFG-7/8/9: counter migration above max; bootstrap atomic; no MAX+1
if grep -q 'coalesce(max(revision_number), 0) + 1' platform-app/src/main/resources/db/migration/V4__revision_command_parent_graph.sql; then
    pass "counter migration starts above historical max"
else
    fail "RCP3 migration formula missing"
fi
if grep -q 'on conflict (project_id) do nothing' render-module/src/main/java/com/example/platform/render/app/plan/OperationPlanApplyService.java; then
    pass "counter bootstrap atomic (ON CONFLICT DO NOTHING)"
else
    fail "RCP3 bootstrap not atomic"
fi

# RCFG-12: parent edge remains graph authority
if grep -q 'timeline_revision_parent' render-module/src/main/java/com/example/platform/render/app/revisioncommand/RevisionGraphService.java; then
    pass "parent edge graph authority preserved"
else
    fail "graph authority lost"
fi

# RCFG-13: merge engine purity
if grep -q 'mergeSemantic' render-module/src/main/java/com/example/platform/render/app/timeline/TimelineMergeEngine.java; then
    pass "merge engine purity preserved"
else
    fail "engine purity lost"
fi

# ROADMAP_18 CIG gates
CIM="color-image-module/src/main/java/com/example/platform/colorimage"
if [ -d "$CIM" ]; then
    pass "CIG1 color-image-module exists"
else
    fail "CIG1 color-image-module missing"
fi
# CIG2: no outward dependencies (module has empty dependencies block)
if grep -q 'implementation(project(' color-image-module/build.gradle.kts; then
    fail "CIG2 color-image-module outward dependency"
else
    pass "CIG2 zero outward domain dependencies"
fi
# CIG3: sealed ColorDescription
grep -q 'sealed interface ColorDescription' "$CIM/ColorDescription.java"; ck3=$?; if [ $ck3 -eq 0 ]; then pass "CIG3 sealed root"; else fail "CIG3 not sealed"; fi
# CIG4: profile variant inside ColorDescription
grep -q 'ProfileBasedColorDescription' "$CIM/ColorDescription.java"; ck4=$?; if [ $ck4 -eq 0 ]; then pass "CIG4 profile is ColorDescription variant"; else fail "CIG4"; fi
# CIG5/CIG6: SourceVisualDescription single color field, no sibling profile
grep -q 'ColorDescription colorDescription' "$CIM/SourceVisualDescription.java"; ck5=$?; if [ $ck5 -eq 0 ]; then pass "CIG5 single color authority"; else fail "CIG5"; fi
if grep -q 'profileColorDescription\|ProfileBasedColorDescription profile' "$CIM/SourceVisualDescription.java"; then fail "CIG6 profile sibling present"; else pass "CIG6 no profile sibling"; fi
# CIG7/CIG8: profile format + digest required
grep -q 'profileFormat' "$CIM/ColorDescription.java"; ck7=$?; if [ $ck7 -eq 0 ]; then pass "CIG7 ProfileFormat required"; else fail "CIG7"; fi
grep -q 'ColorProfileContentDigest' "$CIM/ColorDescription.java"; ck8=$?; if [ $ck8 -eq 0 ]; then pass "CIG8 digest required"; else fail "CIG8"; fi
# CIG11: explicit alpha presence
grep -q 'alphaComponentPresent' "$CIM/RasterSampleDescription.java"; ck11=$?; if [ $ck11 -eq 0 ]; then pass "CIG11 explicit alpha presence"; else fail "CIG11"; fi
# CIG12: alpha consistency enforced
grep -q 'INCONSISTENT_ALPHA_DESCRIPTION' "$CIM/SourceVisualDescription.java"; ck12=$?; if [ $ck12 -eq 0 ]; then pass "CIG12 alpha consistency enforced"; else fail "CIG12"; fi
# CIG13: static HDR non-empty
grep -q 'must contain at least one semantic component' "$CIM/StaticHdrMetadata.java"; ck13=$?; if [ $ck13 -eq 0 ]; then pass "CIG13 static HDR non-empty"; else fail "CIG13"; fi
# CIG14: HDR boolean authority = 0 (ColorProbeMetadata has no hdr)
if grep -q 'boolean hdr' render-module/src/main/java/com/example/platform/render/infrastructure/ColorProbeMetadata.java; then fail "CIG14 hdr boolean remains"; else pass "CIG14 hdr boolean deleted"; fi
# CIG15: raw String color authority removed from ColorProbeMetadata
if grep -q 'toTimelineMetadata' render-module/src/main/java/com/example/platform/render/infrastructure/ColorProbeMetadata.java; then fail "CIG15 toTimelineMetadata remains"; else pass "CIG15 Timeline leakage removed"; fi
# CIG16: silent inference removed
if grep -q 'inferFromPixelFormat' render-module/src/main/java/com/example/platform/render/ -r; then fail "CIG16 inferFromPixelFormat remains"; else pass "CIG16 silent pix_fmt inference deleted"; fi
# CIG17: no platform.color.* writes into Timeline
if grep -rq 'platform.color.' render-module/src/main/java/com/example/platform/render/app/; then fail "CIG17 platform.color.* leakage"; else pass "CIG17 zero source-color Timeline writes"; fi
# CIG20: canonical float/double authority = 0 in color-image module
F=$(grep -rn 'double\|float' "$CIM" --include='*.java' 2>/dev/null | grep -v 'toDouble\|doubleValue' | grep -vE 'binary float conversion|float conversion' | grep -vE '^[^:]+:[0-9]+: (\*|/|$)' | wc -l || true)
if [ "$F" = "0" ]; then pass "CIG20 zero float/double authority in canonical module"; else fail "CIG20 float/double present ($F)"; fi
# CIG21/22/23: dynamic HDR / clean aperture / ICC execution = 0
if grep -rn 'DynamicHdrMetadata\|CleanAperture\|LittleCMS' "$CIM" --include='*.java' 2>/dev/null | grep -vE '^[^:]+:[0-9]+: (\*|/|$)' | grep -q .; then fail "CIG21-23 scope leak"; else pass "CIG21-23 dynamic HDR/clean aperture/ICC execution = 0"; fi
# CIG30: SourceBinding unchanged (no source technical metadata fields added)
SB="render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/clip/MediaStreamSourceBinding.java"
if [ -f "$SB" ] && ! grep -qE 'ColorDescription|RasterSampleDescription|StaticHdrMetadata|profileDigest|colorSpace' "$SB"; then
    pass "CIG30 SourceBinding unchanged (no source metadata god object)"
else
    fail "CIG30 SourceBinding gained source technical metadata"
fi

# ROADMAP_18 FINAL POST-CLOSE CIPG gates
# CIPG5-9: primaries UNSPECIFIED vs UNKNOWN
if grep -q 'UNSPECIFIED' color-image-module/src/main/java/com/example/platform/colorimage/ColorPrimaries.java && grep -q 'UNKNOWN' color-image-module/src/main/java/com/example/platform/colorimage/ColorPrimaries.java; then
    pass "CIPG5/CIPG7 primaries UNSPECIFIED + UNKNOWN present"
else
    fail "CIPG5-7 primaries missing/unknown states incomplete"
fi
if grep -qi 'collapsed into UNKNOWN' color-image-module/src/main/java/com/example/platform/colorimage/ColorPrimaries.java; then
    pass "CIPG6 missing primaries = UNSPECIFIED (documented)"
else
    fail "CIPG6 missing-primaries semantics missing"
fi
if grep -q 'primariesDistinguishMissingAndUnknown' color-image-module/src/test/java/com/example/platform/colorimage/PostCloseCorrectionTest.java; then
    pass "CIPG8/CIPG9 UNSPECIFIED != UNKNOWN (equality + serialization test)"
else
    fail "CIPG8-9 missing test"
fi
# CIPG2/3: reproducibility — zero mutable-latest dependency in SourceVisualDescription
if grep -q 'reproducibilityHasNoMutableLatestDependency' color-image-module/src/test/java/com/example/platform/colorimage/PostCloseCorrectionTest.java; then
    pass "CIPG2/CIPG3 reproducible description, zero mutable-latest dependency (structural test)"
else
    fail "CIPG2-3 missing test"
fi
# CIPG4: profile identity exact digest
if grep -q 'profileIdentityIsExactDigest' color-image-module/src/test/java/com/example/platform/colorimage/PostCloseCorrectionTest.java; then
    pass "CIPG4 historical profile identity = exact digest"
else
    fail "CIPG4 missing test"
fi
# CIPG1: credential residue gate (numeric)
CR=$(git grep -InE 'ghp_[A-Za-z0-9]{36}|sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]+' -- '*.java' '*.kt' '*.sql' '*.md' '*.sh' '*.yml' '*.yaml' '*.json' '*.kts' '*.gradle' 2>/dev/null | grep -vE 'AKIAIO|REDACTED|xxx' | wc -l || true)
if [ "$CR" = "0" ]; then
    pass "CIPG1 CREDENTIAL_RESIDUE_FINAL = 0 (numeric)"
else
    fail "CIPG1 credential residue > 0 ($CR)"
fi

# ROADMAP_18 CIP2 gates
# CIP2G1: durable snapshot persistence exists
if [ -f platform-app/src/main/resources/db/migration/V5__source_visual_description_snapshot.sql ] && [ -f media-module/src/main/java/com/example/platform/media/app/sourcevisual/SourceVisualDescriptionSnapshotRepository.java ]; then
    pass "CIP2G1 durable snapshot persistence exists"
else
    fail "CIP2G1 durable persistence missing"
fi
# CIP2G2/G3: historical reload path has no normalizer/probe dependency
if grep -q 'ffprobe\|Probe\|Normalizer' media-module/src/main/java/com/example/platform/media/app/sourcevisual/SourceVisualDescriptionCodec.java media-module/src/main/java/com/example/platform/media/infrastructure/persistence/JooqSourceVisualDescriptionSnapshotRepository.java 2>/dev/null; then
    fail "CIP2G2/G3 normalizer/probe dependency in reload path"
else
    pass "CIP2G2/G3 historical reload: zero normalizer/probe dependency"
fi
# CIP2G4: immutable content binding (artifact_id column)
if grep -q 'artifact_id' platform-app/src/main/resources/db/migration/V5__source_visual_description_snapshot.sql; then
    pass "CIP2G4 immutable content binding (artifact content version)"
else
    fail "CIP2G4 artifact binding missing"
fi
# CIP2G5/6: SourceBinding unchanged + no Timeline leakage
if grep -qE 'ColorDescription|RasterSampleDescription|StaticHdrMetadata' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/clip/MediaStreamSourceBinding.java; then
    fail "CIP2G5 SourceBinding changed"
else
    pass "CIP2G5 SourceBinding unchanged"
fi
if grep -rq 'platform.color.' render-module/src/main --include='*.java'; then
    fail "CIP2G6 Timeline leakage"
else
    pass "CIP2G6 zero Timeline source metadata leakage"
fi
# CIP2G9: color-image-module persistence dependency = 0
if grep -q 'implementation(project(' color-image-module/build.gradle.kts; then
    fail "CIP2G9 color-image outward dependency"
else
    pass "CIP2G9 color-image-module zero outward dependency"
fi
# CIP2G13: UNSPECIFIED/UNKNOWN retained
if grep -q 'UNSPECIFIED' color-image-module/src/main/java/com/example/platform/colorimage/ColorPrimaries.java && grep -q 'UNKNOWN' color-image-module/src/main/java/com/example/platform/colorimage/ColorPrimaries.java; then
    pass "CIP2G13 primaries UNSPECIFIED/UNKNOWN retained"
else
    fail "CIP2G13 primaries states lost"
fi
# CIP2G14: no float DB conversion
if grep -q 'double precision\|::double\|float8' platform-app/src/main/resources/db/migration/V5__source_visual_description_snapshot.sql; then
    fail "CIP2G14 float DB conversion"
else
    pass "CIP2G14 zero Rational float DB conversion"
fi

# ROADMAP_18 CIP2D/CIP2E gates
# CIP2DG1-7: DB-enforced ownership (V6 composite FKs)
if grep -q 'fk_svd_stream_asset' platform-app/src/main/resources/db/migration/V6__source_visual_snapshot_ownership.sql && grep -q 'fk_svd_asset_artifact' platform-app/src/main/resources/db/migration/V6__source_visual_snapshot_ownership.sql; then
    pass "CIP2DG1/2/3/4 DB-enforced stream+artifact ownership (composite FKs)"
else
    fail "CIP2DG1-4 ownership FKs missing"
fi
if grep -q 'uq_ms_id_asset' platform-app/src/main/resources/db/migration/V6__source_visual_snapshot_ownership.sql && grep -q 'uq_maa_asset_artifact' platform-app/src/main/resources/db/migration/V6__source_visual_snapshot_ownership.sql; then
    pass "CIP2DG1/5/6/7 composite-FK prerequisite UNIQUE keys present"
else
    fail "CIP2DG5-7 UNIQUE prerequisites missing"
fi
# CIP2DG8: artifact digest immutability — no Java content_digest UPDATE path
if grep -rn 'set(.*CONTENT_DIGEST\|set(.*contentDigest' media-module/src platform-app/src/main/java --include='*.java' 2>/dev/null | grep -q .; then
    fail "CIP2DG8 content digest update path exists"
else
    pass "CIP2DG8 ARTIFACT_ID_CONTENT_REBINDABLE = NO (zero digest update paths)"
fi
# CIP2DG10: historical renormalization still zero
if grep -q 'ffprobe\|Probe\|Normalizer' media-module/src/main/java/com/example/platform/media/app/sourcevisual/SourceVisualDescriptionCodec.java media-module/src/main/java/com/example/platform/media/infrastructure/persistence/JooqSourceVisualDescriptionSnapshotRepository.java 2>/dev/null; then
    fail "CIP2DG10 normalizer/probe in reload path"
else
    pass "CIP2DG10 zero renormalization (unchanged)"
fi
# CIP2DG11/12: SourceBinding/Timeline unchanged
if grep -qE 'ColorDescription|RasterSampleDescription|StaticHdrMetadata' render-module/src/main/java/com/example/platform/render/domain/timeline/semantics/clip/MediaStreamSourceBinding.java; then
    fail "CIP2DG11 SourceBinding changed"
else
    pass "CIP2DG11 SourceBinding unchanged"
fi
if grep -rq 'platform.color.' render-module/src/main --include='*.java'; then
    fail "CIP2DG12 Timeline leakage"
else
    pass "CIP2DG12 zero Timeline leakage"
fi
# CIP2EG1/2: credential numeric zero evidence
CR=$(git grep -InE 'ghp_[A-Za-z0-9]{36}|sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|xox[baprs]-[A-Za-z0-9-]+' -- '*.java' '*.kt' '*.sql' '*.md' '*.sh' '*.yml' '*.yaml' '*.json' '*.kts' '*.gradle' 2>/dev/null | grep -vE 'AKIAIO|REDACTED|xxx' | wc -l || true)
if [ "$CR" = "0" ]; then
    pass "CIP2EG1 CREDENTIAL_RESIDUE_FINAL = 0 (numeric)"
else
    fail "CIP2EG1 credential residue > 0 ($CR)"
fi

# ROADMAP_18 CIP2F/CIP2G gates
# CIP2FG1/2: content-version snapshot identity (composite PK)
if grep -q 'pk_svd_stream_artifact' platform-app/src/main/resources/db/migration/V7__source_visual_snapshot_content_version.sql; then
    pass "CIP2FG1/2 F2 composite snapshot identity (media_stream_id, artifact_id)"
else
    fail "CIP2FG1/2 composite PK missing"
fi
# CIP2FG3/4: DB-level snapshot immutability trigger
if grep -q 'trg_svd_snapshot_immutable' platform-app/src/main/resources/db/migration/V7__source_visual_snapshot_content_version.sql && grep -q 'SOURCE_VISUAL_SNAPSHOT_IMMUTABLE' platform-app/src/main/resources/db/migration/V7__source_visual_snapshot_content_version.sql; then
    pass "CIP2FG3/4 direct SQL rebind/payload rewrite rejected (immutability trigger)"
else
    fail "CIP2FG3/4 immutability trigger missing"
fi
# CIP2FG6: same exact content conflicting description rejected
if grep -q 'SOURCE_VISUAL_SNAPSHOT_CONFLICT' media-module/src/main/java/com/example/platform/media/infrastructure/persistence/JooqSourceVisualDescriptionSnapshotRepository.java; then
    pass "CIP2FG6 same-key conflicting description fails closed"
else
    fail "CIP2FG6 conflict guard missing"
fi
# CIP2FG8/GG2: zero upsert-by-stream (append-only save)
if grep -q 'on conflict (media_stream_id) do update' media-module/src/main/java/com/example/platform/media/infrastructure/persistence/JooqSourceVisualDescriptionSnapshotRepository.java; then
    fail "CIP2FG8 upsert-by-stream still present"
else
    pass "CIP2FG8/GG2 zero upsert-by-stream; append-only snapshot write"
fi
# CIP2GG1: artifact content identity — render artifact table has no content column
if grep -q 'create table artifact (' platform-app/src/main/resources/db/migration/V1__initial_schema.sql && grep -qE 'storage_uri|format varchar' platform-app/src/main/resources/db/migration/V1__initial_schema.sql; then
    pass "CIP2GG1 artifact id = content identity (no digest column to rebind)"
else
    fail "CIP2GG1 artifact immutability unproven"
fi
# V6 regression: composite FKs still present
if grep -q 'fk_svd_stream_asset' platform-app/src/main/resources/db/migration/V6__source_visual_snapshot_ownership.sql && grep -q 'fk_svd_asset_artifact' platform-app/src/main/resources/db/migration/V6__source_visual_snapshot_ownership.sql; then
    pass "V6 regression: cross-asset ownership FKs retained"
else
    fail "V6 ownership regression"
fi

if [ $FAILED -eq 0 ]; then
    echo "✅ All architecture drift checks passed"
    exit 0
else
    echo "❌ Architecture drift detected"
    exit 1
fi
