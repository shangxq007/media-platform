#!/usr/bin/env bash
# Provision the pinned oasdiff binary used by scripts/check-api-contract-governance.sh.
#
#   bash scripts/tools/download-oasdiff.sh [destination]
#
# The pin (version, platform, URL, tarball digest, extracted-binary digest) lives in
# scripts/tools/oasdiff.sha256. This script verifies the downloaded tarball AND the extracted
# binary against that pin before installing anything, and checks the installed binary's
# --version afterwards. The binary is normally VENDORED at scripts/tools/oasdiff; this script
# exists to re-create it (fresh copy, deliberate pin bump) or to install it elsewhere on
# linux/amd64. For a pre-provisioned binary on another platform, point the gate at it with
# OASDIFF_BIN=/path/to/oasdiff instead (the gate still enforces the pinned --version).
set -eu
DIR="$(cd "$(dirname "$0")" && pwd)"
PIN="$DIR/oasdiff.sha256"

pin() { sed -n "s/^$1:[[:space:]]*//p" "$PIN" | head -1; }
PIN_VERSION="$(pin version)"
PIN_PLATFORM="$(pin platform)"
PIN_URL="$(pin url)"
PIN_TARBALL_SHA="$(pin sha256_tarball)"
PIN_BINARY_SHA="$(pin sha256_binary)"

if [ -z "$PIN_VERSION" ] || [ -z "$PIN_URL" ] || [ -z "$PIN_BINARY_SHA" ]; then
  echo "ERROR: incomplete pin file: $PIN" >&2
  exit 2
fi

# Normalise the machine name: `uname -m` reports x86_64 where oasdiff releases use amd64.
host_os="$(uname -s | tr '[:upper:]' '[:lower:]')"
machine="$(uname -m)"
case "$host_os-$machine" in
  linux-x86_64|linux-amd64) host="linux-amd64" ;;
  *) host="$host_os-$machine" ;;
esac
if [ "$host" != "$PIN_PLATFORM" ]; then
  echo "ERROR: pinned oasdiff is $PIN_PLATFORM, this host is $host." >&2
  echo "       Use a pre-provisioned binary via OASDIFF_BIN=/path/to/oasdiff (version $PIN_VERSION)." >&2
  exit 2
fi

DEST="${1:-$DIR/oasdiff}"
TMP="$(mktemp -d)"
cleanup() { rm -rf "${TMP:?}"; }
trap cleanup EXIT

echo "downloading $PIN_URL"
curl -fsSL --max-time 300 -o "$TMP/oasdiff.tgz" "$PIN_URL"

actual_tarball_sha="$(sha256sum "$TMP/oasdiff.tgz" | awk '{print $1}')"
if [ "$actual_tarball_sha" != "$PIN_TARBALL_SHA" ]; then
  echo "ERROR: tarball sha256 mismatch: got $actual_tarball_sha, pinned $PIN_TARBALL_SHA" >&2
  exit 1
fi
echo "   tarball sha256 verified ($actual_tarball_sha)"

tar -xzf "$TMP/oasdiff.tgz" -C "$TMP" oasdiff
chmod +x "$TMP/oasdiff"

actual_binary_sha="$(sha256sum "$TMP/oasdiff" | awk '{print $1}')"
if [ "$actual_binary_sha" != "$PIN_BINARY_SHA" ]; then
  echo "ERROR: extracted binary sha256 mismatch: got $actual_binary_sha, pinned $PIN_BINARY_SHA" >&2
  exit 1
fi
echo "   binary sha256 verified ($actual_binary_sha)"

installed_version="$("$TMP/oasdiff" --version | awk '{print $NF}')"
if [ "$installed_version" != "$PIN_VERSION" ]; then
  echo "ERROR: extracted binary reports '$installed_version', pinned '$PIN_VERSION'" >&2
  exit 1
fi
echo "   version verified ($installed_version)"

install -m 0755 "$TMP/oasdiff" "$DEST"
echo "oasdiff v$PIN_VERSION installed at $DEST"
