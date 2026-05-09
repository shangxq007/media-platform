# Message Queue Placeholder Module Outputs

output "queue_arn" {
  description = "ARN of the message queue"
  value       = "arn:aws:sqs:us-east-1:000000000000:${var.queue_name}-${var.environment}"
}

output "queue_url" {
  description = "URL of the message queue"
  value       = "https://sqs.us-east-1.amazonaws.com/000000000000/${var.queue_name}-${var.environment}"
}

output "queue_name" {
  description = "Name of the message queue"
  value       = "${var.queue_name}-${var.environment}"
}
