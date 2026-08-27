#!/usr/bin/env python3
"""Cross-check the FAOF-2 catalog, formal status matrix, witnesses, and theorem names."""

from __future__ import annotations

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CATALOG_PATH = ROOT / "formal/faof2-law-catalog-v1.json"
MATRIX_PATH = ROOT / "formal/faof2-status-matrix-v1.json"
WITNESS_PATH = ROOT / "formal/witnesses/faof2-graph-witnesses-v1.json"


def load(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def unique_by(items: list[dict[str, object]], key: str, label: str) -> dict[str, dict[str, object]]:
    result: dict[str, dict[str, object]] = {}
    for item in items:
        value = item.get(key)
        if not isinstance(value, str) or not value:
            raise AssertionError(f"{label} has missing {key}")
        if value in result:
            raise AssertionError(f"duplicate {label} {key}: {value}")
        result[value] = item
    return result


def validate(
        catalog: dict[str, object],
        matrix: dict[str, object],
        corpus: dict[str, object],
        sources: dict[str, str]) -> tuple[int, int, int]:
    if catalog.get("formalStatusMatrix") != str(MATRIX_PATH.relative_to(ROOT)):
        raise AssertionError("law catalog does not link the exact formal status matrix")
    if matrix.get("lawCatalog") != str(CATALOG_PATH.relative_to(ROOT)):
        raise AssertionError("status matrix does not link the exact law catalog")
    if matrix.get("witnessCorpus") != str(WITNESS_PATH.relative_to(ROOT)):
        raise AssertionError("status matrix does not link the exact witness corpus")

    laws = unique_by(catalog["laws"], "lawId", "catalog law")
    statuses = unique_by(matrix["laws"], "lawId", "status law")
    if laws.keys() != statuses.keys():
        raise AssertionError("catalog and status matrix law IDs differ")

    witness_coverage = {law_id: 0 for law_id in laws}
    for witness in corpus["witnesses"]:
        witness_id = witness.get("id", "<missing>")
        for law_id in witness.get("laws", []):
            if law_id not in laws:
                raise AssertionError(f"witness {witness_id} maps unknown law {law_id}")
            witness_coverage[law_id] += 1

    missing_witnesses = [law_id for law_id, count in witness_coverage.items() if count == 0]
    if missing_witnesses:
        raise AssertionError(f"laws without shared witnesses: {missing_witnesses}")

    for law_id, law in laws.items():
        status = statuses[law_id]
        catalog_status = law.get("status", {})
        if catalog_status.get("formal") != status.get("status"):
            raise AssertionError(f"{law_id}: catalog formal status differs from matrix")
        if catalog_status.get("java") != "CUSTOM_AND_JGRAPHT_CONFORMANCE":
            raise AssertionError(f"{law_id}: Java conformance status is not exact")
        if catalog_status.get("witness") != "COVERED":
            raise AssertionError(f"{law_id}: witness status is not exact")
        for prover in ("lean", "coq"):
            theorem_names = status.get(prover)
            if not isinstance(theorem_names, list):
                raise AssertionError(f"{law_id}: {prover} theorem mapping must be a list")
            for theorem_name in theorem_names:
                declaration = re.compile(
                    rf"\b(?:theorem|lemma)\s+{re.escape(theorem_name)}\b"
                    if prover == "lean"
                    else rf"\b(?:Theorem|Lemma)\s+{re.escape(theorem_name)}\b"
                )
                if not declaration.search(sources[prover]):
                    raise AssertionError(
                        f"{law_id}: mapped {prover} theorem is absent: {theorem_name}"
                    )

    return len(laws), len(corpus["witnesses"]), sum(witness_coverage.values())


def main() -> None:
    catalog = load(CATALOG_PATH)
    matrix = load(MATRIX_PATH)
    corpus = load(WITNESS_PATH)
    sources = {
        prover: (ROOT / relative).read_text(encoding="utf-8")
        for prover, relative in matrix["models"].items()
    }
    laws, witnesses, mappings = validate(catalog, matrix, corpus, sources)
    print(
        "FAOF2_WITNESS_MAPPING=PASS "
        f"laws={laws} witnesses={witnesses} mappings={mappings}"
    )


if __name__ == "__main__":
    main()
