#!/bin/bash
# Render Execution Smoke Test
# Usage: RENDER_EXECUTION_WRITE=1 ./scripts/smoke/render-execution-smoke.sh
set -e

API_BASE="${API_BASE_URL:-https://api.render.cc.cd}"
WRITE_MODE="${RENDER_EXECUTION_WRITE:-0}"
POLL_INTERVAL="${POLL_INTERVAL_SECONDS:-3}"
TIMEOUT="${TIMEOUT_SECONDS:-120}"
TENANT_ID="${SMOKE_TENANT_ID:-smoke-tenant}"
PROJECT_ID="${SMOKE_PROJECT_ID:-smoke-project}"

echo "=== Render Execution Smoke Test ==="
echo "API: $API_BASE"
echo "Write mode: $WRITE_MODE"
echo ""

# 1. Health check
echo "1. Health check..."
HEALTH=$(curl -sS "$API_BASE/actuator/health" 2>/dev/null || echo '{"status":"DOWN"}')
if echo "$HEALTH" | grep -q '"status":"UP"'; then
    echo "   ✅ Health: UP"
else
    echo "   ❌ Health: FAIL"
    exit 1
fi

if [ "$WRITE_MODE" != "1" ]; then
    echo ""
    echo "=== Read-only mode. Set RENDER_EXECUTION_WRITE=1 to run execution test ==="
    exit 0
fi

# 2. Get dev token
echo "2. Getting dev token..."
TOKEN_RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/dev/auth/token" \
    -H "Content-Type: application/json" \
    -d "{\"tenantId\":\"$TENANT_ID\",\"userId\":\"smoke-user\"}" 2>/dev/null)
TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
    echo "   ❌ Failed to get token"
    exit 1
fi
echo "   ✅ Token obtained"

# 3. Create render job (create-only, does NOT execute)
echo "3. Creating render job..."
JOB_RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/render/jobs" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"projectId\":\"$PROJECT_ID\",\"timelineSnapshotId\":\"snap-$(date +%s)\",\"profile\":\"default_1080p\"}" 2>/dev/null)
JOB_ID=$(echo "$JOB_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "$JOB_ID" ]; then
    echo "   ❌ Failed to create job: $JOB_RESPONSE"
    exit 1
fi
echo "   ✅ Job created: $JOB_ID"

# 4. Execute job (canonical execution endpoint)
echo "4. Executing job via canonical endpoint..."
EXEC_RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/render/jobs/$JOB_ID/execute" \
    -H "Authorization: Bearer $TOKEN" 2>/dev/null)
FINAL_STATUS=$(echo "$EXEC_RESPONSE" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "   Execute response status: $FINAL_STATUS"

# 5. Poll if still non-terminal
echo "5. Polling job status..."
ELAPSED=0
TIMEOUT_MS=$((TIMEOUT * 1000))

while [ "$ELAPSED" -lt "$TIMEOUT_MS" ]; do
    if [ "$FINAL_STATUS" = "COMPLETED" ] || [ "$FINAL_STATUS" = "FAILED" ] || [ "$FINAL_STATUS" = "CANCELLED" ] || [ "$FINAL_STATUS" = "REJECTED" ]; then
        break
    fi
    sleep 3
    ELAPSED=$((ELAPSED + 3000))
    STATUS_RESPONSE=$(curl -sS "$API_BASE/api/v1/render/jobs/$JOB_ID" -H "Authorization: Bearer $TOKEN" 2>/dev/null)
    FINAL_STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "   Status: $FINAL_STATUS (${ELAPSED}ms)"
done

echo ""
echo "=== Results ==="
echo "Job ID: $JOB_ID"
echo "Final Status: $FINAL_STATUS"

# 6. Check artifacts if COMPLETED
if [ "$FINAL_STATUS" = "COMPLETED" ]; then
    echo ""
    echo "6. Checking artifacts..."
    ARTIFACTS=$(curl -sS "$API_BASE/api/v1/render/jobs/$JOB_ID/artifacts" -H "Authorization: Bearer $TOKEN" 2>/dev/null)
    ARTIFACT_COUNT=$(echo "$ARTIFACTS" | grep -o '"artifactId"' | wc -l)
    echo "   Artifacts: $ARTIFACT_COUNT"
    
    if [ "$ARTIFACT_COUNT" -gt 0 ]; then
        echo "   ✅ PASS"
    else
        echo "   ❌ FAIL: No artifacts"
        exit 1
    fi
else
    echo "   ❌ FAIL: Status=$FINAL_STATUS"
    exit 1
fi
