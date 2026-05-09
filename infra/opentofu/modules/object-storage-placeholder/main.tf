# Object Storage Placeholder Module
#
# This module documents the object storage infrastructure required by the Media Platform.
# It serves as a placeholder for S3, MinIO, GCS, or other object storage solutions.
#
# For production, replace with actual provider resources:
#   - AWS: aws_s3_bucket
#   - GCP: google_storage_bucket
#   - Azure: azurerm_storage_container
#   - MinIO: minio_s3_bucket (community provider)

terraform {
  required_version = ">= 1.0"
}

# -------------------------------------------------------------------
# Placeholder: null_resource for documentation
# -------------------------------------------------------------------
# This null_resource documents the expected infrastructure without creating real resources.
# Replace with actual provider resources for production use.

resource "null_resource" "object_storage_documentation" {
  triggers = {
    bucket_name = var.bucket_name
    region      = var.region
    environment = var.environment
  }

  provisioner "local-exec" {
    command = "echo 'Object storage bucket ${var.bucket_name} (${var.environment}) would be created in ${var.region}'"
  }
}

# -------------------------------------------------------------------
# Example: AWS S3 (uncomment for production)
# -------------------------------------------------------------------
# resource "aws_s3_bucket" "storage" {
#   bucket = "${var.bucket_name}-${var.environment}"
#
#   tags = {
#     Environment = var.environment
#     ManagedBy   = "opentofu"
#   }
# }
#
# resource "aws_s3_bucket_versioning" "storage" {
#   bucket = aws_s3_bucket.storage.id
#   versioning_configuration {
#     status = var.environment == "prod" ? "Enabled" : "Disabled"
#   }
# }
#
# resource "aws_s3_bucket_server_side_encryption_configuration" "storage" {
#   bucket = aws_s3_bucket.storage.id
#   rule {
#     apply_server_side_encryption_by_default {
#       sse_algorithm = "AES256"
#     }
#   }
# }
