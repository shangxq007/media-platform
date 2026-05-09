# Infrastructure as Code (IaC)

## Purpose

This directory contains **non-production, validate-only** Infrastructure-as-Code scaffolding for local development infrastructure. It is intended to:

- Document the infrastructure topology required by the Media Platform
- Provide a starting point for real cloud deployments
- Enable validation (`fmt`, `plan`, `validate`) without creating real resources

**This IaC is NOT intended for production use.**

## Prerequisites

- [OpenTofu](https://opentofu.org/) (preferred) — open-source fork of Terraform
- [Terraform](https://www.terraform.io/) (compatible) — if you prefer the original

### Installation

```bash
# OpenTofu (recommended)
brew install opentofu          # macOS
# or see https://opentofu.org/docs/intro/install/

# Terraform (alternative)
brew install terraform         # macOS
# or see https://developer.hashicorp.com/terraform/install
```

## Structure

```
infra/
  README.md                     # This file
  opentofu/
    environments/
      local/                    # Local development environment
        main.tf                 # Module composition
        variables.tf            # Input variables
        outputs.tf              # Output values
        terraform.tfvars.example # Example variable values (no secrets)
    modules/
      postgres/                 # PostgreSQL database
      object-storage-placeholder/ # S3/MinIO/GCS bucket placeholder
      queue-placeholder/        # SQS/RabbitMQ/Kafka placeholder
```

## Safety Rules

1. **No real credentials** — Never commit real passwords, API keys, or tokens
2. **No production defaults** — All defaults are for local development only
3. **No unattended apply/destroy** — Always review `plan` output before `apply`
4. **No remote state** — Local backend only for this scaffolding
5. **Validation only** — These configs are for `fmt`/`validate`/`plan`, not for creating real infrastructure

## Command Examples

```bash
cd infra/opentofu/environments/local

# Format check (CI-friendly)
tofu fmt -check -recursive ../../..

# Format fix
tofu fmt -recursive ../../..

# Initialize (no remote backend)
tofu init -backend=false

# Validate configuration
tofu validate

# Preview changes (requires providers)
tofu plan
```

## Terraform Compatibility

OpenTofu is a community-driven fork of Terraform. All commands are compatible:

| OpenTofu Command | Terraform Equivalent |
|-----------------|---------------------|
| `tofu fmt` | `terraform fmt` |
| `tofu init` | `terraform init` |
| `tofu validate` | `terraform validate` |
| `tofu plan` | `terraform plan` |
| `tofu apply` | `terraform apply` |

If using Terraform instead of OpenTofu, simply replace `tofu` with `terraform` in all commands.

### Provider Compatibility

- OpenTofu supports Terraform providers
- Provider version constraints in `required_providers` blocks are compatible
- Some enterprise Terraform features (e.g., Terraform Cloud) are not available in OpenTofu

## Module Documentation

### postgres

Manages a PostgreSQL database instance. For local development, the `docker-compose.yml` at the project root already provides a Postgres container. This module serves as documentation and a starting point for cloud deployment.

**Variables:** `db_name`, `db_username`, `db_password`, `port`, `environment`
**Outputs:** `connection_string`, `host`, `port`

### object-storage-placeholder

Placeholder for object storage (S3, MinIO, GCS). Documents the required infrastructure without creating real resources.

**Variables:** `bucket_name`, `region`, `environment`
**Outputs:** `bucket_arn`, `bucket_name`

### queue-placeholder

Placeholder for message queues (SQS, RabbitMQ, Kafka). Documents the required infrastructure without creating real resources.

**Variables:** `queue_name`, `environment`
**Outputs:** `queue_arn`, `queue_url`

## Next Steps for Production

1. Add a real provider configuration (AWS, GCP, Azure)
2. Configure remote state backend (S3, GCS, Azure Blob)
3. Add environment-specific configurations (staging, production)
4. Implement proper secret management
5. Add CI/CD pipeline integration
