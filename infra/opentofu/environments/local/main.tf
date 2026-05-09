# Local Development Environment
#
# This configuration composes all infrastructure modules for local development.
# It uses null_resource placeholders — no real cloud resources are created.
#
# Usage:
#   tofu init -backend=false
#   tofu validate
#   tofu plan

terraform {
  required_version = ">= 1.0"

  # No provider requirements — using null_resource only
  # Add cloud providers here for production use
}

# -------------------------------------------------------------------
# Local Development Variables
# -------------------------------------------------------------------

locals {
  environment = "local"
  project     = "media-platform"
}

# -------------------------------------------------------------------
# PostgreSQL Database
# -------------------------------------------------------------------

module "postgres" {
  source = "../../modules/postgres"

  db_name     = "platform"
  db_username = "platform"
  db_password = "changeme" # Override via terraform.tfvars
  port        = 5432
  environment = local.environment
}

# -------------------------------------------------------------------
# Object Storage (Placeholder)
# -------------------------------------------------------------------

module "object_storage" {
  source = "../../modules/object-storage-placeholder"

  bucket_name = "media-platform-storage"
  region      = "us-east-1"
  environment = local.environment
}

# -------------------------------------------------------------------
# Message Queue (Placeholder)
# -------------------------------------------------------------------

module "queue" {
  source = "../../modules/queue-placeholder"

  queue_name  = "media-platform-events"
  environment = local.environment
}
