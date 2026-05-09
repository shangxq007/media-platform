#!/bin/bash
set -euo pipefail

# Infrastructure-as-Code Validation Script
#
# Validates OpenTofu configuration without creating real resources.
# Requires OpenTofu (or Terraform) to be installed.
#
# Usage: bash scripts/infra-validate.sh

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OPENTOFU_DIR="${SCRIPT_DIR}/../infra/opentofu/environments/local"

# Check for tofu/terraform
if command -v tofu &> /dev/null; then
    TF_CMD="tofu"
elif command -v terraform &> /dev/null; then
    TF_CMD="terraform"
else
    echo "ERROR: Neither OpenTofu nor Terraform found in PATH."
    echo "Install OpenTofu: https://opentofu.org/docs/intro/install/"
    echo "Install Terraform: https://developer.hashicorp.com/terraform/install"
    exit 1
fi

echo "Using: ${TF_CMD}"
cd "${OPENTOFU_DIR}"

echo ""
echo "=== OpenTofu fmt check ==="
${TF_CMD} fmt -check -recursive ../../..

echo ""
echo "=== OpenTofu init (backend=false) ==="
${TF_CMD} init -backend=false

echo ""
echo "=== OpenTofu validate ==="
${TF_CMD} validate

echo ""
echo "=== All IaC validation passed ==="
