#!/bin/sh
# check-local-guardrails.sh — Lightweight local guardrails
# POSIX shell, no dependencies, safe for agents
# Usage: scripts/guards/check-local-guardrails.sh [files...]

set -e

FAIL=0
WARN=0

log_fail() { echo "FAIL: $1"; FAIL=$((FAIL + 1)); }
log_warn() { echo "WARN: $1"; WARN=$((WARN + 1)); }

# Check staged files if none provided
if [ $# -eq 0 ]; then
  set -- $(git diff --cached --name-only --diff-filter=ACMR 2>/dev/null)
fi

if [ $# -eq 0 ]; then
  echo "No files to check."
  exit 0
fi

for f in "$@"; do
  # Forbidden files
  case "$f" in
    */AGENT_TASK.md|AGENT_TASK.md)
      log_fail "AGENT_TASK.md must not be committed: $f"
      continue
      ;;
    */ARCHITECTURE_MAP_LINKS.md|ARCHITECTURE_MAP_LINKS.md)
      log_fail "ARCHITECTURE_MAP_LINKS.md must not be committed: $f"
      continue
      ;;
    */.env|.env)
      log_fail ".env must not be committed: $f"
      continue
      ;;
    */.env.*|.env.*)
      log_fail ".env.* must not be committed: $f"
      continue
      ;;
  esac

  # Flyway V1 baseline protection
  case "$f" in
    */flyway/sql/V1*)
      log_fail "Flyway V1 baseline must not be modified: $f"
      continue
      ;;
  esac

  # Skip binary files
  if [ -f "$f" ] && ! file "$f" 2>/dev/null | grep -q "text"; then
    continue
  fi

  # Secret patterns (skip test files)
  if [ -f "$f" ]; then
    case "$f" in
      *Test*|*test*|*.md) ;;
      *)
        if grep -l "AKIA[0-9A-Z]\{16\}" "$f" 2>/dev/null; then
          log_fail "AWS access key pattern in: $f"
        fi
        if grep -l "-----BEGIN.*PRIVATE KEY-----" "$f" 2>/dev/null; then
          log_fail "Private key in: $f"
        fi
        ;;
    esac
  fi

  # Forbidden wording — WARN only (context-dependent, CI/Semgrep does stronger checks)
  if [ -f "$f" ]; then
    case "$f" in
      *.md|*.txt|*semgrep*|*Semgrep*) ;;
      *)
        if grep -l "OpenCue Provider\|Remotion production\|Spring AI active\|Artifact DAG runtime" "$f" 2>/dev/null; then
          log_warn "Forbidden wording pattern in: $f (review context)"
        fi
        ;;
    esac
  fi
done

echo ""
echo "Guardrails: $FAIL failures, $WARN warnings"

if [ $FAIL -gt 0 ]; then
  echo "RESULT: BLOCKED"
  exit 1
else
  echo "RESULT: PASS"
  exit 0
fi
