# PostgreSQL Module Variables

variable "db_name" {
  description = "Name of the PostgreSQL database"
  type        = string
  default     = "platform"
}

variable "db_username" {
  description = "Username for the PostgreSQL database"
  type        = string
  default     = "platform"
}

variable "db_password" {
  description = "Password for the PostgreSQL database"
  type        = string
  default     = "changeme"
  sensitive   = true
}

variable "port" {
  description = "Port for the PostgreSQL database"
  type        = number
  default     = 5432
}

variable "environment" {
  description = "Environment name (e.g., local, staging, prod)"
  type        = string
  default     = "local"

  validation {
    condition     = contains(["local", "dev", "staging", "prod"], var.environment)
    error_message = "Environment must be one of: local, dev, staging, prod."
  }
}
