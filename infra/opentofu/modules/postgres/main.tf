# PostgreSQL Database Module
#
# This module documents the PostgreSQL infrastructure required by the Media Platform.
# For local development, use the docker-compose.yml at the project root.
#
# For production, uncomment the docker provider section or add your cloud provider
# (e.g., AWS RDS, GCP Cloud SQL, Azure Database for PostgreSQL).

terraform {
  required_version = ">= 1.0"

  # Uncomment for docker provider:
  # required_providers {
  #   docker = {
  #     source  = "kreuzwerker/docker"
  #     version = "~> 3.0"
  #   }
  # }
}

# -------------------------------------------------------------------
# Option A: Docker provider (uncomment to use for local containers)
# -------------------------------------------------------------------
# resource "docker_image" "postgres" {
#   name         = "postgres:16-alpine"
#   keep_locally = true
# }
#
# resource "docker_container" "postgres" {
#   name  = "postgres-${var.environment}"
#   image = docker_image.postgres.image_id
#
#   env = [
#     "POSTGRES_DB=${var.db_name}",
#     "POSTGRES_USER=${var.db_username}",
#     "POSTGRES_PASSWORD=${var.db_password}",
#   ]
#
#   ports {
#     internal = 5432
#     external = var.port
#   }
#
#   volumes {
#     host_path      = abspath("${path.root}/.pgdata")
#     container_path = "/var/lib/postgresql/data"
#   }
#
#   restart = "unless-stopped"
#
#   healthcheck {
#     test         = ["CMD-SHELL", "pg_isready -U ${var.db_username} -d ${var.db_name}"]
#     interval     = "5s"
#     timeout      = "5s"
#     retries      = 10
#     start_period = "10s"
#   }
# }

# -------------------------------------------------------------------
# Option B: Cloud provider (example for AWS RDS)
# -------------------------------------------------------------------
# Uncomment and configure for production use:
#
# resource "aws_db_instance" "postgres" {
#   identifier     = "${var.db_name}-${var.environment}"
#   engine         = "postgres"
#   engine_version = "16"
#   instance_class = "db.t3.micro"
#
#   db_name  = var.db_name
#   username = var.db_username
#   password = var.db_password
#
#   port = var.port
#
#   skip_final_snapshot = var.environment != "prod"
#   publicly_accessible = var.environment == "local"
#
#   tags = {
#     Environment = var.environment
#     ManagedBy   = "opentofu"
#   }
# }

# -------------------------------------------------------------------
# Placeholder: null_resource for documentation
# -------------------------------------------------------------------
# This null_resource documents the expected infrastructure without creating real resources.
# Replace with actual provider resources for production use.

resource "null_resource" "postgres_documentation" {
  triggers = {
    db_name     = var.db_name
    db_username = var.db_username
    port        = var.port
    environment = var.environment
  }

  provisioner "local-exec" {
    command = "echo 'PostgreSQL ${var.db_name} (${var.environment}) would be created on port ${var.port}'"
  }
}
