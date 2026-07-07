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

# 2. Verify FFmpeg provider (read-only)
echo "2. FFmpeg provider check..."
API_DOCS=$(curl -sS "$API_BASE/v3/api-docs" 2>/dev/null)
if echo "$API_DOCS" | grep -q "render"; then
    echo "   ✅ Render endpoints available"
else
    echo "   ⚠️ Render endpoints not found in OpenAPI"
fi

if [ "$WRITE_MODE" != "1" ]; then
    echo ""
    echo "=== Read-only mode. Set RENDER_EXECUTION_WRITE=1 to run execution test ==="
    exit 0
fi

# 3. Get dev token
echo "3. Getting dev token..."
TOKEN_RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/dev/auth/token" \
    -H "Content-Type: application/json" \
    -d "{\"tenantId\":\"$TENANT_ID\",\"userId\":\"smoke-user\"}" 2>/dev/null)
TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
    echo "   ❌ Failed to get token: $TOKEN_RESPONSE"
    exit 1
fi
echo "   ✅ Token obtained"

# 4. Create tenant and project
echo "4. Creating tenant and project..."
curl -sS -X POST "$API_BASE/api/v1/tenants" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"tenantId\":\"$TENANT_ID\"}" 2>/dev/null > /dev/null || true

curl -sS -X POST "$API_BASE/api/v1/tenants/$TENANT_ID/projects" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"projectId\":\"$PROJECT_ID\",\"name\":\"Smoke Project\"}" 2>/dev/null > /dev/null || true
echo "   ✅ Tenant/project setup attempted"

# 5. Create and execute render job
echo "5. Creating render job..."
TIMELINE_JSON='{"version":"1.0","tracks":[{"id":"v1","type":"video","clips":[{"id":"c1","source":"lavfi","sourceParams":{"filter":"testsrc=size=320x180:rate=30"},"duration":2}]}]}'

JOB_RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/render/jobs" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
        \"projectId\":\"$PROJECT_ID\",
        \"timelineSnapshotId\":\"snap-smoke-$(date +%s)\",
        \"profile\":\"default_1080p\"
    }" 2>/dev/null)

JOB_ID=$(echo "$JOB_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "$JOB_ID" ]; then
    echo "   ❌ Failed to create job: $JOB_RESPONSE"
    exit 1
fi
echo "   ✅ Job created: $JOB_ID"

# 6. Execute job
echo "6. Executing job..."
EXEC_RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/render/jobs/$JOB_ID/execute" \
    -H "Authorization: Bearer $TOKEN" 2>/dev/null)
EXEC_STATUS=$(echo "$EXEC_RESPONSE" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
echo "   Execute response: $EXEC_STATUS"

# 7. Poll job status
echo "7. Polling job status..."
ELAPSED=0
FINAL_STATUS="$EXEC_STATUS"

while [ "$ELAPSED" -lt "$((TIMEOUT * 1000))" ]; do
    # Terminal success
    if [ "$FINAL_STATUS" = "COMPLETED" ]; then
        break
    fi
    # Terminal failure
    if [ "$FINAL_STATUS" = "FAILED" ] || [ "$FINAL_STATUS" = "CANCELLED" ] || [ "$FINAL_STATUS" = "REJECTED" ]; then
        break
    fi
    
    sleep "$POLL_INTERVAL"
    ELAPSED=$((ELAPSED + (POLL_INTERVAL * 1000)))
    
    STATUS_RESPONSE=$(curl -sS "$API_BASE/api/v1/render/jobs/$JOB_ID" \
        -H "Authorization: Bearer $TOKEN" 2>/dev/null)
    FINAL_STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "   Status: $FINAL_STATUS (${ELAPSED}ms)"
done

echo ""
echo "=== Results ==="
echo "Job ID: $JOB_ID"
echo "Final Status: $FINAL_STATUS"
echo "Elapsed: ${ELAPSED}ms"

# 8. Check artifacts if completed
if [ "$FINAL_STATUS" = "COMPLETED" ]; then
    echo ""
    echo "8. Checking artifacts..."
    ARTIFACTS=$(curl -sS "$API_BASE/api/v1/render/jobs/$JOB_ID/artifacts" \
        -H "Authorization: Bearer $TOKEN" 2>/dev/null)
    ARTIFACT_COUNT=$(echo "$ARTIFACTS" | grep -o '"artifactId"' | wc -l)
    echo "   Artifacts: $ARTIFACT_COUNT"
    
    if [ "$ARTIFACT_COUNT" -gt 0 ]; then
        ARTIFACT_ID=$(echo "$ARTIFACTS" | grep -o '"artifactId":"[^"]*"' | head -1 | cut -d'"' -f4)
        echo "   Artifact ID: $ARTIFACT_ID"
        echo "   ✅ PASS: Render execution produced artifact"
    else
        echo "   ❌ FAIL: No artifacts produced"
        exit 1
    fi
elif [ "$FINAL_STATUS" = "FAILED" ]; then
    echo "   ❌ FAIL: Job failed"
    exit 1
elif [ "$FINAL_STATUS" = "CANCELLED" ] || [ "$FINAL_STATUS" = "REJECTED" ]; then
    echo "   ❌ FAIL: Job $FINAL_STATUS"
    exit 1
else
    echo "   ⚠️ TIMEOUT: Job still $FINAL_STATUS after ${ELAPSED}ms"
    exit 1
fi
