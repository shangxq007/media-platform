"""Bounded BMF import/linkage and minimal CPU graph runtime smoke."""

from __future__ import annotations

import hashlib
import importlib.metadata
import json
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
from collections.abc import Mapping
from typing import Any


EXPECTED_GRAPH_MODULE_NAMES = ["c_ffmpeg_decoder", "c_ffmpeg_encoder"]
PPM_FIXTURE = (
    b"P6\n2 2\n255\n"
    + bytes([255, 0, 0, 0, 255, 0, 0, 0, 255, 255, 255, 255])
)


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


def require_path_beneath(path: pathlib.Path, root: pathlib.Path, description: str) -> None:
    if path != root and root not in path.parents:
        raise RuntimeError(f"{description} escaped {root}: {path}")


def validate_python_import_path() -> list[dict[str, str]]:
    nix_store = pathlib.Path("/nix/store")
    observations = []
    for index, entry in enumerate(sys.path):
        if not entry:
            raise RuntimeError(f"sys.path[{index}] is empty and could make cwd an import authority")
        path = pathlib.Path(entry)
        if not path.is_absolute():
            raise RuntimeError(f"sys.path[{index}] is not absolute: {entry!r}")
        resolved = path.resolve()
        require_path_beneath(resolved, nix_store, f"sys.path[{index}]")
        observations.append({"entry": entry, "resolved": str(resolved)})
    return observations


def module_name_from_stream(stream: Any) -> str:
    """Read the frozen SDK's stream/node metadata without changing the graph."""

    expected = set(EXPECTED_GRAPH_MODULE_NAMES)
    seen: set[int] = set()

    def find(value: Any, depth: int = 0) -> str | None:
        if value is None or depth > 4:
            return None
        if isinstance(value, str):
            return value if value in expected else None
        identity = id(value)
        if identity in seen:
            return None
        seen.add(identity)

        if isinstance(value, Mapping):
            for key in ("module_name", "module_name_", "name", "name_"):
                candidate = value.get(key)
                if isinstance(candidate, str) and candidate in expected:
                    return candidate
            for key in ("module_info", "module_info_", "node", "node_", "node_meta"):
                if key in value:
                    candidate = find(value[key], depth + 1)
                    if candidate is not None:
                        return candidate
            return None

        for attribute in ("module_name", "module_name_", "name", "name_"):
            candidate = getattr(value, attribute, None)
            if isinstance(candidate, str) and candidate in expected:
                return candidate
        for method_name in ("get_module_name", "get_name"):
            method = getattr(value, method_name, None)
            if callable(method):
                try:
                    candidate = method()
                except TypeError:
                    continue
                if isinstance(candidate, str) and candidate in expected:
                    return candidate
        for attribute in ("module_info", "module_info_", "node", "node_", "node_meta_"):
            candidate = find(getattr(value, attribute, None), depth + 1)
            if candidate is not None:
                return candidate
        for method_name in ("get_module_info", "get_node", "get_node_meta"):
            method = getattr(value, method_name, None)
            if callable(method):
                try:
                    method_value = method()
                except TypeError:
                    continue
                candidate = find(method_value, depth + 1)
                if candidate is not None:
                    return candidate
        return None

    module_name = find(stream)
    if module_name is None:
        raise RuntimeError("could not read the BMF module name from constructed stream metadata")
    return module_name


def mapped_builtin_module_paths() -> list[pathlib.Path]:
    paths: set[pathlib.Path] = set()
    with pathlib.Path("/proc/self/maps").open(encoding="utf-8") as maps:
        for line in maps:
            fields = line.rstrip().split(maxsplit=5)
            if len(fields) != 6:
                continue
            mapped_path = fields[5].removesuffix(" (deleted)")
            if "libbuiltin_modules" in pathlib.Path(mapped_path).name:
                paths.add(pathlib.Path(mapped_path).resolve(strict=True))
    if not paths:
        raise RuntimeError("no mapped libbuiltin_modules path was present after graph execution")
    return sorted(paths, key=str)


def validate_builtin_module_paths(
    paths: list[pathlib.Path], package_root: pathlib.Path, execution_cwd: pathlib.Path
) -> None:
    forbidden_roots = (
        pathlib.Path("/usr/local/share/bmf_mods"),
        pathlib.Path("/opt/tiger/bmf_mods"),
    )
    for path in paths:
        require_path_beneath(path, package_root, "mapped built-in module")
        if path == execution_cwd or execution_cwd in path.parents:
            raise RuntimeError(f"mapped built-in module used cwd as authority: {path}")
        for forbidden_root in forbidden_roots:
            if path == forbidden_root or forbidden_root in path.parents:
                raise RuntimeError(f"mapped built-in module used ambient root {forbidden_root}: {path}")
        path_text = str(path)
        if (
            (path_text.startswith("/usr/") or path_text.startswith("/home/"))
            and ("/site-packages/" in path_text or "/dist-packages/" in path_text)
        ):
            raise RuntimeError(f"mapped built-in module used host Python packages: {path}")


def execute_minimal_graph(bmf: Any, package_root: pathlib.Path) -> dict[str, Any]:
    original_cwd = pathlib.Path.cwd().resolve()
    with tempfile.TemporaryDirectory(prefix="bmf-b0-minimal-graph-") as td:
        execution_cwd = pathlib.Path(td).resolve()
        fixture = execution_cwd / "single.ppm"
        fixture.write_bytes(PPM_FIXTURE)
        if list(execution_cwd.iterdir()) != [fixture]:
            raise RuntimeError("temporary graph cwd did not contain exactly the deterministic fixture")

        try:
            os.chdir(execution_cwd)
            graph = bmf.graph()
            decoded = graph.decode({"input_path": str(fixture)})
            sink = bmf.encode(decoded["video"], None, {"null_output": 1})
            module_names = [
                module_name_from_stream(decoded["video"]),
                module_name_from_stream(sink),
            ]
            if module_names != EXPECTED_GRAPH_MODULE_NAMES:
                raise RuntimeError(
                    f"minimal graph modules were {module_names!r}, expected "
                    f"{EXPECTED_GRAPH_MODULE_NAMES!r}"
                )
            result = sink.run()
            builtin_paths = mapped_builtin_module_paths()
            validate_builtin_module_paths(builtin_paths, package_root, execution_cwd)
            if list(execution_cwd.iterdir()) != [fixture]:
                raise RuntimeError("minimal graph wrote an unexpected file into its temporary cwd")
        finally:
            os.chdir(original_cwd)

        if pathlib.Path.cwd().resolve() != original_cwd:
            raise RuntimeError("original cwd was not restored before temporary-directory cleanup")

        return {
            "smoke_kind": "MINIMAL_BMF_GRAPH_EXECUTION_SMOKE",
            "status": "PASS",
            "graph_execution_status": "PASS",
            "fixture": {
                "path_kind": "ABSOLUTE_TEMPORARY_PATH",
                "format": "P6_PPM",
                "dimensions": [2, 2],
                "sha256": hashlib.sha256(PPM_FIXTURE).hexdigest(),
            },
            "graph_node_module_names": module_names,
            "loaded_builtin_module_names": module_names,
            "actual_loaded_builtin_module_paths": [str(path) for path in builtin_paths],
            "bmf_package_root": str(package_root),
            "execution_working_directory_kind": "TEMPORARY_DIRECTORY_CONTAINING_ONLY_FIXTURE",
            "absolute_fixture_path_used": True,
            "original_working_directory_restored_before_cleanup": True,
            "ambient_module_path_traversal": False,
            "graph_result_repr": repr(result),
        }


def main() -> int:
    expected_commit = os.environ["BMF_B0_EXPECTED_BMF_COMMIT"]
    expected_version = os.environ["BMF_B0_EXPECTED_BMF_VERSION"]
    expected_ffmpeg_prefix = pathlib.Path(os.environ["BMF_B0_EXPECTED_FFMPEG_PREFIX"])

    try:
        python_no_user_site = os.environ.get("PYTHONNOUSERSITE")
        if python_no_user_site is None or python_no_user_site.casefold() not in {"1", "true"}:
            raise RuntimeError(
                "PYTHONNOUSERSITE must be '1' or 'true' (case-insensitive); "
                f"observed {python_no_user_site!r}"
            )
        python_sys_path = validate_python_import_path()

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

        package_root = pathlib.Path(bmf.__file__).resolve().parent
        graph_smoke = execute_minimal_graph(bmf, package_root)
        import_smoke = {
            "smoke_kind": "IMPORT_AND_NATIVE_LINKAGE_SMOKE",
            "status": "PASS",
            "python_no_user_site": True,
            "python_no_user_site_environment_value": python_no_user_site,
            "sys_path_nix_store_only": True,
        }
        result = {
            "schema_version": 1,
            "proof_scope": "BMF_BUILD_RUNTIME_REPRODUCIBILITY_AND_DEPENDENCY_CLOSURE_PROOF",
            "runtime_smoke_status": "PASS",
            "semantic_conformance": "NOT_EVALUATED",
            "provider_id": "bmf",
            "future_stable_provider_implementation_id": "bmf.cpu.v1",
            "minimal_graph_execution_is_runtime_smoke_not_semantic_conformance_v1": True,
            "bmf_ambient_module_helper_residue": "KNOWN_NON_EXECUTED_UPSTREAM_RESIDUE",
            "import_and_native_linkage_smoke": import_smoke,
            "minimal_bmf_graph_execution_smoke": graph_smoke,
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
                "sys_path": python_sys_path,
            },
            "ffmpeg": ffmpeg,
            "ffprobe": ffprobe,
            "claims_excluded": [
                "DISTRIBUTABILITY",
                "SEMANTIC_CONFORMANCE",
                "OUTPUT_EQUIVALENCE",
                "GAUSSIAN_BLUR",
                "GAUSSIAN_BLUR_CONFORMANCE",
                "CROSS_RUNTIME_CONFORMANCE",
                "PROVIDER_IMPLEMENTATION",
                "PLATFORM_LOWERING",
                "GPU_WORK",
                "B1_WORK",
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
                    "minimal_graph_execution_is_runtime_smoke_not_semantic_conformance_v1": True,
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
