#!/bin/bash
# Export the actual running application's OpenAPI document and publish only after all checks pass.
set -euo pipefail
API_URL="${1:-https://api.render.cc.cd}"
OUTPUT="${2:-docs/api/openapi-preview-current.json}"
GROUP="${OPENAPI_GROUP:-}"
TEMP="$(mktemp "${TMPDIR:-/tmp}/composition-openapi.XXXXXX.json")"
trap 'rm -f "$TEMP"' EXIT
DOC_URL="$API_URL/v3/api-docs${GROUP:+/$GROUP}"
echo "Exporting OpenAPI from: $DOC_URL"
curl -fsS "$DOC_URL" -o "$TEMP"
python3 - "$TEMP" <<'PY'
import json, sys
path=sys.argv[1]; document=json.load(open(path, encoding='utf-8')); version=document.get('openapi','')
if not version.startswith('3.1.'): raise SystemExit(f'ERROR: runtime export is {version!r}; OpenAPI 3.1.x is required')
methods={'get','put','post','delete','options','head','patch','trace'}; operations=0
for route,item in document.get('paths',{}).items():
  for method,operation in item.items():
    if method.lower() in methods:
      operations+=1
      if not operation.get('operationId'): raise SystemExit(f'ERROR: missing operationId for {method.upper()} {route}')
print(f'Validated OpenAPI {version} with {operations} operations')
PY
python3 scripts/api/verify-composition-openapi.py --runtime "$TEMP"
mkdir -p "$(dirname "$OUTPUT")"
mv "$TEMP" "$OUTPUT"
trap - EXIT
echo "Published generated runtime OpenAPI to: $OUTPUT"
