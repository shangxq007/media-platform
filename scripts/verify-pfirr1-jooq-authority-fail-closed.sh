#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

FILES=(
  "typed-schema-module/jooq-baseline.properties"
  "typed-schema-module/jooq-plain-sql-allowlist.txt"
  "typed-schema-module/jooq-dynamic-identifier-allowlist.txt"
)
TASKS=(
  "verifyJooqNoNewUntypedIdentifiers"
  "verifyJooqPlainSqlAllowlist"
  "verifyJooqDynamicIdentifierAllowlist"
)

TMP_DIR="$(mktemp -d)"

restore_all() {
  for file in "${FILES[@]}"; do
    backup="$TMP_DIR/$(basename "$file")"
    if [[ -f "$backup" ]]; then
      cp "$backup" "$file"
    fi
  done
  rm -rf "$TMP_DIR"
}
trap restore_all EXIT

for file in "${FILES[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "FAIL: expected committed authority file is missing before negative proof: $file" >&2
    exit 1
  fi
  cp "$file" "$TMP_DIR/$(basename "$file")"
done

for i in "${!FILES[@]}"; do
  file="${FILES[$i]}"
  task="${TASKS[$i]}"
  log="$TMP_DIR/case-$i.log"

  rm "$file"

  set +e
  ./gradlew --no-daemon "$task" >"$log" 2>&1
  status=$?
  set -e

  if [[ $status -eq 0 ]]; then
    cat "$log" >&2
    echo "FAIL: $task passed with missing verification authority: $file" >&2
    exit 1
  fi

  if [[ -e "$file" ]]; then
    cat "$log" >&2
    echo "FAIL: $task recreated verification authority: $file" >&2
    exit 1
  fi

  if ! grep -q "Verification authority must be pre-existing and version-controlled" "$log"; then
    cat "$log" >&2
    echo "FAIL: $task failed for an unexpected reason; fail-closed authority message not observed" >&2
    exit 1
  fi

  cp "$TMP_DIR/$(basename "$file")" "$file"
  echo "PASS: $task rejects missing authority and does not recreate $file"
done

git diff --exit-code -- "${FILES[@]}"
echo "OK: PFIRR1-B1 jOOQ authority verification is fail-closed and non-mutating"
