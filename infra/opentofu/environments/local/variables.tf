# Local Environment Variables
#
# Override these in terraform.tfvars (do not commit real values)

variable "environment" {
  description = "Environment name"
  type        = string
  default     = "local"
}

variable "project_name" {
  description = "Project name for resource naming"
  type        = string
  default     = "media-platform"
}
