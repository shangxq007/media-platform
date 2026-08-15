#!/usr/bin/env bash
# Pinned oasdiff binary (v1.28.0) for the API contract governance gate.
# Run from repo root: bash scripts/tools/download-oasdiff.sh
set -eu
DIR="$(cd "$(dirname "$0")" && pwd)"
URL="https://github.com/oasdiff/oasdiff/releases/download/v1.28.0/oasdiff_1.28.0_linux_amd64.tar.gz"
TMP="$(mktemp -d)"
curl -sL --max-time 120 -o "$TMP/oasdiff.tgz" "$URL"
tar -xzf "$TMP/oasdiff.tgz" -C "$TMP" oasdiff
chmod +x "$TMP/oasdiff"
cp "$TMP/oasdiff" "$DIR/oasdiff"
rm -rf "$TMP"
echo "oasdiff v1.28.0 installed at $DIR/oasdiff"
