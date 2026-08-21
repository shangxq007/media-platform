#!/usr/bin/env python3
"""Mechanical validator for the media-platform Integrated Architecture Roadmap V2
governance ledger — ARV2_FINAL_MECHANICAL_GUARD (MG-01..MG-38).

Docs-only governance guard. Reads:
  docs/architecture/governance/media-platform-integrated-architecture-roadmap-v2.md
and verifies the §22 traceability table, §22.1 register, status-axis enums,
roadmap rows, contradiction table (§24), and pre-V2 git provenance for
EXACT / NEW_V2_ADOPTED classifications.

Evidence model:
- MECHANICAL_GUARD (this script): facts recomputed from the document + git.
- MANUAL_GOVERNANCE_REVIEW: semantic judgments (near-synonym / duplicate
  authority / supersession semantics) reported separately, never folded into
  the mechanical denominator.

Fail-closed design: missing sections, unparseable headers, or zero rows raise
a clear diagnostic and exit non-zero. No `check(..., True)` is used for any
required acceptance property.

Exit 0 = MG-01..MG-38 all PASS. Non-zero = mechanical failure (prints failed MG ids).
"""
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
DOC = REPO / "docs/architecture/governance/media-platform-integrated-architecture-roadmap-v2.md"
BASE_MAIN = "19db3aead6c27e6ddf1e7d3faab62b287a48cef0"

ARCH_ENUM = {"PROPOSED", "ADOPTED", "FROZEN", "SUPERSEDED", "DEFERRED"}
IMPL_ENUM = {"NOT_STARTED", "FOUNDATION_ONLY", "PARTIALLY_IMPLEMENTED", "IMPLEMENTED"}
MILESTONE_ENUM = {"NOT_APPLICABLE", "NOT_STARTED", "IN_PROGRESS", "CLOSED", "FUTURE"}
CLASS_ENUM = {"EXACT_EXISTING_FROZEN_ID", "NEW_V2_UMBRELLA_ID", "NEW_V2_ADOPTED_DECISION_ID"}
RELATION_RE = re.compile(r"^(COMPOSES|GROUPS|SUMMARIZES):\s*(.+)$")

results = {}  # mg -> (ok, detail)


def check(mg, ok, detail=""):
    results[mg] = (ok, detail)
    return ok


def fail(msg):
    print(f"FATAL: {msg}")
    sys.exit(2)


def get_section(txt, header, next_header):
    if header not in txt:
        fail(f"required section missing: {header}")
    if next_header not in txt.split(header, 1)[1]:
        fail(f"required section terminator missing after: {header}")
    return txt.split(header, 1)[1].split(next_header, 1)[0]


def git(args):
    return subprocess.run(["git"] + args, capture_output=True, text=True,
                          cwd=str(REPO))


def main():
    if not DOC.exists():
        fail(f"document not found: {DOC}")
    txt = DOC.read_text(encoding="utf-8")

    # ---------- §22 traceability rows ----------
    sec22 = get_section(txt, "## 22. Decision traceability table", "### 22.1")
    rows22 = re.findall(
        r"^\| ([A-Z][A-Z0-9_]+_V1) \| (ADD|REFINE) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|",
        sec22, re.M)
    ids22 = [r[0] for r in rows22]
    arch_st = [r[3].strip() for r in rows22]
    impl_st = [r[4].strip() for r in rows22]
    mile_st = [r[5].strip() for r in rows22]
    row_count = len(rows22)
    if row_count == 0:
        fail("§22 parsed row count is zero")

    # ---------- §22.1 register ----------
    sec221 = get_section(txt, "### 22.1", "## 23.")
    full_reg = re.findall(
        r"^\| ([A-Z][A-Z0-9_]+_V1) \| (EXACT_EXISTING_FROZEN_ID|NEW_V2_UMBRELLA_ID|NEW_V2_ADOPTED_DECISION_ID) \| (VERIFIED|NOT_APPLICABLE|NO_PRE_V2_EVIDENCE) \| ([^|]+) \| ([^|]+) \| ([^|]+) \| ([^|]+) \|",
        sec221, re.M)
    reg_count = len(full_reg)
    if reg_count == 0:
        fail("§22.1 parsed register count is zero")
    ids221 = [r[0] for r in full_reg]
    classes = [r[1] for r in full_reg]
    proofs = [r[2] for r in full_reg]
    sha_by_id = {r[0]: r[3].strip() for r in full_reg}
    path_by_id = {r[0]: r[4].strip() for r in full_reg}
    rel_by_id = {r[0]: r[5].strip() for r in full_reg}      # RELATION / SOURCE_IDS
    status_by_id = {r[0]: r[6].strip() for r in full_reg}   # STATUS

    # ---------- MG-01..MG-10 cardinality ----------
    check("MG-01", row_count == 26, f"§22 rows={row_count}")
    check("MG-02", reg_count == 26, f"§22.1 rows={reg_count}")
    check("MG-03", set(ids22) == set(ids221), "ID sets differ")
    check("MG-04", len(set(ids22)) == len(ids22), "dup in §22")
    check("MG-05", len(set(ids221)) == len(ids221), "dup in §22.1")
    check("MG-06", all(ids221.count(i) == 1 for i in ids221), "multi-class rows")
    check("MG-07", all(c in CLASS_ENUM for c in classes), "bad classification")

    exact_c = classes.count("EXACT_EXISTING_FROZEN_ID")
    umb_c = classes.count("NEW_V2_UMBRELLA_ID")
    new_c = classes.count("NEW_V2_ADOPTED_DECISION_ID")
    check("MG-08", exact_c + umb_c + new_c == row_count,
          f"{exact_c}+{umb_c}+{new_c}={exact_c+umb_c+new_c} vs rows={row_count}")

    # MG-09: ledger metrics text must match computed counts
    metrics_sec = sec221 + sec22
    m_row = re.search(r"TRACEABILITY_ROW_COUNT = (\d+)", metrics_sec)
    m_ex = re.search(r"EXACT_EXISTING_FROZEN_ID_COUNT = (\d+)", metrics_sec)
    m_um = re.search(r"NEW_V2_UMBRELLA_ID_COUNT = (\d+)", metrics_sec)
    m_na = re.search(r"NEW_V2_ADOPTED_DECISION_ID_COUNT = (\d+)", metrics_sec)
    metrics_ok = bool(m_row and m_ex and m_um and m_na
                      and int(m_row.group(1)) == row_count
                      and int(m_ex.group(1)) == exact_c
                      and int(m_um.group(1)) == umb_c
                      and int(m_na.group(1)) == new_c)
    check("MG-09", metrics_ok,
          f"metrics vs computed: rows={m_row.group(1) if m_row else '?'}/{row_count}, "
          f"ex={m_ex.group(1) if m_ex else '?'}/{exact_c}, "
          f"um={m_um.group(1) if m_um else '?'}/{umb_c}, "
          f"na={m_na.group(1) if m_na else '?'}/{new_c}")

    stale = re.findall(r"TRACEABILITY_ROW_COUNT = 25|EXACT_EXISTING_FROZEN_ID_COUNT = 18|18 \+ 7 = 25",
                       metrics_sec)
    check("MG-10", not stale, f"stale metrics={stale}")

    # ---------- MG-11..MG-16 status axes ----------
    check("MG-11", all(s in ARCH_ENUM for s in arch_st),
          f"bad arch={[s for s in arch_st if s not in ARCH_ENUM]}")
    check("MG-12", all(s in IMPL_ENUM for s in impl_st),
          f"bad impl={[s for s in impl_st if s not in IMPL_ENUM]}")
    check("MG-13", all(s in MILESTONE_ENUM for s in mile_st),
          f"bad mile={[s for s in mile_st if s not in MILESTONE_ENUM]}")
    check("MG-14", not any("CLOSED" in s for s in arch_st), "CLOSED in ARCH_STATUS")
    check("MG-15", not any("CLOSED" in s for s in impl_st), "CLOSED in IMPL_STATUS")
    check("MG-16", not any("(governance)" in s for s in impl_st),
          "IMPLEMENTED (governance) in IMPL_STATUS")

    # ---------- MG-17..MG-19 umbrellas ----------
    umb_ids = [i for i, c in zip(ids221, classes) if c == "NEW_V2_UMBRELLA_ID"]
    rel_ok, src_ok = [], []
    for i in umb_ids:
        rel_txt = rel_by_id.get(i, "")
        m = RELATION_RE.match(rel_txt)
        rel_ok.append(bool(m))
        src_ok.append(bool(m) and m.group(2).strip() != "")
    check("MG-17", all(rel_ok),
          f"umbrella relation missing={[i for i, ok in zip(umb_ids, rel_ok) if not ok]}")
    check("MG-18", all(src_ok),
          f"umbrella source payload missing={[i for i, ok in zip(umb_ids, src_ok) if not ok]}")
    mile_by_id = {r[0]: r[5].strip() for r in rows22}
    check("MG-19", all(mile_by_id.get(i) == "NOT_APPLICABLE" for i in umb_ids),
          f"umbrella milestone violations={[i for i in umb_ids if mile_by_id.get(i) != 'NOT_APPLICABLE']}")

    # ---------- MG-20..MG-23 EXACT provenance ----------
    exact_ids = [i for i, c in zip(ids221, classes) if c == "EXACT_EXISTING_FROZEN_ID"]
    proof_by_id = {i: p for i, p in zip(ids221, proofs)}
    check("MG-20", all(proof_by_id.get(i) == "VERIFIED" for i in exact_ids),
          "EXACT proof not VERIFIED")
    anc_ok, str_ok = [], []
    for i in exact_ids:
        sha = sha_by_id.get(i)
        path = path_by_id.get(i)
        if not sha or sha.startswith("V2_"):
            anc_ok.append(False)
            str_ok.append(False)
            continue
        anc = git(["merge-base", "--is-ancestor", sha, BASE_MAIN]).returncode == 0
        anc_ok.append(anc)
        if anc and path:
            full_path = f"docs/architecture/governance/{path}" if not path.startswith("docs/") else path
            show = git(["show", f"{sha}:{full_path}"])
            str_ok.append(i in show.stdout)
        else:
            str_ok.append(False)
    check("MG-21", all(anc_ok), f"ancestor fail={[i for i, ok in zip(exact_ids, anc_ok) if not ok]}")
    check("MG-22", all(str_ok), f"string fail={[i for i, ok in zip(exact_ids, str_ok) if not ok]}")
    check("MG-23", all(not sha_by_id.get(i, "").startswith("V2_") for i in exact_ids),
          "V2-created evidence used as EXACT proof")

    # ---------- MG-24..MG-25 NEW_V2_ADOPTED negative provenance ----------
    new_ids = [i for i, c in zip(ids221, classes) if c == "NEW_V2_ADOPTED_DECISION_ID"]
    check("MG-24", all(proof_by_id.get(i) == "NO_PRE_V2_EVIDENCE" for i in new_ids),
          "NEW_ADOPTED proof not NO_PRE_V2_EVIDENCE")
    absent_ok = []
    for i in new_ids:
        # bounded: exact ID must not appear in any commit reachable from BASE_MAIN
        r = git(["log", BASE_MAIN, "-S", i, "--format=%H", "--"])
        absent_ok.append(r.stdout.strip() == "")
    check("MG-25", all(absent_ok),
          f"NEW_ADOPTED pre-V2 history hit={[i for i, ok in zip(new_ids, absent_ok) if not ok]}")

    # ---------- MG-26 derived invalid/unclassified/ambiguous ----------
    invalid_class = [i for i, c in zip(ids221, classes) if c not in CLASS_ENUM]
    unclassified = list(set(ids22) - set(ids221)) + list(set(ids221) - set(ids22))
    multi_class = [i for i in set(ids221) if ids221.count(i) > 1]
    derived_bad = len(invalid_class) + len(unclassified) + len(multi_class)
    check("MG-26", derived_bad == 0,
          f"derived invalid/unclassified/ambiguous={derived_bad} "
          f"(invalid={invalid_class}, unclassified={unclassified}, multi={multi_class})")

    # ---------- MG-27..MG-30 roadmap ----------
    sec14 = get_section(txt, "## 14. Current milestone state", "## 15.")
    rows14 = [int(n) for n in re.findall(r"^\| #(\d+) \|", sec14, re.M)]
    check("MG-27", sorted(rows14) == list(range(1, 29)),
          f"roadmap numbers={sorted(rows14) if rows14 else 'empty'} (need 1..28 unique)")
    r20_line = [l for l in sec14.splitlines() if l.startswith("| #20 |")]
    r21_line = [l for l in sec14.splitlines() if l.startswith("| #21 |")]
    r22_line = [l for l in sec14.splitlines() if l.startswith("| #22 |")]
    check("MG-28", bool(r20_line) and "CLOSED" in r20_line[0], f"#20 line={r20_line}")
    check("MG-29", bool(r21_line) and "NOT STARTED" in r21_line[0], f"#21 line={r21_line}")
    check("MG-30", bool(r22_line) and "NOT STARTED" in r22_line[0], f"#22 line={r22_line}")

    # ---------- MG-31..MG-33 operation triples ----------
    op_sec = get_section(txt, "## 6. Layer 3 — Operation", "## 7.")
    triples = {
        "OPERATION_MODEL_FOUNDATION_V1": "MG-31",
        "OPERATION_PLAN_TRANSACTION_MODEL_V1": "MG-32",
        "REVISION_COMMAND_MODEL_V1": "MG-33",
    }
    for name, mg in triples.items():
        if name not in op_sec:
            check(mg, False, f"{name} block missing")
            continue
        seg = op_sec.split(name, 1)[1].split("**", 2)[1]
        ok = ("ARCHITECTURE_STATUS = FROZEN" in seg
              and "IMPLEMENTATION_STATUS = IMPLEMENTED" in seg
              and "MILESTONE_STATUS = CLOSED" in seg
              and "FROZEN / CLOSED" not in seg
              and "IMPLEMENTED / CLOSED" not in seg)
        check(mg, ok, name)

    # ---------- MG-34..MG-37 contradiction table ----------
    sec24 = get_section(txt, "## 24. Contradiction review", "## 25.")
    contra_rows = re.findall(r"^\| (.+?) \| (.+?) \| (.+?) \|$", sec24, re.M)
    data_rows = [r for r in contra_rows if r[0].strip() != "Authority pair" and "---" not in r[0]]
    pair_count = len(data_rows)
    consistent_count = sum(1 for r in data_rows if "consistent" in r[1].strip().lower())
    unresolved_count = sum(1 for r in data_rows if "unresolved" in r[1].strip().lower()
                           or "inconsistent" in r[1].strip().lower())
    check("MG-34", pair_count > 0, f"contradiction rows={pair_count}")
    check("MG-35", consistent_count == pair_count,
          f"consistent={consistent_count}/{pair_count}")
    check("MG-36", unresolved_count == 0, f"unresolved rows={unresolved_count}")
    summary_m = re.search(r"UNRESOLVED_CONTRADICTIONS = (\d+)", sec24)
    check("MG-37", bool(summary_m) and int(summary_m.group(1)) == unresolved_count,
          f"summary={summary_m.group(1) if summary_m else '?'} vs computed={unresolved_count}")

    # ---------- MG-38 no unconditional acceptance in guard source ----------
    src = Path(__file__).read_text(encoding="utf-8")
    unconditional = re.findall(r'check\("(MG-\d+)", True[,)]', src)
    check("MG-38", not unconditional,
          f"unconditional True acceptance present: {unconditional}")

    # ---------- report ----------
    failed = [k for k, (ok, _) in results.items() if not ok]
    passed = len(results) - len(failed)
    print(f"MECHANICAL_GUARD: {passed}/{len(results)} PASS")
    if failed:
        print("FAILED:", failed)
        for k in failed:
            print(f"  {k}: {results[k][1]}")
        sys.exit(1)
    print("ARV2_FINAL_MECHANICAL_GUARD = 38/38 PASS")
    print(f"ledger: rows={row_count} exact={exact_c} umbrella={umb_c} new_adopted={new_c}")
    print(f"contradictions: pairs={pair_count} consistent={consistent_count} unresolved={unresolved_count}")
    sys.exit(0)


if __name__ == "__main__":
    main()
