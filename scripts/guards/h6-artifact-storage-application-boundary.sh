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

artifact_catalog_canonical_write_authority_count() {
  count_java_matches 'relationRepository[[:space:]]*\.[[:space:]]*save[[:space:]]*\(' \
    "$1/artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactCatalogService.java"
}

artifact_catalog_mutation_method_count() {
  count_java_matches 'public[^{;]*(relate|register|save|create|update|delete|remove|tombstone|purge)[A-Za-z0-9_]*[[:space:]]*\(' \
    "$1/artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactCatalogService.java"
}

artifact_canonical_write_authority_count() {
  count_java_matches 'relationRepository[[:space:]]*\.[[:space:]]*save[[:space:]]*\(' \
    "$1/artifact-module/src/main"
}

known_storage_uri_direct_render_table_read_count() {
  count_java_matches 'RENDER_JOB' \
    "$1/platform-app/src/main/java/com/example/platform/web/media/KnownStorageUriIndexService.java"
}

known_storage_uri_direct_delivery_table_read_count() {
  count_java_matches 'DELIVERY_JOB' \
    "$1/platform-app/src/main/java/com/example/platform/web/media/KnownStorageUriIndexService.java"
}

known_storage_uri_cross_owner_dsl_authority_count() {
  count_java_matches 'DSLContext' \
    "$1/platform-app/src/main/java/com/example/platform/web/media/KnownStorageUriIndexService.java"
}

known_uri_index_partial_failure_continue_count() {
  count_java_matches 'catch[[:space:]]*\(' \
    "$1/platform-app/src/main/java/com/example/platform/web/media/KnownStorageUriIndexService.java"
}

cross_owner_raw_table_semantic_authority_count() {
  count_java_matches '(RENDER_JOB|DELIVERY_JOB)' \
    "$1/platform-app/src/main/java/com/example/platform/web/media"
}

storage_object_id_physical_coordinate_classification_count() {
  count_java_matches '[Ss]torageObjectId[^;]*(physical[[:space:]]+coordinate|storage[[:space:]]+URI)' \
    "$1/artifact-module/src/main" "$1/platform-app/src/main"
}

storage_object_id_uri_parse_count() {
  count_java_matches '(parseUri|URI\.create|new[[:space:]]+URI|Paths\.get)[[:space:]]*\([^;]*[Ss]torageObjectId' \
    "$1/artifact-module/src/main" "$1/platform-app/src/main"
}

storage_object_id_provider_bucket_key_inference_count() {
  count_java_matches '[Ss]torageObjectId[^;]*(provider|bucket|objectKey)' \
    "$1/artifact-module/src/main" "$1/platform-app/src/main"
}

artifact_placement_authority_count() {
  count_java_matches 'ArtifactStorageMaintenance(Entry|Query|Adapter)|class[[:space:]]+ArtifactPlacement' \
    "$1/artifact-module/src/main" "$1/platform-app/src/main"
}

artifact_id_only_destructive_mutation_count() {
  count_java_matches 'public[[:space:]]+(long|void|boolean|int)[[:space:]]+(countReplicas|deleteReplica|updateState|markPurged)[[:space:]]*\([[:space:]]*String[[:space:]]+artifactId' \
    "$1/artifact-module/src/main"
}

artifact_destructive_without_tenant_ownership_predicate_count() {
  count_java_matches 'where\(ARTIFACT\.ID\.eq\(artifactId\)\)|findTombstonedBefore\((null|"\*")' \
    "$1/artifact-module/src/main"
}

ambient_tenant_fallback_count() {
  count_java_matches 'TenantContext\.get\(\)|tenantId[[:space:]]*!=[[:space:]]*null|tenantId\.equals\("\*"\)' \
    "$1/artifact-module/src/main/java/com/example/platform/artifact/app" \
    "$1/artifact-module/src/main/java/com/example/platform/artifact/infrastructure"
}

invented_tenant_literal_count() {
  count_java_matches '(findTombstonedBefore|countReplicas|deleteReplica|updateState|markPurged|deleteCheck|tombstone)[[:space:]]*\([[:space:]]*(null|"\*"|"system")' \
    "$1/artifact-module/src/main"
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
    "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/infrastructure" \
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
  printf '%s\n' \
    'class ArtifactCatalogService {' \
    '  ArtifactRelationRepository relationRepository;' \
    '  public void relateArtifacts(){ relationRepository.save(null); }' \
    '}' \
    > "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactCatalogService.java"
  printf '%s\n' \
    'import org.jooq.DSLContext;' \
    'import static tables.RenderJob.RENDER_JOB;' \
    'import static tables.DeliveryJob.DELIVERY_JOB;' \
    'class KnownStorageUriIndexService {' \
    '  DSLContext dsl;' \
    '  void index(){ try { dsl.select(RENDER_JOB.ARTIFACT_URI, DELIVERY_JOB.SOURCE_URI); } catch (Exception ignored) {} }' \
    '}' \
    > "$mutation_root/platform-app/src/main/java/com/example/platform/web/media/KnownStorageUriIndexService.java"
  printf '%s\n' \
    'class OpaqueIdentityViolation {' \
    '  // StorageObjectId is a physical coordinate with provider bucket objectKey.' \
    '  void parse(Binding binding){ BlobStorage.parseUri(binding.storageObjectId().value()); }' \
    '}' \
    > "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/app/OpaqueIdentityViolation.java"
  printf '%s\n' 'class ArtifactStorageMaintenanceAdapter {}' \
    > "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactStorageMaintenanceAdapter.java"
  printf '%s\n' \
    'class ArtifactRepository {' \
    '  public void updateState(String artifactId, Object state){ where(ARTIFACT.ID.eq(artifactId)); }' \
    '  void gc(String tenantId){ if (tenantId != null) findTombstonedBefore(null, cutoff); }' \
    '}' \
    > "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/infrastructure/ArtifactRepository.java"

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
  [[ "$(artifact_catalog_canonical_write_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(artifact_catalog_mutation_method_count "$mutation_root")" -gt 0 ]]
  [[ "$(artifact_canonical_write_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(known_storage_uri_direct_render_table_read_count "$mutation_root")" -gt 0 ]]
  [[ "$(known_storage_uri_direct_delivery_table_read_count "$mutation_root")" -gt 0 ]]
  [[ "$(known_storage_uri_cross_owner_dsl_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(known_uri_index_partial_failure_continue_count "$mutation_root")" -gt 0 ]]
  [[ "$(cross_owner_raw_table_semantic_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(storage_object_id_physical_coordinate_classification_count "$mutation_root")" -gt 0 ]]
  [[ "$(storage_object_id_uri_parse_count "$mutation_root")" -gt 0 ]]
  [[ "$(storage_object_id_provider_bucket_key_inference_count "$mutation_root")" -gt 0 ]]
  [[ "$(artifact_placement_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(artifact_id_only_destructive_mutation_count "$mutation_root")" -gt 0 ]]
  [[ "$(artifact_destructive_without_tenant_ownership_predicate_count "$mutation_root")" -gt 0 ]]
  [[ "$(ambient_tenant_fallback_count "$mutation_root")" -gt 0 ]]
  [[ "$(invented_tenant_literal_count "$mutation_root")" -gt 0 ]]
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
catalog_write_count="$(artifact_catalog_canonical_write_authority_count "$repo_root")"
catalog_mutation_count="$(artifact_catalog_mutation_method_count "$repo_root")"
artifact_write_count="$(artifact_canonical_write_authority_count "$repo_root")"
known_render_count="$(known_storage_uri_direct_render_table_read_count "$repo_root")"
known_delivery_count="$(known_storage_uri_direct_delivery_table_read_count "$repo_root")"
known_dsl_count="$(known_storage_uri_cross_owner_dsl_authority_count "$repo_root")"
known_partial_count="$(known_uri_index_partial_failure_continue_count "$repo_root")"
cross_owner_raw_count="$(cross_owner_raw_table_semantic_authority_count "$repo_root")"
storage_id_physical_count="$(storage_object_id_physical_coordinate_classification_count "$repo_root")"
storage_id_uri_parse_count="$(storage_object_id_uri_parse_count "$repo_root")"
storage_id_inference_count="$(storage_object_id_provider_bucket_key_inference_count "$repo_root")"
artifact_placement_count="$(artifact_placement_authority_count "$repo_root")"
id_only_destructive_count="$(artifact_id_only_destructive_mutation_count "$repo_root")"
destructive_predicate_count="$(artifact_destructive_without_tenant_ownership_predicate_count "$repo_root")"
ambient_tenant_count="$(ambient_tenant_fallback_count "$repo_root")"
invented_tenant_count="$(invented_tenant_literal_count "$repo_root")"

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
printf 'ARTIFACT_CATALOG_CANONICAL_WRITE_AUTHORITY_COUNT=%s\n' "$catalog_write_count"
printf 'ARTIFACT_CATALOG_MUTATION_METHOD_COUNT=%s\n' "$catalog_mutation_count"
printf 'ARTIFACT_CANONICAL_WRITE_AUTHORITY_COUNT=%s\n' "$artifact_write_count"
printf 'KNOWN_STORAGE_URI_DIRECT_RENDER_TABLE_READ_COUNT=%s\n' "$known_render_count"
printf 'KNOWN_STORAGE_URI_DIRECT_DELIVERY_TABLE_READ_COUNT=%s\n' "$known_delivery_count"
printf 'KNOWN_STORAGE_URI_CROSS_OWNER_DSL_AUTHORITY_COUNT=%s\n' "$known_dsl_count"
printf 'KNOWN_URI_INDEX_PARTIAL_FAILURE_CONTINUE_COUNT=%s\n' "$known_partial_count"
printf 'CROSS_OWNER_RAW_TABLE_SEMANTIC_AUTHORITY_COUNT=%s\n' "$cross_owner_raw_count"
printf 'STORAGE_OBJECT_ID_PHYSICAL_COORDINATE_CLASSIFICATION_COUNT=%s\n' "$storage_id_physical_count"
printf 'STORAGE_OBJECT_ID_URI_PARSE_COUNT=%s\n' "$storage_id_uri_parse_count"
printf 'STORAGE_OBJECT_ID_PROVIDER_BUCKET_KEY_INFERENCE_COUNT=%s\n' "$storage_id_inference_count"
printf 'ARTIFACT_PLACEMENT_AUTHORITY_COUNT=%s\n' "$artifact_placement_count"
printf 'ARTIFACT_ID_ONLY_DESTRUCTIVE_MUTATION_COUNT=%s\n' "$id_only_destructive_count"
printf 'ARTIFACT_DESTRUCTIVE_WITHOUT_TENANT_OWNERSHIP_PREDICATE_COUNT=%s\n' "$destructive_predicate_count"
printf 'AMBIENT_TENANT_FALLBACK_COUNT=%s\n' "$ambient_tenant_count"
printf 'INVENTED_TENANT_LITERAL_COUNT=%s\n' "$invented_tenant_count"
printf 'H6_NEW_HOSTILE_CONTROL_FAILURES=0\n'

if (( raw_count + unscoped_count + old_access_count + old_dto_count + wrapper_count + dual_count \
    + latest_count + artifact_timeline_count + storage_media_count + web_repository_count \
    + web_maintenance_record_count + unclassified + catalog_write_count + catalog_mutation_count \
    + known_render_count + known_delivery_count + known_dsl_count + known_partial_count \
    + cross_owner_raw_count + storage_id_physical_count + storage_id_uri_parse_count \
    + storage_id_inference_count + artifact_placement_count + id_only_destructive_count \
    + destructive_predicate_count + ambient_tenant_count + invented_tenant_count != 0 )); then
  exit 1
fi

if (( artifact_write_count != 1 )); then
  exit 1
fi
