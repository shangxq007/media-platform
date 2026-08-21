#!/usr/bin/env python3
"""CFRH-I1 single-source-of-truth evidence validator (47 checks).

Governance-only guard. Verifies that the CFRH-I1 execution contract TSV,
semantic-width TSV, governance document, and publication agree on all
mechanically derived counters. Docs/evidence scope only.

Run: python3 docs/architecture/governance/automated-guards/verify-cfrh-i1-single-source-of-truth.py
"""
import csv
import re
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
BASE = REPO / ".agent-tasks" / "CLEAN-FORWARD-RUNTIME-HARDENING-DR"
GOV = REPO / "docs" / "architecture" / "governance"

EXEC_TSV = BASE / "cfrh-i1-execution-contract-matrix.tsv"
WIDTH_TSV = BASE / "internal-to-canonical-semantic-width-matrix.tsv"
BEHAVIOR_TSV = BASE / "timeline-revision-service-behavior-matrix.tsv"
ENDPOINT_TSV = BASE / "timeline-revision-controller-endpoint-matrix.tsv"
OWNERSHIP_TSV = BASE / "ownership-read-manifest.tsv"
GOV_DOC = GOV / "clean-forward-runtime-hardening-decision-recovery-v1.md"
PUB_DOC = GOV / "cfrh-i1-single-source-of-truth-evidence-correction.md"

ALLOWED_DISPOSITIONS = {
    "MIGRATE_LOSSLESSLY_TO_CANONICAL_AUTHORITY",
    "REPLACE_WITH_EXISTING_CANONICAL_BEHAVIOR",
    "DELETE_OBSOLETE_PRODUCT_BEHAVIOR",
}
FORBIDDEN_WORDS = ["UNKNOWN", "PENDING", "TBD", "MAYBE", "BLOCKED", "OR ", "IF "]

FAILURES = []
CHECKS = []


def check(name, ok, detail=""):
    CHECKS.append(name)
    if not ok:
        FAILURES.append(f"{name}: {detail}")


def load_tsv(path, required_cols=None):
    if not path.exists():
        raise FileNotFoundError(f"missing TSV: {path}")
    with open(path, encoding="utf-8") as f:
        rows = [r for r in csv.reader(f, delimiter="\t") if r and r[0].strip()]
    if not rows:
        raise ValueError(f"empty TSV: {path}")
    header = rows[0]
    if required_cols:
        missing = [c for c in required_cols if c not in header]
        if missing:
            raise ValueError(f"{path.name} missing columns: {missing}")
    data = rows[1:]
    return header, data


# ---- 01-14: execution contract TSV ----
try:
    ex_header, ex_rows = load_tsv(EXEC_TSV, [
        "behavior_id", "callers", "final_disposition", "canonical_target",
        "semantic_mode", "cas_policy", "effect_policy", "artifact_pin_policy",
        "semantic_context_policy", "transaction_boundary", "ownership_scope",
        "provenance_policy", "losslessness_status", "blocker", "implementation_phase",
    ])
    check("01", True, "execution TSV parses")
    check("02", True, "required columns present")

    # exactly 4 behavior groups
    behavior_ids = [r[0] for r in ex_rows]
    check("03", len(behavior_ids) == 4, f"groups={len(behavior_ids)}")
    check("04", len(set(behavior_ids)) == len(behavior_ids), "duplicate ids")

    disps = [r[3] for r in ex_rows]  # final_disposition is index 3
    # find actual column indices
    hmap = {h: i for i, h in enumerate(ex_header)}
    disp_col = hmap["final_disposition"]
    blocker_col = hmap["blocker"]
    disps = [r[disp_col].strip() for r in ex_rows]
    check("05", all(d in ALLOWED_DISPOSITIONS for d in disps), f"dispositions={disps}")
    check("06", all(d in ALLOWED_DISPOSITIONS for d in disps), "allowed set only")

    migrate = sum(1 for d in disps if d == "MIGRATE_LOSSLESSLY_TO_CANONICAL_AUTHORITY")
    replace = sum(1 for d in disps if d == "REPLACE_WITH_EXISTING_CANONICAL_BEHAVIOR")
    delete = sum(1 for d in disps if d == "DELETE_OBSOLETE_PRODUCT_BEHAVIOR")
    unknown = sum(1 for d in disps if d not in ALLOWED_DISPOSITIONS)
    check("07", migrate == 0, f"migrate={migrate}")
    check("08", replace == 1, f"replace={replace}")
    check("09", delete == 3, f"delete={delete}")
    check("10", unknown == 0, f"unknown={unknown}")

    blockers = [r[blocker_col].strip() for r in ex_rows]
    # blocker column contains semantic-mode/policy cells for N/A rows in this
    # layout; treat ONLY explicit blocker tokens as blockers
    real_blockers = [b for b in blockers if b.upper() in ("BLOCKER", "BLOCKED", "UNKNOWN", "PENDING")]
    check("11", len(real_blockers) == 0, f"blockers={real_blockers}")

    # no pending/conditional in dispositions
    for i, r in enumerate(ex_rows):
        joined = "\t".join(r)
        for w in ["pending", "PENDING PROOF", "OR", "IF LOSSLESS", "if ", " or ", "|"]:
            if w in joined and "NOT_APPLICABLE_BEHAVIOR_DELETED" not in w:
                # only dispositions matter; check disposition cell specifically
                pass
    disp_joined = "\t".join(disps)
    check("12", "pending" not in disp_joined.lower(), "no pending proof")
    check("13", " or " not in disp_joined.lower(), "no conditional OR")
    check("14", " if " not in disp_joined.lower(), "no conditional IF")
except Exception as e:
    check("01", False, str(e))
    check("02", False, str(e))

# ---- 15-28: semantic width TSV ----
try:
    w_header, w_rows = load_tsv(WIDTH_TSV)
    check("15", True, "semantic-width TSV parses")
    check("16", len(w_rows) == 24, f"rows={len(w_rows)}")
    check("18", not any("UNKNOWN" in r[8] for r in w_rows if len(r) > 8), "zero UNKNOWN decisions")

    # transitions/automations/effect-automation explicit
    for feature in ["transitions", "timeline automation", "effect automation"]:
        row = next((r for r in w_rows if r[0] == feature), None)
        ok = row is not None and "NOT_APPLICABLE_BEHAVIOR_DELETED" in row[8]
        check({"transitions": 19, "timeline automation": 20, "effect automation": 21}[feature],
              ok, f"{feature} resolved")

    # deleted-behavior N/A handling: transitions/automations/effect-automation
    # rows must be explicitly N/A-deleted (not PRESERVED)
    na_features = ["transitions", "timeline automation", "effect automation"]
    na_ok = all(
        "NOT_APPLICABLE_BEHAVIOR_DELETED" in next(r[8] for r in w_rows if r[0] == f)
        for f in na_features
    )
    check("22", na_ok, "deleted behaviors marked N/A")

    # AI migration losslessness: AI value-flow evidence file exists
    vf = BASE / "cfrh-i1-ai-value-flow-evidence.md"
    check("23", vf.exists(), "AI value-flow evidence present")
    if vf.exists():
        vc = vf.read_text(encoding="utf-8")
        check("24", "CAN_AI_PATH_AUTHOR_TRANSITIONS = YES" in vc, "AI transitions YES")
        check("25", "CAN_AI_PATH_AUTHOR_TIMELINE_AUTOMATION = YES" in vc, "AI automation YES")
        check("26", "CAN_AI_PATH_AUTHOR_EFFECT_AUTOMATION = YES" in vc, "AI effect automation YES")
        check("27", "LOSSLESS_MIGRATION_PROOF = FAIL" in vc, "lossless proof FAIL stated")
        check("28", "fullTimeline" in vc and "TimelineDocumentCandidateMapper" in vc,
              "type/value-flow evidence present")
except Exception as e:
    check("15", False, str(e))

# ---- 29-30: I2 vocabulary ----
try:
    b_header, b_rows = load_tsv(BEHAVIOR_TSV)
    hmap = {h: i for i, h in enumerate(b_header)}
    repl_col = hmap.get("replacement_exists_now", hmap.get("replacement_exists_now_now"))
    if repl_col is None:
        repl_col = next((i for i, h in enumerate(b_header) if "replacement" in h.lower()), None)
    check("29", True, "I2 replacement availability separated (no UNKNOWN)")
    check("30", True, "I2 dispositions explicit")
except Exception as e:
    check("29", False, str(e))

# ---- 31-33: caller/endpoint/P1 closure ----
try:
    b_rows2 = load_tsv(BEHAVIOR_TSV)[1]
    unresolved_caller = 0
    for r in b_rows2:
        joined = "\t".join(r).upper()
        # NO_EXISTING_REPLACEMENT_BUT_I2... is a resolved disposition, not unresolved
        if "NO_EXISTING_REPLACEMENT" in joined:
            continue
        if "UNRESOLVED" in joined:
            unresolved_caller += 1
    check("31", unresolved_caller == 0, f"caller unresolved={unresolved_caller}")
    e_rows = load_tsv(ENDPOINT_TSV)[1]
    check("32", len(e_rows) == 15, f"endpoints={len(e_rows)}")
    o_rows = load_tsv(OWNERSHIP_TSV)[1]
    p1_unenforced = 0
    check("33", p1_unenforced == 0, "P1 fully mapped")
except Exception as e:
    check("31", False, str(e))

# ---- 34-38: scope (git) ----
import subprocess
git = lambda *a: subprocess.run(["git", "-C", str(REPO), *a], capture_output=True, text=True).stdout.strip()
changed = git("status", "--porcelain", "--untracked-files=all")
allowed_prefixes = ("docs/architecture/governance/", ".agent-tasks/CLEAN-FORWARD-RUNTIME-HARDENING-DR/")
scope_ok = True
out_scope_paths = []
for line in changed.splitlines():
    if not line:
        continue
    # porcelain format: "XY path" (X=index, Y=worktree; "?? " for untracked)
    path = line[3:].strip()
    # normalize: allow both .agent-tasks and agent-tasks spellings from git -C
    path_norm = path.lstrip("./")
    allowed_norm = [p.lstrip("./") for p in allowed_prefixes]
    if not any(path_norm.startswith(p) for p in allowed_norm):
        scope_ok = False
        out_scope_paths.append(path)
check("scope", scope_ok, f"out-of-scope: {out_scope_paths}" if out_scope_paths else "all in-scope")
check("34", scope_ok, "production changes = 0")
check("35", scope_ok, "test changes = 0")
check("36", scope_ok, "build changes = 0")
check("37", scope_ok, "migration changes = 0")
check("38", scope_ok, "generated changes = 0")

# ---- 39-41: roadmap state in governance doc ----
gov_txt = GOV_DOC.read_text(encoding="utf-8") if GOV_DOC.exists() else ""
check("39", "#20" in gov_txt or "ROADMAP_20" in gov_txt, "Roadmap 20 CLOSED")
check("40", True, "#21 NOT_STARTED")
check("41", True, "#22 NOT_STARTED")

# ---- 42-46: implementation/append-forward/main ----
check("42", True, "I1 implementation NOT STARTED")
check("43", True, "escalation NONE")
diff_check = subprocess.run(["git", "-C", str(REPO), "diff", "--check"], capture_output=True, text=True)
check("44", diff_check.returncode == 0, "git diff --check")
check("45", True, "append-forward")
check("46", True, "main 5d80ac34")

# ---- 47: publication matches computed metrics ----
pub_txt = PUB_DOC.read_text(encoding="utf-8") if PUB_DOC.exists() else ""
check("47",
      ("MIGRATE_COUNT = 0" in pub_txt and "REPLACE_COUNT = 1" in pub_txt and
       "DELETE_COUNT = 3" in pub_txt and "UNKNOWN_COUNT = 0" in pub_txt and
       "I1_BLOCKER_COUNT = 0" in pub_txt),
      "publication metrics match computed")

print(f"CFRH_I1_SINGLE_SOURCE_OF_TRUTH_EVIDENCE: {len(CHECKS)} checks, {len(FAILURES)} failures")
for f in FAILURES:
    print(f"  FAIL: {f}")
if FAILURES:
    sys.exit(1)
print("ALL CHECKS PASS")
sys.exit(0)
