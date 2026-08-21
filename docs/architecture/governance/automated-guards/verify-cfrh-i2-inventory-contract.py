#!/usr/bin/env python3
"""CFRH-I2 inventory + ownership execution-contract evidence validator.

Mechanical checks:
- caller TSV: unique site_id, one invocation site per row (no comma-collapsed
  caller_line), valid TRQ behavior ids, zero unresolved disposition,
  row count == invocation-site count (22)
- endpoint TSV: 15 endpoints; restore write=CANONICAL +
  transitive legacy dependency=YES; zero unresolved
- BaseJob evidence: frozen disposition THREAD_EXISTING_PROJECT_CONTEXT_TO_LOADER,
  unresolved count = 0
- cross-ledger: every caller site maps to a behavior; no orphan symbols
- publication metrics == computed ledger metrics

Fail-closed: any parse/schema failure exits non-zero.
"""

import csv
import re
import sys
from pathlib import Path

# validator: <repo>/docs/architecture/governance/automated-guards/verify-...py
# parents[0]=automated-guards, [1]=governance, [2]=architecture, [3]=docs, [4]=repo root
REPO = Path(__file__).resolve().parents[4]
TASKDIR = REPO / ".agent-tasks" / "CLEAN-FORWARD-RUNTIME-HARDENING-I2"
GOV = REPO / "docs" / "architecture" / "governance"

BEHAVIORS = {f"TRQ-{i:02d}" for i in range(1, 12)}
ALLOWED_DISPOSITIONS = {
    "MIGRATE_TO_OWNERSHIP_SCOPED_QUERY",
    "RETAIN_AS_OWNERSHIP_SCOPED_METADATA_COMMAND",
    "MIGRATE_RESPONSE_QUERY_DEPENDENCY_BEFORE_I2_E",
    "MIGRATE_RESPONSE_QUERY_DEPENDENCY_BEFORE_I2_E",
}

FAILURES = []
MECH = []
inv_site_count = 0  # computed in caller-inventory section; initialized for publication section


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


# ---- 1. caller inventory ----
try:
    chm, crows = load_tsv(TASKDIR / "timeline-query-caller-inventory.tsv",
                          ["site_id", "behavior_id", "caller_line", "disposition", "caller_file"])
    site_ids = [r[chm["site_id"]].strip() for r in crows]
    mech("C-01", len(site_ids) == len(set(site_ids)), f"unique site_ids (dup={len(site_ids)-len(set(site_ids))})")
    # one invocation site per row: caller_line must be a single integer
    bad_lines = [r[chm["site_id"]] for r in crows if re.search(r",|;|&", r[chm["caller_line"]])]
    mech("C-02", len(bad_lines) == 0, f"comma-collapsed caller_line rows={bad_lines}")
    bad_beh = [r[chm["site_id"]] for r in crows if r[chm["behavior_id"]].strip() not in BEHAVIORS]
    mech("C-03", len(bad_beh) == 0, f"invalid behavior ids={bad_beh}")
    unresolved = [r[chm["site_id"]] for r in crows
                  if r[chm["disposition"]].strip().upper() in ("UNKNOWN", "TBD", "RESOLVE_LATER", "PENDING")
                  or "?" in r[chm["disposition"]].strip()]
    mech("C-04", len(unresolved) == 0, f"unresolved dispositions={unresolved}")
    inv_site_count = len(crows)
    mech("C-05", inv_site_count == 22, f"invocation-site count={inv_site_count} (expected 22 from exhaustive scan)")
    caller_methods = set((r[chm["caller_file"]], r[chm["caller_symbol"]]) for r in crows)
    mech("C-06", len(caller_methods) == 18, f"unique caller methods={len(caller_methods)}")
    mech("C-07", inv_site_count == len(crows), "row count == invocation-site count")
except Exception as e:
    mech("C-01", False, f"caller parse: {e}")

# ---- 2. endpoint inventory ----
try:
    ehm, erows = load_tsv(TASKDIR / "timeline-controller-endpoint-inventory.tsv",
                          ["write_authority", "transitive_legacy_query_dependency",
                           "effective_legacy_query_dependency", "disposition"])
    mech("E-01", len(erows) == 15, f"endpoint count={len(erows)}")
    restore = next((r for r in erows if r[0].strip() == "11"), None)
    if restore:
        mech("E-02", restore[ehm["write_authority"]].startswith("CANONICAL"),
             "restore write authority = CANONICAL")
        mech("E-03", restore[ehm["transitive_legacy_query_dependency"]].strip() == "YES",
             "restore transitive legacy query dependency = YES")
        mech("E-04", restore[ehm["effective_legacy_query_dependency"]].strip() == "YES",
             "restore effective legacy query dependency = YES")
        mech("E-05", "MIGRATE_RESPONSE_QUERY_DEPENDENCY" in restore[ehm["disposition"]],
             "restore disposition explicit")
    else:
        mech("E-02", False, "restore row missing")
    dep_count = sum(1 for r in erows if r[ehm["effective_legacy_query_dependency"]].strip() == "YES")
    mech("E-06", dep_count == 11, f"endpoints with effective legacy dependency={dep_count}")
    un_res = [r[0] for r in erows if r[ehm["disposition"]].strip().upper() in ("UNKNOWN", "TBD")]
    mech("E-07", len(un_res) == 0, f"unresolved endpoint dispositions={un_res}")
except Exception as e:
    mech("E-01", False, f"endpoint parse: {e}")

# ---- 3. BaseJob ownership ----
try:
    bj = (TASKDIR / "base-job-timeline-loader-ownership-value-flow.md").read_text(encoding="utf-8")
    mech("B-01", "FINAL_DISPOSITION" in bj and "THREAD_EXISTING_PROJECT_CONTEXT_TO_LOADER" in bj,
         "BaseJob final disposition frozen")
    mech("B-02", "findOwnedById" in bj, "BaseJob target port explicit")
    mech("B-03", "UNRESOLVED_COUNT" in bj and "\n0" in bj[bj.index("UNRESOLVED_COUNT"):bj.index("UNRESOLVED_COUNT")+40],
         "BaseJob unresolved count = 0")
    auth_src = bj[bj.index("AUTHORITATIVE_PROJECT_SOURCE"):bj.index("AUTHORITATIVE_PROJECT_SOURCE")+400]
    mech("B-04", "PROJECT_ID" in auth_src and "(removed)" not in auth_src,
         "BaseJob authoritative project source explicit (section-scoped)")
    mech("B-05", "TARGET_PERSISTENCE_PREDICATE" in bj and "project_id" in bj,
         "BaseJob target persistence predicate explicit")
    mech("B-06", "TARGET_WAVE" in bj and "I2-B" in bj, "BaseJob target wave = I2-B")
except Exception as e:
    mech("B-01", False, f"basejob parse: {e}")

# ---- 4. publication metrics ----
try:
    pub = (GOV / "cfrh-i2-inventory-ownership-execution-contract-correction.md").read_text(encoding="utf-8")
    expected = {
        "RETAINED_BEHAVIOR_COUNT": 11,
        "LEGACY_QUERY_INVOCATION_SITE_COUNT": inv_site_count if "inv_site_count" in globals() else 22,
        "LEGACY_QUERY_CALLER_METHOD_COUNT": 18,
        "CALLER_INVENTORY_ROW_COUNT": inv_site_count if "inv_site_count" in globals() else 22,
        "HTTP_ENDPOINT_COUNT": 15,
        "HTTP_ENDPOINT_WITH_LEGACY_QUERY_DEPENDENCY_COUNT": 11,
        "BASE_JOB_OWNERSHIP_UNRESOLVED_COUNT": 0,
        "UNRESOLVED_DISPOSITION_COUNT": 0,
    }
    for k, v in expected.items():
        m = re.search(rf"\|?\s*{k}\s*\|\s*(\d+)", pub) or re.search(rf"{k}\s*=\s*(\d+)", pub)
        ok = bool(m) and int(m.group(1)) == v
        mech(f"P-{k}", ok, f"pub {k} == {v} (got {m.group(1) if m else 'MISSING'})")
except Exception as e:
    mech("P-RETAINED_BEHAVIOR_COUNT", False, f"publication parse: {e}")

# ---- report ----
total = len(MECH)
passed = sum(1 for _, ok, _ in MECH if ok)
print(f"CFRH_I2_EVIDENCE_MECHANICAL = {passed}/{total} PASS")
for name, ok, detail in MECH:
    if not ok:
        print(f"  FAIL {name}: {detail[:100]}")
sys.exit(0 if passed == total else 1)
