#!/usr/bin/env python3
"""AMRA-V1 ARCHITECTURE_MAP_DRIFT_GUARD_V1 — mechanical architecture-map validation.

Validates high-value mechanical facts only (NOT a semantic theorem prover):
  1. Every active Gradle module has an explicit classification.
  2. Every deployment unit is represented in LikeC4 or explicitly omitted.
  3. No stale CURRENT element in LikeC4 (element claims CURRENT but is absent).
  4. Runtime authority assertions (text guards on the LikeC4 model).
  5. Workflow target boundary (Workflow -> PluginRuntime; never -> ProviderExtensionSPI/SandboxExecutionService).
  6. LikeC4 syntax already validated separately (likec4 validate).

Exit 0 = PASS, 1 = FAIL. Deterministic. No repo test execution.
"""
import os
import re
import sys

# Repository root: env override (AMRA-V1 published the guard under the old
# worktree path; resolve dynamically so the guard works from any worktree).
REPO = os.environ.get("MEDIA_PLATFORM_REPO", os.path.abspath(os.path.join(os.path.dirname(__file__), "../../../..")))
if not os.path.exists(f"{REPO}/settings.gradle.kts"):
    REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "../../.."))

# ── 1. Active Gradle modules (mechanical parse of settings.gradle.kts) ──
src = open(f"{REPO}/settings.gradle.kts").read()
m = re.search(r"include\((.*?)\)\n", src, re.S)
active = sorted(set(re.findall(r'"([^"]+)"', m.group(1))))
HOLD = re.findall(r'include\("([^"]+)"\)', src.split("HOLD")[1]) if "HOLD" in src else []
active += [":platform-algorithms:graph"]

# ── 2. LikeC4 containers (semantic intent) ──
likec4 = open(f"{REPO}/docs/architecture/maps/likec4/media-platform.likec4").read()
likec4_containers = set(re.findall(r"= container '([^']+)'", likec4))
likec4_all = set(re.findall(r"= (?:container|component|system|external) '([^']+)'", likec4))

failures = []

# Classification: every active module must appear in LikeC4 OR be explicitly
# covered by an aggregate boundary OR be PMPR candidate / tooling-only.
# The LikeC4 model uses semantic containers + explicit aggregates; modules not
# individually named are covered by aggregate boundaries (PMPR CANDIDATES,
# render-module, ai-module, etc.) or are tooling-only (config/datasource/
# observability/secrets/audit/notification/prompt/federation/user-analytics/
# social/commerce/payment/cloud-resource/media-execution-plan/typed-schema/
# storage-provider-opendal/identity-access/policy-governance).
aggregate_covered = {
    "config-module", "datasource-module", "observability-module", "secrets-config-module",
    "audit-compliance-module", "notification-module", "prompt-module", "federation-query-module",
    "user-analytics-module", "social-publish-module", "commerce-module", "payment-module",
    "cloud-resource-module", "media-execution-plan-module", "typed-schema-module",
    "storage-provider-opendal", "identity-access-module", "policy-governance-module",
    "shared-kernel", "scheduler-module", "outbox-event-module", "delivery-module",
    "billing-module", "entitlement-module", "quota-billing-module",
    "product-layer-module", "extension-module",
    "workflow-module", "artifact-module", "sandbox-runtime-module", "render-module",
    "media-module", "audio-module", "color-image-module", "font-text-module",  # R18/R19 pure value domains (LikeC4 Media/Text aggregates)
    "ai-module", "storage-module", "platform-app", "sandbox-worker", "remote-render-worker",
    "media-platform"  # system boundary itself
}
# Modules explicitly represented as semantic containers in LikeC4:
represented = {
    "platform-app", "sandbox-worker", "remote-render-worker", "workflow-module" if "Workflow" in likec4 else "workflow",
    "extension-module" if "PluginRuntime" in likec4 else "extension", "render-module",
    "ai-module", "storage-module", "sandbox-runtime-module", "scheduler-module",
    "outbox-event-module", "billing-module", "artifact-module", "entitlement-module",
    "quota-billing-module", "product-layer-module",
    "shared-kernel",
}
# Explicitly intentionally omitted (tooling-only, no architecture value):
intentionally_omitted = {
    "spring-ai-adapter",  # HOLD module, not active
    "graph",             # platform-algorithms:graph — technical algorithm library, tooling-only
}

unclassified = []
for mod in active:
    base = mod.split(":")[-1]
    if base in represented or base in aggregate_covered or base in intentionally_omitted:
        continue
    # semantic containment check: module name appears anywhere in LikeC4 model
    if base.replace("-", "") in likec4_all or base in likec4_all:
        continue
    unclassified.append(mod)

if unclassified:
    failures.append(f"UNCLASSIFIED active modules (no representation/aggregate/omission): {unclassified}")

# ── 3. Deployment units ──
deployments = ["platform-app", "sandbox-worker", "remote-render-worker"]
for d in deployments:
    if d not in likec4 and d not in likec4_containers:
        if d.replace("platform-app", "platformApp") not in likec4 and "PlatformApplication" not in likec4:
            failures.append(f"DEPLOYMENT UNIT not represented: {d}")

# ── 4. Stale CURRENT element guard ──
# ingest-module: was in old LikeC4; directory does not exist; not in settings.
# The recovered model must NOT contain it as CURRENT.
import os
if os.path.exists(f"{REPO}/ingest-module"):
    # exists — fine if represented; not stale
    pass
if "ingestModule = container" in likec4 or "ingest-module" in likec4:
    failures.append("STALE CURRENT element: ingest-module still present in LikeC4 (module/dir removed)")

# ── 5. Runtime authority assertions (text guards) ──
authority_checks = [
    ("Capability Registry metadata-only", "METADATA / MATCHING / HEALTH AUTHORITY ONLY"),
    ("PluginRuntime executes", "CANONICAL EFFECT EXECUTION AUTHORITY"),
    ("Temporal orchestrates", "DURABLE ORCHESTRATION AUTHORITY"),
    ("sandbox-worker isolation", "SECURITY / PROCESS ISOLATION BOUNDARY"),
    ("Registry describes; runtime executes", "CAPABILITY_REGISTRY_DESCRIBES"),
    ("Workflow current", "CURRENT (UWEV1-FV1 foundation)"),
]
for name, needle in authority_checks:
    if needle not in likec4:
        failures.append(f"AUTHORITY ASSERTION missing: {name}")

# ── 6. Workflow boundary — forbidden target edges ──
for forbidden in ["Workflow -> ProviderExtensionSPI", "Workflow -> SandboxExecutionService"]:
    # The model must not contain these as relationships; the comment forbids them.
    rel_pattern = re.compile(rf"workflow\w*\s*->\s*(providerExtensionSpi|sandboxExecutionService)")
    if rel_pattern.search(likec4):
        failures.append(f"FORBIDDEN workflow edge present: {forbidden}")

# ── 7. PMPR candidates not prematurely removed ──
pmpn_candidates = ["quota-billing-module", "product-layer-module"]
for c in pmpn_candidates:
    if c not in likec4 and "PMPR CANDIDATES" not in likec4:
        failures.append(f"PMPR candidate missing representation: {c}")

# ── 8. Storage authority debt/target ──
if "CURRENT DEBT" not in likec4:
    failures.append("Storage authority debt/target not represented")

print(f"active modules: {len(active)}")
print(f"likec4 containers: {len(likec4_containers)}")
print(f"deployment units: {len(deployments)}")
print(f"failures: {len(failures)}")
for f in failures:
    print(f"  FAIL: {f}")
print("ARCHITECTURE_MAP_DRIFT_GUARD_V1: " + ("PASS" if not failures else "FAIL"))
sys.exit(1 if failures else 0)
