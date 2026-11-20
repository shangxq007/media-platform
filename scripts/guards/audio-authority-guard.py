#!/usr/bin/env python3
"""Fail-closed contract guard for the audio-mixing authority/BMF-first slice."""
from __future__ import annotations

import argparse
import re
import shutil
import tempfile
from pathlib import Path

REQUIRED_DECISIONS = {
    "OD-001", "OD-002", "OD-003", "OD-004", "OD-005", "OD-006",
    "OD-009", "OD-010", "OD-011",
}

FILES = {
    "decisions": "docs/governance/OWNER_DECISIONS.md",
    "definition": "operation-module/src/main/java/com/example/platform/operation/operation/OperationDefinition.java",
    "planner": "operation-module/src/main/java/com/example/platform/operation/plan/OperationPlanner.java",
    "materializer": "render-module/src/main/java/com/example/platform/render/domain/renderplan/DefaultRenderMaterializer.java",
    "bmf": "bmf-provider-module/src/main/java/com/example/platform/bmf/BmfCpuRuntimeAdapter.java",
    "clip_editor": "frontend/src/product/timeline/ClipEditor.tsx",
}


def read(root: Path, key: str) -> str:
    path = root / FILES[key]
    if not path.is_file() or path.is_symlink():
        raise RuntimeError(f"required authority file missing: {FILES[key]}")
    return path.read_text(encoding="utf-8")


def check(root: Path) -> list[str]:
    failures: list[str] = []
    try:
        decisions = read(root, "decisions")
        ids = re.findall(r"\|\s*(OD-\d{3})\s*\|", decisions)
        if len(ids) != len(set(ids)):
            failures.append("duplicate Owner decision id")
        missing = REQUIRED_DECISIONS - set(ids)
        if missing:
            failures.append("missing Owner decisions: " + ",".join(sorted(missing)))
        for text in ("Canonical Timeline", "Operation", "CAS", "BMF", "formal path",
                     "no compatibility layer", "explicit Canonical AudioRoute"):
            if text.lower() not in decisions.lower():
                failures.append("Owner decision text missing: " + text)

        definition = read(root, "definition")
        for op in ("audio.gain.set", "audio.mute.set", "audio.balance.set"):
            if op not in definition:
                failures.append("canonical audio operation missing: " + op)

        planner = read(root, "planner")
        if "AudioRoute.of" not in planner or "AudioMixReplaced" not in planner:
            failures.append("canonical AudioRoute lifecycle is not wired through planning")

        materializer = read(root, "materializer")
        if "TimedTrackInput" not in materializer or "routeFor(" not in materializer:
            failures.append("materializer does not carry canonical audio routing")
        if "AUDIO_MIX node" in materializer and "No separate AUDIO_MIX" not in materializer:
            failures.append("parallel AUDIO_MIX authority remains")

        bmf = read(root, "bmf")
        if "amix" not in bmf or "'pan'" not in bmf or "'volume'" not in bmf:
            failures.append("BMF graph does not execute gain/pan/mix")
        if "FFMPEG_ARGV" in bmf:
            failures.append("BMF graph must not become an FFmpeg provider argv path")

        editor = read(root, "clip_editor")
        for label in ("Audio gain", "Mute clip", "Audio pan", "Apply audio mix"):
            if label not in editor:
                failures.append("browser audio control missing: " + label)
    except RuntimeError as error:
        failures.append(str(error))
    return failures


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path.cwd())
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    root = args.root.resolve()
    failures = check(root)
    if args.self_test:
        with tempfile.TemporaryDirectory() as tmp:
            copy = Path(tmp)
            for rel in FILES.values():
                src = root / rel
                dst = copy / rel
                dst.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(src, dst)
            mutated = copy / FILES["definition"]
            mutated.write_text(mutated.read_text(encoding="utf-8").replace("audio.gain.set", "audio.gain.removed"),
                               encoding="utf-8")
            negative = check(copy)
            if not negative:
                failures.append("negative control was not rejected")
        print("AUDIO_AUTHORITY_GUARD_SELFTEST=" + ("PASS" if not failures else "FAIL"))
    for failure in failures:
        print("AUDIO_AUTHORITY_GUARD_FAILURE=" + failure)
    print("AUDIO_AUTHORITY_GUARD=" + ("PASS" if not failures else "FAIL"))
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
