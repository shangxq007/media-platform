#!/bin/bash
# Export OpenAPI spec from preview environment
set -e

API_URL="${1:-https://api.render.cc.cd}"
OUTPUT="${2:-docs/api/openapi-preview-current.json}"

echo "Exporting OpenAPI from: $API_URL"
curl -sS "$API_URL/v3/api-docs" -o "$OUTPUT"

python3 - "$OUTPUT" <<'PY'
import json
import sys
from pathlib import Path

path = Path(sys.argv[1])
document = json.loads(path.read_text(encoding="utf-8"))
version = document.get("openapi", "")
if not version.startswith("3.1."):
    raise SystemExit(f"ERROR: runtime export is {version!r}; OpenAPI 3.1.x is required")
operations = 0
for route, item in document.get("paths", {}).items():
    for method, operation in item.items():
        if method.lower() in {"get", "put", "post", "delete", "options", "head", "patch", "trace"}:
            operations += 1
            if not operation.get("operationId"):
                raise SystemExit(f"ERROR: missing operationId for {method.upper()} {route}")
print(f"Validated OpenAPI {version} with {operations} operations")
PY

echo "Exported to: $OUTPUT"
echo "Paths: $(grep -o '"/' "$OUTPUT" | wc -l)"
echo "Size: $(wc -c < "$OUTPUT") bytes"
