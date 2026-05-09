# Local Environment Outputs

# PostgreSQL
output "postgres_connection_string" {
  description = "PostgreSQL connection string"
  value       = module.postgres.connection_string
  sensitive   = true
}

output "postgres_host" {
  description = "PostgreSQL host"
  value       = module.postgres.host
}

output "postgres_port" {
  description = "PostgreSQL port"
  value       = module.postgres.port
}

# Object Storage
output "storage_bucket_arn" {
  description = "Object storage bucket ARN"
  value       = module.object_storage.bucket_arn
}

output "storage_bucket_name" {
  description = "Object storage bucket name"
  value       = module.object_storage.bucket_name
}

# Message Queue
output "queue_arn" {
  description = "Message queue ARN"
  value       = module.queue.queue_arn
}

output "queue_url" {
  description = "Message queue URL"
  value       = module.queue.queue_url
}
