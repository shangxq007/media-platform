#!/bin/bash
# Agent API Loop Harness
# Usage:
#   Read-only: ./scripts/agent/agent-api-loop.sh
#   Write mode: AGENT_API_WRITE=1 ./scripts/agent/agent-api-loop.sh
set -e

API_BASE="${API_BASE_URL:-https://api.render.cc.cd}"
WRITE_MODE="${AGENT_API_WRITE:-0}"
POLL_INTERVAL="${AGENT_POLL_INTERVAL:-2000}"
POLL_TIMEOUT="${AGENT_POLL_TIMEOUT:-60000}"
CORRELATION_ID="${AGENT_CORRELATION_ID:-agent-$(date +%s)}"

echo "{"
echo "  \"correlationId\": \"$CORRELATION_ID\","
echo "  \"mode\": \"$([ "$WRITE_MODE" = "1" ] && echo "create-preview-job" || echo "read-only")\","
echo "  \"startTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\","

# 1. Health check
echo "  \"health\": {"
HEALTH=$(curl -sS "$API_BASE/actuator/health" 2>/dev/null || echo '{"status":"DOWN"}')
HEALTH_STATUS=$(echo "$HEALTH" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
echo "    \"status\": \"$HEALTH_STATUS\","
echo "    \"ok\": $([ "$HEALTH_STATUS" = "UP" ] && echo "true" || echo "false")"
echo "  },"

if [ "$HEALTH_STATUS" != "UP" ]; then
    echo "  \"ok\": false,"
    echo "  \"errors\": [\"Health check failed\"]"
    echo "}"
    exit 1
fi

# 2. List render jobs (read-only)
echo "  \"renderJobs\": {"
JOBS=$(curl -sS "$API_BASE/api/v1/render/jobs" 2>/dev/null || echo "[]")
JOB_COUNT=$(echo "$JOBS" | grep -o '"id"' | wc -l)
echo "    \"count\": $JOB_COUNT"
echo "  },"

# 3. Create render job (write mode only)
if [ "$WRITE_MODE" = "1" ]; then
    echo "  \"createJob\": {"
    RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/render/jobs" \
        -H "Content-Type: application/json" \
        -d "{
            \"tenantId\": \"agent\",
            \"projectId\": \"agent-smoke\",
            \"profile\": \"default_1080p\"
        }" 2>/dev/null || echo "{}")
    
    JOB_ID=$(echo "$RESPONSE" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
    JOB_STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
    
    echo "    \"jobId\": \"$JOB_ID\","
    echo "    \"status\": \"$JOB_STATUS\""
    echo "  },"
    
    # 4. Poll job status
    if [ -n "$JOB_ID" ]; then
        echo "  \"polling\": {"
        ELAPSED=0
        FINAL_STATUS="$JOB_STATUS"
        
        while [ "$ELAPSED" -lt "$POLL_TIMEOUT" ]; do
            if [ "$FINAL_STATUS" = "COMPLETED" ] || [ "$FINAL_STATUS" = "FAILED" ] || [ "$FINAL_STATUS" = "CANCELLED" ]; then
                break
            fi
            
            sleep $((POLL_INTERVAL / 1000))
            ELAPSED=$((ELAPSED + POLL_INTERVAL))
            
            STATUS_RESPONSE=$(curl -sS "$API_BASE/api/v1/render/jobs/$JOB_ID" 2>/dev/null || echo "{}")
            FINAL_STATUS=$(echo "$STATUS_RESPONSE" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
        done
        
        echo "    \"jobId\": \"$JOB_ID\","
        echo "    \"finalStatus\": \"$FINAL_STATUS\","
        echo "    \"elapsedMs\": $ELAPSED"
        echo "  },"
    fi
fi

echo "  \"ok\": true,"
echo "  \"endTime\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\""
echo "}"
