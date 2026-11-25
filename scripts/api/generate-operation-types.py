"""Generate frontend Operation types from the exported batch protocol schema."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
schema = json.loads((ROOT / "contracts/http/media-api/timeline-batch-v2.schema.json").read_text())


def ts(node):
    if "enum" in node:
        return " | ".join(json.dumps(value) for value in node["enum"])
    if "oneOf" in node or "anyOf" in node:
        return " | ".join(ts(value) for value in node.get("oneOf", node.get("anyOf")))
    kind = node["type"]
    if kind == "array":
        return "Array<" + ts(node["items"]) + ">"
    if kind == "object":
        return "{ " + "; ".join(
            name + ("" if name in node.get("required", []) else "?") + ": " + ts(value)
            for name, value in node["properties"].items()) + " }"
    return {"integer": "number", "number": "number"}.get(kind, kind)


steps = schema["properties"]["operations"]["items"]["oneOf"]
batch = dict(schema, properties={key: value for key, value in schema["properties"].items() if key != "operations"})
lines = ["// Generated from timeline-batch-v2.schema.json by scripts/api/generate-operation-types.py.",
         "export type SemanticStep ="] + ["  | " + ts(step) for step in steps]
lines.append("export type SemanticBatch = " + ts(batch) + " & { operations: SemanticStep[] }")
(ROOT / "frontend/src/contracts/semantic-batch.generated.ts").write_text("\n".join(lines) + "\n")
