#!/usr/bin/env bash
set -euo pipefail

repo_root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

count_matches() {
  local pattern="$1"
  shift
  local matches
  matches="$(rg -n --glob '!**/build/**' --glob '!frontend/dist/**' "$pattern" "$@" 2>/dev/null || true)"
  if [[ -z "$matches" ]]; then
    printf '0'
  else
    printf '%s\n' "$matches" | wc -l | tr -d ' '
  fi
}

raw_storage_uri_product_projection_count() {
  count_matches 'storageUri|storage_uri' \
    "$1/artifact-module/src/main/java/com/example/platform/artifact/api" \
    "$1/artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactSummary.java" \
    "$1/artifact-module/src/main/java/com/example/platform/artifact/app/ArtifactAccess.java" \
    "$1/render-module/src/main/java/com/example/platform/render/app/dto" \
    "$1/frontend/src/contracts/app" \
    "$1/frontend/src/components/artifacts" \
    "$1/frontend/src/components/render-jobs" \
    "$1/frontend/src/components/smoke-editor/ArtifactPanel.tsx" \
    "$1/frontend/src/api/app/artifacts.client.ts" \
    "$1/frontend/src/api/render-jobs.ts"
}

timeline_mutable_latest_artifact_resolution_count() {
  count_matches '(find|resolve|load)[A-Za-z0-9_]*Latest[A-Za-z0-9_]*Artifact|Artifact[A-Za-z0-9_]*(find|resolve|load)[A-Za-z0-9_]*Latest' \
    "$1/timeline-module/src/main"
}

artifact_timeline_authority_count() {
  count_matches 'import com\.example\.platform\.timeline\.|Timeline(SourceBinding|Revision|Document|Clip)' \
    "$1/artifact-module/src/main"
}

storage_canonical_media_authority_count() {
  count_matches 'import com\.example\.platform\.(media|artifact)\.|class (MediaAsset|ArtifactRepository)|record (MediaAsset|Artifact)\(' \
    "$1/storage-module/src/main" "$1/storage-provider-opendal/src/main"
}

unscoped_artifact_enumeration_authority_count() {
  count_matches '"/render/jobs/\{jobId\}/artifacts|public List<ArtifactCatalogEntry> listArtifacts\(\)|listArtifactsByProject\(String projectId\)|listArtifactsByRenderJob\(String renderJobId\)' \
    "$1/artifact-module/src/main" "$1/render-module/src/main" "$1/platform-app/src/main"
}

run_negative_controls() {
  local mutation_root
  mutation_root="$(mktemp -d /tmp/h6-boundary-guard.XXXXXX)"
  trap 'case "${mutation_root:-}" in /tmp/h6-boundary-guard.*) rm -rf -- "$mutation_root" ;; esac' RETURN

  mkdir -p \
    "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/api" \
    "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/app" \
    "$mutation_root/render-module/src/main/java/com/example/platform/render/app/dto" \
    "$mutation_root/render-module/src/main" \
    "$mutation_root/platform-app/src/main" \
    "$mutation_root/frontend/src/contracts/app" \
    "$mutation_root/frontend/src/components/artifacts" \
    "$mutation_root/frontend/src/components/render-jobs" \
    "$mutation_root/frontend/src/components/smoke-editor" \
    "$mutation_root/frontend/src/api/app" \
    "$mutation_root/frontend/src/api" \
    "$mutation_root/timeline-module/src/main" \
    "$mutation_root/storage-module/src/main" \
    "$mutation_root/storage-provider-opendal/src/main"

  printf '%s\n' 'record LeakyArtifact(String storageUri) {}' \
    > "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/api/LeakyArtifact.java"
  printf '%s\n' 'class Bad { void x(){ resolveLatestArtifact(); } }' \
    > "$mutation_root/timeline-module/src/main/Bad.java"
  printf '%s\n' 'import com.example.platform.timeline.app.TimelineRevision;' \
    > "$mutation_root/artifact-module/src/main/java/com/example/platform/artifact/app/Bad.java"
  printf '%s\n' 'class ArtifactRepository {}' \
    > "$mutation_root/storage-module/src/main/Bad.java"
  printf '%s\n' '@GetMapping("/render/jobs/{jobId}/artifacts")' \
    > "$mutation_root/render-module/src/main/Bad.java"

  [[ "$(raw_storage_uri_product_projection_count "$mutation_root")" -gt 0 ]]
  [[ "$(timeline_mutable_latest_artifact_resolution_count "$mutation_root")" -gt 0 ]]
  [[ "$(artifact_timeline_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(storage_canonical_media_authority_count "$mutation_root")" -gt 0 ]]
  [[ "$(unscoped_artifact_enumeration_authority_count "$mutation_root")" -gt 0 ]]
}

run_negative_controls

raw_count="$(raw_storage_uri_product_projection_count "$repo_root")"
latest_count="$(timeline_mutable_latest_artifact_resolution_count "$repo_root")"
artifact_timeline_count="$(artifact_timeline_authority_count "$repo_root")"
storage_media_count="$(storage_canonical_media_authority_count "$repo_root")"
unscoped_count="$(unscoped_artifact_enumeration_authority_count "$repo_root")"

printf 'RAW_STORAGE_URI_PRODUCT_PROJECTION_COUNT=%s\n' "$raw_count"
printf 'TIMELINE_MUTABLE_LATEST_ARTIFACT_RESOLUTION_COUNT=%s\n' "$latest_count"
printf 'ARTIFACT_TIMELINE_AUTHORITY_COUNT=%s\n' "$artifact_timeline_count"
printf 'STORAGE_CANONICAL_MEDIA_AUTHORITY_COUNT=%s\n' "$storage_media_count"
printf 'UNSCOPED_ARTIFACT_ENUMERATION_AUTHORITY_COUNT=%s\n' "$unscoped_count"
printf 'H6_GUARD_NEGATIVE_CONTROLS=PASS\n'

if (( raw_count + latest_count + artifact_timeline_count + storage_media_count + unscoped_count != 0 )); then
  exit 1
fi
