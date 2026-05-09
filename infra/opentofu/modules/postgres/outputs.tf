# PostgreSQL Module Outputs

output "connection_string" {
  description = "PostgreSQL connection string"
  value       = "postgresql://${var.db_username}:${var.db_password}@localhost:${var.port}/${var.db_name}"
  sensitive   = true
}

output "host" {
  description = "PostgreSQL host"
  value       = "localhost"
}

output "port" {
  description = "PostgreSQL port"
  value       = var.port
}

output "database_name" {
  description = "PostgreSQL database name"
  value       = var.db_name
}

output "username" {
  description = "PostgreSQL username"
  value       = var.db_username
}
