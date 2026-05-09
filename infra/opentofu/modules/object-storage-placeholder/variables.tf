# Object Storage Placeholder Module Variables

variable "bucket_name" {
  description = "Name of the object storage bucket"
  type        = string
  default     = "media-platform-storage"
}

variable "region" {
  description = "Region for the object storage bucket"
  type        = string
  default     = "us-east-1"
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
