# Message Queue Placeholder Module
#
# This module documents the message queue infrastructure required by the Media Platform.
# It serves as a placeholder for SQS, RabbitMQ, Kafka, or other messaging solutions.
#
# For production, replace with actual provider resources:
#   - AWS: aws_sqs_queue
#   - RabbitMQ: rabbitmq_queue (community provider)
#   - Kafka: kafka_topic (community provider)
#   - GCP: google_pubsub_topic

terraform {
  required_version = ">= 1.0"
}

# -------------------------------------------------------------------
# Placeholder: null_resource for documentation
# -------------------------------------------------------------------
# This null_resource documents the expected infrastructure without creating real resources.
# Replace with actual provider resources for production use.

resource "null_resource" "queue_documentation" {
  triggers = {
    queue_name  = var.queue_name
    environment = var.environment
  }

  provisioner "local-exec" {
    command = "echo 'Message queue ${var.queue_name} (${var.environment}) would be created'"
  }
}

# -------------------------------------------------------------------
# Example: AWS SQS (uncomment for production)
# -------------------------------------------------------------------
# resource "aws_sqs_queue" "queue" {
#   name = "${var.queue_name}-${var.environment}"
#
#   visibility_timeout_seconds = 30
#   message_retention_seconds  = 345600 # 4 days
#   receive_wait_time_seconds  = 10
#
#   tags = {
#     Environment = var.environment
#     ManagedBy   = "opentofu"
#   }
# }
