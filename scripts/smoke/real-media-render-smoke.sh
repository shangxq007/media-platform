#!/bin/bash
# Real Media Render Smoke Test
# Usage: REAL_MEDIA_RENDER_WRITE=1 bash scripts/smoke/real-media-render-smoke.sh

set -e

API_BASE="${API_BASE:-https://api.render.cc.cd}"
TENANT_ID="ten_307b8956545642a9a45097f2f480a7b4"
PROJECT_ID="prj_6802ca7a12c24aafa31cf77fa63890be"

if [ "${REAL_MEDIA_RENDER_WRITE}" != "1" ]; then
    echo "❌ REAL_MEDIA_RENDER_WRITE=1 required"
    exit 1
fi

echo "=== Real Media Render Smoke ==="
echo "API: $API_BASE"

# Get token
TOKEN=$(curl -sS -X POST "$API_BASE/api/v1/dev/auth/token" \
    -H "Content-Type: application/json" \
    -d '{"tenantId":"smoke-tenant","userId":"smoke-user"}' 2>/dev/null | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "❌ Failed to get token"
    exit 1
fi
echo "✅ Token obtained"

# Generate small test mp4 using ffmpeg
echo ""
echo "1. Generating test mp4..."
TEST_MP4="/tmp/real-media-test-input.mp4"
ffmpeg -y -f lavfi -i "color=c=blue:size=320x180:rate=30:duration=2" -pix_fmt yuv420p "$TEST_MP4" 2>/dev/null
echo "   Generated: $TEST_MP4 ($(ls -la "$TEST_MP4" | awk '{print $5}') bytes)"

# Upload media
echo ""
echo "2. Uploading media..."
UPLOAD_RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/render/jobs/dev/preview/media" \
    -H "Authorization: Bearer $TOKEN" \
    -F "file=@$TEST_MP4;type=video/mp4" 2>/dev/null)
echo "   Response: $UPLOAD_RESPONSE"

MEDIA_URI=$(echo "$UPLOAD_RESPONSE" | grep -o '"storageUri":"[^"]*"' | cut -d'"' -f4)
if [ -z "$MEDIA_URI" ]; then
    echo "❌ Failed to upload media"
    exit 1
fi
echo "✅ Media uploaded: $MEDIA_URI"

# Submit render with real media
echo ""
echo "3. Submitting real media render..."
TIMELINE="{\"version\":\"1.0\",\"tracks\":[{\"id\":\"v1\",\"type\":\"video\",\"clips\":[{\"id\":\"c1\",\"mediaReference\":\"$MEDIA_URI\",\"source_range\":{\"start_time\":0,\"duration\":2}}]}]}"

SUBMIT_RESPONSE=$(curl -sS -X POST "$API_BASE/api/v1/render/jobs/submit" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"tenantId\":\"$TENANT_ID\",\"projectId\":\"$PROJECT_ID\",\"prompt\":\"$TIMELINE\",\"profile\":\"default_1080p\"}" 2>/dev/null)
echo "   Response: $SUBMIT_RESPONSE"

JOB_ID=$(echo "$SUBMIT_RESPONSE" | grep -o '"jobId":"[^"]*"' | cut -d'"' -f4)
if [ -z "$JOB_ID" ]; then
    echo "❌ Failed to submit render"
    exit 1
fi
echo "✅ Job submitted: $JOB_ID"

# Poll status
echo ""
echo "4. Polling status..."
for i in $(seq 1 30); do
    sleep 3
    STATUS=$(curl -sS "$API_BASE/api/v1/render/jobs/$JOB_ID" -H "Authorization: Bearer $TOKEN" 2>/dev/null)
    STATE=$(echo "$STATUS" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
    echo "   $i: $STATE"
    
    if [ "$STATE" = "COMPLETED" ]; then
        echo "✅ Job completed"
        break
    fi
    if [ "$STATE" = "FAILED" ] || [ "$STATE" = "CANCELLED" ] || [ "$STATE" = "REJECTED" ]; then
        echo "❌ Job failed: $STATE"
        exit 1
    fi
    if [ "$i" -eq 30 ]; then
        echo "❌ Timeout waiting for completion"
        exit 1
    fi
done

# Get artifacts
echo ""
echo "5. Getting artifacts..."
ARTIFACTS=$(curl -sS "$API_BASE/api/v1/render/jobs/$JOB_ID/artifacts" -H "Authorization: Bearer $TOKEN" 2>/dev/null)
echo "   $ARTIFACTS"

ARTIFACT_ID=$(echo "$ARTIFACTS" | grep -o '"artifactId":"[^"]*"' | head -1 | cut -d'"' -f4)
if [ -z "$ARTIFACT_ID" ]; then
    echo "❌ No artifacts found"
    exit 1
fi
echo "✅ Artifact: $ARTIFACT_ID"

# Download content
echo ""
echo "6. Downloading artifact content..."
OUTPUT_FILE="/tmp/real-media-render-output.mp4"
HTTP_CODE=$(curl -sS -o "$OUTPUT_FILE" -w "%{http_code}" \
    "$API_BASE/api/v1/render/jobs/$JOB_ID/artifacts/$ARTIFACT_ID/content" \
    -H "Authorization: Bearer $TOKEN" 2>/dev/null)

if [ "$HTTP_CODE" != "200" ]; then
    echo "❌ Download failed: HTTP $HTTP_CODE"
    exit 1
fi

FILE_SIZE=$(ls -la "$OUTPUT_FILE" 2>/dev/null | awk '{print $5}')
if [ "$FILE_SIZE" -eq 0 ]; then
    echo "❌ Downloaded file is empty"
    exit 1
fi

echo "✅ Downloaded: $OUTPUT_FILE ($FILE_SIZE bytes)"
echo ""
echo "=== PASS ==="
