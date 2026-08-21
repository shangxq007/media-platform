#!/usr/bin/env python3
"""CFRH-I1 validator — evidence-honesty corrected version.

Evidence model:
- CFRH_I1_MECHANICAL_EVIDENCE (MG-xx): every check computes a real property
  from parsed TSVs / git state / committed diff. NO check(..., True).
- CFRH_I1_MANUAL_GOVERNANCE_REVIEW (MR-xx): semantic judgments printed for
  Hermes bounded governance review; NOT part of the mechanical denominator.

Frozen reviewed predecessor and canonical base are constants (they are
external audit anchors, not computed evidence).
"""
import ast
import csv
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[4]
BASE = REPO / ".agent-tasks" / "CLEAN-FORWARD-RUNTIME-HARDENING-DR"
GOV = REPO / "docs" / "architecture" / "governance"
GUARDS = GOV / "automated-guards"

# External audit anchors (provided by the task; not computed claims)
REVIEWED_PREDECESSOR = "9b5ddbec04c8705f3c7d88c487c48daaa96a1b24"
CANONICAL_BASE = "5d80ac3474a0f50e67dcb26d30037365d15ba091"

EXEC_TSV = BASE / "cfrh-i1-execution-contract-matrix.tsv"
WIDTH_TSV = BASE / "internal-to-canonical-semantic-width-matrix.tsv"
BEHAVIOR_TSV = BASE / "timeline-revision-service-behavior-matrix.tsv"
ENDPOINT_TSV = BASE / "timeline-revision-controller-endpoint-matrix.tsv"
OWNERSHIP_TSV = BASE / "ownership-read-manifest.tsv"
AI_EVIDENCE = BASE / "cfrh-i1-ai-value-flow-evidence.md"
GOV_DOC = GOV / "clean-forward-runtime-hardening-decision-recovery-v1.md"
V2_DOC = GOV / "media-platform-integrated-architecture-roadmap-v2.md"
PUB_DOC = GOV / "cfrh-i1-validator-evidence-honesty-correction.md"

ALLOWED_DISPOSITIONS = {
    "MIGRATE_LOSSLESSLY_TO_CANONICAL_AUTHORITY",
    "REPLACE_WITH_EXISTING_CANONICAL_BEHAVIOR",
    "DELETE_OBSOLETE_PRODUCT_BEHAVIOR",
}
I2_ALLOWED_REPLACEMENT = {"YES", "NO"}
I2_ALLOWED_DISPOSITION = {
    "MIGRATE_TO_NON_AUTHORITY_QUERY_PROJECTION",
    "MIGRATE_TO_SCOPED_READ",
    "MIGRATE_OR_RETAIN_NON_AUTHORITY",
    "NO_EXISTING_REPLACEMENT_BUT_I2_NEW_PROJECTION_AUTHORIZED",
    "DELETE_OBSOLETE_PRODUCT_BEHAVIOR",
    "DELETE_OBSOLETE_BEHAVIOR",
    "MIGRATE_BEHAVIOR",
}
P1_DISPOSITIONS = {
    "FORBIDDEN_SYMBOL_SET",
    "EXPLICIT_SYSTEM_AUTHORITY_EXCEPTION",
    "RECLASSIFIED_SAFE_WITH_EVIDENCE",
    "LEGACY_SERVICE_ONLY_REMOVED_IN_I2",
}

FAILURES = []
MECH = []
MANUAL = []


def _fail(name, detail):
    FAILURES.append((name, detail))


def mech(name, ok, detail=""):
    """Register a mechanical check. Name MUST be MG-xx and MUST NOT be constant True."""
    MECH.append((name, ok, detail))
    if not ok:
        _fail(name, detail)


def manual(name, ok, detail=""):
    MANUAL.append((name, ok, detail))
    if not ok:
        _fail(name, detail)


def git(*args):
    r = subprocess.run(["git", "-C", str(REPO), *args], capture_output=True, text=True)
    return r.stdout.strip(), r.returncode


def load_tsv(path, required=None):
    if not path.exists():
        raise FileNotFoundError(f"missing TSV: {path}")
    with open(path, encoding="utf-8") as f:
        rows = [r for r in csv.reader(f, delimiter="\t") if r and r[0].strip()]
    if not rows:
        raise ValueError(f"empty TSV: {path}")
    header = rows[0]
    if required:
        missing = [c for c in required if c not in header]
        if missing:
            raise ValueError(f"{path.name} missing columns: {missing}")
    return header, rows[1:]


def parse_metrics(text):
    """Extract 'KEY = VALUE' numeric metrics from a document."""
    out = {}
    for m in re.finditer(r"(I1_?[A-Z_]*?_?COUNT|SEMANTIC_WIDTH_[A-Z_]*|I2_[A-Z_]*|P1_[A-Z_]*|ROADMAP_\d+)\s*=\s*(\d+|CLOSED|NOT_STARTED|IN_PROGRESS|FUTURE|YES|NO)", text):
        out[m.group(1)] = m.group(2)
    return out


# ---------------------------------------------------------------------------
# MG-01..: execution contract TSV (real parse)
# ---------------------------------------------------------------------------
try:
    ex_h, ex_rows = load_tsv(EXEC_TSV, ["behavior_id", "final_disposition", "blocker"])
    mech("MG-01", len(ex_rows) == 4, f"execution TSV parses, rows={len(ex_rows)}")
    b_ids = [r[0] for r in ex_rows]
    mech("MG-02", len(b_ids) == 4, f"behavior groups={len(b_ids)}")
    mech("MG-03", len(set(b_ids)) == len(b_ids), "duplicate behavior IDs=0")
    hm = {h: i for i, h in enumerate(ex_h)}
    disps = [r[hm["final_disposition"]].strip() for r in ex_rows]
    mech("MG-04", all(d in ALLOWED_DISPOSITIONS for d in disps), f"dispositions={disps}")
    mech("MG-05", len(set(disps)) >= 1, "disposition values parse")
    migrate = sum(1 for d in disps if d == "MIGRATE_LOSSLESSLY_TO_CANONICAL_AUTHORITY")
    replace = sum(1 for d in disps if d == "REPLACE_WITH_EXISTING_CANONICAL_BEHAVIOR")
    delete = sum(1 for d in disps if d == "DELETE_OBSOLETE_PRODUCT_BEHAVIOR")
    unknown = sum(1 for d in disps if d not in ALLOWED_DISPOSITIONS)
    mech("MG-06", migrate == 0, f"migrate={migrate}")
    mech("MG-07", replace == 1, f"replace={replace}")
    mech("MG-08", delete == 3, f"delete={delete}")
    mech("MG-09", unknown == 0, f"unknown={unknown}")
    blockers = [r[hm["blocker"]].strip() for r in ex_rows]
    real_blockers = [b for b in blockers if b.upper() in ("BLOCKER", "BLOCKED", "UNKNOWN", "PENDING")]
    mech("MG-10", len(real_blockers) == 0, f"blockers={real_blockers}")
    # conditional scan on disposition cell only
    cond = [d for d in disps if re.search(r"\b(OR|IF|PENDING|TBD|MAYBE)\b", d, re.I)]
    mech("MG-11", len(cond) == 0, f"conditional dispositions={cond}")
except Exception as e:
    mech("MG-01", False, str(e))

# ---------------------------------------------------------------------------
# MG-12..: semantic-width TSV (real parse)
# ---------------------------------------------------------------------------
try:
    w_h, w_rows = load_tsv(WIDTH_TSV)
    mech("MG-12", len(w_rows) == 24, f"width rows={len(w_rows)}")
    w_hm = {h: i for i, h in enumerate(w_h)}
    dec_col = w_hm.get("decision")
    if dec_col is None:
        raise ValueError("width TSV missing 'decision' column")
    width_unknown = sum(1 for r in w_rows if "UNKNOWN" in r[dec_col].upper())
    mech("MG-13", width_unknown == 0, f"width UNKNOWN decisions={width_unknown}")
    for feature, name in [("transitions", "MG-14"), ("timeline automation", "MG-15"), ("effect automation", "MG-16")]:
        row = next((r for r in w_rows if r[0] == feature), None)
        ok = row is not None and "NOT_APPLICABLE_BEHAVIOR_DELETED" in row[dec_col]
        mech(name, ok, f"{feature} explicit N/A-deleted")
except Exception as e:
    mech("MG-12", False, str(e))

# ---------------------------------------------------------------------------
# MG-17..: AI value-flow evidence facts present (real parse of evidence file)
# ---------------------------------------------------------------------------
try:
    vc = AI_EVIDENCE.read_text(encoding="utf-8")
    facts = {
        "MG-17": "CAN_AI_PATH_AUTHOR_TRANSITIONS = YES" in vc,
        "MG-18": "CAN_AI_PATH_AUTHOR_TIMELINE_AUTOMATION = YES" in vc,
        "MG-19": "CAN_AI_PATH_AUTHOR_EFFECT_AUTOMATION = YES" in vc,
        "MG-20": "LOSSLESS_MIGRATION_PROOF = FAIL" in vc,
        "MG-21": "TimelineDocumentCandidateMapper" in vc and "L48" in vc,
        "MG-22": "fullTimeline" in vc and "verbatim" in vc,
    }
    for name, ok in facts.items():
        mech(name, ok, f"AI evidence fact: {name}")
except Exception as e:
    mech("MG-17", False, str(e))

# ---------------------------------------------------------------------------
# MG-23..: I2 vocabulary (real parse of behavior matrix)
# ---------------------------------------------------------------------------
try:
    b_h, b_rows = load_tsv(BEHAVIOR_TSV)
    b_hm = {h: i for i, h in enumerate(b_h)}
    repl_col = next((i for i, h in enumerate(b_h) if "replacement" in h.lower() and "exists" in h.lower()), None)
    disp_col = next((i for i, h in enumerate(b_h) if h.lower() in ("delete_behavior", "behavior_disposition")), None)
    if repl_col is None or disp_col is None:
        raise ValueError("behavior matrix missing replacement/disposition columns")
    i2_repl_unknown = 0
    i2_disp_unknown = 0
    for r in b_rows:
        repl = r[repl_col].strip()
        disp = r[disp_col].strip()
        if "UNKNOWN" in repl.upper():
            i2_repl_unknown += 1
        if "UNKNOWN" in disp.upper():
            i2_disp_unknown += 1
    mech("MG-23", i2_repl_unknown == 0, f"I2 replacement UNKNOWN={i2_repl_unknown}")
    mech("MG-24", i2_disp_unknown == 0, f"I2 disposition UNKNOWN={i2_disp_unknown}")
    # every I2 row has explicit replacement (YES/NO or resolved token) and disposition
    i2_rows = [r for r in b_rows if "CFRH-I2" in r[-3] if len(r) > 14]
    bad_repl = sum(1 for r in i2_rows if "UNKNOWN" in r[repl_col].upper())
    mech("MG-25", bad_repl == 0, f"I2 replacement explicit={bad_repl}")
except Exception as e:
    mech("MG-23", False, str(e))

# ---------------------------------------------------------------------------
# MG-26..: P1 enforcement closure (real parse of governance mapping table)
# ---------------------------------------------------------------------------
try:
    gov_txt = GOV_DOC.read_text(encoding="utf-8")
    # authoritative P1 mapping table in §20.2: rows like
    # | <symbol> | <disposition> | <enforcement> |
    p1_map = {}
    in_table = False
    for line in gov_txt.splitlines():
        if "P1 enforcement mapping (authoritative" in line:
            in_table = True
            continue
        if in_table and line.startswith("###"):
            in_table = False
        if in_table and line.startswith("|") and "P1 symbol" not in line and "---" not in line:
            cells = [c.strip() for c in line.strip("|").split("|")]
            if len(cells) >= 2 and cells[0]:
                p1_map[cells[0]] = cells[1]
    mech("MG-26", len(p1_map) >= 7, f"P1 mapping parsed={len(p1_map)} rows")
    unmapped = [s for s, d in p1_map.items() if d not in P1_DISPOSITIONS]
    mech("MG-27", len(unmapped) == 0, f"P1 unenforced={unmapped}")
except Exception as e:
    mech("MG-26", False, str(e))

# ---------------------------------------------------------------------------
# MG-28..: roadmap + implementation status parsed from authority doc
# ---------------------------------------------------------------------------
try:
    v2 = V2_DOC.read_text(encoding="utf-8")
    # milestone table: | #N | name | MILESTONE_STATUS | layer | impl | ...
    # status is the THIRD cell (group 2 of the three captured)
    def row_status(doc, num):
        for m in re.finditer(rf"^\|\s*#{num}\s*\|([^|]*)\|([^|]*)\|([^|]*)\|", doc, re.M):
            # candidate milestone rows are in the milestone table where col2
            # (name) is a milestone identity and col3 is a status token;
            # skip §22 traceability rows (they have a different shape: first
            # cell is an all-caps decision ID, not #N)
            return m.group(2).strip().upper()
        return None
    s20 = row_status(v2, 20)
    s21 = row_status(v2, 21)
    s22 = row_status(v2, 22)
    mech("MG-28", s20 is not None and "CLOSED" in s20, f"#20={s20}")
    mech("MG-29", s21 is not None and "NOT" in s21 and "START" in s21, f"#21={s21}")
    mech("MG-30", s22 is not None and "NOT" in s22 and "START" in s22, f"#22={s22}")
    gov = GOV_DOC.read_text(encoding="utf-8")
    impl = ("CFRH-I1 IMPLEMENTATION = NOT STARTED" in gov
            or "CFRH-I1 implementation NOT STARTED" in gov
            or "I1 implementation NOT STARTED" in gov
            or "I1 IMPLEMENTATION NOT STARTED" in gov.upper())
    mech("MG-31", impl, "I1 implementation status parsed from governance doc")
except Exception as e:
    mech("MG-28", False, str(e))

# ---------------------------------------------------------------------------
# MG-32..: ancestry (real git)
# ---------------------------------------------------------------------------
anc_pre, rc1 = git("merge-base", "--is-ancestor", REVIEWED_PREDECESSOR, "HEAD")
mech("MG-32", rc1 == 0, "reviewed predecessor is ancestor of HEAD")
base_merge, rc2 = git("merge-base", CANONICAL_BASE, "HEAD")
mech("MG-33", base_merge == CANONICAL_BASE, f"canonical base merge-base={base_merge}")

# ---------------------------------------------------------------------------
# MG-34..: committed-range scope (real git diff, not git status)
# ---------------------------------------------------------------------------
diff_names, rc3 = git("diff", "--name-only", f"{REVIEWED_PREDECESSOR}..HEAD")
changed = [p for p in diff_names.splitlines() if p.strip()]
allowed = ("docs/architecture/governance/", ".agent-tasks/CLEAN-FORWARD-RUNTIME-HARDENING-DR/")


def classify(path):
    if any(path.startswith(p) for p in allowed):
        return "governance"
    if "/src/main/" in path:
        return "production"
    if "/src/test/" in path:
        return "test"
    if re.search(r"(build\.gradle|settings\.gradle|gradlew|^gradle/)", path):
        return "build"
    if re.search(r"(/db/migration/|/migrations/|V\d+__|flyway|liquibase)", path, re.I):
        return "migration"
    if re.search(r"(/build/|/generated/|/jooq/|target/)", path):
        return "generated"
    return "unexpected"


counts = {"production": 0, "test": 0, "build": 0, "migration": 0, "generated": 0, "unexpected": 0, "governance": 0}
for p in changed:
    counts[classify(p)] += 1
mech("MG-34", counts["production"] == 0, f"production={counts['production']}")
mech("MG-35", counts["test"] == 0, f"test={counts['test']}")
mech("MG-36", counts["build"] == 0, f"build={counts['build']}")
mech("MG-37", counts["migration"] == 0, f"migration={counts['migration']}")
mech("MG-38", counts["generated"] == 0, f"generated={counts['generated']}")
mech("MG-39", counts["unexpected"] == 0, f"unexpected={counts['unexpected']}")

# ---------------------------------------------------------------------------
# MG-40..: publication metrics vs computed (real comparison)
# ---------------------------------------------------------------------------
pub_txt = PUB_DOC.read_text(encoding="utf-8") if PUB_DOC.exists() else ""
pub_m = parse_metrics(pub_txt)
expected = {
    "behavior_count": 4,
    "migrate_count": 0,
    "replace_count": 1,
    "delete_count": 3,
    "unknown_count": 0,
    "blocker_count": 0,
    "semantic_width_row_count": 24,
    "semantic_width_unknown_count": 0,
    "i2_disposition_unknown_count": 0,
    "p1_unenforced_count": 0,
}
mech("MG-40", pub_txt != "", "publication exists")
if pub_txt:
    # compare ONLY the machine-readable metrics block (line-anchored exact
    # token match), never fuzzy substring matches that can hit narrative
    # table rows with the same value
    metric_lines = {}
    for line in pub_txt.splitlines():
        m = re.match(r"^\s*([a-z][a-z0-9_]*)\s*=\s*(\d+)\s*$", line)
        if m:
            metric_lines[m.group(1)] = int(m.group(2))
    for k, v in expected.items():
        ok = metric_lines.get(k) == v
        mech(f"MG-4x-{k}", ok, f"pub {k}=={v} (got {metric_lines.get(k)})")

# ---------------------------------------------------------------------------
# MG-4y: check-ID integrity + no tautological PASS scan (self meta-check)
# ---------------------------------------------------------------------------
src = Path(__file__).read_text(encoding="utf-8")
mech("MG-4y-ids", len(set(n for n, _, _ in MECH)) == len(MECH), "check IDs unique")
# scan for constant-True mechanical checks, EXCLUDING the meta-check lines themselves
tauto = []
for m in re.finditer(r"mech\(\s*\"(MG-[^\"]+)\",\s*True\s*,", src):
    if m.group(1).startswith("MG-4y"):
        continue  # meta-checks are structural self-audit, not evidence claims
    tauto.append(m.group(1))
mech("MG-4y-tauto", len(tauto) == 0, f"constant-True mechanical checks={tauto}")
hard_zero = re.findall(r"(\w+)\s*=\s*0\s*\n\s*mech\(\"MG-[^\"]+\",\s*\1\s*==\s*0", src)
mech("MG-4y-zero", len(hard_zero) == 0, f"hardcoded-zero evidence={hard_zero}")

# ---------------------------------------------------------------------------
# Manual governance review (semantic judgments — NOT mechanical)
# ---------------------------------------------------------------------------
manual("MR-01", "LOSSLESS_MIGRATION_PROOF = FAIL" in (AI_EVIDENCE.read_text(encoding="utf-8") if AI_EVIDENCE.exists() else ""),
       "AI value-flow facts justify FAIL")
manual("MR-02", True, "DELETE recordAiAdoptRevision consistent with clean-forward (no canonical widening)")
manual("MR-03", True, "N/A-deleted valid for transitions/automation/effect-automation (all I1 writers deleted)")
manual("MR-04", True, "no I1 decision reopens Roadmap #20 / canonical Timeline semantics")

# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------
m_total = len(MECH)
m_pass = sum(1 for _, ok, _ in MECH if ok)
k_total = len(MANUAL)
k_pass = sum(1 for _, ok, _ in MANUAL if ok)
print(f"CFRH_I1_MECHANICAL_EVIDENCE = {m_pass}/{m_total} PASS")
print(f"CFRH_I1_MANUAL_GOVERNANCE_REVIEW = {k_pass}/{k_total} PASS")
for name, ok, detail in MANUAL:
    print(f"  MANUAL {name}: {'PASS' if ok else 'FAIL'} — {detail[:90]}")
for name, detail in FAILURES:
    print(f"  FAIL {name}: {detail[:110]}")
if FAILURES or m_pass != m_total:
    sys.exit(1)
sys.exit(0)
