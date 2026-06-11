#!/usr/bin/env bash
set -uo pipefail

FRONTEND_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$FRONTEND_DIR/src"
PKG_JSON="$FRONTEND_DIR/package.json"
VITE_CONFIG="$FRONTEND_DIR/vite.config.ts"
TSCONFIG="$FRONTEND_DIR/tsconfig.json"
ESLINT_CONFIG="$FRONTEND_DIR/eslint.config.js"

ERRORS=0

echo "=== React-first frontend guard ==="

# 1. No .vue files
VUE_FILES=$(find "$SRC_DIR" -name "*.vue" 2>/dev/null | wc -l | tr -d ' ')
if [ "$VUE_FILES" -ne 0 ]; then
  echo "FAIL: Found $VUE_FILES .vue files in src/"
  find "$SRC_DIR" -name "*.vue" | head -5
  ERRORS=$((ERRORS + 1))
else
  echo "PASS: No .vue files in src/"
fi

# 2. No Vue dependencies in package.json
if grep -qE '"vue"|"@vitejs/plugin-vue"|"vue-router"|"pinia"|"@vue/|"vue-tsc"|"eslint-plugin-vue"|"@vue/test-utils"' "$PKG_JSON"; then
  echo "FAIL: package.json contains Vue dependencies"
  grep -nE '"vue"|"@vitejs/plugin-vue"|"vue-router"|"pinia"|"@vue/|"vue-tsc"|"eslint-plugin-vue"|"@vue/test-utils"' "$PKG_JSON"
  ERRORS=$((ERRORS + 1))
else
  echo "PASS: No Vue dependencies in package.json"
fi

# 3. No Vue plugin in vite.config.ts
if grep -q '@vitejs/plugin-vue' "$VITE_CONFIG" 2>/dev/null; then
  echo "FAIL: vite.config.ts uses Vue plugin"
  ERRORS=$((ERRORS + 1))
else
  echo "PASS: vite.config.ts does not use Vue plugin"
fi

# 4. No createApp from vue in source
if grep -rq "from 'vue'" "$SRC_DIR" --include="*.ts" --include="*.tsx" 2>/dev/null; then
  echo "FAIL: Source contains imports from 'vue'"
  grep -rn "from 'vue'" "$SRC_DIR" --include="*.ts" --include="*.tsx" | head -3
  ERRORS=$((ERRORS + 1))
else
  echo "PASS: No 'vue' imports in source"
fi

# 5. No vue-router / pinia references in source
if grep -rl "vue-router\|pinia\|@vue/" "$SRC_DIR" --include="*.ts" --include="*.tsx" 2>/dev/null; then
  echo "FAIL: Source contains vue-router/pinia references"
  grep -rl "vue-router\|pinia\|@vue/" "$SRC_DIR" --include="*.ts" --include="*.tsx" | head -3
  ERRORS=$((ERRORS + 1))
else
  echo "PASS: No vue-router/pinia references in source"
fi

# 6. No Vue-specific tsconfig references
if grep -q '\.vue' "$TSCONFIG" 2>/dev/null; then
  echo "FAIL: tsconfig.json references .vue files"
  ERRORS=$((ERRORS + 1))
else
  echo "PASS: tsconfig.json does not reference .vue files"
fi

# 7. No eslint-plugin-vue
if grep -q 'eslint-plugin-vue' "$ESLINT_CONFIG" 2>/dev/null; then
  echo "FAIL: eslint.config.js uses eslint-plugin-vue"
  ERRORS=$((ERRORS + 1))
else
  echo "PASS: eslint.config.js does not use eslint-plugin-vue"
fi

echo ""
if [ "$ERRORS" -eq 0 ]; then
  echo "ALL CHECKS PASSED"
  exit 0
else
  echo "FAILED: $ERRORS check(s) failed"
  exit 1
fi
