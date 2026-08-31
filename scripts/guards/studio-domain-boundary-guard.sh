#!/usr/bin/env bash
set -euo pipefail

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

production="studio-module/src/main/java"
build_file="studio-module/build.gradle.kts"

count_matches() {
  local pattern="$1"
  shift
  { rg -i -n --glob '!build/**' --glob '!.git/**' --glob '!.worktrees/**' "$pattern" "$@" 2>/dev/null || true; } | wc -l | tr -d ' '
}

changed_paths="$({ git diff --name-only HEAD; git ls-files --others --exclude-standard; } | sort -u)"
scope_drift_count="$({ printf '%s\n' "$changed_paths" | sed '/^$/d' | rg -v '^(settings\.gradle\.kts|studio-module/.*|scripts/guards/studio-domain-boundary-guard\.sh)$' || true; } | wc -l | tr -d ' ')"

path_count() {
  local pattern="$1"
  { printf '%s\n' "$changed_paths" | rg "$pattern" || true; } | wc -l | tr -d ' '
}

STUDIO_TO_TIMELINE_DIRECT_MUTATION_COUNT="$(count_matches 'TimelineRevisionSaveService|TimelineRepository|TimelinePatchApplicationService|TimelinePatchEngine|TimelineClip' "$production")"
STUDIO_GENERIC_TIMELINE_PATCH_USAGE_COUNT="$(count_matches 'TimelineDiff|JsonPatch|JSON[ -]?Patch|TimelinePatch' "$production")"
STUDIO_PRIVATE_GENERIC_COMMAND_BUS_COUNT="$(count_matches 'CommandBus|GenericCommand|RawAction' "$production")"
STUDIO_PRIVATE_OPERATION_INVOCATION_AUTHORITY_COUNT="$(count_matches 'OperationInvocationPort|OperationPlanApplyService' "$production")"
STUDIO_DIRECT_OWNER_REPOSITORY_BYPASS_COUNT="$(count_matches 'Repository|EntityManager|DSLContext|org\.jooq' "$production")"
STUDIO_PRIVATE_CROSS_OWNER_READ_AUTHORITY_COUNT="$(count_matches '^import com\.example\.platform\.(media|artifact|timeline|operation|render|workflow|storage|workerfabric|providerplugin|identity|commerce|billing|entitlement)' "$production")"
STUDIO_RENDER_AUTHORITY_COUNT="$(count_matches 'RenderPlan|RenderGraph|RenderNode|ExecutionRequirement|ExecutableTaskGraph' "$production")"
STUDIO_PROVIDER_IMPLEMENTATION_COUNT="$(count_matches 'Blender|Houdini|OpenUSD|Omniverse|BMF|FFmpeg|CUDA|PlanLowerer|RuntimeAdapter' "$production")"
STUDIO_RUNTIME_IMPLEMENTATION_COUNT="$(count_matches 'WorkerId|DeviceId|GpuId|Sandbox|Container|Scheduler|Lease' "$production")"
STUDIO_STORAGE_IMPLEMENTATION_IMPORT_COUNT="$(count_matches '^import com\.example\.platform\.storage|StorageObjectId|StorageProvider' "$production")"
STUDIO_PRIVATE_COMPOSITE_RESOURCE_AUTHORITY_COUNT="$(count_matches 'CompositeResource|SemanticFacet|UniversalCharacterCard|UniversalResource|UniversalSceneAsset|StudioPrivateCompositeGraph' "$production")"
STUDIO_WORKFLOW_INTEGRATION_COUNT="$(count_matches '^import com\.example\.platform\.workflow|Workflow' "$production")"
PHYSICAL_STORAGE_COORDINATE_FIELD_COUNT="$(count_matches 'bucket|objectKey|signedUrl|filesystemPath|providerNativeId|usdPrim|openUsd|gpuId|workerId|deviceId' "$production")"
NEW_MODULITH_ALLOWLIST_EXCEPTION_COUNT="$({ git diff -U0 HEAD -- platform-app/src/test/java/com/example/platform/ModularityTest.java | rg '^\+.*ALLOWED_VIOLATIONS' || true; } | wc -l | tr -d ' ')"
DATABASE_SCHEMA_CHANGE_COUNT="$(path_count '(^|/)db/|\.sql$')"
FLYWAY_CHANGE_COUNT="$(path_count 'migration|flyway')"
JOOQ_CHANGE_COUNT="$(path_count 'jooq-codegen|jooq')"
JOOQ_GENERATED_CHANGE_COUNT="$(path_count 'typed-schema-module/src/(main/)?java/.*/generated')"
PUBLIC_HTTP_ROUTE_CHANGE_COUNT="$(path_count 'Controller\.java$|platform-app/src/main/java/.*/web/')"
GRAPHQL_SCHEMA_CHANGE_COUNT="$(path_count '\.graphqls?$|graphql/')"
MCP_SURFACE_CHANGE_COUNT="$(path_count 'Mcp|mcp/')"
FRONTEND_SOURCE_CHANGE_COUNT="$(path_count '^frontend/')"
REVERSE_OWNER_DEPENDENCY_COUNT="$({ rg -n --glob '!studio-module/**' --glob '!build/**' --glob '!.git/**' --glob '!.worktrees/**' "com\\.example\\.platform\\.studio|project\\([\"']:studio-module[\"']\\)" -- '*-module/src/main/**' '*-module/build.gradle.kts' 2>/dev/null || true; } | wc -l | tr -d ' ')"
STUDIO_BUILD_FORBIDDEN_DEPENDENCY_COUNT="$(count_matches 'spring-boot-starter-(web|data|jdbc|jooq)|org\.jooq|postgresql|flyway|provider|timeline-module|render-module|workflow-module|operation-module|storage-module' "$build_file")"
STUDIO_SCOPE_DRIFT_COUNT="$scope_drift_count"

counts=(
  STUDIO_TO_TIMELINE_DIRECT_MUTATION_COUNT STUDIO_GENERIC_TIMELINE_PATCH_USAGE_COUNT
  STUDIO_PRIVATE_GENERIC_COMMAND_BUS_COUNT STUDIO_PRIVATE_OPERATION_INVOCATION_AUTHORITY_COUNT
  STUDIO_DIRECT_OWNER_REPOSITORY_BYPASS_COUNT STUDIO_PRIVATE_CROSS_OWNER_READ_AUTHORITY_COUNT
  STUDIO_RENDER_AUTHORITY_COUNT STUDIO_PROVIDER_IMPLEMENTATION_COUNT STUDIO_RUNTIME_IMPLEMENTATION_COUNT
  STUDIO_STORAGE_IMPLEMENTATION_IMPORT_COUNT STUDIO_PRIVATE_COMPOSITE_RESOURCE_AUTHORITY_COUNT
  STUDIO_WORKFLOW_INTEGRATION_COUNT PHYSICAL_STORAGE_COORDINATE_FIELD_COUNT
  NEW_MODULITH_ALLOWLIST_EXCEPTION_COUNT DATABASE_SCHEMA_CHANGE_COUNT FLYWAY_CHANGE_COUNT
  JOOQ_CHANGE_COUNT JOOQ_GENERATED_CHANGE_COUNT PUBLIC_HTTP_ROUTE_CHANGE_COUNT GRAPHQL_SCHEMA_CHANGE_COUNT
  MCP_SURFACE_CHANGE_COUNT FRONTEND_SOURCE_CHANGE_COUNT REVERSE_OWNER_DEPENDENCY_COUNT
  STUDIO_BUILD_FORBIDDEN_DEPENDENCY_COUNT STUDIO_SCOPE_DRIFT_COUNT
)

failed=0
for name in "${counts[@]}"; do
  value="${!name}"
  printf '%s=%s\n' "$name" "$value"
  if [[ "$value" != "0" ]]; then failed=1; fi
done

if [[ "$failed" != "0" ]]; then
  printf 'STUDIO_ARCHITECTURE_GUARD=FAIL\n'
  exit 1
fi
printf 'STUDIO_ARCHITECTURE_GUARD=PASS\n'
