#!/usr/bin/env python3
"""Executable RED mutations for the FAOF-2 repository validation checks."""

from __future__ import annotations

import copy
import importlib.util
import sys
from pathlib import Path


sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[2]


def import_script(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


holes = import_script("faof2_proof_holes", ROOT / "scripts/formal/check-proof-holes.py")
mapping = import_script("faof2_witness_mapping", ROOT / "scripts/formal/check-witness-mapping.py")


def expect_mapping_red(name: str, catalog, matrix, corpus, sources) -> None:
    try:
        mapping.validate(catalog, matrix, corpus, sources)
    except AssertionError:
        return
    raise AssertionError(f"mapping RED mutation passed: {name}")


def main() -> None:
    lean = Path("Mutation.lean")
    coq = Path("Mutation.v")
    proof_mutations = (
        (lean, "theorem red : True := by\n  sorry\n"),
        (lean, "axiom red : True\n"),
        (lean, "theorem red : True := by?\n"),
        (coq, "Theorem red : True. admit. Qed.\n"),
        (coq, "Theorem red : True. Admitted.\n"),
        (coq, "Axiom red : True.\n"),
    )
    for source, mutation in proof_mutations:
        if not holes.proof_holes(source, mutation):
            raise AssertionError(f"proof-hole RED mutation passed: {mutation.strip()}")
    if holes.proof_holes(lean, "/- sorry axiom -/\ntheorem green : True := by trivial"):
        raise AssertionError("Lean comment text was treated as a proof hole")
    if holes.proof_holes(coq, "(* admit Admitted Axiom *)\nTheorem green : True. auto. Qed."):
        raise AssertionError("Coq comment text was treated as a proof hole")

    catalog = mapping.load(mapping.CATALOG_PATH)
    matrix = mapping.load(mapping.MATRIX_PATH)
    corpus = mapping.load(mapping.WITNESS_PATH)
    sources = {
        prover: (ROOT / relative).read_text(encoding="utf-8")
        for prover, relative in matrix["models"].items()
    }
    mapping.validate(catalog, matrix, corpus, sources)

    unknown_law = copy.deepcopy(corpus)
    unknown_law["witnesses"][0]["laws"].append("FAOF2-GRAPH-999")
    expect_mapping_red("unknown-witness-law", catalog, matrix, unknown_law, sources)

    status_drift = copy.deepcopy(matrix)
    status_drift["laws"][0]["status"] = "UNPROVED"
    expect_mapping_red("status-drift", catalog, status_drift, corpus, sources)

    missing_theorem = copy.deepcopy(matrix)
    missing_theorem["laws"][0]["lean"].append("not_a_real_theorem")
    expect_mapping_red("missing-theorem", catalog, missing_theorem, corpus, sources)

    print(
        "FAOF2_FORMAL_CHECKS_RED_MATRIX=PASS "
        f"proof_mutations={len(proof_mutations)} mapping_mutations=3"
    )


if __name__ == "__main__":
    main()
