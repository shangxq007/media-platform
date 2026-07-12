#!/bin/bash
# Preview Render Smoke Test
# Usage: PREVIEW_SMOKE_WRITE=1 ./scripts/smoke/preview-render-smoke.sh
set -e

API_URL="${API_URL:-https://api.render.cc.cd}"
WRITE_MODE="${PREVIEW_SMOKE_WRITE:-0}"

echo "=== Preview Render Smoke Test ==="
echo "API: $API_URL"
echo "Write mode: $WRITE_MODE"
echo ""

# 1. Health check
echo "1. Health check..."
HEALTH=$(curl -sS "$API_URL/actuator/health" 2>/dev/null)
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    echo "   ✅ Health: UP"
else
    echo "   ❌ Health: FAIL"
    exit 1
fi

# 2. OpenAPI docs
echo "2. OpenAPI check..."
API_DOCS=$(curl -sS "$API_URL/v3/api-docs" 2>/dev/null)
PATH_COUNT=$(echo "$API_DOCS" | grep -o '"/' | wc -l)
if [ "$PATH_COUNT" -gt 100 ]; then
    echo "   ✅ OpenAPI: $PATH_COUNT paths"
else
    echo "   ⚠️ OpenAPI: only $PATH_COUNT paths"
fi

# 3. Render jobs endpoint (read-only)
echo "3. Render jobs endpoint..."
HTTP_CODE=$(curl -sS -o /dev/null -w "%{http_code}" "$API_URL/api/v1/render/jobs" 2>/dev/null)
if [ "$HTTP_CODE" = "200" ] || [ "$HTTP_CODE" = "401" ]; then
    echo "   ✅ Render jobs: HTTP $HTTP_CODE"
else
    echo "   ⚠️ Render jobs: HTTP $HTTP_CODE"
fi

# 4. Create render job (write mode only)
if [ "$WRITE_MODE" = "1" ]; then
    echo "4. Create render job (write mode)..."
    RESPONSE=$(curl -sS -X POST "$API_URL/api/v1/render/jobs" \
        -H "Content-Type: application/json" \
        -d '{
            "tenantId": "smoke-test",
            "projectId": "smoke-project",
            "profile": "default_1080p"
        }' 2>/dev/null)
    
    JOB_ID=$(echo "$RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    if [ -n "$JOB_ID" ]; then
        echo "   ✅ Job created: $JOB_ID"
        
        # Check status
        echo "5. Job status..."
        STATUS=$(curl -sS "$API_URL/api/v1/render/jobs/$JOB_ID" 2>/dev/null)
        echo "   Status: $(echo "$STATUS" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)"
    else
        echo "   ⚠️ Job creation: $RESPONSE"
    fi
else
    echo "4. Create render job: SKIPPED (set PREVIEW_SMOKE_WRITE=1 to enable)"
fi

echo ""
echo "=== Smoke test complete ==="
