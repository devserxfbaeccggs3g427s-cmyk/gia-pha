terraform {
  required_version = ">= 1.9.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
}

variable "service_name" { type = string }
variable "environment"  { type = string }

resource "aws_kms_key" "service" {
  description         = "familya/${var.environment}/${var.service_name}"
  enable_key_rotation = true
  multi_region        = false
}

resource "aws_kms_alias" "service" {
  name          = "alias/familya-${var.environment}-${var.service_name}"
  target_key_id = aws_kms_key.service.key_id
}

resource "aws_secretsmanager_secret" "service" {
  name       = "familya/${var.environment}/${var.service_name}/db"
  kms_key_id = aws_kms_key.service.arn
}

output "kms_key_arn" { value = aws_kms_key.service.arn }
output "secret_arn"  { value = aws_secretsmanager_secret.service.arn }
