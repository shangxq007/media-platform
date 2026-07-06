#!/bin/bash
# Export OpenAPI spec from preview environment
set -e

API_URL="${1:-https://api.render.cc.cd}"
OUTPUT="${2:-docs/api/openapi-preview-current.json}"

echo "Exporting OpenAPI from: $API_URL"
curl -sS "$API_URL/v3/api-docs" -o "$OUTPUT"

echo "Exported to: $OUTPUT"
echo "Paths: $(grep -o '"/' "$OUTPUT" | wc -l)"
echo "Size: $(wc -c < "$OUTPUT") bytes"
