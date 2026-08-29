"""Runtime-only BMF import and dependency observation for the bounded B0 POC."""

from __future__ import annotations

import importlib.metadata
import json
import os
import pathlib
import shutil
import subprocess
import sys
from typing import Any


def command_observation(program: str) -> dict[str, Any]:
    resolved = shutil.which(program)
    if resolved is None:
        raise RuntimeError(f"{program} is absent from the exact runtime PATH")

    completed = subprocess.run(
        [resolved, "-hide_banner", "-version"],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    lines = completed.stdout.splitlines()
    configuration = next(
        (line.removeprefix("configuration: ") for line in lines if line.startswith("configuration: ")),
        None,
    )
    return {
        "executable": str(pathlib.Path(resolved).resolve()),
        "version_line": lines[0] if lines else "",
        "configuration": configuration,
    }


def main() -> int:
    expected_commit = os.environ["BMF_B0_EXPECTED_BMF_COMMIT"]
    expected_version = os.environ["BMF_B0_EXPECTED_BMF_VERSION"]
    expected_ffmpeg_prefix = pathlib.Path(os.environ["BMF_B0_EXPECTED_FFMPEG_PREFIX"])

    try:
        import numpy
        import bmf
        from bmf.lib import _bmf, _hmp

        bmf_version = bmf.get_version()
        bmf_commit = bmf.get_commit()
        ffmpeg = command_observation("ffmpeg")
        ffprobe = command_observation("ffprobe")

        if bmf_version != expected_version:
            raise RuntimeError(
                f"BMF version mismatch: observed {bmf_version!r}, expected {expected_version!r}"
            )
        if bmf_commit != expected_commit:
            raise RuntimeError(
                f"BMF commit mismatch: observed {bmf_commit!r}, expected {expected_commit!r}"
            )
        expected_bin = expected_ffmpeg_prefix / "bin"
        for observation in (ffmpeg, ffprobe):
            if pathlib.Path(observation["executable"]).parent != expected_bin:
                raise RuntimeError(
                    "FFmpeg observation escaped the expected Nix store prefix: "
                    f"{observation['executable']!r}"
                )

        result = {
            "schema_version": 1,
            "proof_scope": "BMF_BUILD_RUNTIME_REPRODUCIBILITY_AND_DEPENDENCY_CLOSURE_PROOF",
            "runtime_smoke_status": "OBSERVED",
            "semantic_conformance": "NOT_EVALUATED",
            "provider_id": "bmf",
            "future_stable_provider_implementation_id": "bmf.cpu.v1",
            "bmf": {
                "distribution_version": importlib.metadata.version("BabitMF"),
                "native_build_version": bmf_version,
                "native_build_commit": bmf_commit,
                "package_file": str(pathlib.Path(bmf.__file__).resolve()),
                "native_bmf_file": str(pathlib.Path(_bmf.__file__).resolve()),
                "native_hmp_file": str(pathlib.Path(_hmp.__file__).resolve()),
            },
            "python": {
                "executable": str(pathlib.Path(sys.executable).resolve()),
                "implementation": sys.implementation.name,
                "version": sys.version.splitlines()[0],
                "numpy_version": numpy.__version__,
                "numpy_file": str(pathlib.Path(numpy.__file__).resolve()),
            },
            "ffmpeg": ffmpeg,
            "ffprobe": ffprobe,
            "claims_excluded": [
                "DISTRIBUTABILITY",
                "SEMANTIC_CONFORMANCE",
                "OUTPUT_EQUIVALENCE",
                "GAUSSIAN_BLUR_CONFORMANCE",
            ],
        }
        print(json.dumps(result, sort_keys=True, separators=(",", ":")))
        return 0
    except Exception as error:  # Emit bounded machine-readable failure evidence.
        print(
            json.dumps(
                {
                    "schema_version": 1,
                    "proof_scope": "BMF_BUILD_RUNTIME_REPRODUCIBILITY_AND_DEPENDENCY_CLOSURE_PROOF",
                    "runtime_smoke_status": "ERROR",
                    "semantic_conformance": "NOT_EVALUATED",
                    "error_type": type(error).__name__,
                    "error": str(error),
                },
                sort_keys=True,
                separators=(",", ":"),
            )
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
