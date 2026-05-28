#!/usr/bin/env bash
# validate-production-readiness.sh — Static validation of production K8s manifests.
#
# Usage:
#   ./scripts/validate-production-readiness.sh gitops/production
#   ./scripts/validate-production-readiness.sh build/k8s/production
#
# This script performs static checks on rendered K8s manifests.
# It does NOT connect to any Kubernetes cluster.
#
# Exit codes:
#   0 = all checks passed
#   1 = one or more FAIL checks
#   2 = script error (missing directory, etc.)

MANIFESTS_DIR="${1:?Usage: $0 <manifests-directory>}"

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

# Collect all image: lines and all YAML content once
IMAGES=$(grep -rE '^\s*image:' "$MANIFESTS_DIR"/*.yaml 2>/dev/null || true)
ALL_YAML=$(cat "$MANIFESTS_DIR"/*.yaml 2>/dev/null || true)
DEPLOYMENT_FILES=$(find "$MANIFESTS_DIR" -name "deployment-*.yaml" -type f 2>/dev/null)

# ── 1. Image safety ──────────────────────────────────────────────────────
echo "=== 1. Image Safety ==="

if echo "$IMAGES" | grep -qE ':latest'; then
  fail "Found ':latest' image tags"
else
  pass "No ':latest' image tags"
fi

if echo "$IMAGES" | grep -qE ':dev'; then
  fail "Found ':dev' image tags"
else
  pass "No ':dev' image tags"
fi

if echo "$IMAGES" | grep -q 'platform-api:'; then
  pass "platform-api image present"
else
  fail "platform-api image missing"
fi

if echo "$IMAGES" | grep -q 'platform-render-worker:'; then
  pass "platform-render-worker image present"
else
  fail "platform-render-worker image missing"
fi

if echo "$IMAGES" | grep -q 'platform-sandbox-worker:'; then
  pass "platform-sandbox-worker image present"
else
  fail "platform-sandbox-worker image missing"
fi

# Check three images use same tag
API_TAG=$(echo "$IMAGES" | grep -oE 'platform-api:[a-zA-Z0-9._-]+' | head -1 | cut -d: -f2 || echo "")
RW_TAG=$(echo "$IMAGES" | grep -oE 'platform-render-worker:[a-zA-Z0-9._-]+' | head -1 | cut -d: -f2 || echo "")
SW_TAG=$(echo "$IMAGES" | grep -oE 'platform-sandbox-worker:[a-zA-Z0-9._-]+' | head -1 | cut -d: -f2 || echo "")

if [ -n "$API_TAG" ] && [ -n "$RW_TAG" ] && [ -n "$SW_TAG" ]; then
  if [ "$API_TAG" = "$RW_TAG" ] && [ "$RW_TAG" = "$SW_TAG" ]; then
    pass "Three core images use same tag: $API_TAG"
  else
    fail "Image tags differ: api=$API_TAG, rw=$RW_TAG, sw=$SW_TAG"
  fi
fi

# ── 2. Production config safety ──────────────────────────────────────────
echo ""
echo "=== 2. Production Config Safety ==="

if echo "$ALL_YAML" | grep -qE 'allow-in-process-eval.*true'; then
  fail "Found 'allow-in-process-eval: true'"
else
  pass "No 'allow-in-process-eval: true'"
fi

if echo "$ALL_YAML" | grep -qE 'dev-auth-endpoint.*true'; then
  fail "Found 'dev-auth-endpoint: true'"
else
  pass "No 'dev-auth-endpoint: true'"
fi

if echo "$ALL_YAML" | grep -qE 'oidc-dev-bootstrap.*true'; then
  fail "Found 'oidc-dev-bootstrap: true'"
else
  pass "No 'oidc-dev-bootstrap: true'"
fi

if echo "$ALL_YAML" | grep -qE 'execution-mode.*in-process'; then
  fail "Found 'sandbox.execution-mode: in-process'"
else
  pass "No 'sandbox.execution-mode: in-process'"
fi

if echo "$ALL_YAML" | grep -vE '#.*tenant-1|NOTE.*tenant-1' | grep -q 'tenant-1'; then
  warn "Found 'tenant-1' in non-comment context"
else
  pass "No 'tenant-1' in production config"
fi

# ── 3. SecurityContext ───────────────────────────────────────────────────
echo ""
echo "=== 3. SecurityContext ==="

DEPLOYMENT_COUNT=0
DEPLOYMENTS_WITH_SC=0

for f in $DEPLOYMENT_FILES; do
  DEPLOYMENT_COUNT=$((DEPLOYMENT_COUNT + 1))
  fname=$(basename "$f")

  if grep -q "runAsNonRoot: true" "$f"; then
    DEPLOYMENTS_WITH_SC=$((DEPLOYMENTS_WITH_SC + 1))
  else
    fail "$fname missing pod securityContext.runAsNonRoot"
  fi

  if ! grep -q "allowPrivilegeEscalation: false" "$f"; then
    fail "$fname missing allowPrivilegeEscalation: false"
  fi

  if ! grep -q "readOnlyRootFilesystem: true" "$f"; then
    fail "$fname missing readOnlyRootFilesystem: true"
  fi

  if ! grep -q "drop:" "$f"; then
    fail "$fname missing capabilities.drop"
  fi
done

if [ $DEPLOYMENT_COUNT -gt 0 ] && [ $DEPLOYMENTS_WITH_SC -eq $DEPLOYMENT_COUNT ]; then
  pass "All $DEPLOYMENT_COUNT deployments have pod securityContext"
fi

# ── 4. Resources ─────────────────────────────────────────────────────────
echo ""
echo "=== 4. Resource Limits ==="

for f in $DEPLOYMENT_FILES; do
  fname=$(basename "$f")
  if ! grep -q "resources:" "$f"; then
    fail "$fname missing resources"
  fi
  if ! grep -q "requests:" "$f"; then
    fail "$fname missing resources.requests"
  fi
  if ! grep -q "limits:" "$f"; then
    fail "$fname missing resources.limits"
  fi
done

pass "Resource limits check complete"

# ── 5. Probes ────────────────────────────────────────────────────────────
echo ""
echo "=== 5. Health Probes ==="

for f in $DEPLOYMENT_FILES; do
  fname=$(basename "$f")
  if ! grep -q "readinessProbe:" "$f"; then
    warn "$fname missing readinessProbe"
  fi
  if ! grep -q "livenessProbe:" "$f"; then
    warn "$fname missing livenessProbe"
  fi
done

pass "Probe check complete"

# ── 6. Network Isolation ─────────────────────────────────────────────
echo ""
echo "=== 6. Network Isolation ==="

# 6a. sandbox-worker NetworkPolicy — must exist and deny all egress
NP_SW="$MANIFESTS_DIR/networkpolicy-sandbox-worker.yaml"
if [ -f "$NP_SW" ]; then
  pass "sandbox-worker NetworkPolicy exists"

  if grep -q "Egress" "$NP_SW"; then
    pass "sandbox-worker NetworkPolicy has Egress policyType"
  else
    fail "sandbox-worker NetworkPolicy missing Egress policyType"
  fi

  if grep -q "egress: \[\]" "$NP_SW" 2>/dev/null; then
    pass "sandbox-worker egress is deny-all"
  else
    fail "sandbox-worker egress is NOT deny-all (must be egress: [])"
  fi
else
  fail "sandbox-worker NetworkPolicy missing"
fi

# 6b. platform-api egress NetworkPolicy — must exist
NP_API="$MANIFESTS_DIR/networkpolicy-api-egress.yaml"
if [ -f "$NP_API" ]; then
  pass "platform-api egress NetworkPolicy exists"
else
  fail "platform-api egress NetworkPolicy missing (networkpolicy-api-egress.yaml)"
fi

# 6c. render-worker egress NetworkPolicy — must exist
NP_RW="$MANIFESTS_DIR/networkpolicy-render-worker-egress.yaml"
if [ -f "$NP_RW" ]; then
  pass "render-worker egress NetworkPolicy exists"
else
  fail "render-worker egress NetworkPolicy missing (networkpolicy-render-worker-egress.yaml)"
fi

# 6d. Check all NetworkPolicy files for dangerous patterns
NP_ALL_FILES=$(find "$MANIFESTS_DIR" -name "networkpolicy-*.yaml" -type f 2>/dev/null)
for npf in $NP_ALL_FILES; do
  npfname=$(basename "$npf")

  # Check for 0.0.0.0/0 (open egress to all)
  # ALLOWED only in networkpolicy-egress-proxy.yaml (the controlled egress point)
  # All other policies must NOT have 0.0.0.0/0
  if grep -qE '0\.0\.0\.0/0' "$npf" 2>/dev/null; then
    if grep -vE '^\s*#' "$npf" | grep -qE '0\.0\.0\.0/0'; then
      if [ "$npfname" = "networkpolicy-egress-proxy.yaml" ]; then
        pass "$npfname has 0.0.0.0/0 (allowed: egress proxy controlled egress point)"
      else
        fail "$npfname contains ipBlock 0.0.0.0/0 (open egress)"
      fi
    fi
  fi

  # Check for metadata IP allow (169.254.169.254) — never allowed in any policy
  if grep -vE '^\s*#' "$npf" | grep -qE '169\.254\.169\.254'; then
    fail "$npfname allows metadata IP 169.254.169.254"
  fi

  # Check for broad namespaceSelector without podSelector (except kube-dns)
  # This detects rules like: namespaceSelector: {kubernetes.io/metadata.name: media-platform}
  # without a corresponding podSelector (too broad — allows all pods in namespace)
  if grep -qE 'namespaceSelector:' "$npf" 2>/dev/null; then
    BROAD_NS=false
    # For each namespaceSelector line, check if podSelector follows within 8 lines
    # and the namespaceSelector is NOT for kube-system
    while IFS= read -r linenum; do
      # Get lines after this namespaceSelector
      NEXT_LINES=$(sed -n "$((linenum+1)),$((linenum+8))p" "$npf" 2>/dev/null)
      # Skip kube-system namespaceSelector (allowed for DNS)
      if echo "$NEXT_LINES" | grep -q 'kube-system'; then
        continue
      fi
      # Check if podSelector follows within 8 lines
      if ! echo "$NEXT_LINES" | grep -q 'podSelector:'; then
        BROAD_NS=true
      fi
    done < <(grep -n 'namespaceSelector:' "$npf" 2>/dev/null | cut -d: -f1)

    if [ "$BROAD_NS" = true ]; then
      fail "$npfname has broad namespaceSelector without podSelector (allows all pods in namespace)"
    fi
  fi

  # Check kube-dns egress port is only 53
  if grep -q 'kube-dns' "$npf" 2>/dev/null; then
    # Extract the kube-dns egress block (from kube-dns to the next egress entry or end)
    DNS_BLOCK=$(sed -n '/kube-dns/,/^    # [0-9]\+\.\|^    - to:/{/^    # [0-9]\+\.\|^    - to:/d;p}' "$npf" 2>/dev/null)
    if [ -z "$DNS_BLOCK" ]; then
      # Fallback: extract from kube-dns to the end of ports section
      DNS_BLOCK=$(awk '/kube-dns/{found=1} found{print} found && /^      ports:/{count++; if(count>=1) exit}' "$npf" 2>/dev/null)
    fi
    if echo "$DNS_BLOCK" | grep -qE 'port: [0-9]+'; then
      DNS_PORTS=$(echo "$DNS_BLOCK" | grep -oE 'port: [0-9]+' | grep -oE '[0-9]+' | sort -u)
      for dp in $DNS_PORTS; do
        if [ "$dp" != "53" ]; then
          fail "$npfname kube-dns egress allows non-DNS port: $dp"
        fi
      done
      if echo "$DNS_PORTS" | grep -q '^53$'; then
        pass "$npfname kube-dns egress limited to port 53"
      fi
    fi
  fi
done

if [ -z "$NP_ALL_FILES" ]; then
  warn "No NetworkPolicy files found in manifests directory"
fi

# ── 6e. Egress proxy manifests ─────────────────────────────────────
echo ""
echo "=== 6e. Egress Proxy ==="

EP_DEPLOY="$MANIFESTS_DIR/deployment-egress-proxy.yaml"
EP_SVC="$MANIFESTS_DIR/service-egress-proxy.yaml"
EP_CM="$MANIFESTS_DIR/configmap-egress-proxy.yaml"
EP_NP="$MANIFESTS_DIR/networkpolicy-egress-proxy.yaml"

if [ -f "$EP_DEPLOY" ]; then
  pass "egress-proxy Deployment exists"
else
  fail "egress-proxy Deployment missing (deployment-egress-proxy.yaml)"
fi

if [ -f "$EP_SVC" ]; then
  pass "egress-proxy Service exists"
else
  fail "egress-proxy Service missing (service-egress-proxy.yaml)"
fi

if [ -f "$EP_CM" ]; then
  pass "egress-proxy ConfigMap exists"
else
  fail "egress-proxy ConfigMap missing (configmap-egress-proxy.yaml)"
fi

if [ -f "$EP_NP" ]; then
  pass "egress-proxy NetworkPolicy exists"

  # Check egress-proxy ingress allows api but NOT sandbox-worker
  if grep -A 20 'ingress:' "$EP_NP" 2>/dev/null | grep -q 'component: api'; then
    pass "egress-proxy ingress allows platform-api"
  else
    fail "egress-proxy ingress does not allow platform-api"
  fi

  if grep -A 20 'ingress:' "$EP_NP" 2>/dev/null | grep -q 'component: render-worker'; then
    pass "egress-proxy ingress allows render-worker"
  else
    fail "egress-proxy ingress does not allow render-worker"
  fi

  if grep -A 30 'ingress:' "$EP_NP" 2>/dev/null | grep -q 'sandbox-worker'; then
    fail "egress-proxy ingress allows sandbox-worker (must NOT)"
  else
    pass "egress-proxy ingress does not allow sandbox-worker"
  fi
else
  fail "egress-proxy NetworkPolicy missing (networkpolicy-egress-proxy.yaml)"
fi

# Check api egress policy allows egress-proxy
if [ -f "$NP_API" ]; then
  if grep -q 'egress-proxy' "$NP_API" 2>/dev/null; then
    if grep -A 5 'egress-proxy' "$NP_API" | grep -q 'port: 3128'; then
      pass "platform-api egress allows egress-proxy:3128"
    else
      fail "platform-api egress references egress-proxy but not on port 3128"
    fi
  else
    fail "platform-api egress does not reference egress-proxy"
  fi
fi

# Check render-worker egress policy allows egress-proxy
if [ -f "$NP_RW" ]; then
  if grep -q 'egress-proxy' "$NP_RW" 2>/dev/null; then
    if grep -A 5 'egress-proxy' "$NP_RW" | grep -q 'port: 3128'; then
      pass "render-worker egress allows egress-proxy:3128"
    else
      fail "render-worker egress references egress-proxy but not on port 3128"
    fi
  else
    fail "render-worker egress does not reference egress-proxy"
  fi
fi

# Check squid config blocks metadata IP
if [ -f "$EP_CM" ]; then
  if grep -q '169.254.169.254' "$EP_CM" 2>/dev/null; then
    pass "egress-proxy Squid config blocks metadata IP"
  else
    fail "egress-proxy Squid config does not block 169.254.169.254"
  fi

  if grep -q 'deny metadata_ip' "$EP_CM" 2>/dev/null; then
    pass "egress-proxy Squid config denies metadata_ip ACL"
  else
    fail "egress-proxy Squid config missing 'deny metadata_ip' rule"
  fi

  # Check allowed_domains ACL exists (not open proxy)
  if grep -q 'acl allowed_domains dstdomain' "$EP_CM" 2>/dev/null; then
    pass "Squid config has allowed_domains ACL"
  else
    fail "Squid config missing allowed_domains ACL (open proxy)"
  fi

  # Check http_access allow allowed_domains exists
  if grep -q 'http_access allow allowed_domains' "$EP_CM" 2>/dev/null; then
    pass "Squid config allows only allowed_domains"
  else
    fail "Squid config missing 'http_access allow allowed_domains'"
  fi

  # Check http_access deny all exists
  if grep -vE '^\s*#' "$EP_CM" | grep -q 'http_access deny all'; then
    pass "Squid config has 'http_access deny all' (no open proxy)"
  else
    fail "Squid config missing 'http_access deny all' (open proxy)"
  fi

  # Check no active 'http_access allow all' (open proxy)
  # Must NOT match 'http_access allow allowed_domains' — only bare 'allow all'
  if grep -vE '^\s*#' "$EP_CM" | grep -qE 'http_access allow all(\s|$)'; then
    fail "Squid config has 'http_access allow all' (open proxy)"
  fi

  # Check metadata deny is before allow
  DENY_LINE=$(grep -n 'deny metadata_ip' "$EP_CM" 2>/dev/null | head -1 | cut -d: -f1)
  ALLOW_LINE=$(grep -n 'allow allowed_domains' "$EP_CM" 2>/dev/null | head -1 | cut -d: -f1)
  if [ -n "$DENY_LINE" ] && [ -n "$ALLOW_LINE" ]; then
    if [ "$DENY_LINE" -lt "$ALLOW_LINE" ]; then
      pass "Squid metadata deny is before allow (correct order)"
    else
      fail "Squid metadata deny is after allow (wrong order)"
    fi
  fi
fi

# ── 6f. Squid allowed-domains.txt ──────────────────────────────────
echo ""
echo "=== 6f. Allowed Domains ==="

EP_AD="$MANIFESTS_DIR/configmap-egress-proxy.yaml"
if [ -f "$EP_AD" ]; then
  # Check allowed-domains.txt key exists in ConfigMap
  if grep -q 'allowed-domains.txt' "$EP_AD" 2>/dev/null; then
    pass "allowed-domains.txt exists in egress-proxy ConfigMap"

    # Check no wildcard *
    if grep -A 100 'allowed-domains.txt' "$EP_AD" | grep -B0 -A0 '^\s*\*\s*$' 2>/dev/null | grep -q '\*'; then
      fail "allowed-domains.txt contains wildcard '*'"
    else
      pass "allowed-domains.txt has no wildcard '*'"
    fi

    # Check no bare .
    if grep -A 100 'allowed-domains.txt' "$EP_AD" | grep -E '^\s*\.\s*$' 2>/dev/null | grep -qE '^\s*\.\s*$'; then
      fail "allowed-domains.txt contains bare '.' (allows all domains)"
    else
      pass "allowed-domains.txt has no bare '.'"
    fi

    # Check for example.com placeholder — WARN
    if grep -A 100 'allowed-domains.txt' "$EP_AD" | grep -q 'example\.com'; then
      warn "allowed-domains.txt contains example.com placeholder — replace before production"
    else
      pass "allowed-domains.txt has no example.com placeholder"
    fi

    # Check file is not empty (has at least one non-comment domain)
    DOMAIN_COUNT=$(grep -A 100 'allowed-domains.txt' "$EP_AD" | sed -n '/^  squid.conf/,$d; /^  allowed-domains.txt/,/^  [a-z]/{ /^\s*[^#[:space:]].*\./p }' 2>/dev/null | wc -l)
    if [ "$DOMAIN_COUNT" -gt 0 ]; then
      pass "allowed-domains.txt has $DOMAIN_COUNT domain(s)"
    else
      fail "allowed-domains.txt has no domains (proxy will deny all)"
    fi
  else
    fail "allowed-domains.txt missing from egress-proxy ConfigMap"
  fi
fi

# ── 6g. Proxy Environment Variables ────────────────────────────────
echo ""
echo "=== 6g. Proxy Environment ==="

DEPLOY_API="$MANIFESTS_DIR/deployment-api.yaml"
DEPLOY_RW="$MANIFESTS_DIR/deployment-render-worker.yaml"
DEPLOY_SW="$MANIFESTS_DIR/deployment-sandbox-worker.yaml"

# Check api has HTTP_PROXY
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
  else
    fail "deployment-api missing NO_PROXY"
  fi

  # Check lowercase variants
  if grep -qE '^\s*- name: http_proxy$' "$DEPLOY_API" 2>/dev/null; then
    pass "deployment-api has lowercase http_proxy"
  else
    fail "deployment-api missing lowercase http_proxy"
  fi

  if grep -qE '^\s*- name: https_proxy$' "$DEPLOY_API" 2>/dev/null; then
    pass "deployment-api has lowercase https_proxy"
  else
    fail "deployment-api missing lowercase https_proxy"
  fi

  if grep -qE '^\s*- name: no_proxy$' "$DEPLOY_API" 2>/dev/null; then
    pass "deployment-api has lowercase no_proxy"
  else
    fail "deployment-api missing lowercase no_proxy"
  fi

  # Check NO_PROXY contains critical exclusions
  NO_PROXY_VAL=$(grep -A1 'name: NO_PROXY' "$DEPLOY_API" 2>/dev/null | grep 'value:' | head -1)
  if echo "$NO_PROXY_VAL" | grep -q '\.svc'; then
    pass "deployment-api NO_PROXY contains .svc"
  else
    fail "deployment-api NO_PROXY missing .svc"
  fi

  if echo "$NO_PROXY_VAL" | grep -q '\.cluster.local'; then
    pass "deployment-api NO_PROXY contains .cluster.local"
  else
    fail "deployment-api NO_PROXY missing .cluster.local"
  fi
fi

# Check render-worker has HTTP_PROXY
if [ -f "$DEPLOY_RW" ]; then
  if grep -q 'HTTP_PROXY' "$DEPLOY_RW" 2>/dev/null; then
    pass "deployment-render-worker has HTTP_PROXY"
  else
    fail "deployment-render-worker missing HTTP_PROXY"
  fi

  if grep -q 'HTTPS_PROXY' "$DEPLOY_RW" 2>/dev/null; then
    pass "deployment-render-worker has HTTPS_PROXY"
  else
    fail "deployment-render-worker missing HTTPS_PROXY"
  fi

  if grep -q 'NO_PROXY' "$DEPLOY_RW" 2>/dev/null; then
    pass "deployment-render-worker has NO_PROXY"
  else
    fail "deployment-render-worker missing NO_PROXY"
  fi

  # Check lowercase variants
  if grep -qE '^\s*- name: http_proxy$' "$DEPLOY_RW" 2>/dev/null; then
    pass "deployment-render-worker has lowercase http_proxy"
  else
    fail "deployment-render-worker missing lowercase http_proxy"
  fi

  if grep -qE '^\s*- name: https_proxy$' "$DEPLOY_RW" 2>/dev/null; then
    pass "deployment-render-worker has lowercase https_proxy"
  else
    fail "deployment-render-worker missing lowercase https_proxy"
  fi

  if grep -qE '^\s*- name: no_proxy$' "$DEPLOY_RW" 2>/dev/null; then
    pass "deployment-render-worker has lowercase no_proxy"
  else
    fail "deployment-render-worker missing lowercase no_proxy"
  fi
fi

# Check sandbox-worker does NOT have proxy env
if [ -f "$DEPLOY_SW" ]; then
  HAS_PROXY=false
  if grep -qE 'HTTP_PROXY|http_proxy|HTTPS_PROXY|https_proxy' "$DEPLOY_SW" 2>/dev/null; then
    HAS_PROXY=true
  fi
  if [ "$HAS_PROXY" = true ]; then
    fail "deployment-sandbox-worker has proxy env (must NOT — sandbox-worker is isolated)"
  else
    pass "deployment-sandbox-worker has no proxy env (correct — isolated)"
  fi

  # Check sandbox-worker does NOT have JAVA_TOOL_OPTIONS proxy settings
  if grep -q 'JAVA_TOOL_OPTIONS' "$DEPLOY_SW" 2>/dev/null; then
    if grep -A1 'JAVA_TOOL_OPTIONS' "$DEPLOY_SW" | grep -q 'proxyHost'; then
      fail "deployment-sandbox-worker has JAVA_TOOL_OPTIONS with proxyHost (must NOT)"
    fi
  else
    pass "deployment-sandbox-worker has no JAVA_TOOL_OPTIONS (correct — isolated)"
  fi
fi

# ── 6h. JAVA_TOOL_OPTIONS proxy check ──────────────────────────────
echo ""
echo "=== 6h. JVM Proxy Options ==="

# Check if JAVA_TOOL_OPTIONS exists in api or render-worker and validate nonProxyHosts
for jdeploy in "$DEPLOY_API" "$DEPLOY_RW"; do
  [ -f "$jdeploy" ] || continue
  jname=$(basename "$jdeploy")
  if grep -q 'JAVA_TOOL_OPTIONS' "$jdeploy" 2>/dev/null; then
    JTO_VAL=$(grep -A1 'JAVA_TOOL_OPTIONS' "$jdeploy" 2>/dev/null | grep 'value:' | head -1)
    if echo "$JTO_VAL" | grep -q 'proxyHost'; then
      # JAVA_TOOL_OPTIONS has proxy settings — check nonProxyHosts
      if echo "$JTO_VAL" | grep -q 'nonProxyHosts'; then
        pass "$jname JAVA_TOOL_OPTIONS has nonProxyHosts"
      else
        fail "$jname JAVA_TOOL_OPTIONS has proxyHost but missing nonProxyHosts"
      fi
    fi
  fi
done

# ── 6i. Smoke test configuration ───────────────────────────────────
echo ""
echo "=== 6i. Smoke Test Configuration ==="

# Check configmap for smoke config
CM_FILE="$MANIFESTS_DIR/configmap.yaml"
if [ -f "$CM_FILE" ]; then
  # Check smoke config keys exist
  if grep -q 'EGRESS_PROXY_SMOKE_ENABLED' "$CM_FILE" 2>/dev/null; then
    pass "ConfigMap has EGRESS_PROXY_SMOKE_ENABLED"
  else
    warn "ConfigMap missing EGRESS_PROXY_SMOKE_ENABLED"
  fi

  # If smoke is enabled, URL must be configured
  SMOKE_ENABLED=$(grep 'EGRESS_PROXY_SMOKE_ENABLED' "$CM_FILE" 2>/dev/null | grep -oE '"[a-z]+"' | tr -d '"' || echo "")
  if [ "$SMOKE_ENABLED" = "true" ]; then
    SMOKE_URL=$(grep 'EGRESS_PROXY_SMOKE_URL' "$CM_FILE" 2>/dev/null | grep -oE '".*"' | tr -d '"' || echo "")
    if [ -z "$SMOKE_URL" ] || [ "$SMOKE_URL" = '""' ]; then
      fail "EGRESS_PROXY_SMOKE_ENABLED=true but EGRESS_PROXY_SMOKE_URL is empty"
    else
      # Check URL is not metadata/localhost
      if echo "$SMOKE_URL" | grep -qE '169\.254\.169\.254|localhost|127\.0\.0\.1'; then
        fail "EGRESS_PROXY_SMOKE_URL must not be metadata/localhost: $SMOKE_URL"
      else
        pass "EGRESS_PROXY_SMOKE_URL configured: $(echo "$SMOKE_URL" | sed 's|https\?://||' | cut -d/ -f1)"
      fi
    fi

    # Check include-in-readiness
    SMOKE_READINESS=$(grep 'EGRESS_PROXY_SMOKE_INCLUDE_IN_READINESS' "$CM_FILE" 2>/dev/null | grep -oE '"[a-z]+"' | tr -d '"' || echo "")
    if [ "$SMOKE_READINESS" = "true" ]; then
      warn "EGRESS_PROXY_SMOKE_INCLUDE_IN_READINESS=true — external dependency downtime will affect readiness"
    fi
  fi

  # Check JVM proxy config
  if grep -q 'EGRESS_PROXY_JVM_ENABLED' "$CM_FILE" 2>/dev/null; then
    JVM_ENABLED=$(grep 'EGRESS_PROXY_JVM_ENABLED' "$CM_FILE" 2>/dev/null | grep -oE '"[a-z]+"' | tr -d '"' || echo "")
    if [ "$JVM_ENABLED" = "true" ]; then
      warn "EGRESS_PROXY_JVM_ENABLED=true — JVM system properties will be set for proxy"
    else
      pass "JVM proxy properties disabled (default)"
    fi
  fi
fi

# ── 7. Secrets ───────────────────────────────────────────────────────────
echo ""
echo "=== 7. Secret Safety ==="

SECRET_FILE="$MANIFESTS_DIR/secret.yaml"
if [ -f "$SECRET_FILE" ]; then
  if grep -q "REPLACE_ME" "$SECRET_FILE" 2>/dev/null; then
    warn "Secret contains placeholder values (expected before real deployment)"
  fi

  # Check for obviously real secrets (not placeholder)
  # Only check indented key-value pairs inside stringData:
  REAL_SECRET=false
  IN_STRINGDATA=false
  while IFS= read -r line; do
    # Detect when we enter stringData: section
    if echo "$line" | grep -qE '^\s*stringData:'; then
      IN_STRINGDATA=true
      continue
    fi
    # Detect when we leave stringData: section (new top-level key)
    if [ "$IN_STRINGDATA" = true ] && echo "$line" | grep -qE '^[a-zA-Z]'; then
      IN_STRINGDATA=false
    fi

    # Only check inside stringData section
    [ "$IN_STRINGDATA" = false ] && continue

    # Skip comments and empty lines
    echo "$line" | grep -qE '^\s*#' && continue
    echo "$line" | grep -qE '^\s*$' && continue

    # Extract key and value
    key=$(echo "$line" | cut -d: -f1 | tr -d ' ')
    val=$(echo "$line" | cut -d: -f2- | tr -d "'\" " | tr -d "'\" ")

    # Skip if key is empty
    [ -z "$key" ] && continue

    # Skip placeholder values
    if echo "$val" | grep -qE '^REPLACE_ME|^CHANGE_ME|^TODO|^FIXME'; then
      continue
    fi

    # If key contains sensitive name and value is non-placeholder, flag it
    if echo "$key" | grep -qiE 'PASSWORD|SECRET|TOKEN|APIKEY|ACCESSKEY|PRIVATEKEY|CLIENTSECRET'; then
      if [ -n "$val" ] && [ "$val" != "REPLACE_ME_BEFORE_DEPLOY" ] && \
         [ "$val" != "REPLACE_ME_BEFORE_DEPLOY_MIN_256_BITS" ]; then
        REAL_SECRET=true
        fail "Possible real secret in secret.yaml: $key"
      fi
    fi
  done < "$SECRET_FILE"

  if [ "$REAL_SECRET" = false ]; then
    pass "No obvious real secrets in secret.yaml"
  fi
else
  warn "No secret.yaml found (may be created separately)"
fi

# ── 8. Namespace ─────────────────────────────────────────────────────────
echo ""
echo "=== 8. Namespace ==="

# Check namespace from multiple sources:
# 1. kustomization.yaml namespace field
# 2. namespace.yaml metadata.name
# 3. Deployment/Service metadata.namespace in rendered manifests

NS=""

# Source 1: kustomization.yaml
KUSTO="$MANIFESTS_DIR/kustomization.yaml"
if [ -f "$KUSTO" ]; then
  KUSTO_NS=$(grep -E '^\s*namespace:' "$KUSTO" | head -1 | awk '{print $2}' || echo "")
  if [ -n "$KUSTO_NS" ]; then
    NS="$KUSTO_NS"
  fi
fi

# Source 2: namespace.yaml metadata.name
NS_FILE="$MANIFESTS_DIR/namespace.yaml"
if [ -z "$NS" ] && [ -f "$NS_FILE" ]; then
  # Find metadata.name after "metadata:" block
  NS_YAML=$(awk '/^kind: Namespace/{found=1} found && /^  name:/{print; exit}' "$NS_FILE" 2>/dev/null || echo "")
  if [ -n "$NS_YAML" ]; then
    NS=$(echo "$NS_YAML" | awk '{print $2}')
  fi
fi

# Source 3: Deployment metadata.namespace
if [ -z "$NS" ]; then
  for f in $DEPLOYMENT_FILES; do
    DEPLOY_NS=$(grep -m1 'namespace:' "$f" 2>/dev/null | awk '{print $2}' || echo "")
    if [ -n "$DEPLOY_NS" ]; then
      NS="$DEPLOY_NS"
      break
    fi
  done
fi

if [ -n "$NS" ]; then
  if echo "$NS" | grep -qE "^default$|^kube-system$|^kube-public$|^kube-node-lease$"; then
    fail "Production using system namespace: $NS"
  else
    pass "Namespace: $NS"
  fi
else
  warn "No namespace found (may be specified by ArgoCD Application destination)"
fi

# ── 9. Ingress TLS ──────────────────────────────────────────────────────
echo ""
echo "=== 9. Ingress TLS ==="

INGRESS_FILE="$MANIFESTS_DIR/ingress.yaml"
if [ -f "$INGRESS_FILE" ]; then
  if grep -q "tls:" "$INGRESS_FILE"; then
    pass "Ingress TLS configured"
  else
    warn "Ingress missing TLS configuration"
  fi

  if grep -qE 'example\.com|localhost' "$INGRESS_FILE"; then
    warn "Ingress using placeholder hostname"
  else
    pass "Ingress hostname appears real"
  fi
else
  warn "No ingress.yaml found"
fi

# ── Summary ──────────────────────────────────────────────────────────────
echo ""
echo "=== Summary ==="
echo "  PASS:     $PASS_COUNT"
echo "  FAIL:     $FAIL_COUNT"
echo "  WARN:     $WARN_COUNT"

if [ $FAIL_COUNT -gt 0 ]; then
  echo ""
  echo "❌ $FAIL_COUNT critical issues found. Fix before deploying to production."
  exit 1
else
  echo ""
  echo "✅ All critical checks passed."
  if [ $WARN_COUNT -gt 0 ]; then
    echo "⚠️  $WARN_COUNT warnings. Review before deploying."
  fi
  exit 0
fi
