#!/usr/bin/env python3
"""CFRH-I2 final validator exactness closure validator.

F1 — FULL 4-TUPLE source<->ledger exact identity:
     (caller_class, caller_method, source_line, callee_symbol) set equality,
     NO 3-tuple fallback; behavior_id<->callee_symbol enforced via TRQ map.

F2 — REAL cross-ledger closure:
     repository-read-symbol-inventory (12 symbols) reconciled against
     ownership-read-manifest + SAFE KEEP + internal TRQ-support; orphan
     repository symbols and orphan ownership symbols computed separately
     from p1_extra_symbol_count.

F3 — authoritative-doc single truth: explicit doc set scanned for four stale
     patterns (19-caller, 14-site, ambiguous 11-query-endpoints, unmarked
     PRODUCTION_CALLER_COUNT=19); each claim must carry a
     HISTORICAL/REJECTED/SUPERSEDED/PREDECESSOR marker in local context.

F4 — EXACT required publication metric set (37 metrics): every required
     metric present and equal to its computed value; no checked>=N shortcut.

WHAT THE VALIDATOR CLAIMS = WHAT THE VALIDATOR ACTUALLY COMPUTES.
"""

import csv
import re
import sys
from pathlib import Path

# validator: <repo>/docs/architecture/governance/automated-guards/verify-...py
REPO = Path(__file__).resolve().parents[4]
TASKDIR = REPO / ".agent-tasks" / "CLEAN-FORWARD-RUNTIME-HARDENING-I2"
GOV = REPO / "docs" / "architecture" / "governance"

BEHAVIORS = {f"TRQ-{i:02d}" for i in range(1, 12)}
TRQ_METHODS = {
    "TRQ-01": "findHead", "TRQ-02": "findById", "TRQ-03": "getRevisionSnapshotPayload",
    "TRQ-04": "listHistory", "TRQ-05": "updateAnnotation", "TRQ-06": "listFacets",
    "TRQ-07": "listEditSessions", "TRQ-08": "compareRevisions", "TRQ-09": "previewPatchReplay",
    "TRQ-10": "previewPatchSteps", "TRQ-11": "getDetail",
}
METHOD_TO_TRQ = {v: k for k, v in TRQ_METHODS.items()}

FAILURES = []
MECH = []


def mech(name, ok, detail=""):
    MECH.append((name, ok, detail))
    if not ok:
        FAILURES.append((name, detail))


def load_tsv(path, required_cols):
    with open(path, encoding="utf-8") as f:
        rows = [r for r in csv.reader(f, delimiter="\t") if r and not r[0].startswith("#")]
    hdr = [h.strip() for h in rows[0]]
    for col in required_cols:
        if col not in hdr:
            raise ValueError(f"missing column {col} in {path}")
    hm = {h: i for i, h in enumerate(hdr)}
    return hm, rows[1:]


# ---------------------------------------------------------------- F1: source scan
def scan_production_sources():
    """Scan actual production Java sources for TRQ invocation sites.

    Returns set of (class_name, enclosing_method, line, callee_method).
    Handles single-line and multi-line (receiver on prev line) invocations.
    """
    sites = set()
    for base in ["platform-app", "render-module"]:
        for f in (REPO / base / "src" / "main").rglob("*.java"):
            _scan_file(f, sites)
    return sites


def _scan_file(f, sites):
    lines = f.read_text(encoding="utf-8", errors="replace").splitlines()
    # receiver field names of type TimelineRevisionService
    recv = set()
    for line in lines:
        if "TimelineRevisionService" in line and "private final" in line:
            m = re.search(r"private final\s+\S*TimelineRevisionService\s+(\w+);", line)
            if m:
                recv.add(m.group(1))
    if not recv:
        recv = {"revisionService", "timelineRevisionService"}
    # method declaration start lines: line_no -> method name
    method_starts = {}
    for i, line in enumerate(lines):
        m = re.search(r"(?:public|private|protected)\s+[\w<>\[\],.\s]+\s+(\w+)\s*\(", line)
        if m and "class " not in line and "//" not in line[:line.find(m.group(0))]:
            method_starts[i + 1] = m.group(1)

    def enclosing(line_no):
        cand = [ln for ln in method_starts if ln <= line_no]
        return method_starts[max(cand)] if cand else "?"

    for i, line in enumerate(lines):
        for meth in METHOD_TO_TRQ:
            # single-line: receiver.method( anywhere on the line
            for m in re.finditer(r"([A-Za-z_][\w]*)\.\s*" + meth + r"\s*\(", line):
                if m.group(1) in recv:
                    sites.add((f.stem, enclosing(i + 1), i + 1, meth))
            # multi-line: previous line ends with receiver, this line starts with .method(
            if i > 0 and re.match(r"^\s*\." + meth + r"\s*\(", line):
                prev = lines[i - 1].rstrip()
                if prev.endswith(tuple(recv)):
                    sites.add((f.stem, enclosing(i + 1), i + 1, meth))
    return sites


def enclosing_method_lookup():
    """Map (class, line) -> enclosing method for ledger reconciliation."""
    mapping = {}
    for base in ["platform-app", "render-module"]:
        for f in (REPO / base / "src" / "main").rglob("*.java"):
            lines = f.read_text(encoding="utf-8", errors="replace").splitlines()
            cur_method = None
            depth = 0
            for i, line in enumerate(lines):
                mth = re.search(r"(?:public|private|protected)\s+[\w<>\[\],.\s]+\s+(\w+)\s*\(", line)
                if mth and "class " not in line and depth == 0:
                    cur_method = mth.group(1)
                depth += line.count("{") - line.count("}")
                mapping[(f.stem, i + 1)] = cur_method
    return mapping


SOURCE_SITES = scan_production_sources()
METHOD_MAP = enclosing_method_lookup()

# ---------------------------------------------------------------- F1: ledger parse
chm, crows = load_tsv(TASKDIR / "timeline-query-caller-inventory.tsv",
                      ["site_id", "behavior_id", "caller_file", "caller_symbol",
                       "caller_line", "callee_symbol", "disposition"])

site_ids = [r[chm["site_id"]].strip() for r in crows]
mech("F1-01", len(site_ids) == len(set(site_ids)),
     f"unique site_ids (dups={len(site_ids)-len(set(site_ids))})")

# caller_line must be pure integer
bad_lines = [r[chm["site_id"]] for r in crows if not re.fullmatch(r"[0-9]+", r[chm["caller_line"]].strip())]
mech("F1-02", len(bad_lines) == 0, f"non-integer/compressed caller_line rows={bad_lines}")

# build ledger site set: (class, method, line, callee)
ledger_sites = set()
for r in crows:
    cls = r[chm["caller_file"]].replace(".java", "").strip()
    sym = r[chm["caller_symbol"]].strip()
    ln = int(r[chm["caller_line"]].strip())
    callee = r[chm["callee_symbol"]].strip()
    ledger_sites.add((cls, sym, ln, callee))

# reconcile: EXACT 4-tuple closure — no 3-tuple fallback
missing_ledger = SOURCE_SITES - ledger_sites
extra_ledger = ledger_sites - SOURCE_SITES

mech("F1-03", len(SOURCE_SITES) == 22, f"source invocation-site count={len(SOURCE_SITES)}")
mech("F1-04", len(ledger_sites) == 22, f"ledger invocation-site count={len(ledger_sites)}")
mech("F1-05", len(missing_ledger) == 0, f"missing ledger sites={sorted(missing_ledger)[:3]}")
mech("F1-06", len(extra_ledger) == 0, f"extra ledger sites={sorted(extra_ledger)[:3]}")
mech("F1-07", len(ledger_sites) == len(crows), "row count == ledger site count (no compression)")
mech("F1-08", SOURCE_SITES == ledger_sites,
     "FULL 4-TUPLE SET EQUALITY (class, method, line, callee) — no partial matching")

# behavior_id <-> callee_symbol mapping through frozen TRQ map
behavior_callee_mismatch = []
for r in crows:
    beh = r[chm["behavior_id"]].strip()
    callee = r[chm["callee_symbol"]].strip()
    if beh in TRQ_METHODS and TRQ_METHODS[beh] != callee:
        behavior_callee_mismatch.append((r[chm["site_id"]], beh, callee))
mech("F1-09", len(behavior_callee_mismatch) == 0,
     f"behavior/callee mismatches={behavior_callee_mismatch}")

# ---------------------------------------------------------------- F2: computed metrics
computed = {}
computed["caller_inventory_row_count"] = len(crows)
computed["ledger_invocation_site_count"] = len(ledger_sites)
computed["source_invocation_site_count"] = len(SOURCE_SITES)
computed["unique_caller_method_count"] = len({(r[chm["caller_file"]], r[chm["caller_symbol"]]) for r in crows})
computed["missing_ledger_site_count"] = len(missing_ledger)
computed["extra_ledger_site_count"] = len(extra_ledger)
computed["duplicate_ledger_site_count"] = len(site_ids) - len(set(site_ids))
computed["unresolved_caller_disposition_count"] = sum(
    1 for r in crows if r[chm["disposition"]].strip().upper() in ("UNKNOWN", "TBD", "RESOLVE_LATER", "PENDING")
    or "?" in r[chm["disposition"]].strip())

# expected contract values (frozen)
expected = {
    "source_invocation_site_count": 22,
    "ledger_invocation_site_count": 22,
    "unique_caller_method_count": 18,
    "caller_inventory_row_count": 22,
    "missing_ledger_site_count": 0,
    "extra_ledger_site_count": 0,
    "duplicate_ledger_site_count": 0,
    "unresolved_caller_disposition_count": 0,
}
for k, v in expected.items():
    mech(f"F2-{k}", computed[k] == v, f"{k}: computed={computed[k]} expected={v}")

# ---------------------------------------------------------------- F3: endpoint closure
ehm, erows = load_tsv(TASKDIR / "timeline-controller-endpoint-inventory.tsv",
                      ["write_authority", "direct_legacy_query_dependency",
                       "transitive_legacy_query_dependency",
                       "effective_legacy_query_dependency", "disposition"])
computed["http_endpoint_count"] = len(erows)
computed["direct_legacy_query_endpoint_count"] = sum(
    1 for r in erows if r[ehm["direct_legacy_query_dependency"]].strip() == "YES")
computed["transitive_only_legacy_query_endpoint_count"] = sum(
    1 for r in erows
    if r[ehm["direct_legacy_query_dependency"]].strip() == "NO"
    and r[ehm["transitive_legacy_query_dependency"]].strip() == "YES")
computed["effective_legacy_query_dependency_endpoint_count"] = sum(
    1 for r in erows if r[ehm["effective_legacy_query_dependency"]].strip() == "YES")
computed["canonical_write_authority_endpoint_count"] = sum(
    1 for r in erows if r[ehm["write_authority"]].startswith("CANONICAL"))
computed["unresolved_endpoint_count"] = sum(
    1 for r in erows if r[ehm["disposition"]].strip().upper() in ("UNKNOWN", "TBD"))

mech("F3-01", computed["http_endpoint_count"] == 15, f"http_endpoint_count={computed['http_endpoint_count']}")
mech("F3-02", computed["effective_legacy_query_dependency_endpoint_count"] == 11,
     f"effective dep endpoints={computed['effective_legacy_query_dependency_endpoint_count']}")
# coherence: effective == direct OR transitive
contradictions = 0
for r in erows:
    direct = r[ehm["direct_legacy_query_dependency"]].strip() == "YES"
    trans = r[ehm["transitive_legacy_query_dependency"]].strip() == "YES"
    eff = r[ehm["effective_legacy_query_dependency"]].strip() == "YES"
    if eff != (direct or trans):
        contradictions += 1
mech("F3-03", contradictions == 0, f"endpoint dependency contradictions={contradictions}")
restore = next((r for r in erows if r[0].strip() == "11"), None)
if restore:
    mech("F3-04", restore[ehm["write_authority"]].startswith("CANONICAL"), "restore write=CANONICAL")
    mech("F3-05", restore[ehm["direct_legacy_query_dependency"]].strip() == "NO", "restore direct=NO")
    mech("F3-06", restore[ehm["transitive_legacy_query_dependency"]].strip() == "YES", "restore transitive=YES")
    mech("F3-07", restore[ehm["effective_legacy_query_dependency"]].strip() == "YES", "restore effective=YES")
mech("F3-08", computed["unresolved_endpoint_count"] == 0, f"unresolved endpoints={computed['unresolved_endpoint_count']}")

# ---------------------------------------------------------------- F3: P1 exact symbol set
GOVDOC = GOV / "clean-forward-runtime-hardening-decision-recovery-v1.md"
gov_txt = GOVDOC.read_text(encoding="utf-8")
p1_map = {}
in_table = False
for line in gov_txt.splitlines():
    if "P1 enforcement mapping" in line:
        in_table = True
        continue
    if in_table and line.startswith("###"):
        in_table = False
    if in_table and line.startswith("|") and "P1 symbol" not in line and "---" not in line:
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) >= 2 and cells[0]:
            p1_map[cells[0]] = cells[1]
p1_expected = {"TimelineSnapshotService.findPayload",
               "TimelineSnapshotService.findById",
               "TimelineSnapshotService.findLatestByProject",
               "TimelineRevisionRepository.findById",
               "TimelineRevisionRepository.findHeadByProject",
               "TimelineRevisionRepository.listByProject",
               "TimelineRevisionService legacy read authority",
               "TimelineSnapshotService.listDistinctProjectIds"}
p1_actual = set(p1_map.keys())
p1_missing = p1_expected - p1_actual
p1_extra = p1_actual - p1_expected
mech("F3-09", len(p1_expected) == 8, f"P1 expected={len(p1_expected)}")
mech("F3-10", len(p1_actual) == 8, f"P1 actual={len(p1_actual)}")
mech("F3-11", len(p1_missing) == 0, f"P1 missing={sorted(p1_missing)}")
mech("F3-12", len(p1_extra) == 0, f"P1 extra={sorted(p1_extra)}")

# ownership classifications: parse cfrh-i2 decision-recovery doc §4 table
I2GOVDOC = GOV / "cfrh-i2-timeline-read-ownership-query-closure-decision-recovery.md"
i2gov = I2GOVDOC.read_text(encoding="utf-8")
cat_counts = {}
in_sec4 = False
for line in i2gov.splitlines():
    if line.startswith("## 4. Ownership inventory"):
        in_sec4 = True
        continue
    if in_sec4 and line.startswith("## "):
        break
    if in_sec4 and line.startswith("|") and "Category" not in line and "---" not in line:
        cells = [c.strip() for c in line.strip("|").split("|")]
        if len(cells) >= 2 and cells[0] and cells[1].isdigit():
            cat_counts[cells[0]] = int(cells[1])

forbidden = cat_counts.get("A PRODUCTION_AMBIENT_GLOBAL_READ_FORBIDDEN", -1)
tenant_unv = cat_counts.get("B PROJECT_SCOPED_BUT_TENANT_NOT_VERIFIED", -1)
load_check = cat_counts.get("C LOAD_THEN_CHECK_OWNERSHIP", -1)
sys_auth = cat_counts.get("D EXPLICIT_SYSTEM_AUTHORITY_EXCEPTION", -1)
already_safe = cat_counts.get("G ALREADY_OWNERSHIP_SCOPED", -1)
legacy_only = cat_counts.get("E LEGACY_SERVICE_ONLY_AND_DISAPPEARS_WITH_I2", -1)
safe_keep = cat_counts.get("SAFE (KEEP)", -1)
surface_count = forbidden + tenant_unv + load_check + sys_auth + legacy_only + already_safe
computed["ownership_surface_count"] = surface_count
mech("F3-13", surface_count == 19, f"ownership_surface_count={surface_count} (from §4 table)")
mech("F3-14", forbidden == 5, f"forbidden_ambient_global={forbidden}")
mech("F3-15", tenant_unv == 6, f"tenant_unverified={tenant_unv}")
mech("F3-16", load_check == 5, f"load_then_check={load_check}")
mech("F3-17", sys_auth == 1, f"system_authority_exception={sys_auth}")
mech("F3-18", already_safe == 1 and safe_keep == 1,
     f"already_safe={already_safe} safe_keep={safe_keep}")

# unknown caller behavior
unknown_beh = [r[chm["site_id"]] for r in crows if r[chm["behavior_id"]].strip() not in BEHAVIORS]
mech("F3-19", len(unknown_beh) == 0, f"unknown caller behavior={unknown_beh}")

# orphan callers: every ledger caller class must exist in production source
prod_classes = {f.stem for base in ["platform-app", "render-module"] for f in (REPO / base / "src" / "main").rglob("*.java")}
orphan_callers = [r[chm["site_id"]] for r in crows if r[chm["caller_file"]].replace(".java", "") not in prod_classes]
mech("F3-20", len(orphan_callers) == 0, f"orphan callers={orphan_callers}")

# P1 symbols must appear in the ownership manifest
ohm2, orows2 = load_tsv(TASKDIR / "ownership-read-manifest.tsv", ["symbol"])
manifest_symbols = {r[ohm2["symbol"]].strip() for r in orows2}
p1_in_manifest = p1_expected.intersection(manifest_symbols)
mech("F3-21", len(p1_in_manifest) >= 8, f"P1 symbols present in ownership manifest={len(p1_in_manifest)}")

# ---------------------------------------------------------------- F2: repository-symbol cross-ledger closure
rhm, rrows = load_tsv(TASKDIR / "repository-read-symbol-inventory.tsv",
                      ["symbol", "safe", "system_only", "legacy_only", "to_delete"])
repo_symbols = [r[rhm["symbol"]].strip() for r in rrows]
repo_symbol_set = set(repo_symbols)

# governance classification for each repository symbol:
#   A: present in ownership manifest with explicit disposition
#   B: SAFE KEEP (findOwnedById — canonical safe API, §4 SAFE(KEEP) category)
#   C: system-authority symbol (listDistinctProjectIds)
#   D: internal supporting method governed transitively by an accepted TRQ
#      behavior (repo.updateAnnotation -> TRQ-05; listDistinctSources/listAuthorFacets
#      -> TRQ-06; listEditSessions -> TRQ-07)
#   E: ORPHAN / UNGOVERNED
SAFE_KEEP = {"TimelineSnapshotService.findOwnedById"}
INTERNAL_SUPPORT = {
    "TimelineRevisionRepository.updateAnnotation": "TRQ-05",
    "TimelineRevisionRepository.listDistinctSources": "TRQ-06",
    "TimelineRevisionRepository.listAuthorFacets": "TRQ-06",
    "TimelineRevisionRepository.listEditSessions": "TRQ-07",
}
orphan_repo_symbols = []
for sym in repo_symbols:
    if sym in manifest_symbols:
        continue  # A: manifest-governed
    if sym in SAFE_KEEP:
        continue  # B: safe keep
    if sym in INTERNAL_SUPPORT:
        continue  # D: internal support for accepted behavior
    orphan_repo_symbols.append(sym)  # E: orphan

computed["repository_symbol_count"] = len(repo_symbols)
computed["governed_repository_symbol_count"] = len(repo_symbols) - len(orphan_repo_symbols)
computed["orphan_repository_symbol_count"] = len(orphan_repo_symbols)
mech("F2-01", len(repo_symbols) == 12, f"repository_symbol_count={len(repo_symbols)}")
mech("F2-02", len(orphan_repo_symbols) == 0, f"orphan repository symbols={orphan_repo_symbols}")
mech("F2-03", computed["governed_repository_symbol_count"] == 12,
     f"governed_repository_symbol_count={computed['governed_repository_symbol_count']}")

# orphan ownership-manifest symbols: no repository/source/P1/system/safe correspondence
ORPHAN_CHECK_MANIFEST_OK = set()
for sym in manifest_symbols:
    if sym in repo_symbol_set or sym in SAFE_KEEP:
        ORPHAN_CHECK_MANIFEST_OK.add(sym)
    elif sym == "TimelineRevisionService legacy read authority":
        ORPHAN_CHECK_MANIFEST_OK.add(sym)  # service-level aggregate (caller ledger governs)
    elif sym == "TimelineRevisionService.updateAnnotation":
        ORPHAN_CHECK_MANIFEST_OK.add(sym)  # TRQ-05 aggregate (repo.updateAnnotation internal)
    elif sym == "TimelineMergeEngine.loadRevision/loadPayload":
        ORPHAN_CHECK_MANIFEST_OK.add(sym)  # merge engine surface (I2-D scope)
orphan_manifest_symbols = manifest_symbols - ORPHAN_CHECK_MANIFEST_OK
computed["orphan_ownership_symbol_count"] = len(orphan_manifest_symbols)
mech("F2-04", len(orphan_manifest_symbols) == 0, f"orphan ownership symbols={sorted(orphan_manifest_symbols)}")

# ---------------------------------------------------------------- F3: authoritative doc single truth
# finite explicit authoritative doc set (current docs, not history)
AUTH_DOCS = [
    TASKDIR / "cfrh-i2-execution-plan.md",
    GOV / "cfrh-i2-timeline-read-ownership-query-closure-decision-recovery.md",
    GOV / "cfrh-i2-inventory-ownership-execution-contract-correction.md",
    GOV / "cfrh-i2-final-mechanical-evidence-closure.md",
    GOV / "cfrh-i2-final-validator-exactness-closure.md",
]
docs_txt = "\n".join(p.read_text(encoding="utf-8", errors="replace")
                     for p in AUTH_DOCS if p.exists())

HIST_MARK = r"HISTORICAL|REJECTED|SUPERSEDED|PREDECESSOR|PREDECESSOR_METRIC"


def stale_in_docs(pattern, label):
    """Return list of unmarked current-state claims matching pattern."""
    hits = []
    for m in re.finditer(pattern, docs_txt, re.I):
        window = docs_txt[max(0, m.start() - 150):m.end() + 150]
        if not re.search(HIST_MARK, window):
            hits.append(m.group(0)[:60])
    return hits


# A. stale 19-caller / current caller count
stale_19 = stale_in_docs(r"\b19\s*(?:callers|production callers|caller sites)", "19-caller")
computed["stale_19_caller_claim_count"] = len(stale_19)
mech("F3-01", len(stale_19) == 0, f"stale '19 caller' current-state claims={stale_19}")

# B. stale 14-site wording
stale_14 = stale_in_docs(r"\b14\s*(?:sites|caller sites|invocation sites)", "14-site")
computed["stale_14_site_claim_count"] = len(stale_14)
mech("F3-02", len(stale_14) == 0, f"stale '14 site' current-state claims={stale_14}")

# C. ambiguous naked "11 query endpoints" as current truth
amb_11 = stale_in_docs(r"11\s*query\s*endpoints", "11-query-endpoints")
computed["ambiguous_11_query_endpoint_claim_count"] = len(amb_11)
mech("F3-03", len(amb_11) == 0,
     f"ambiguous '11 query endpoints' current-state claims={amb_11}")

# D. unmarked PRODUCTION_CALLER_COUNT = 19
unmarked_19 = stale_in_docs(r"PRODUCTION_CALLER_COUNT\s*=\s*19", "unmarked-PC19")
computed["unmarked_rejected_production_caller_metric_count"] = len(unmarked_19)
mech("F3-04", len(unmarked_19) == 0, f"unmarked PRODUCTION_CALLER_COUNT=19={unmarked_19}")

# ---------------------------------------------------------------- F4: required publication metric set
pub = (GOV / "cfrh-i2-final-validator-exactness-closure.md").read_text(encoding="utf-8")
metric_lines = {}
for m in re.finditer(r"^([a-z_][a-z0-9_]*)\s*=\s*(\d+)\s*$", pub, re.M):
    metric_lines[m.group(1)] = int(m.group(2))

computed_all = dict(computed)
computed_all.update({
    "ownership_surface_count": computed["ownership_surface_count"],
    "p1_symbol_count": len(p1_expected),
    "forbidden_ambient_global_count": forbidden,
    "tenant_unverified_count": tenant_unv,
    "load_then_check_count": load_check,
    "system_authority_exception_count": sys_auth,
    "already_safe_count": already_safe,
    "safe_keep_count": safe_keep,
    "orphan_caller_count": len(orphan_callers),
    "orphan_symbol_count": len(p1_extra),
    "unknown_caller_behavior_count": len(unknown_beh),
    "p1_expected_symbol_count": len(p1_expected),
    "p1_actual_symbol_count": len(p1_actual),
    "p1_missing_symbol_count": len(p1_missing),
    "p1_extra_symbol_count": len(p1_extra),
    "http_endpoint_count": computed["http_endpoint_count"],
    "direct_legacy_query_endpoint_count": computed["direct_legacy_query_endpoint_count"],
    "transitive_only_legacy_query_endpoint_count": computed["transitive_only_legacy_query_endpoint_count"],
    "effective_legacy_query_dependency_endpoint_count": computed["effective_legacy_query_dependency_endpoint_count"],
    "unresolved_endpoint_count": computed["unresolved_endpoint_count"],
    "endpoint_contradiction_count": contradictions,
    "behavior_callee_mismatch_count": len(behavior_callee_mismatch),
    "invalid_caller_line_count": len(bad_lines),
})

REQUIRED_PUBLICATION_METRIC_SET = {
    "caller_inventory_row_count", "source_invocation_site_count",
    "ledger_invocation_site_count", "unique_caller_method_count",
    "missing_ledger_site_count", "extra_ledger_site_count",
    "duplicate_ledger_site_count", "unresolved_caller_disposition_count",
    "behavior_callee_mismatch_count", "invalid_caller_line_count",
    "http_endpoint_count", "direct_legacy_query_endpoint_count",
    "transitive_only_legacy_query_endpoint_count",
    "effective_legacy_query_dependency_endpoint_count",
    "unresolved_endpoint_count", "endpoint_contradiction_count",
    "ownership_surface_count",
    "p1_symbol_count", "p1_expected_symbol_count", "p1_actual_symbol_count",
    "p1_missing_symbol_count", "p1_extra_symbol_count",
    "forbidden_ambient_global_count", "tenant_unverified_count",
    "load_then_check_count", "system_authority_exception_count",
    "already_safe_count", "safe_keep_count",
    "repository_symbol_count", "governed_repository_symbol_count",
    "orphan_repository_symbol_count", "orphan_ownership_symbol_count",
    "orphan_caller_count", "unknown_caller_behavior_count",
    "stale_19_caller_claim_count", "stale_14_site_claim_count",
    "ambiguous_11_query_endpoint_claim_count",
}

missing_required = sorted(REQUIRED_PUBLICATION_METRIC_SET - set(metric_lines.keys()))
pub_mismatch = []
checked_required = 0
for k in sorted(REQUIRED_PUBLICATION_METRIC_SET):
    if k in metric_lines:
        checked_required += 1
        if metric_lines[k] != computed_all[k]:
            pub_mismatch.append((k, metric_lines[k], computed_all[k]))
mech("F4-01", len(missing_required) == 0,
     f"missing required publication metrics={missing_required}")
mech("F4-02", len(pub_mismatch) == 0, f"publication/computed mismatches={pub_mismatch}")
mech("F4-03", checked_required == len(REQUIRED_PUBLICATION_METRIC_SET),
     f"checked_required={checked_required} required={len(REQUIRED_PUBLICATION_METRIC_SET)}")

# ---------------------------------------------------------------- report
total = len(MECH)
passed = sum(1 for _, ok, _ in MECH if ok)
print(f"CFRH_I2_FINAL_MECHANICAL_EVIDENCE = {passed}/{total} PASS")
for name, ok, detail in MECH:
    if not ok:
        print(f"  FAIL {name}: {detail[:140]}")
sys.exit(0 if passed == total else 1)
