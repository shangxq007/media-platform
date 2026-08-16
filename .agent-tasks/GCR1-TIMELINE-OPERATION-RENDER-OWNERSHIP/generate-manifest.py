#!/usr/bin/env python3
"""GCR-1 Phase A: migration manifest generator (rule-based classification + overrides)."""
import os, re, csv, collections, subprocess

WT = "/home/user/Documents/workspace/projects/media-platform/.worktrees/gcr1-timeline-operation-render-ownership"
RENDER = f"{WT}/render-module/src/main/java"
OUT = "/tmp/GCR1_EVIDENCE"
os.makedirs(OUT, exist_ok=True)

def pkg_of(path):
    rel = os.path.relpath(path, RENDER)
    return rel[:-5].replace(os.sep, ".")

def kind_of(content):
    m = re.search(r"(?m)^(?:public\s+)?(?:final\s+|abstract\s+|sealed\s+|non-sealed\s+|static\s+)*(class|record|enum|interface)\s+(\w+)", content)
    return (m.group(1), m.group(2)) if m else ("?", "?")

# --- collect production files ---
files = []
for root, _, fnames in os.walk(RENDER):
    for fn in fnames:
        if fn.endswith(".java"):
            files.append(os.path.join(root, fn))
files.sort()

# --- operation-module additional surface: render.domain.plan + render.app.plan (§11) ---
OP_PLAN_EXTRA = {
    "ApplyContext": ("OPERATION_APPLICATION", "operation-module", "plan"),
    "ApplyResult": ("OPERATION_APPLICATION", "operation-module", "plan"),
    "AuthorizationDecision": ("OPERATION_APPLICATION", "operation-module", "plan"),
    "OperationPlanDigest": ("OPERATION_CANONICAL", "operation-module", "plan"),
    "OperationPlan": ("OPERATION_CANONICAL", "operation-module", "plan"),
    "OperationPlanner": ("OPERATION_CANONICAL", "operation-module", "plan"),
    "OperationPlanPreview": ("OPERATION_CANONICAL", "operation-module", "plan"),
    "PlanErrorCode": ("OPERATION_APPLICATION", "operation-module", "plan"),
    "PlanException": ("OPERATION_APPLICATION", "operation-module", "plan"),
    "PlannedChange": ("OPERATION_CANONICAL", "operation-module", "plan"),
    "TargetRevisionRef": ("OPERATION_APPLICATION", "operation-module", "plan"),
}
OP_APP_EXTRA = {
    "OperationPlanApplyService": ("OPERATION_APPLICATION", "operation-module", "app"),
}

# --- caller count: occurrences of the simple type name in OTHER production files repo-wide ---
def caller_count(simple_name):
    if len(simple_name) < 3:
        return 0
    r = subprocess.run(["grep", "-rl", rf"\b{re.escape(simple_name)}\b", f"{WT}/render-module/src/main", f"{WT}/render-module/src/test", f"{WT}/platform-app/src/main", f"{WT}/platform-app/src/test"], capture_output=True, text=True)
    hits = [l for l in r.stdout.splitlines() if l.strip()]
    return len(hits)

# --- classification rules: package prefix -> (category, final_module, final_package_suffix) ---
# final_package_suffix None => stays under render module (re-home to render.domain.<suffix>)
RULES = [
    # (prefix, category, module, final_suffix)
    ("com.example.platform.render.domain.timeline.canonical.", "TIMELINE_CANONICAL", "timeline-module", "canonical"),
    ("com.example.platform.render.domain.timeline.canonicalmodel.", "TIMELINE_CANONICAL", "timeline-module", "canonicalmodel"),
    ("com.example.platform.render.domain.timeline.semantics.", "TIMELINE_CANONICAL", "timeline-module", "semantics"),
    ("com.example.platform.render.domain.timeline.diff.", "TIMELINE_CANONICAL", "timeline-module", "diff"),
    ("com.example.platform.render.domain.timeline.patch.", "TIMELINE_CANONICAL", "timeline-module", "patch"),
    ("com.example.platform.render.domain.timeline.version.", "TIMELINE_APPLICATION", "timeline-module", "version"),
    ("com.example.platform.render.domain.timeline.render.effect.", "RENDER_EFFECT", "render-module", "effect"),
    ("com.example.platform.render.domain.timeline.render.plan.", "RENDER_PLANNING", "render-module", "plan"),
    ("com.example.platform.render.domain.timeline.render.transition.", "RENDER_TRANSITION", "render-module", "transition"),
    ("com.example.platform.render.domain.timeline.compile.", "RENDER_PLANNING", "render-module", "compile"),
    ("com.example.platform.render.domain.timeline.standards.", "INTERCHANGE", "render-module", "standards"),
    ("com.example.platform.render.domain.operation.", "OPERATION_CANONICAL", "operation-module", "operation"),
]
# root timeline package special cases
ROOT_SPECIAL = {
    "TimelineSpec": ("RENDER_PROJECTION", "render-module", "interchange"),
    "TimelineTextOverlay": ("RENDER_PROJECTION", "render-module", "interchange"),
    "TimelineScriptParser": ("INTERCHANGE", "render-module", "interchange"),
    "OpenTimelineioAdapter": ("INTERCHANGE", "render-module", "interchange"),
    "TimelineExtensions": ("INTERCHANGE", "render-module", "interchange"),
    "TimelineExtensionsReader": ("INTERCHANGE", "render-module", "interchange"),
    "TimelineOutputSpec": ("RENDER_PLANNING", "render-module", "interchange"),
    "TimelineAudioSpec": ("RENDER_PLANNING", "render-module", "interchange"),
    "TimelineClip": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),  # root legacy (canonical.TimelineClip is authority)
    "TimelineTrack": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),
    "TimelineTransition": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),
    "TimelineMarker": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),
    "TimelineSegment": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),
    "TimelineSticker": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),
    "TimelineStickerReader": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),
    "TimelineClipEffect": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),
    "TimelineValidationResult": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),
    "TimelineAssetRef": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),
    "TimelinePlatformMetadata": ("OBSOLETE_UNSHIPPED", "render-module", "interchange"),
    "ExternalRenderNode": ("RENDER_PLANNING", "render-module", "interchange"),
    "FinalComposerHint": ("RENDER_PLANNING", "render-module", "interchange"),
    "SegmentPolicy": ("RENDER_PLANNING", "render-module", "interchange"),
}
# internal.* package special cases
INTERNAL_SPECIAL = {
    "IncrementalRenderPlan": ("RENDER_PLANNING", "render-module", "planning"),
    "IncrementalTask": ("RENDER_PLANNING", "render-module", "planning"),
    "ReusableArtifact": ("RENDER_PLANNING", "render-module", "planning"),
    "RenderImpactResult": ("RENDER_PLANNING", "render-module", "planning"),
    "DirtyScope": ("RENDER_PLANNING", "render-module", "planning"),
    "TimelineReview": ("TIMELINE_APPLICATION", "timeline-module", "review"),
    "TimelineComment": ("TIMELINE_APPLICATION", "timeline-module", "review"),
    "ReviewDecision": ("TIMELINE_APPLICATION", "timeline-module", "review"),
    "ReviewTargetType": ("TIMELINE_APPLICATION", "timeline-module", "review"),
    "ReviewThread": ("TIMELINE_APPLICATION", "timeline-module", "review"),
    "TimelineResolutionIntent": ("TIMELINE_APPLICATION", "timeline-module", "internal"),
    "TimelineMergeRequest": ("TIMELINE_CANONICAL", "timeline-module", "internal"),
    "TimelineMergeResult": ("TIMELINE_CANONICAL", "timeline-module", "internal"),
    "TimelineMergeSummary": ("TIMELINE_CANONICAL", "timeline-module", "internal"),
    "TimelineConflict": ("TIMELINE_CANONICAL", "timeline-module", "internal"),
    "TimelineConflictType": ("TIMELINE_CANONICAL", "timeline-module", "internal"),
    "SemanticChange": ("TIMELINE_CANONICAL", "timeline-module", "internal"),
    "SemanticChangeType": ("TIMELINE_CANONICAL", "timeline-module", "internal"),
    "SemanticDiffResult": ("TIMELINE_CANONICAL", "timeline-module", "internal"),
    "EntityKind": ("TIMELINE_CANONICAL", "timeline-module", "internal"),
    "EntityRef": ("TIMELINE_CANONICAL", "timeline-module", "internal"),
}

rows = []
tl_ns = 0; op_ns = 0
for f in files:
    p = pkg_of(f)
    if not (p.startswith("com.example.platform.render.domain.timeline")
            or p.startswith("com.example.platform.render.domain.operation")
            or p.startswith("com.example.platform.render.domain.plan")
            or p.startswith("com.example.platform.render.app.plan")):
        continue
    with open(f, encoding="utf-8", errors="replace") as fh:
        content = fh.read()
    kind, simple = kind_of(content)
    base = os.path.basename(f)[:-5]
    pkg_root = p.rsplit(".", 1)[0] if p.endswith("." + base) else p
    category = module = suffix = None
    if pkg_root == "com.example.platform.render.domain.timeline" and base in ROOT_SPECIAL:
        category, module, suffix = ROOT_SPECIAL[base]
    elif pkg_root == "com.example.platform.render.domain.timeline.internal" and base in INTERNAL_SPECIAL:
        category, module, suffix = INTERNAL_SPECIAL[base]
    elif base.endswith("package-info"):
        category, module, suffix = "PACKAGE_INFO", "render-module", "package-info"
    elif pkg_root == "com.example.platform.render.domain.plan" and base in OP_PLAN_EXTRA:
        category, module, suffix = OP_PLAN_EXTRA[base]
    elif pkg_root == "com.example.platform.render.app.plan" and base in OP_APP_EXTRA:
        category, module, suffix = OP_APP_EXTRA[base]
    elif pkg_root.startswith("com.example.platform.render.app.planner") or pkg_root.startswith("com.example.platform.render.domain.planner"):
        category, module, suffix = "RENDER_PLANNING", "render-module", "planner"
    else:
        for prefix, cat, mod, suf in RULES:
            if p.startswith(prefix):
                category, module, suffix = cat, mod, suf
                break
    if category is None:
        category, module, suffix = "UNCLASSIFIED", "?", "?"
    if p.startswith("com.example.platform.render.domain.timeline"):
        tl_ns += 1
    elif p.startswith("com.example.platform.render.domain.operation"):
        op_ns += 1
    final_pkg = f"com.example.platform.{module.replace('-module','')}.{suffix}" if module != "render-module" else f"com.example.platform.render.domain.{suffix}"
    disposition = "MOVE" if module != "render-module" else ("RENAME" if "render" in p else "KEEP")
    rows.append([os.path.relpath(f, WT), p, simple, kind, category, module, final_pkg, disposition, caller_count(simple)])

rows.sort(key=lambda r: (r[1], r[2]))
with open(f"{OUT}/migration-manifest.tsv", "w", newline="") as fh:
    w = csv.writer(fh, delimiter="\t")
    w.writerow(["CURRENT_PATH", "CURRENT_PACKAGE", "TYPE", "KIND", "CLASSIFICATION", "FINAL_MODULE", "FINAL_PACKAGE", "DISPOSITION", "CALLER_FILES"])
    w.writerows(rows)

# summary
cat_counts = collections.Counter(r[4] for r in rows)
mod_counts = collections.Counter(r[5] for r in rows)
print(f"TIMELINE_NAMESPACE_PRODUCTION_FILES = {tl_ns}")
print(f"OPERATION_NAMESPACE_PRODUCTION_FILES = {op_ns}")
print(f"TOTAL_MANIFEST_ROWS = {len(rows)}")
print("\n分类汇总:")
for c, n in sorted(cat_counts.items()):
    print(f"  {c:50s} {n}")
print("\n目标模块汇总:")
for m, n in sorted(mod_counts.items()):
    print(f"  {m:20s} {n}")
print(f"\nmanifest: {OUT}/migration-manifest.tsv")
