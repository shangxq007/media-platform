#!/usr/bin/env bash
# verify-egress-smoke-config.sh — Static verification of egress proxy smoke rollout readiness.
#
# Usage:
#   ./scripts/verify-egress-smoke-config.sh gitops/staging
#   ./scripts/verify-egress-smoke-config.sh gitops/production
#   ./scripts/verify-egress-smoke-config.sh gitops/staging --strict
#
# This script performs static checks on rendered K8s manifests to verify
# that the egress proxy smoke test configuration is complete and correct.
# It does NOT connect to any Kubernetes cluster.
#
# Exit codes:
#   0 = all checks passed
#   1 = one or more FAIL checks
#   2 = script error

MANIFESTS_DIR="${1:?Usage: $0 <manifests-directory> [--strict]}"
STRICT=false

for arg in "$@"; do
  if [ "$arg" = "--strict" ]; then
    STRICT=true
  fi
done

if [ ! -d "$MANIFESTS_DIR" ]; then
  echo "ERROR: Directory not found: $MANIFESTS_DIR" >&2
  exit 2
fi

FAIL_COUNT=0
WARN_COUNT=0
PASS_COUNT=0

pass() { echo "  [PASS] $1"; PASS_COUNT=$((PASS_COUNT + 1)); }
fail() { echo "  [FAIL] $1"; FAIL_COUNT=$((FAIL_COUNT + 1)); }
warn() { echo "  [WARN] $1"; WARN_COUNT=$((WARN_COUNT + 1)); }

CM_FILE="$MANIFESTS_DIR/configmap.yaml"
EP_CM="$MANIFESTS_DIR/configmap-egress-proxy.yaml"
DEPLOY_API="$MANIFESTS_DIR/deployment-api.yaml"
DEPLOY_RW="$MANIFESTS_DIR/deployment-render-worker.yaml"
DEPLOY_SW="$MANIFESTS_DIR/deployment-sandbox-worker.yaml"
EP_DEPLOY="$MANIFESTS_DIR/deployment-egress-proxy.yaml"

# ── 1. Egress-proxy resources ──────────────────────────────────────
echo "=== 1. Egress Proxy Resources ==="

if [ -f "$EP_DEPLOY" ]; then
  pass "egress-proxy Deployment exists"
else
  fail "egress-proxy Deployment missing"
fi

if [ -f "$MANIFESTS_DIR/service-egress-proxy.yaml" ]; then
  pass "egress-proxy Service exists"
else
  fail "egress-proxy Service missing"
fi

if [ -f "$EP_CM" ]; then
  pass "egress-proxy ConfigMap exists"
else
  fail "egress-proxy ConfigMap missing"
fi

if [ -f "$MANIFESTS_DIR/networkpolicy-egress-proxy.yaml" ]; then
  pass "egress-proxy NetworkPolicy exists"
else
  fail "egress-proxy NetworkPolicy missing"
fi

# ── 2. Smoke configuration ─────────────────────────────────────────
echo ""
echo "=== 2. Smoke Configuration ==="

if [ -f "$CM_FILE" ]; then
  if grep -q 'EGRESS_PROXY_SMOKE_ENABLED' "$CM_FILE" 2>/dev/null; then
    pass "ConfigMap has EGRESS_PROXY_SMOKE_ENABLED"

    SMOKE_ENABLED=$(grep 'EGRESS_PROXY_SMOKE_ENABLED' "$CM_FILE" 2>/dev/null | grep -oE '"[a-z]+"' | tr -d '"' || echo "")
    if [ "$SMOKE_ENABLED" = "true" ]; then
      pass "Smoke test enabled"

      # URL must be present
      SMOKE_URL=$(grep 'EGRESS_PROXY_SMOKE_URL' "$CM_FILE" 2>/dev/null | grep -oE '"[^"]*"' | tr -d '"' || echo "")
      if [ -n "$SMOKE_URL" ] && [ "$SMOKE_URL" != '""' ]; then
        pass "Smoke URL configured: $(echo "$SMOKE_URL" | sed 's|https\?://||' | cut -d/ -f1)"

        # URL must not be metadata/localhost
        if echo "$SMOKE_URL" | grep -qE '169\.254\.169\.254|localhost|127\.0\.0\.1|0\.0\.0\.0'; then
          fail "Smoke URL must not be metadata/localhost: $SMOKE_URL"
        else
          pass "Smoke URL is not metadata/localhost"
        fi

        # Extract host from smoke URL and check if it's in allowed-domains
        SMOKE_HOST=$(echo "$SMOKE_URL" | sed 's|https\?://||' | cut -d/ -f1 | cut -d: -f1 | tr '[:upper:]' '[:lower:]')
        if [ -f "$EP_CM" ] && [ -n "$SMOKE_HOST" ]; then
          # Get the parent domain (e.g., httpbin.org from foo.httpbin.org)
          PARENT_DOMAIN=$(echo "$SMOKE_HOST" | sed 's/^[^.]*//')
          if grep -vE '^\s*#' "$EP_CM" | grep -qE "^\s*\.$(echo "$SMOKE_HOST" | sed 's/\./\\./g')\s*$" 2>/dev/null; then
            pass "Smoke URL host ($SMOKE_HOST) found in allowed-domains.txt"
          elif [ -n "$PARENT_DOMAIN" ] && grep -vE '^\s*#' "$EP_CM" | grep -qE "^\s*$(echo "$PARENT_DOMAIN" | sed 's/\./\\./g')\s*$" 2>/dev/null; then
            pass "Smoke URL parent domain ($PARENT_DOMAIN) found in allowed-domains.txt"
          else
            fail "Smoke URL host ($SMOKE_HOST) NOT found in allowed-domains.txt — Squid will deny"
          fi
        fi
      else
        fail "EGRESS_PROXY_SMOKE_ENABLED=true but EGRESS_PROXY_SMOKE_URL is empty"
      fi
    else
      if [ "$STRICT" = true ]; then
        fail "Smoke test disabled (strict mode requires enabled)"
      else
        warn "Smoke test disabled (enable in staging before production)"
      fi
    fi

    # Check include-in-readiness
    SMOKE_READINESS=$(grep 'EGRESS_PROXY_SMOKE_INCLUDE_IN_READINESS' "$CM_FILE" 2>/dev/null | grep -oE '"[a-z]+"' | tr -d '"' || echo "")
    if [ "$SMOKE_READINESS" = "true" ]; then
      warn "Smoke test included in readiness — external downtime affects Pod readiness"
    else
      pass "Smoke test NOT included in readiness (default)"
    fi
  else
    fail "ConfigMap missing EGRESS_PROXY_SMOKE_ENABLED"
  fi
else
  fail "ConfigMap (configmap.yaml) not found"
fi

# ── 3. JVM proxy configuration ─────────────────────────────────────
echo ""
echo "=== 3. JVM Proxy Configuration ==="

if [ -f "$CM_FILE" ]; then
  if grep -q 'EGRESS_PROXY_JVM_ENABLED' "$CM_FILE" 2>/dev/null; then
    JVM_ENABLED=$(grep 'EGRESS_PROXY_JVM_ENABLED' "$CM_FILE" 2>/dev/null | grep -oE '"[a-z]+"' | tr -d '"' || echo "")
    if [ "$JVM_ENABLED" = "true" ]; then
      warn "JVM proxy properties enabled — affects ALL JVM HTTP connections"
    else
      pass "JVM proxy properties disabled (default)"
    fi
  else
    warn "ConfigMap missing EGRESS_PROXY_JVM_ENABLED"
  fi
fi

# ── 4. Proxy environment variables ─────────────────────────────────
echo ""
echo "=== 4. Proxy Environment ==="

# Check api
if [ -f "$DEPLOY_API" ]; then
  if grep -q 'HTTP_PROXY' "$DEPLOY_API" 2>/dev/null; then
    pass "deployment-api has HTTP_PROXY"
  else
    fail "deployment-api missing HTTP_PROXY"
  fi

  if grep -q 'HTTPS_PROXY' "$DEPLOY_API" 2>/dev/null; then
    pass "deployment-api has HTTPS_PROXY"
  else
    fail "deployment-api missing HTTPS_PROXY"
  fi

  if grep -q 'NO_PROXY' "$DEPLOY_API" 2>/dev/null; then
    pass "deployment-api has NO_PROXY"

    NO_PROXY_VAL=$(grep -A1 'name: NO_PROXY' "$DEPLOY_API" 2>/dev/null | grep 'value:' | head -1)
    MISSING=""
    for exclusion in '.svc' '.cluster.local' 'postgresql' 'minio' 'sandbox-worker'; do
      if ! echo "$NO_PROXY_VAL" | grep -q "$exclusion"; then
        MISSING="$MISSING $exclusion"
      fi
    done
    if [ -n "$MISSING" ]; then
      fail "deployment-api NO_PROXY missing:$MISSING"
    else
      pass "deployment-api NO_PROXY has all critical exclusions"
    fi
  else
    fail "deployment-api missing NO_PROXY"
  fi
fi

# Check render-worker
if [ -f "$DEPLOY_RW" ]; then
  if grep -q 'HTTP_PROXY' "$DEPLOY_RW" 2>/dev/null; then
    pass "deployment-render-worker has HTTP_PROXY"
  else
    fail "deployment-render-worker missing HTTP_PROXY"
  fi

  if grep -q 'NO_PROXY' "$DEPLOY_RW" 2>/dev/null; then
    pass "deployment-render-worker has NO_PROXY"
  else
    fail "deployment-render-worker missing NO_PROXY"
  fi
fi

# Check sandbox-worker has NO proxy
if [ -f "$DEPLOY_SW" ]; then
  if grep -qE 'HTTP_PROXY|http_proxy|HTTPS_PROXY|https_proxy' "$DEPLOY_SW" 2>/dev/null; then
    fail "deployment-sandbox-worker has proxy env (must NOT)"
  else
    pass "deployment-sandbox-worker has no proxy env (correct)"
  fi
fi

# ── 5. Allowed domains ─────────────────────────────────────────────
echo ""
echo "=== 5. Allowed Domains ==="

if [ -f "$EP_CM" ]; then
  if grep -q 'allowed-domains.txt' "$EP_CM" 2>/dev/null; then
    pass "allowed-domains.txt exists in egress-proxy ConfigMap"

    # Check for example.com placeholder
    if grep -A 100 'allowed-domains.txt' "$EP_CM" | grep -q 'example\.com'; then
      if [ "$STRICT" = true ]; then
        fail "allowed-domains.txt contains example.com placeholder (strict mode)"
      else
        warn "allowed-domains.txt contains example.com placeholder — replace before production"
      fi
    else
      pass "allowed-domains.txt has no example.com placeholder"
    fi

    # Check no wildcard
    if grep -A 100 'allowed-domains.txt' "$EP_CM" | grep -E '^\s*\*\s*$' 2>/dev/null | grep -q '\*'; then
      fail "allowed-domains.txt contains wildcard '*'"
    else
      pass "allowed-domains.txt has no wildcard"
    fi

    # Count domains
    DOMAIN_COUNT=$(grep -A 100 'allowed-domains.txt' "$EP_CM" | sed -n '/^  squid.conf/,$d; /^  allowed-domains.txt/,/^  [a-z]/{ /^\s*[^#[:space:]].*\./p }' 2>/dev/null | wc -l)
    if [ "$DOMAIN_COUNT" -gt 0 ]; then
      pass "allowed-domains.txt has $DOMAIN_COUNT domain(s)"
    else
      fail "allowed-domains.txt has no domains (proxy will deny all)"
    fi
  else
    fail "allowed-domains.txt missing from egress-proxy ConfigMap"
  fi

  # Check Squid config
  if grep -q 'http_access deny all' "$EP_CM" 2>/dev/null; then
    pass "Squid has 'http_access deny all' (no open proxy)"
  else
    fail "Squid missing 'http_access deny all'"
  fi

  if grep -q 'acl allowed_domains dstdomain' "$EP_CM" 2>/dev/null; then
    pass "Squid has allowed_domains ACL"
  else
    fail "Squid missing allowed_domains ACL"
  fi
fi

# ── Summary ─────────────────────────────────────────────────────────
echo ""
echo "=== Summary ==="
echo "  PASS:     $PASS_COUNT"
echo "  FAIL:     $FAIL_COUNT"
echo "  WARN:     $WARN_COUNT"

if [ "$STRICT" = true ]; then
  echo "  Mode:     STRICT"
fi

if [ $FAIL_COUNT -gt 0 ]; then
  echo ""
  echo "❌ $FAIL_COUNT critical issues found. Fix before proceeding."
  exit 1
else
  echo ""
  echo "✅ All critical checks passed."
  if [ $WARN_COUNT -gt 0 ]; then
    echo "⚠️  $WARN_COUNT warnings. Review before proceeding."
  fi
  exit 0
fi
