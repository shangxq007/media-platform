#!/usr/bin/env python3
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]


def read(relative):
    path = root / relative
    return path.read_text(errors="ignore") if path.exists() else ""


def production_java():
    for path in root.rglob("*.java"):
        relative = path.relative_to(root).as_posix()
        if "/src/main/java/" in relative and "/build/" not in relative and "/.worktrees/" not in relative:
            yield relative, path.read_text(errors="ignore")


files = list(production_java())
failures = []


def report(name, count, hits=()):
    print(f"{name}={count}")
    for hit in list(hits)[:50]:
        print(f"  HIT {hit}")
    if count != 0:
        failures.append(name)


def regex_hits(pattern, predicate=lambda _path: True, flags=0):
    hits = []
    for relative, text in files:
        if predicate(relative):
            hits.extend([relative] * len(re.findall(pattern, text, flags)))
    return hits


artifact_cache = "render-module/src/main/java/com/example/platform/render/infrastructure/renderplan/ArtifactCache.java"
report("LEGACY_HASH_TO_URI_ARTIFACT_CACHE_AUTHORITY_COUNT", int((root / artifact_cache).exists()),
       [artifact_cache] if (root / artifact_cache).exists() else [])

legacy_concurrent = regex_hits(
    r"ConcurrentHashMap\s*<\s*String\s*,\s*String\s*>",
    lambda path: path.startswith("render-module/") and ("ArtifactCache" in path or "reuse" in path.lower()))
report("LEGACY_CONCURRENT_HASHMAP_ARTIFACT_REUSE_AUTHORITY_COUNT", len(legacy_concurrent), legacy_concurrent)

identity_files = [
    (path, text) for path, text in files
    if re.search(r"ExecutionReuseKey|ArtifactReuseIndexPort|ReusableArtifactRecord|ValidatedReuseDecision", text)
    and "MaterializedArtifact.java" not in path
]
raw_uri_hits = []
for path, text in identity_files:
    matches = re.findall(r"storageUri|outputUri|reuseArtifactUri|String\s+uri\b|URI\s+uri\b|Path\s+\w*(key|identity)", text)
    raw_uri_hits.extend([path] * len(matches))
report("RAW_STORAGE_URI_AS_REUSE_IDENTITY_COUNT", len(raw_uri_hits), raw_uri_hits)

direct_skip = regex_hits(
    r"skipExecution|reuseArtifactUri|shouldSkipIncrementalReuse|incrementalMode[^\n]{0,80}[\"']reuse[\"']",
    lambda path: path.startswith("render-module/")
    and ("Incremental" in path or "SegmentPlanFilter" in path or "Pipeline" in path))
report("INCREMENTAL_PLAN_DIRECT_SKIP_WITHOUT_ARTIFACT_VALIDATION_COUNT", len(direct_skip), direct_skip)

reusable_value = read("render-module/src/main/java/com/example/platform/render/domain/planning/ReusableArtifact.java")
direct_truth = 0 if ("String uri" not in reusable_value and "advisory only" in reusable_value) else 1
report("INCREMENTAL_PLAN_DIRECT_REUSE_TRUTH_COUNT", direct_truth,
       ["ReusableArtifact.java"] if direct_truth else [])

opendal = regex_hits(
    r"org\.apache\.opendal|\bOpenDal\w*|\bOpenDAL\b",
    lambda path: path.startswith("worker-fabric-module/") or "/providernative/" in path)
report("PROVIDER_DIRECT_OPENDAL_DEPENDENCY_COUNT", len(opendal), opendal)

backend = regex_hits(
    r"software\.amazon\.awssdk|\bS3\w*|\bRustFS\b|\bR2\b|s3://|r2://|rustfs://",
    lambda path: path.startswith("worker-fabric-module/") or "/providernative/" in path)
report("PROVIDER_DIRECT_S3_RUSTFS_R2_DEPENDENCY_COUNT", len(backend), backend)

resolver = read("worker-fabric-module/src/main/java/com/example/platform/workerfabric/reuse/ArtifactReuseResolver.java")
cache_authority = 0 if all(token in resolver for token in (
    "ArtifactQueryService", "getArtifact(tenantId", "ArtifactState.AVAILABLE", "contentDigest().matches")) else 1
report("CACHE_AS_ARTIFACT_EXISTENCE_AUTHORITY_COUNT", cache_authority,
       ["ArtifactReuseResolver.java"] if cache_authority else [])

index = read("worker-fabric-module/src/main/java/com/example/platform/workerfabric/infrastructure/JooqArtifactReuseIndex.java")
eviction_delete = 0 if (
    "delete from wf_artifact_reuse_index" in index
    and not re.search(r"delete\s+from\s+artifact\b", index, re.I)) else 1
report("CACHE_EVICTION_DIRECT_ARTIFACT_DELETE_AUTHORITY_COUNT", eviction_delete,
       ["JooqArtifactReuseIndex.java"] if eviction_delete else [])

plan_lowerer = read("worker-fabric-module/src/main/java/com/example/platform/workerfabric/domain/providernative/PlanLowerer.java")
lowerer_reads = len(re.findall(
    r"ArtifactReuseIndex|ArtifactCache|cache\.|lookup\(|StorageProvider|Materializer", plan_lowerer))
report("PLAN_LOWERER_MUTABLE_CACHE_READ_COUNT", lowerer_reads,
       ["PlanLowerer.java"] * lowerer_reads)

redis = regex_hits(r"\bRedis\w*|redis://", lambda path: path.startswith("worker-fabric-module/"))
report("REDIS_EXECUTION_AUTHORITY_COUNT", len(redis), redis)

distributed_locks = regex_hits(
    r"Alluxio|JuiceFS|Dragonfly|distributed\s+cache\s+lock|single-flight.{0,40}execution\s+ownership",
    lambda path: path.startswith("worker-fabric-module/"), re.I)
report("DISTRIBUTED_CACHE_EXECUTION_LOCK_AUTHORITY_COUNT", len(distributed_locks), distributed_locks)

output_commit = read(
    "worker-fabric-module/src/main/java/com/example/platform/workerfabric/reuse/ArtifactOutputCommitOrchestrator.java")
staged_direct_commit = 0 if all(token in output_commit for token in (
    "provider.beginWrite(", "provider.write(", "provider.completeWrite(",
    "bindRequest(metadata, publication)")) and not re.search(
        r"commit\s*\(\s*StagedExecutionOutput[^)]*ArtifactCommitRequest",
        output_commit, re.S) else 1
report("STAGED_DIRECT_ARTIFACT_COMMIT_WITHOUT_DURABLE_BINDING_COUNT", staged_direct_commit,
       ["ArtifactOutputCommitOrchestrator.java"] if staged_direct_commit else [])

provider_native_source = "\n".join(
    text for path, text in files if "/domain/providernative/" in path)
provider_storage_authority = len(re.findall(
    r"StorageProvider|StorageObjectId|StorageReplicaId|ArtifactCommitService|ArtifactReuseIndexPort",
    provider_native_source))
report("PROVIDER_DIRECT_STORAGE_OR_ARTIFACT_AUTHORITY_COUNT", provider_storage_authority,
       ["worker-fabric-module/domain/providernative"] * provider_storage_authority)

closed_loop = read(
    "worker-fabric-module/src/main/java/com/example/platform/workerfabric/reuse/RuntimeClosedLoopOrchestrator.java")
unvalidated_pruning = 0 if all(token in closed_loop for token in (
    "decision.outcome() == ValidatedReuseDecision.Outcome.VALIDATED_HIT",
    "validatedHitIds.add(task.id())",
    "Set.copyOf(validatedHitIds)")) and all(token not in closed_loop for token in (
        "request.reusedTasks", "request.validatedHitIds")) else 1
report("UNVALIDATED_REUSE_PRUNING_COUNT", unvalidated_pruning,
       ["RuntimeClosedLoopOrchestrator.java"] if unvalidated_pruning else [])

orchestration_bypass = 0 if all(token in closed_loop for token in (
    "outputCommitOrchestrator.commit(", "completionOrchestrator.complete(")) and all(
        token not in closed_loop for token in (
            "ArtifactCommitService", ".completeIfCurrent(",
            ".stageWinningPublication(", ".activateWinningPublication(")) else 1
report("PHASE16_RUNTIME_ORCHESTRATION_BYPASS_COUNT", orchestration_bypass,
       ["RuntimeClosedLoopOrchestrator.java"] if orchestration_bypass else [])

tenant_guard = 0 if all(token in resolver for token in (
    "tenantId.equals(record.tenantId())", "getArtifact(tenantId")) else 1
report("CROSS_TENANT_REUSE_WITHOUT_AUTHORIZATION_COUNT", tenant_guard,
       ["ArtifactReuseResolver.java"] if tenant_guard else [])

deriver = read("media-execution-plan-module/src/main/java/com/example/platform/execution/taskgraph/ExecutionReuseKeyDeriver.java")
predecessor_section = deriver[deriver.find("private static String predecessorContribution"):]
future_pin = len(re.findall(r"ArtifactId|ContentDigest|artifactPin", predecessor_section))
report("FUTURE_ARTIFACT_PIN_REQUIRED_FOR_COMPUTED_REUSE_KEY_COUNT", future_pin,
       ["ExecutionReuseKeyDeriver.java"] * future_pin)

media_build = read("media-execution-plan-module/build.gradle.kts")
dependency_count = media_build.count("worker-fabric-module")
report("MEDIA_EXECUTION_PLAN_TO_WORKER_FABRIC_DEPENDENCY_COUNT", dependency_count,
       ["media-execution-plan-module/build.gradle.kts"] * dependency_count)

runtime_adapter_path = (
    "worker-fabric-module/src/main/java/com/example/platform/workerfabric/"
    "domain/providernative/RuntimeAdapter.java")
runtime_binding_path = (
    "worker-fabric-module/src/main/java/com/example/platform/workerfabric/"
    "domain/providernative/ProviderNativeRuntimeBinding.java")
runtime_adapter = read(runtime_adapter_path)
runtime_binding = read(runtime_binding_path)
old_runtime_signature = len(re.findall(
    r"List\s*<\s*MaterializedArtifact\s*>\s+runtimeLocalInputs",
    runtime_adapter + "\n" + runtime_binding))
report("OLD_RUNTIME_ADAPTER_MATERIALIZED_ARTIFACT_SIGNATURE_COUNT", old_runtime_signature,
       [runtime_adapter_path] * old_runtime_signature)

compatibility_overloads = (
    max(0, runtime_adapter.count("ProviderExecutionOutput execute(") - 1)
    + max(0, runtime_binding.count("public ProviderExecutionOutput execute(") - 1))
report("RUNTIME_INPUT_COMPATIBILITY_OVERLOAD_COUNT", compatibility_overloads,
       [runtime_adapter_path, runtime_binding_path] if compatibility_overloads else [])

runtime_contract_source = runtime_adapter + "\n" + runtime_binding + "\n" + closed_loop
untyped_runtime_inputs = len(re.findall(
    r"List\s*<\s*(?:Object|Map(?:\s*<)?|Path|String)\b[^>]*>\s+runtimeLocalInputs"
    r"|\bList\s+runtimeLocalInputs\b",
    runtime_contract_source))
report("UNTYPED_RUNTIME_INPUT_COLLECTION_COUNT", untyped_runtime_inputs,
       [runtime_adapter_path] * untyped_runtime_inputs)

positional_role_inference = len(re.findall(
    r"(?:runtimeLocalInputs|inputs)\s*\.\s*(?:get\s*\(\s*\d+\s*\)|getFirst\s*\(\s*\))",
    runtime_contract_source))
report("INPUT_ROLE_INFERRED_FROM_LIST_INDEX_COUNT", positional_role_inference,
       [runtime_adapter_path] * positional_role_inference)

logical_pin_dedup = len(re.findall(
    r"(?:LinkedHashSet|HashSet|Set)\s*<\s*ArtifactPin\s*>", closed_loop))
report("LOGICAL_RUNTIME_INPUT_DEDUP_BY_ARTIFACT_PIN_COUNT", logical_pin_dedup,
       ["RuntimeClosedLoopOrchestrator.java"] * logical_pin_dedup)

worker_execution_type_leaks = regex_hits(
    r"execution\.planning\.ExecutionIoProjection\.InputBinding"
    r"|execution\.domain\.ExecutionStepId",
    lambda path: path.startswith("worker-fabric-module/src/main/"))
report("WORKER_FABRIC_DIRECT_INTERNAL_EXECUTION_TYPE_DEPENDENCY_COUNT",
       len(worker_execution_type_leaks), worker_execution_type_leaks)

task_dependency = read(
    "media-execution-plan-module/src/main/java/com/example/platform/execution/"
    "taskgraph/ExecutableTaskDependency.java")
input_binding_leaks = len(re.findall(r"\bInputBinding\s+consumerInput\b", task_dependency))
report("TASKGRAPH_DEPENDENCY_INPUT_BINDING_LEAK_COUNT", input_binding_leaks,
       ["ExecutableTaskDependency.java"] * input_binding_leaks)

fenced_publication = 0 if all(token in index for token in (
    "publication_status = 'WINNING'", "publication_status = 'PENDING'",
    "wf_completion_event", "wf_execution_attempt.state", "currentOwner")) else 1
report("STALE_GENERATION_WINNING_REUSE_PUBLICATION_AUTHORITY_COUNT", fenced_publication,
       ["JooqArtifactReuseIndex.java"] if fenced_publication else [])

if failures:
    print("PHASE16_CLEAN_FORWARD_GUARDS=FAIL")
    print("FAILED_COUNTERS=" + ",".join(failures))
    sys.exit(1)
print("PHASE16_CLEAN_FORWARD_GUARDS=PASS")
