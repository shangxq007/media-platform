# Message Queue Placeholder Module Variables

variable "queue_name" {
  description = "Name of the message queue"
  type        = string
  default     = "media-platform-events"
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
