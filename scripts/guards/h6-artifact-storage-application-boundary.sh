#!/usr/bin/env bash
set -euo pipefail

repo_root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

count_matches() {
  local pattern="$1"
  shift
  local matches
  matches="$(rg -n --glob '!**/build/**' --glob '!**/node_modules/**' --glob '!frontend/dist/**' \
    "$pattern" "$@" 2>/dev/null || true)"
  [[ -z "$matches" ]] && printf '0' || printf '%s\n' "$matches" | wc -l | tr -d ' '
}

count_java_matches() {
  local pattern="$1"
  shift
  count_matches "$pattern" --glob '*.java' "$@"
}

raw_storage_uri_product_projection_count() {
  count_matches 'storageUri|storage_uri|storageKey|storage_key|artifactUri|artifact_uri|objectKey|object_key|bucketName|bucket_name' \
    "$1/artifact-module/src/main/java/com/example/platform/artifact/api" \
    "$1/artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactSummary.java" \
    "$1/artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactAccess.java" \
    "$1/render-module/src/main/java/com/example/platform/render/app/dto" \
    "$1/platform-app/src/main/java/com/example/platform/web/assets" \
    "$1/frontend/src/contracts/app/artifact.ts" \
    "$1/frontend/src/components" \
    "$1/frontend/src/pages/DevConsolePage.tsx" \
    "$1/shared-kernel/src/main/java/com/example/platform/shared/events/ArtifactCreatedEvent.java"
}

unscoped_artifact_enumeration_count() {
  count_matches 'listArtifacts\(\)|listArtifactsBy(Project|RenderJob)|getArtifactsByJob\(|/render/jobs/\{jobId\}/artifacts|findAllArtifacts|listAllArtifacts' \
    "$1/artifact-module/src/main" "$1/render-module/src/main" "$1/platform-app/src/main" \
    "$1/identity-access-module/src/main" "$1/frontend/src"
}

old_artifact_access_caller_count() {
  count_matches 'ArtifactAccessService|getArtifactContent\(|createAccessDescriptor\(|/render/jobs/\{jobId\}/artifacts/\{artifactId\}/(content|access)|getArtifactAccess\(' \
    "$1/render-module/src/main" "$1/platform-app/src/main" "$1/frontend/src"
}

old_storage_uri_dto_usage_count() {
  count_matches 'ArtifactInfoResponse|ProjectAssetRef|ArtifactTombstonedEvent|record RegisteredArtifact\([^)]*storageUri|ArtifactCatalogEntry\([^)]*storageUri|ArtifactCreatedEvent\([^)]*storageUri' \
    "$1/artifact-module/src/main" "$1/render-module/src/main" "$1/identity-access-module/src/main" \
    "$1/platform-app/src/main" "$1/shared-kernel/src/main" "$1/frontend/src"
}

compatibility_wrapper_count() {
  count_matches 'class RenderArtifactQueryService|interface ClientExportArtifactPort|class ClientExportArtifactAdapter|class ArtifactAccessService|Artifact(Summary|Access)V2' \
    "$1/artifact-module/src/main" "$1/render-module/src/main" "$1/platform-app/src/main" \
    "$1/identity-access-module/src/main" "$1/frontend/src"
}

dual_artifact_authority_count() {
  count_matches 'Map<String, *ArtifactCatalogEntry>|class ArtifactRepository|registerArtifact\(' \
    "$1/storage-module/src/main" "$1/render-module/src/main" "$1/platform-app/src/main" \
    "$1/identity-access-module/src/main" "$1/artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactCatalogService.java"
}

mutable_latest_artifact_authority_count() {
  count_matches '(find|resolve|load)[A-Za-z0-9_]*Latest[A-Za-z0-9_]*Artifact|Artifact[A-Za-z0-9_]*(find|resolve|load)[A-Za-z0-9_]*Latest' \
    "$1/timeline-module/src/main" "$1/artifact-module/src/main" "$1/render-module/src/main"
}

artifact_to_timeline_authority_count() {
  local artifact_count listener_count
  artifact_count="$(count_matches 'import com\.example\.platform\.timeline\.|Timeline(SourceBinding|Revision|Document|Clip)' \
    "$1/artifact-module/src/main")"
  listener_count="$(count_matches 'ArtifactTombstonedEvent|onArtifactTombstoned|artifact.*tombstoneRegistryByStorageUri' \
    "$1/platform-app/src/main")"
  printf '%s' "$((artifact_count + listener_count))"
}

storage_canonical_media_authority_count() {
  count_matches 'import com\.example\.platform\.(media|artifact)\.|class (MediaAsset|ArtifactRepository)|record (MediaAsset|Artifact)\(' \
    "$1/storage-module/src/main" "$1/storage-provider-opendal/src/main"
}

web_direct_artifact_repository_dependency_count() {
  count_java_matches '^[[:space:]]*import[[:space:]]+(static[[:space:]]+)?com\.example\.platform\.artifact\.infrastructure(\.[A-Za-z_$][A-Za-z0-9_$]*)*;|com\.example\.platform\.artifact\.infrastructure\.[A-Za-z_$][A-Za-z0-9_$]*' \
    "$1/platform-app/src/main/java/com/example/platform/web"
}

web_direct_storage_maintenance_record_dependency_count() {
  count_java_matches '(com\.example\.platform\.artifact\.infrastructure\.)?ArtifactRepository[[:space:]]*\.[[:space:]]*StorageMaintenanceRecord\b' \
    "$1/platform-app/src/main/java/com/example/platform/web"
}

unclassified_count() {
  local ledger="$1/docs/architecture/governance/h6-artifact-storage-clean-forward-ledger.md"
  [[ -f "$ledger" ]] || { printf '1'; return; }
  local value
  value="$(rg -o 'UNCLASSIFIED=[0-9]+' "$ledger" | tail -n 1 | cut -d= -f2 || true)"
  [[ -n "$value" ]] && printf '%s' "$value" || printf '1'
}

run_negative_controls() {
  local mutation_root
  mutation_root="$(mktemp -d /tmp/h6-boundary-guard.XXXXXX)"
  trap 'case "${mutation_root:-}" in /tmp/h6-boundary-guard.*) rm -rf -- "$mutation_root" ;; esac' RETURN
  mkdir -p \
    "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/api" \
    "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/app" \
    "$mutation_root/render-module/src/main/java/com/example/platform/render/app/dto" \
    "$mutation_root/render-module/src/main/java/com/example/platform/render/app" \
    "$mutation_root/render-module/src/main" \
    "$mutation_root/platform-app/src/main/java/com/example/platform/web/media" \
    "$mutation_root/platform-app/src/main" \
    "$mutation_root/identity-access-module/src/main" \
    "$mutation_root/frontend/src/contracts/app" \
    "$mutation_root/frontend/src/components" \
    "$mutation_root/frontend/src/pages" \
    "$mutation_root/shared-kernel/src/main/java/com/example/platform/shared/events" \
    "$mutation_root/timeline-module/src/main" \
    "$mutation_root/storage-module/src/main" \
    "$mutation_root/storage-provider-opendal/src/main" \
    "$mutation_root/docs/architecture/governance"

  printf '%s\n' 'record LeakyArtifact(String storageUri) {}' > "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/api/LeakyArtifact.java"
  printf '%s\n' 'class Bad { void x(){ listArtifacts(); } }' > "$mutation_root/render-module/src/main/Unscoped.java"
  printf '%s\n' 'class ArtifactAccessService {}' > "$mutation_root/render-module/src/main/java/com/example/platform/render/app/ArtifactAccessService.java"
  printf '%s\n' 'record ProjectAssetRef(String storageUri) {}' > "$mutation_root/identity-access-module/src/main/ProjectAssetRef.java"
  printf '%s\n' 'interface ClientExportArtifactPort {}' > "$mutation_root/render-module/src/main/ClientExportArtifactPort.java"
  printf '%s\n' 'class Bad { void registerArtifact(){} }' > "$mutation_root/platform-app/src/main/Dual.java"
  printf '%s\n' 'class Bad { void x(){ resolveLatestArtifact(); } }' > "$mutation_root/timeline-module/src/main/Latest.java"
  printf '%s\n' 'class Bad { void onArtifactTombstoned(){ tombstoneRegistryByStorageUri(); } }' > "$mutation_root/platform-app/src/main/java/com/example/platform/web/media/TimelineAuthority.java"
  printf '%s\n' 'class ArtifactRepository {}' > "$mutation_root/storage-module/src/main/ArtifactRepository.java"
  printf '%s\n' \
    'import com.example.platform.artifact.infrastructure.ArtifactRepository;' \
    'class DirectArtifactRepositoryDependency { ArtifactRepository repository; }' \
    > "$mutation_root/platform-app/src/main/java/com/example/platform/web/media/DirectArtifactRepositoryDependency.java"
  printf '%s\n' \
    'class NestedPersistenceDependency {' \
    '  com.example.platform.artifact.infrastructure.ArtifactRepository.StorageMaintenanceRecord record;' \
    '}' \
    > "$mutation_root/platform-app/src/main/java/com/example/platform/web/media/NestedPersistenceDependency.java"
  printf '%s\n' 'UNCLASSIFIED=1' > "$mutation_root/docs/architecture/governance/h6-artifact-storage-clean-forward-ledger.md"

  [[ "$(raw_storage_uri_product_projection_count "$mutation_root")" -gt 0 ]]
  [[ "$(unscoped_artifact_enumeration_count "$mutation_root")" -gt 0 ]]
  [[ "$(old_artifact_access_caller_count "$mutation_root")" -gt 0 ]]
  [[ "$(old_storage_uri_dto_usage_count "$mutation_root")" -gt 0 ]]
  [[ "$(compatibility_wrapper_count "$mutation_root")" -gt 0 ]]
  [[ "$(dual_artifact_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(mutable_latest_artifact_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(artifact_to_timeline_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(storage_canonical_media_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(web_direct_artifact_repository_dependency_count "$mutation_root")" -gt 0 ]]
  [[ "$(web_direct_storage_maintenance_record_dependency_count "$mutation_root")" -gt 0 ]]
  [[ "$(unclassified_count "$mutation_root")" -gt 0 ]]
}

run_negative_controls

raw_count="$(raw_storage_uri_product_projection_count "$repo_root")"
unscoped_count="$(unscoped_artifact_enumeration_count "$repo_root")"
old_access_count="$(old_artifact_access_caller_count "$repo_root")"
old_dto_count="$(old_storage_uri_dto_usage_count "$repo_root")"
wrapper_count="$(compatibility_wrapper_count "$repo_root")"
dual_count="$(dual_artifact_authority_count "$repo_root")"
latest_count="$(mutable_latest_artifact_authority_count "$repo_root")"
artifact_timeline_count="$(artifact_to_timeline_authority_count "$repo_root")"
storage_media_count="$(storage_canonical_media_authority_count "$repo_root")"
web_repository_count="$(web_direct_artifact_repository_dependency_count "$repo_root")"
web_maintenance_record_count="$(web_direct_storage_maintenance_record_dependency_count "$repo_root")"
unclassified="$(unclassified_count "$repo_root")"

printf 'RAW_STORAGE_URI_PRODUCT_PROJECTION_COUNT=%s\n' "$raw_count"
printf 'UNSCOPED_ARTIFACT_ENUMERATION_COUNT=%s\n' "$unscoped_count"
printf 'OLD_ARTIFACT_ACCESS_CALLER_COUNT=%s\n' "$old_access_count"
printf 'OLD_STORAGE_URI_DTO_USAGE_COUNT=%s\n' "$old_dto_count"
printf 'COMPATIBILITY_WRAPPER_COUNT=%s\n' "$wrapper_count"
printf 'DUAL_ARTIFACT_AUTHORITY_COUNT=%s\n' "$dual_count"
printf 'MUTABLE_LATEST_ARTIFACT_AUTHORITY_COUNT=%s\n' "$latest_count"
printf 'ARTIFACT_TO_TIMELINE_AUTHORITY_COUNT=%s\n' "$artifact_timeline_count"
printf 'STORAGE_CANONICAL_MEDIA_AUTHORITY_COUNT=%s\n' "$storage_media_count"
printf 'WEB_DIRECT_ARTIFACT_REPOSITORY_DEPENDENCY_COUNT=%s\n' "$web_repository_count"
printf 'WEB_DIRECT_STORAGE_MAINTENANCE_RECORD_DEPENDENCY_COUNT=%s\n' "$web_maintenance_record_count"
printf 'UNCLASSIFIED=%s\n' "$unclassified"
printf 'H6_RED_MUTATION_CONTROLS=PASS\n'

if (( raw_count + unscoped_count + old_access_count + old_dto_count + wrapper_count + dual_count \
    + latest_count + artifact_timeline_count + storage_media_count + web_repository_count \
    + web_maintenance_record_count + unclassified != 0 )); then
  exit 1
fi
