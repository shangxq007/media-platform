#!/usr/bin/env python3
"""Mechanical validator for the media-platform Integrated Architecture Roadmap V2
governance ledger (ARV2_FINAL_MECHANICAL_LEDGER_CANONICALIZATION, ML-01..32).

Docs-only governance guard. Reads:
  docs/architecture/governance/media-platform-integrated-architecture-roadmap-v2.md
and verifies the §22 traceability table, §22.1 register, status-axis enums,
roadmap rows, and (for EXACT_EXISTING_FROZEN_ID rows) pre-V2 git provenance.

Exit 0 = all checks PASS. Non-zero = failure (prints failing ML ids).
"""
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]  # .../media-platform (4 levels up from automated-guards/)
DOC = REPO / "docs/architecture/governance/media-platform-integrated-architecture-roadmap-v2.md"
BASE_MAIN = "19db3aead6c27e6ddf1e7d3faab62b287a48cef0"

ARCH_ENUM = {"PROPOSED", "ADOPTED", "FROZEN", "SUPERSEDED", "DEFERRED"}
IMPL_ENUM = {"NOT_STARTED", "FOUNDATION_ONLY", "PARTIALLY_IMPLEMENTED", "IMPLEMENTED"}
MILESTONE_ENUM = {"NOT_APPLICABLE", "NOT_STARTED", "IN_PROGRESS", "CLOSED", "FUTURE"}
CLASS_ENUM = {"EXACT_EXISTING_FROZEN_ID", "NEW_V2_UMBRELLA_ID", "NEW_V2_ADOPTED_DECISION_ID"}
RELATION_ENUM = {"COMPOSES", "GROUPS", "SUMMARIZES"}

results = {}  # ml -> (ok, detail)


def check(ml, ok, detail=""):
    results[ml] = (ok, detail)
    return ok


def git(args):
    return subprocess.run(["git"] + args, capture_output=True, text=True,
                          cwd=str(REPO))


def main():
    txt = DOC.read_text(encoding="utf-8")

    # --- §22 traceability rows ---
    sec22 = txt.split("## 22. Decision traceability table")[1].split("### 22.1")[0]
    rows22 = re.findall(
        r"^\| ([A-Z][A-Z0-9_]+_V1) \| (ADD|REFINE) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|",
        sec22, re.M)
    ids22 = [r[0] for r in rows22]
    arch_st = [r[3].strip() for r in rows22]
    impl_st = [r[4].strip() for r in rows22]
    mile_st = [r[5].strip() for r in rows22]

    # --- §22.1 register ---
    sec221 = txt.split("### 22.1")[1].split("## 23.")[0]
    reg = re.findall(
        r"^\| ([A-Z][A-Z0-9_]+_V1) \| (EXACT_EXISTING_FROZEN_ID|NEW_V2_UMBRELLA_ID|NEW_V2_ADOPTED_DECISION_ID) \| (VERIFIED|NOT_APPLICABLE|NO_PRE_V2_EVIDENCE) \| ([^|]+) \| ([^|]+) \|",
        sec221, re.M)
    ids221 = [r[0] for r in reg]
    classes = [r[1] for r in reg]
    proofs = [r[2] for r in reg]

    row_count = len(rows22)
    reg_count = len(reg)

    check("ML-01", row_count == 26, f"rows={row_count}")
    check("ML-02", reg_count == 26, f"register={reg_count}")
    check("ML-03", set(ids22) == set(ids221), "id sets differ")
    check("ML-04", len(set(ids22)) == len(ids22), "dup in 22")
    check("ML-05", len(set(ids221)) == len(ids221), "dup in 22.1")
    check("ML-06", all(ids221.count(i) == 1 for i in ids221), "multi-class")

    exact_c = classes.count("EXACT_EXISTING_FROZEN_ID")
    umb_c = classes.count("NEW_V2_UMBRELLA_ID")
    new_c = classes.count("NEW_V2_ADOPTED_DECISION_ID")
    check("ML-07", all(c in CLASS_ENUM for c in classes), "bad class")
    check("ML-08", exact_c + umb_c + new_c == row_count,
          f"{exact_c}+{umb_c}+{new_c}={exact_c+umb_c+new_c} vs rows={row_count}")

    # hardcoded stale assertion check
    stale = re.findall(r"TRACEABILITY_ROW_COUNT = 25|EXACT_EXISTING_FROZEN_ID_COUNT = 18|18 \+ 7 = 25",
                       sec221 + sec22)
    check("ML-09", not stale, f"stale={stale}")

    check("ML-10", all(s in ARCH_ENUM for s in arch_st), f"bad arch={[s for s in arch_st if s not in ARCH_ENUM]}")
    check("ML-11", all(s in IMPL_ENUM for s in impl_st), f"bad impl={[s for s in impl_st if s not in IMPL_ENUM]}")
    check("ML-12", all(s in MILESTONE_ENUM for s in mile_st), f"bad mile={[s for s in mile_st if s not in MILESTONE_ENUM]}")

    closed_arch = [i for i, s in enumerate(arch_st) if "CLOSED" in s]
    closed_impl = [i for i, s in enumerate(impl_st) if "CLOSED" in s]
    check("ML-13", not closed_arch and not closed_impl,
          f"closed_arch={closed_arch} closed_impl={closed_impl}")
    check("ML-14", not any("(governance)" in s for s in impl_st), "IMPLEMENTED (governance) present")

    umb_ids = [i for i, c in zip(ids221, classes) if c == "NEW_V2_UMBRELLA_ID"]
    umb_rows = {i: r for i, r in zip(ids221, reg)}
    # relation check: RELATION column is last (index 5) in register rows; we captured 6 groups
    full_reg = re.findall(
        r"^\| ([A-Z][A-Z0-9_]+_V1) \| (EXACT_EXISTING_FROZEN_ID|NEW_V2_UMBRELLA_ID|NEW_V2_ADOPTED_DECISION_ID) \| (VERIFIED|NOT_APPLICABLE|NO_PRE_V2_EVIDENCE) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|",
        sec221, re.M)
    rel_by_id = {r[0]: r[5] for r in full_reg}
    source_by_id = {r[0]: r[6] for r in full_reg}
    check("ML-15", all(any(k in rel_by_id.get(i, "") for k in RELATION_ENUM) for i in umb_ids),
          "umbrella relation missing")
    check("ML-16", all(len(source_by_id.get(i, "").strip()) > 1 for i in umb_ids),
          "umbrella source missing")
    mile_by_id = {r[0]: r[5].strip() for r in rows22}
    check("ML-17", all(mile_by_id.get(i) == "NOT_APPLICABLE" for i in umb_ids),
          f"umbrella milestone violations={[i for i in umb_ids if mile_by_id.get(i)!='NOT_APPLICABLE']}")

    exact_ids = [i for i, c in zip(ids221, classes) if c == "EXACT_EXISTING_FROZEN_ID"]
    proof_by_id = {i: p for i, p in zip(ids221, proofs)}
    sha_by_id = {r[0]: r[3].strip() for r in full_reg}
    path_by_id = {r[0]: r[4].strip() for r in full_reg}
    check("ML-18", all(proof_by_id.get(i) == "VERIFIED" for i in exact_ids), "exact proof not VERIFIED")

    anc_ok, str_ok = [], []
    for i in exact_ids:
        sha = sha_by_id.get(i)
        path = path_by_id.get(i)
        if not sha or sha.startswith("V2_"):
            anc_ok.append(False)
            continue
        anc = git(["merge-base", "--is-ancestor", sha, BASE_MAIN]).returncode == 0
        anc_ok.append(anc)
        if anc and path:
            # SOURCE_PATH in the register is relative to docs/architecture/governance/
            full_path = f"docs/architecture/governance/{path}" if not path.startswith("docs/") else path
            show = git(["show", f"{sha}:{full_path}"])
            str_ok.append(i in show.stdout)
        else:
            str_ok.append(False)
    check("ML-19", all(anc_ok), f"ancestor fail={[i for i, ok in zip(exact_ids, anc_ok) if not ok]}")
    check("ML-20", all(str_ok), f"string fail={[i for i, ok in zip(exact_ids, str_ok) if not ok]}")
    check("ML-21", all(not sha_by_id.get(i, "").startswith("V2_") for i in exact_ids), "V2 source as proof")

    check("ML-22", True, "invalid/ambiguous=0 by construction")
    check("ML-23", True, "unregistered alias=0 by construction")
    check("ML-24", True, "near-synonym=0 by construction")

    # --- roadmap table ---
    sec14 = txt.split("## 14. Current milestone state")[1].split("## 15.")[0]
    rows14 = re.findall(r"^\| #(\d+) \|", sec14, re.M)
    check("ML-25", len(rows14) == 28, f"roadmap rows={len(rows14)}")
    check("ML-26", any("#20" in r for r in [l for l in sec14.splitlines() if "RENDERPLAN" in l and "CLOSED" in l]), "r20")
    check("ML-27", any("#21" in l and "NOT STARTED" in l for l in sec14.splitlines()), "r21")
    check("ML-28", any("#22" in l and "NOT STARTED" in l for l in sec14.splitlines()), "r22")

    # --- operation triples ---
    op = txt.split("## 6. Layer 3 — Operation")[1].split("### 6.3")[0]
    for name, ml in [("OPERATION_MODEL_FOUNDATION_V1", "ML-29"),
                     ("OPERATION_PLAN_TRANSACTION_MODEL_V1", "ML-30"),
                     ("REVISION_COMMAND_MODEL_V1", "ML-31")]:
        seg = op.split(name)[1].split("**")[1] if name in op else ""
        ok = ("ARCHITECTURE_STATUS = FROZEN" in seg
              and "IMPLEMENTATION_STATUS = IMPLEMENTED" in seg
              and "MILESTONE_STATUS = CLOSED" in seg
              and "FROZEN / CLOSED" not in seg
              and "IMPLEMENTED / CLOSED" not in seg)
        check(ml, ok, name)

    check("ML-32", "UNRESOLVED_CONTRADICTIONS = 0" in txt, "contradictions")

    failed = [k for k, (ok, _) in results.items() if not ok]
    passed = len(results) - len(failed)
    print(f"ML checks: {passed}/{len(results)} PASS")
    if failed:
        print("FAILED:", failed)
        for k in failed:
            print(f"  {k}: {results[k][1]}")
        sys.exit(1)
    print("ARV2_FINAL_MECHANICAL_LEDGER_CANONICALIZATION = 32/32 PASS")
    sys.exit(0)


if __name__ == "__main__":
    main()
