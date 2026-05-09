# Object Storage Placeholder Module Outputs

output "bucket_arn" {
  description = "ARN of the object storage bucket"
  value       = "arn:aws:s3:::${var.bucket_name}-${var.environment}"
}

output "bucket_name" {
  description = "Name of the object storage bucket"
  value       = "${var.bucket_name}-${var.environment}"
}

output "region" {
  description = "Region of the object storage bucket"
  value       = var.region
}
