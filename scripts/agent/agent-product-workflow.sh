#!/bin/bash
# Agent Product Workflow Harness
# Usage:
#   Read-only: ./scripts/agent/agent-product-workflow.sh
#   Write mode: AGENT_PRODUCT_WRITE=1 ./scripts/agent/agent-product-workflow.sh
set -e

API_BASE="${API_BASE_URL:-https://api.render.cc.cd}"
WRITE_MODE="${AGENT_PRODUCT_WRITE:-0}"
POLL_INTERVAL="${AGENT_POLL_INTERVAL_SECONDS:-2}"
TIMEOUT="${AGENT_TIMEOUT_SECONDS:-60}"
CORRELATION_ID="${AGENT_CORRELATION_ID:-product-$(date +%s)}"
WORKSPACE_ID="${AGENT_WORKSPACE_ID:-}"
PRODUCT_ID="${AGENT_PRODUCT_ID:-}"
JOB_ID="${AGENT_JOB_ID:-}"
ARTIFACT_ID="${AGENT_ARTIFACT_ID:-}"

# Start result
echo "{"
echo "  \"correlationId\": \"$CORRELATION_ID\","
echo "  \"mode\": \"$([ \"$WRITE_MODE\" = \"1\" ] && echo \"product-workflow\" || echo \"read-only\")\","
echo "  \"startTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","

# 1. Health check
HEALTH=$(curl -sS "$API_BASE/actuator/health" 2>/dev/null || echo '{"status":"DOWN"}')
HEALTH_STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
if [ "$HEALTH_STATUS" = "UP" ]; then
    HEALTH_OK="true"
else
    HEALTH_OK="false"
fi
echo "  \"health\": {\"status\": \"$HEALTH_STATUS\", \"ok\": $HEALTH_OK},"

if [ "$HEALTH_STATUS" != "UP" ]; then
    echo "  \"ok\": false,"
    echo "  \"errors\": [\"Health check failed\"]"
    echo "}"
    exit 1
fi

# 2. List workspaces
WORKSPACES=$(curl -sS "$API_BASE/api/v1/workspaces" 2>/dev/null || echo "[]")
WORKSPACE_COUNT=$(echo "$WORKSPACES" | grep -o '"id"' | wc -l)
echo "  \"workspaces\": {\"count\": $WORKSPACE_COUNT},"

# 3. List products
PRODUCTS=$(curl -sS "$API_BASE/api/v1/product/products" 2>/dev/null || echo "[]")
PRODUCT_COUNT=$(echo "$PRODUCTS" | grep -o '"id"' | wc -l)
echo "  \"products\": {\"count\": $PRODUCT_COUNT},"

# 4. List render jobs
JOBS=$(curl -sS "$API_BASE/api/v1/render/jobs" 2>/dev/null || echo "[]")
JOB_COUNT=$(echo "$JOBS" | grep -o '"id"' | wc -l)
echo "  \"renderJobs\": {\"count\": $JOB_COUNT},"

# 5. Fetch specific job if ID provided
if [ -n "$JOB_ID" ]; then
    JOB_DATA=$(curl -sS "$API_BASE/api/v1/render/jobs/$JOB_ID" 2>/dev/null || echo "{}")
    JOB_STATUS=$(echo "$JOB_DATA" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "  \"selectedJob\": {\"id\": \"$JOB_ID\", \"status\": \"$JOB_STATUS\"},"
fi

# 6. Fetch artifact if ID provided
if [ -n "$ARTIFACT_ID" ]; then
    ARTIFACT_DATA=$(curl -sS "$API_BASE/api/v1/artifacts/$ARTIFACT_ID" 2>/dev/null || echo "{}")
    ARTIFACT_MIME=$(echo "$ARTIFACT_DATA" | grep -o '"mimeType":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "  \"artifact\": {\"id\": \"$ARTIFACT_ID\", \"mimeType\": \"$ARTIFACT_MIME\"},"
fi

# 7. Create render job (write mode only)
if [ "$WRITE_MODE" = "1" ]; then
    PAYLOAD="{\"tenantId\":\"agent\",\"projectId\":\"${PRODUCT_ID:-agent-smoke}\",\"profile\":\"default_1080p\"}"
    
    CREATE_RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/render/jobs" \
        -H "Content-Type: application/json" \
        -d "$PAYLOAD" 2>/dev/null || echo "{}")
    
    CREATED_JOB_ID=$(echo "$CREATE_RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    CREATED_JOB_STATUS=$(echo "$CREATE_RESPONSE" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
    
    echo "  \"createdJob\": {\"jobId\": \"$CREATED_JOB_ID\", \"status\": \"$CREATED_JOB_STATUS\"},"
    
    # Poll job status
    if [ -n "$CREATED_JOB_ID" ]; then
        ELAPSED=0
        FINAL_STATUS="$CREATED_JOB_STATUS"
        
        while [ "$ELAPSED" -lt "$((TIMEOUT * 1000))" ]; do
            if [ "$FINAL_STATUS" = "COMPLETED" ] || [ "$FINAL_STATUS" = "FAILED" ] || [ "$FINAL_STATUS" = "CANCELLED" ]; then
                break
            fi
            
            sleep "$POLL_INTERVAL"
            ELAPSED=$((ELAPSED + (POLL_INTERVAL * 1000)))
            
            POLL_RESPONSE=$(curl -sS "$API_BASE/api/v1/render/jobs/$CREATED_JOB_ID" 2>/dev/null || echo "{}")
            FINAL_STATUS=$(echo "$POLL_RESPONSE" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
        done
        
        echo "  \"polling\": {\"jobId\": \"$CREATED_JOB_ID\", \"finalStatus\": \"$FINAL_STATUS\", \"elapsedMs\": $ELAPSED},"
    fi
fi

echo "  \"ok\": true,"
echo "  \"endTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\""
echo "}"
